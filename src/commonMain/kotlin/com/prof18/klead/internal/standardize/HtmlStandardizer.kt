package com.prof18.klead.internal.standardize

import com.fleeksoft.ksoup.nodes.Element
import com.fleeksoft.ksoup.nodes.TextNode
import com.prof18.klead.internal.dom.ChildListBatch
import com.prof18.klead.internal.dom.limitDepth

// Orchestrates the standardization passes that run after content detection and clutter removal.
// The pass order matters: callouts and embeds are rewritten before headings so title matching
// sees final markup, and empty-wrapper cleanup runs last to sweep what the other passes left.
internal object HtmlStandardizer {
    fun apply(content: Element, title: String?, checkCancelled: () -> Unit = {}) {
        val steps: List<() -> Unit> = listOf(
            // The normalizers and renderers recurse per level, and footnote merges or extractor
            // fragments can nest content again after the document-level cap.
            { content.limitDepth() },
            { HtmlEmbedNormalizer.normalizeEmbeds(content) },
            { HtmlCalloutNormalizer.normalizeCallouts(content) },
            // Each callout gains a content wrapper, so nested callouts can double the depth after the cap.
            { content.limitDepth() },
            { HtmlTitleNormalizer.normalizeHeadings(content, title) },
            { HtmlArxivNormalizer.normalize(content) },
            { HtmlChromeNormalizer.removeMetadataChrome(content) },
            { HtmlCodeNormalizer.normalizeCodeBlocks(content) },
            { HtmlImageNormalizer.normalizeImages(content) },
            { HtmlImageNormalizer.normalizeGalleryImageLists(content) },
            { HtmlImageNormalizer.normalizeImageAspectPlaceholders(content) },
            { HtmlFootnoteNormalizer.normalizeFootnotes(content, checkCancelled) },
            // Footnote lists are wrapped again (section, list, item), which can also pass the cap.
            { content.limitDepth() },
            { HtmlChromeNormalizer.removeTrailingSectionHeadings(content) },
            { convertSeparatorSvgsToRules(content) },
            { HtmlChromeNormalizer.removeEmptyDocumentTail(content) },
            { normalizeTables(content) },
            { separateLabelValueSpans(content) },
            { removeEmptySvgElements(content) },
            { removeEmptyWrappers(content) },
        )
        for (step in steps) {
            checkCancelled()
            step()
        }
    }

    private fun normalizeTables(content: Element) {
        // Replacements are batched per parent (one insert and removal per table reindexed its siblings
        // every time). Innermost tables go first, so a cell's own table edits are flushed before the cell's
        // content moves out; each table's verdict depends only on its own rows, so the order is free.
        val batch = ChildListBatch()
        content.select("table").toList().asReversed().forEach { table ->
            val cells = table.directTableCells()
            if (!table.hasClass("layout") && cells.size != 1) return@forEach
            val replacement = cells.flatMap { cell ->
                batch.flush(cell)
                val nodes = cell.childNodes().toList()
                cell.empty()
                if (cells.size == 1) nodes else listOf(Element("div").appendChildren(nodes))
            }
            batch.replace(table, replacement)
        }
        batch.flushAll()
    }

    private fun Element.directTableCells(): List<Element> {
        val rows = children().flatMap { child ->
            when (child.normalName()) {
                "tr" -> listOf(child)
                "tbody", "thead", "tfoot" -> child.children().filter { it.normalName() == "tr" }
                else -> emptyList()
            }
        }
        return rows.flatMap { row ->
            row.children().filter { it.normalName() == "td" || it.normalName() == "th" }
        }
    }

    // An inline SVG with role="separator" is a drawn section break (a dinkus); as an image it renders at full
    // width, so it becomes the rule it stands for.
    private fun convertSeparatorSvgsToRules(content: Element) {
        content.select("svg[role=separator]").forEach { svg ->
            if (!svg.isInsidePreformattedCode() && svg.text().isBlank()) svg.replaceWith(Element("hr"))
        }
    }

    // Score and stat widgets lay out a label span and a value span side by side with CSS and no text between
    // them ("TMDB" "66%"), which would read as "TMDB66%". Only a widget repeated across sibling blocks with
    // the same classes qualifies, and only where a word label meets a number, so split words, units and
    // version strings stay intact.
    private fun separateLabelValueSpans(content: Element) {
        content.select("div, li")
            .filter { it.isLabelValueWidget() }
            .groupBy { it.parent() to it.widgetShape() }
            .values
            .filter { it.size >= 2 }
            .flatten()
            .forEach { block ->
                block.children().zipWithNext().forEach { (label, value) ->
                    if (label.nextSibling() == value && label.text().isWordLabelBefore(value.text())) {
                        label.after(TextNode(" "))
                    }
                }
            }
    }

    private fun Element.isLabelValueWidget(): Boolean = hasAttr("class") && !isInsidePreformattedCode() &&
        childrenSize() in 2..MAX_LABEL_VALUE_SPANS &&
        textNodes().all { it.isBlank() } &&
        children().all { it.isShortStyledLeafSpan() }

    private fun Element.widgetShape(): List<String> = listOf(className()) + children().map { it.className() }

    private fun String.isWordLabelBefore(next: String): Boolean {
        val label = trim()
        val end = label.last()
        return label.count { it.isLetter() } >= 2 && end.isLetter() && end.code < CJK_START &&
            next.trimStart().first().isDigit()
    }

    private fun Element.isShortStyledLeafSpan(): Boolean = normalName() == "span" && hasAttr("class") &&
        childrenSize() == 0 && text().isNotBlank() && text().length <= MAX_LABEL_VALUE_TEXT_LENGTH

    private fun removeEmptySvgElements(content: Element) {
        content.select("svg").toList().asReversed().forEach { svg ->
            if (!svg.isInsidePreformattedCode() && svg.children().isEmpty() && svg.text().isBlank()) {
                svg.remove()
            }
        }
    }

    private fun removeEmptyWrappers(content: Element) {
        // Edits are batched per parent: removing or unwrapping thousands of siblings one at a time reindexed
        // the parent each time. The reverse traversal reaches an element after all of its descendants, so
        // flushing an element's own child edits before judging it sees the same tree as editing in place.
        val batch = ChildListBatch()
        content.select("span, div, picture, figure").toList().asReversed().forEach { element ->
            batch.flush(element)
            if (element.isInsidePreformattedCode()) return@forEach
            if (element.hasInternalKleadAttribute()) return@forEach
            if (element.normalName() == "figure" && element.hasAccessibleFigureContent()) return@forEach
            if (element.isSourceOnlyPicture()) {
                // A source cannot render without an img fallback. Removing the picture lets
                // this same reverse traversal discard any empty parent layout wrappers.
                batch.remove(element)
            } else if (element.childrenSize() == 0 && element.isNonBreakingSpaceWrapper()) {
                batch.replace(element, TextNode(" "))
            } else if (element.childrenSize() == 0 && element.isWhitespaceSeparatorWrapper(batch)) {
                batch.replace(element, TextNode(element.wholeText()))
            } else if (element.childrenSize() == 0 && element.text().isBlank()) {
                batch.remove(element)
            } else if (element.tagName() == "span" && element.attributes().isEmpty()) {
                batch.unwrap(element)
            }
        }
        batch.flushAll()
        content.childNodes().filterIsInstance<TextNode>().forEach { text ->
            if (text.text().isBlank()) batch.remove(text)
        }
        batch.flushAll()
    }

    private fun Element.isSourceOnlyPicture(): Boolean = normalName() == "picture" &&
        text().isBlank() && children().all { it.normalName() == "source" }

    private fun Element.hasAccessibleFigureContent(): Boolean = attr("aria-label").isNotBlank() ||
        attr("aria-labelledby").isNotBlank() || attr("role").equals("img", ignoreCase = true)

    private fun Element.hasInternalKleadAttribute(): Boolean =
        attributes().asList().any { attribute -> attribute.key.startsWith("data-klead-") }

    private fun Element.isInsidePreformattedCode(): Boolean = normalName() == "code" ||
        normalName() == "pre" ||
        parents().any { it.normalName() == "code" || it.normalName() == "pre" }

    private fun Element.isNonBreakingSpaceWrapper(): Boolean =
        wholeText().isNotEmpty() && wholeText().all { it == '\u00A0' || it == '\u202F' }

    private fun Element.isWhitespaceSeparatorWrapper(batch: ChildListBatch): Boolean = normalName() == "span" &&
        wholeText().isNotEmpty() && wholeText().all { it == ' ' || it == '\t' } &&
        hasInlineNeighbor(before = true, batch) && hasInlineNeighbor(before = false, batch)

    // A sibling with a pending removal is already gone as far as this pass is concerned.
    private fun Element.hasInlineNeighbor(before: Boolean, batch: ChildListBatch): Boolean {
        var current = this
        while (true) {
            var sibling = if (before) current.previousSibling() else current.nextSibling()
            while (sibling != null && batch.isRemoved(sibling)) {
                sibling = if (before) sibling.previousSibling() else sibling.nextSibling()
            }
            if (sibling != null) return true
            current = current.parent()?.takeIf { it.normalName() == "span" } ?: return false
        }
    }

    private const val MAX_LABEL_VALUE_SPANS = 6
    private const val MAX_LABEL_VALUE_TEXT_LENGTH = 32

    // CJK scripts write a label and its number without a space (令和6年).
    private const val CJK_START = 0x2E80
}
