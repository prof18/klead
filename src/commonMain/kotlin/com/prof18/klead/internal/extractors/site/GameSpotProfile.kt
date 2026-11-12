package com.prof18.klead.internal.extractors.site

import com.fleeksoft.ksoup.nodes.Document
import com.fleeksoft.ksoup.nodes.Element
import com.prof18.klead.extractors.ExtractorResult
import com.prof18.klead.internal.extractors.DomExtractor
import com.prof18.klead.internal.extractors.DomExtractorContext
import com.prof18.klead.internal.json.parseToJsonElementOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

internal object GameSpotProfile : DomExtractor {
    override val id: String = "gamespot"
    override val domains: Set<String> = setOf("gamespot.com")
    override val postContentRemoveSelectors: List<String> = listOf(
        ".right-rail",
        ".single-sidebar",
        """[aria-label="Article sidebar"]""",
        ".social-post-share",
        ".google-follow-us-wrapper",
        ".entry-footer",
        ".related-tags-list",
        ".featured-game",
        ".authors-section",
        ".latest-post-news",
    )

    // Video pages carry a one-line body, so pin the content root to keep that short result.
    override fun extract(context: DomExtractorContext): ExtractorResult? {
        context.document.selectFirst("$VIDEO_ARTICLE $VIDEO_EMBED") ?: return null
        return ExtractorResult(contentSelector = VIDEO_ARTICLE)
    }

    override fun preProcess(content: Element, context: DomExtractorContext) {
        // Every ld+json content URL in document order, parsed once instead of once per embed.
        val contentUrls by lazy(LazyThreadSafetyMode.NONE) { context.document.jwContentUrls() }
        content.select(VIDEO_EMBED).forEach { embed ->
            // A figure holding two embeds is replaced with the first; the second is then detached.
            val replaced = embed.closest("figure") ?: embed
            if (replaced.parent() == null) return@forEach
            val mediaId = embed.attr("data-jw-id").trim()
            if (!JW_MEDIA_ID.matches(mediaId)) return@forEach
            val prefix = "https://cdn.jwplayer.com/videos/$mediaId"
            val href = contentUrls.firstOrNull { it.startsWith(prefix) } ?: context.url ?: return@forEach
            // Markdown figures keep only their images, so the linked poster goes in a paragraph.
            val poster = Element("p")
            poster.appendElement("a").attr("href", href)
                .appendElement("img")
                .attr("src", "https://cdn.jwplayer.com/v2/media/$mediaId/poster.jpg?width=1280")
                .attr("alt", "Watch the video")
            replaced.replaceWith(poster)
        }
        removeDeckRepeatedInBody(content)
    }

    // Video pages repeat the deck as the only body paragraph.
    private fun removeDeckRepeatedInBody(content: Element) {
        val deck = content.selectFirst(".entry-summary") ?: return
        val deckText = deck.text().trim().ifBlank { return }
        val body = content.selectFirst(".entry-content") ?: return
        if (body.text().trim() == deckText) deck.remove()
    }

    private fun Document.jwContentUrls(): List<String> =
        select("""script[type="application/ld+json"]""").flatMap { script ->
            val json = JSON.parseToJsonElementOrNull(script.data().ifBlank { script.html() })
            json?.contentUrls()?.toList().orEmpty()
        }

    private fun JsonElement.contentUrls(): Sequence<String> = when (this) {
        is JsonArray -> asSequence().flatMap { it.contentUrls() }

        is JsonObject -> {
            val own = (get("contentUrl") as? JsonPrimitive)?.contentOrNull?.trim()
            sequenceOf(own).filterNotNull() + values.asSequence().flatMap { it.contentUrls() }
        }

        else -> emptySequence()
    }

    private const val VIDEO_ARTICLE = "article.type-videos"
    private const val VIDEO_EMBED = ".wp-block-gamespot-blocks-video-embed[data-jw-id]"
    private val JW_MEDIA_ID = Regex("[A-Za-z0-9]{8}")

    private val JSON = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }
}
