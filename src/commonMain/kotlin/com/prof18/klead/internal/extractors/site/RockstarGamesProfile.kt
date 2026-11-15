package com.prof18.klead.internal.extractors.site

import com.fleeksoft.ksoup.nodes.Element
import com.prof18.klead.extractors.ExtractorResult
import com.prof18.klead.internal.dom.ParsedSelectors
import com.prof18.klead.internal.extractors.DomExtractor
import com.prof18.klead.internal.extractors.DomExtractorContext

internal object RockstarGamesProfile : DomExtractor {
    override val id: String = "rockstargames"
    override val domains: Set<String> = setOf("rockstargames.com")

    // Video pages carry a one-line description next to a "Plays best on" promo and a "Watch more" grid in
    // the same container, so pin the content root to the headline, date and description block.
    override fun extract(context: DomExtractorContext): ExtractorResult? {
        context.document.selectFirst(ParsedSelectors[VIDEO_DETAILS]) ?: return null
        return ExtractorResult(contentSelector = VIDEO_DETAILS)
    }

    // The video plays in Rockstar's own player, an untrusted iframe outside the content root. Stand in for it
    // with the page's poster image linking back to the page.
    override fun preProcess(content: Element, context: DomExtractorContext) {
        val pageUrl = context.url ?: return
        if (context.document.selectFirst(ParsedSelectors[PLAYER_IFRAME]) == null) return
        val poster = context.document.metaContent("og:image") ?: return
        val title = context.document.metaContent("og:title").orEmpty()

        val image = Element("img").attr("src", poster).attr("alt", title)
        val link = Element("a").attr("href", pageUrl).appendChild(image)
        content.prependChild(Element("p").appendChild(link))
    }

    private const val VIDEO_DETAILS = "div:has(> div > h3 + q):has(> div > p)"
    private const val PLAYER_IFRAME = """iframe[src*="rockstargames.com/videoplayer"]"""
}
