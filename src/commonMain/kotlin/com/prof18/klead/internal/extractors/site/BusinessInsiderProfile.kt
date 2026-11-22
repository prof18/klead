package com.prof18.klead.internal.extractors.site

import com.fleeksoft.ksoup.nodes.Element
import com.prof18.klead.extractors.ExtractorResult
import com.prof18.klead.internal.dom.ParsedSelectors
import com.prof18.klead.internal.extractors.DomExtractor
import com.prof18.klead.internal.extractors.DomExtractorContext

internal object BusinessInsiderProfile : DomExtractor {
    override val id: String = "business-insider"
    override val domains: Set<String> = setOf("businessinsider.com")
    override val postContentRemoveSelectors: List<String> = listOf(
        """[data-component-type="post-byline"]""",
        ".post-byline",
        ".byline-wrapper",
        ".byline-author-container",
        """[data-component-type="timestamp"]""",
        """[data-component-type="post-bottom-authors"]""",
        ".post-bottom-authors",
        ".post-video-recirc",
        """[data-component-type="post-video-recirc"]""",
        ".back-to-home-container",
        ".back-to-home",
    )

    // Video pages hold the description twice (a collapsed mobile copy and the desktop one) between
    // expander toggles and icon SVGs, so pin the desktop copy and put the player poster above it.
    override fun extract(context: DomExtractorContext): ExtractorResult? {
        val description = context.document.selectFirst(ParsedSelectors[VIDEO_DESCRIPTION]) ?: return null
        if (description.select("p").none { it.text().isNotBlank() }) return null
        return ExtractorResult(contentSelector = VIDEO_DESCRIPTION)
    }

    override fun preProcess(content: Element, context: DomExtractorContext) {
        if (!content.`is`(ParsedSelectors[VIDEO_DESCRIPTION])) return
        content.select(".large-video-description-expander").remove()
        content.select("p").filter { it.text().isBlank() && it.selectFirst("img, picture, iframe") == null }
            .forEach { it.remove() }

        val poster = context.document.selectFirst(ParsedSelectors[VIDEO_POSTER]) ?: return
        val posterSrc = poster.absUrl("src").ifBlank { return }
        val image = Element("img")
            .attr("src", posterSrc)
            .attr("alt", poster.attr("alt").ifBlank { "Watch the video" })
        poster.attr("srcset").ifBlank { null }?.let { image.attr("srcset", it) }
        // Markdown figures keep only their images, so the linked poster goes in a paragraph.
        val container = Element("p")
        val href = context.url
        if (href != null) {
            container.appendElement("a").attr("href", href).appendChild(image)
        } else {
            container.appendChild(image)
        }
        content.prependChild(container)
    }

    private const val VIDEO_DESCRIPTION = "section.large-video-description"
    private const val VIDEO_POSTER = "[data-video-player][data-media-id] .video-thumbnail img[src]"
}
