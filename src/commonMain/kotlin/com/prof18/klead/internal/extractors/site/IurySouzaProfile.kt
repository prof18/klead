package com.prof18.klead.internal.extractors.site

import com.fleeksoft.ksoup.nodes.Element
import com.fleeksoft.ksoup.nodes.Node
import com.fleeksoft.ksoup.nodes.TextNode
import com.prof18.klead.internal.extractors.DomExtractor
import com.prof18.klead.internal.extractors.DomExtractorContext

internal object IurySouzaProfile : DomExtractor {
    override val id: String = "iury-souza"
    override val domains: Set<String> = setOf("iurysouza.dev")

    // Glossary popups depend on publisher CSS for visibility and retain absolute positioning.
    override val postContentRemoveSelectors: List<String> = listOf(POPUP_SELECTOR)

    override fun preProcess(content: Element, context: DomExtractorContext) {
        content.select(POPUP_SELECTOR).forEach(::repairSplitParagraph)
    }

    // A popup div inside a paragraph closes the paragraph during HTML parsing, leaving its
    // continuation as siblings followed by the empty paragraph created by the original </p>.
    private fun repairSplitParagraph(popup: Element) {
        val paragraph = popup.previousElementSibling()?.takeIf { it.normalName() == "p" } ?: return
        if (paragraph.children().lastOrNull()?.normalName() != "span") return

        val continuation = mutableListOf<Node>()
        var sibling = popup.nextSibling()
        while (sibling != null) {
            if (sibling is Element && sibling.normalName() == "p" && sibling.childNodes().isEmpty()) {
                continuation.forEach(paragraph::appendChild)
                sibling.remove()
                return
            }
            if (sibling !is TextNode && (sibling !is Element || sibling.normalName() !in INLINE_TAGS)) return
            continuation += sibling
            sibling = sibling.nextSibling()
        }
    }

    private const val POPUP_SELECTOR = "[data-show]:has(> .wiki-markdown)"
    private val INLINE_TAGS = setOf("a", "span", "strong", "em", "b", "i", "code", "br", "sup", "sub")
}
