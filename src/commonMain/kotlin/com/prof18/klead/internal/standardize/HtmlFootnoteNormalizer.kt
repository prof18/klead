package com.prof18.klead.internal.standardize

import com.fleeksoft.ksoup.nodes.Element
import com.fleeksoft.ksoup.select.QueryParser

// Orchestrates footnote normalization: format-specific converters run first (their markup is
// the most distinctive), then the generic list/definition collectors, then loose trailing
// definitions, and finally the divider cleanup once every definition has moved into the shared
// footnote section.
internal object HtmlFootnoteNormalizer {
    // About twenty passes, each over the whole content: check for cancellation between them.
    fun normalizeFootnotes(content: Element, checkCancelled: () -> Unit = {}) {
        val passes: List<(Element) -> Unit> = listOf(
            HtmlFootnoteFormats::normalizeDataDefinitionFootnotes,
            HtmlFootnoteFormats::normalizeTexinfoFootnotes,
            HtmlFootnoteFormats::normalizeOReillyFootnotes,
            HtmlFootnoteFormats::normalizeInlineFootnoteSpans,
            HtmlFootnoteFormats::normalizeInlineFootnoteContainers,
            HtmlFootnoteListNormalizer::normalizeParagraphFootnoteDefinitions,
            HtmlFootnoteListNormalizer::normalizeNamedAnchorFootnotes,
            HtmlFootnoteListNormalizer::normalizeBrSeparatedNamedAnchorFootnotes,
            HtmlFootnoteFormats::normalizeSidenoteFootnotes,
            HtmlFootnoteFormats::normalizeOrgModeFootdefs,
            HtmlFootnoteFormats::normalizeSubstackFootnotes,
            HtmlFootnoteFormats::normalizeWikidotFootnotes,
            HtmlFootnoteFormats::normalizeDhammatalksFootnotes,
            HtmlFootnoteListNormalizer::normalizeFootnoteDefinitionBlocks,
            HtmlFootnoteListNormalizer::normalizeAsideFootnoteLists,
            HtmlFootnoteListNormalizer::normalizeReferenceDivFootnotes,
            HtmlFootnoteListNormalizer::normalizeReferenceFootnoteLists,
            HtmlFootnoteListNormalizer::normalizeFootnoteLists,
            HtmlFootnoteListNormalizer::normalizeLooseFootnoteSections,
            HtmlFootnoteListNormalizer::normalizeTrailingLooseFootnoteDefinitions,
            ::removeFootnoteDividers,
            ::wrapLooseFootnoteItems,
        )
        for (pass in passes) {
            checkCancelled()
            pass(content)
        }
    }

    private fun wrapLooseFootnoteItems(content: Element) {
        content.selectDescendants("section[data-footnotes]").forEach { section ->
            val items = section.select(CHILD_ITEM_EVALUATOR).toList()
            if (items.isEmpty()) return@forEach

            val list = Element("ol")
            val firstNumber = SIMPLE_FOOTNOTE_ID_NUMBER.matchEntire(items.first().id())
                ?.groupValues?.get(1)?.toIntOrNull()
            if (firstNumber != null && firstNumber > 1) list.attr("start", firstNumber.toString())
            items.first().before(list)
            items.forEach { item ->
                // A lone paragraph adds a blank line between the marker and its text in
                // readers that apply paragraph margins inside list items.
                val paragraph = item.children().singleOrNull()?.takeIf { it.normalName() == "p" }
                if (paragraph != null && item.ownText().isBlank()) paragraph.unwrap()
                list.appendChild(item)
            }
        }
    }

    private val SIMPLE_FOOTNOTE_ID_NUMBER = Regex("""(?i)^(?:fn|footnote[-_]?)?(\d+)$""")

    private fun removeFootnoteDividers(content: Element) {
        content.selectDescendants("hr").forEach { divider ->
            val parent = divider.parent()
            val isInsideFootnotes = parent?.hasClass("footnotes") == true ||
                parent?.hasAttr("data-footnotes") == true
            if (isInsideFootnotes) {
                if (divider.isFootnoteSeparatorChrome()) {
                    divider.remove()
                }
                return@forEach
            }

            val next = divider.nextElementSibling()
            val followsFootnoteHint = next == null && divider.previousElementSibling()?.hasFootnoteHint() == true
            val isTrailingBeforeFootnotes = followsFootnoteHint ||
                next?.hasAttr("data-footnotes") == true
            if (isTrailingBeforeFootnotes) {
                divider.remove()
            }
        }
    }

    private fun Element.isFootnoteSeparatorChrome(): Boolean = classNames().any {
        it.contains("separator", ignoreCase = true) ||
            it.contains("separatator", ignoreCase = true)
    }

    private fun Element.hasFootnoteHint(): Boolean {
        val hints = "${id()} ${className()} ${attributes().asList().joinToString(" ") { it.value }}".lowercase()
        return "footnote" in hints || "footnotes" in hints || "ftnt" in hints
    }
}

// Parsed once: see ParsedSelectors for why selectors with combinators are not parsed per call.
private val CHILD_ITEM_EVALUATOR = QueryParser.parse("> li")
