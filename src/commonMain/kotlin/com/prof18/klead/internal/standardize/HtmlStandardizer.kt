package com.prof18.klead.internal.standardize

import com.fleeksoft.ksoup.nodes.Element
import com.fleeksoft.ksoup.nodes.TextNode
import com.prof18.klead.internal.dom.replaceWithChildren

// Orchestrates the standardization passes that run after content detection and clutter removal.
// The pass order matters: callouts and embeds are rewritten before headings so title matching
// sees final markup, and empty-wrapper cleanup runs last to sweep what the other passes left.
internal object HtmlStandardizer {
    fun apply(content: Element, title: String?) {
        HtmlEmbedNormalizer.normalizeEmbeds(content)
        HtmlCalloutNormalizer.normalizeCallouts(content)
        HtmlTitleNormalizer.normalizeHeadings(content, title)
        HtmlArxivNormalizer.normalize(content)
        HtmlChromeNormalizer.removeMetadataChrome(content)
        HtmlCodeNormalizer.normalizeCodeBlocks(content)
        HtmlImageNormalizer.normalizeImages(content)
        HtmlImageNormalizer.normalizeGalleryImageLists(content)
        HtmlImageNormalizer.normalizeImageAspectPlaceholders(content)
        HtmlFootnoteNormalizer.normalizeFootnotes(content)
        HtmlChromeNormalizer.removeTrailingSectionHeadings(content)
        normalizeTables(content)
        removeEmptySvgElements(content)
        removeEmptyWrappers(content)
    }

    private fun normalizeTables(content: Element) {
        content.select("table").forEach { table ->
            val cells = table.directTableCells()
            if (!table.hasClass("layout") && cells.size != 1) return@forEach
            for (cell in cells) {
                val nodes = cell.childNodes().toList()
                if (cells.size == 1) {
                    nodes.forEach { table.before(it) }
                } else {
                    val block = Element("div")
                    nodes.forEach { block.appendChild(it) }
                    table.before(block)
                }
            }
            table.remove()
        }
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

    private fun removeEmptySvgElements(content: Element) {
        content.select("svg").toList().asReversed().forEach { svg ->
            if (!svg.isInsidePreformattedCode() && svg.children().isEmpty() && svg.text().isBlank()) {
                svg.remove()
            }
        }
    }

    private fun removeEmptyWrappers(content: Element) {
        content.select("span, div").toList().asReversed().forEach { element ->
            if (element.isInsidePreformattedCode()) return@forEach
            if (element.hasInternalKleadAttribute()) return@forEach
            if (element.children().isEmpty() && element.isNonBreakingSpaceWrapper()) {
                element.replaceWith(TextNode(" "))
            } else if (element.children().isEmpty() && element.isWhitespaceSeparatorWrapper()) {
                element.replaceWith(TextNode(element.wholeText()))
            } else if (element.children().isEmpty() && element.text().isBlank()) {
                element.remove()
            } else if (element.tagName() == "span" && element.attributes().isEmpty()) {
                element.replaceWithChildren()
            }
        }
        content.childNodes().filterIsInstance<TextNode>().forEach { text ->
            if (text.text().isBlank()) text.remove()
        }
    }

    private fun Element.hasInternalKleadAttribute(): Boolean =
        attributes().asList().any { attribute -> attribute.key.startsWith("data-klead-") }

    private fun Element.isInsidePreformattedCode(): Boolean = normalName() == "code" ||
        normalName() == "pre" ||
        parents().any { it.normalName() == "code" || it.normalName() == "pre" }

    private fun Element.isNonBreakingSpaceWrapper(): Boolean =
        wholeText().isNotEmpty() && wholeText().all { it == '\u00A0' || it == '\u202F' }

    private fun Element.isWhitespaceSeparatorWrapper(): Boolean = normalName() == "span" &&
        wholeText().isNotEmpty() && wholeText().all { it == ' ' || it == '\t' } &&
        hasInlineNeighbor(before = true) && hasInlineNeighbor(before = false)

    private fun Element.hasInlineNeighbor(before: Boolean): Boolean {
        var current = this
        while (true) {
            if ((if (before) current.previousSibling() else current.nextSibling()) != null) return true
            current = current.parent()?.takeIf { it.normalName() == "span" } ?: return false
        }
    }
}
