package com.prof18.klead.internal.extractors.site

import com.fleeksoft.ksoup.nodes.Comment
import com.fleeksoft.ksoup.nodes.Element
import com.fleeksoft.ksoup.nodes.Node
import com.fleeksoft.ksoup.nodes.TextNode
import com.prof18.klead.internal.extractors.DomExtractor
import com.prof18.klead.internal.extractors.DomExtractorContext

internal object TimesOfIndiaProfile : DomExtractor {
    override val id: String = "timesofindia"
    override val domains: Set<String> = setOf("timesofindia.indiatimes.com")

    // Without a selector, detection picks a wrapper that also holds the Videos, Photostories and
    // Trending Stories rails below the story.
    override val contentSelectors: List<String> = listOf("[data-articlebody]")

    // The story ends with a "Download the TOI App" promo link.
    override val postContentRemoveSelectors: List<String> = listOf(".cdatainfo:has(a[href*=sng.link])")

    override fun preProcess(content: Element, context: DomExtractorContext) {
        content.select("span.br").mapNotNull { it.parent() }.distinct().forEach(::wrapParagraphs)
    }

    // The story is bare text split by empty span.br markers, with ad slots and the lead image as
    // sibling divs, so the paragraphs only exist as runs of inline nodes between them.
    // The new child list is built in one pass and set once: inserting each paragraph and removing each
    // marker reindexed the body for every marker. A run with content becomes a paragraph, its comments
    // staying in front of it; a run without content stays as it is; markers are dropped.
    private fun wrapParagraphs(body: Element) {
        val children = mutableListOf<Node>()
        val paragraphs = mutableListOf<Pair<Element, List<Node>>>()
        val run = mutableListOf<Node>()

        fun flush() {
            if (run.any { it is Element || (it is TextNode && !it.isBlank()) }) {
                val paragraph = Element("p")
                children.addAll(run.filterIsInstance<Comment>())
                children.add(paragraph)
                paragraphs.add(paragraph to run.filter { it !is Comment })
            } else {
                children.addAll(run)
            }
            run.clear()
        }

        body.childNodes().toList().forEach { node ->
            when {
                node is Element && node.isBreakMarker() -> flush()

                node is TextNode || node is Comment || (node is Element && node.normalName() in INLINE_TAGS) ->
                    run += node

                else -> {
                    flush()
                    children.add(node)
                }
            }
        }
        flush()
        body.empty()
        paragraphs.forEach { (paragraph, nodes) -> paragraph.appendChildren(nodes) }
        body.appendChildren(children)
    }

    private fun Element.isBreakMarker(): Boolean =
        normalName() == "br" || (normalName() == "span" && hasClass("br") && childNodeSize() == 0)

    private val INLINE_TAGS = setOf("a", "span", "strong", "em", "b", "i", "u", "code", "sup", "sub", "mark", "small")
}
