package com.prof18.klead.internal.standardize

import com.fleeksoft.ksoup.nodes.Element

// Orchestrates footnote normalization: format-specific converters run first (their markup is
// the most distinctive), then the generic list/definition collectors, then loose trailing
// definitions, and finally the divider cleanup once every definition has moved into the shared
// footnote section.
internal object HtmlFootnoteNormalizer {
    fun normalizeFootnotes(content: Element) {
        HtmlFootnoteFormats.normalizeDataDefinitionFootnotes(content)
        HtmlFootnoteFormats.normalizeTexinfoFootnotes(content)
        HtmlFootnoteFormats.normalizeOReillyFootnotes(content)
        HtmlFootnoteFormats.normalizeInlineFootnoteSpans(content)
        HtmlFootnoteFormats.normalizeInlineFootnoteContainers(content)
        HtmlFootnoteListNormalizer.normalizeParagraphFootnoteDefinitions(content)
        HtmlFootnoteListNormalizer.normalizeNamedAnchorFootnotes(content)
        HtmlFootnoteListNormalizer.normalizeBrSeparatedNamedAnchorFootnotes(content)
        HtmlFootnoteFormats.normalizeSidenoteFootnotes(content)
        HtmlFootnoteFormats.normalizeOrgModeFootdefs(content)
        HtmlFootnoteFormats.normalizeSubstackFootnotes(content)
        HtmlFootnoteFormats.normalizeWikidotFootnotes(content)
        HtmlFootnoteFormats.normalizeDhammatalksFootnotes(content)
        HtmlFootnoteListNormalizer.normalizeFootnoteDefinitionBlocks(content)
        HtmlFootnoteListNormalizer.normalizeAsideFootnoteLists(content)
        HtmlFootnoteListNormalizer.normalizeReferenceDivFootnotes(content)
        HtmlFootnoteListNormalizer.normalizeReferenceFootnoteLists(content)
        HtmlFootnoteListNormalizer.normalizeFootnoteLists(content)
        HtmlFootnoteListNormalizer.normalizeLooseFootnoteSections(content)
        HtmlFootnoteListNormalizer.normalizeTrailingLooseFootnoteDefinitions(content)
        removeFootnoteDividers(content)
    }

    private fun removeFootnoteDividers(content: Element) {
        content.select("hr").forEach { divider ->
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
