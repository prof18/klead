package com.prof18.klead.internal.extractors.site

import com.fleeksoft.ksoup.nodes.Element
import com.prof18.klead.internal.extractors.DomExtractor
import com.prof18.klead.internal.extractors.DomExtractorContext

internal object FinanzenProfile : DomExtractor {
    override val id: String = "finanzen"
    override val domains: Set<String> = setOf("finanzen.net")
    override val contentSelectors: List<String> = listOf("article.news-container")
    override val postContentRemoveSelectors: List<String> = listOf(
        ".news-container > span",
        ".news-container > .grid > time",
        ".news-container > .grid + hr.separator",
        // Nested articles are advertising and related-news widgets, not the story body or gallery.
        ".news-container > article",
        ".ad-container",
        ".ad-hint",
        ".page-content__ad",
        ".native-content-ad-container",
        ".ai-summary-button",
        ".ai-summary-box",
        ".carousel__controls",
    )

    override fun preProcess(content: Element, context: DomExtractorContext) {
        content.select(".accordion").filter { accordion ->
            accordion.selectFirst(".accordion__label")?.text() == "Werte in diesem Artikel"
        }.forEach { it.remove() }
    }
}
