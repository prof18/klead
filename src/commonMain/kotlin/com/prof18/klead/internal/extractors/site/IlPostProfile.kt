package com.prof18.klead.internal.extractors.site

import com.fleeksoft.ksoup.nodes.Document
import com.fleeksoft.ksoup.nodes.Element
import com.prof18.klead.RemovalRecord
import com.prof18.klead.extractors.ExtractorMetadata
import com.prof18.klead.extractors.ExtractorResult
import com.prof18.klead.internal.dom.parseKleadUri
import com.prof18.klead.internal.extractors.DomExtractor
import com.prof18.klead.internal.extractors.DomExtractorContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject

internal object IlPostProfile : DomExtractor {
    override val id: String = "ilpost"
    override val domains: Set<String> = setOf("ilpost.it")
    override val postContentRemoveSelectors: List<String> = listOf(
        "#audioPlayerArticle",
        ".audio-player",
        ".audioplayer",
        "h2[class*=author]",
        "[data-mp3]",
        "[data-audio-src]",
    )

    override fun extract(context: DomExtractorContext): ExtractorResult? {
        val article = context.document.ilPostArticleData() ?: return null
        return ExtractorResult(
            metadata = ExtractorMetadata(
                author = article.author,
            ),
        )
    }

    override fun preProcess(content: Element, context: DomExtractorContext) {
        normalizePodcastPlayer(content, context.document.ilPostEpisodeData())
        normalizeRelatedEpisodes(content)
    }

    private fun normalizePodcastPlayer(content: Element, episode: IlPostEpisodeData?) {
        episode ?: return
        val playerContent = content.selectFirst("#podcast-player__timeline")?.parent() ?: return
        val replacement = episode.audioUrl?.validatedIlPostAudioUrl()?.let { audioUrl ->
            Element("audio")
                .attr("controls", "")
                .attr("preload", "metadata")
                .attr("src", audioUrl)
                .also { audio ->
                    audio.appendElement("a")
                        .attr("href", audioUrl)
                        .text("Listen to this episode")
                }
        } ?: episode.pageUrl?.validatedIlPostEpisodeUrl()?.let { episodeUrl ->
            Element("p").also { paragraph ->
                paragraph.appendElement("a")
                    .attr("href", episodeUrl)
                    .text("Listen to this episode on Il Post")
            }
        } ?: return

        playerContent.replaceWith(replacement)
    }

    private fun normalizeRelatedEpisodes(content: Element) {
        content.select("""[class*="episode_podcast-episodes-archive-container"]""").forEach { archive ->
            val episodeItems = archive.children().filter { child ->
                child.classNames().any { className ->
                    className.startsWith("_episode-item_") && !className.startsWith("_episode-item__")
                }
            }
            if (episodeItems.isEmpty()) return@forEach

            val list = Element("ul")
            episodeItems.forEach { item ->
                val titleLink = item.selectFirst("""[class*="_episode-item__title"] a[href]""")
                    ?: return@forEach
                val title = titleLink.text().trim().ifBlank { return@forEach }
                val href = titleLink.attr("href").trim().ifBlank { return@forEach }
                val summary = item.selectFirst("""[class*="_episode-item__summary"]""")
                    ?.text()
                    ?.trim()
                    ?.takeIf { it.isNotBlank() && it != title }
                val details = item.selectFirst("""[class*="_episode-item__details"]""")
                    ?.text()
                    ?.trim()
                    ?.replace(DETAIL_SEPARATOR, " · ")
                    ?.takeIf { it.isNotBlank() }

                list.appendElement("li").also { listItem ->
                    listItem.appendElement("a")
                        .attr("href", href)
                        .text(title)
                    summary?.let { listItem.appendText(" — $it") }
                    details?.let { listItem.appendElement("small").text(" ($it)") }
                }
            }
            if (list.children().isEmpty()) return@forEach

            episodeItems.first().before(list)
            episodeItems.forEach(Element::remove)
        }
    }

    override fun postProcess(content: Element, context: DomExtractorContext, debug: MutableList<RemovalRecord>) {
        promoteGalleryThumbnails(content)

        val summary = context.document.ilPostArticleData()?.summary
            ?: context.document.selectFirst(
                """meta[name=description][content], meta[property=og:description][content]""",
            )
                ?.attr("content")
                ?.trim()
                ?.ifBlank { null }
            ?: return

        if (content.visibleText().normalizedText().contains(summary.normalizedText())) return

        content.prependChild(Element("h2").text(summary))
    }

    private fun promoteGalleryThumbnails(content: Element) {
        content.select(".gallery img.attachment-thumbnail[src], .gallery img.size-thumbnail[src]").forEach { image ->
            val thumbnailSrc = image.attr("src")
            if ("/wp-content/uploads/" !in thumbnailSrc) return@forEach
            val originalSrc = thumbnailSrc.withoutWordPressImageSize() ?: return@forEach

            image.attr("src", originalSrc)
            image.removeAttr("width")
            image.removeAttr("height")
            image.removeAttr("sizes")
            image.removeClass("attachment-thumbnail")
            image.removeClass("size-thumbnail")
        }
    }

    private fun String.withoutWordPressImageSize(): String? {
        val original = replace(WORDPRESS_IMAGE_SIZE_PATH) { match ->
            "/${match.groupValues[1]}${match.groupValues[2]}"
        }
        return original.takeIf { it != this }
    }

    private fun Document.ilPostArticleData(): IlPostArticleData? {
        val root = ilPostNextData() ?: return null
        val article = root.objectAt("props", "pageProps", "data", "data", "main", "data") ?: return null
        return IlPostArticleData(
            summary = article.stringAt("summary") ?: article.stringAt("excerpt"),
            author = article.authorName(),
        )
    }

    private fun Document.ilPostEpisodeData(): IlPostEpisodeData? {
        val root = ilPostNextData() ?: return null
        val episode = root.objectAt("props", "pageProps", "data", "data", "episode")
            ?.arrayAt("data")
            ?.firstOrNull() as? JsonObject
            ?: return null
        return IlPostEpisodeData(
            audioUrl = episode.stringAt("episode_raw_url"),
            pageUrl = episode.stringAt("url") ?: episode.stringAt("share_url"),
        )
    }

    private fun Document.ilPostNextData(): JsonObject? {
        val script = selectFirst("script#__NEXT_DATA__") ?: return null
        val jsonText = script.data()
            .ifBlank { script.html() }
            .ifBlank { null }
            ?: return null
        return runCatching { JSON.parseToJsonElement(jsonText).jsonObject }.getOrNull()
    }

    private fun JsonObject.authorName(): String? {
        stringAt("public_author", "name")?.let { return it }

        val author = objectAt("author") ?: return null
        return listOfNotNull(
            author.stringAt("first_name"),
            author.stringAt("last_name"),
        ).joinToString(" ")
            .trim()
            .ifBlank { null }
    }

    private fun JsonObject.objectAt(vararg keys: String): JsonObject? {
        var current: JsonElement = this
        for (key in keys) {
            current = (current as? JsonObject)?.get(key) ?: return null
        }
        return current as? JsonObject
    }

    private fun JsonObject.stringAt(vararg keys: String): String? {
        var current: JsonElement = this
        for (key in keys) {
            current = (current as? JsonObject)?.get(key) ?: return null
        }
        return (current as? JsonPrimitive)
            ?.contentOrNull
            ?.trim()
            ?.ifBlank { null }
    }

    private fun JsonObject.arrayAt(vararg keys: String): JsonArray? {
        var current: JsonElement = this
        for (key in keys) {
            current = (current as? JsonObject)?.get(key) ?: return null
        }
        return current as? JsonArray
    }

    private fun String.validatedIlPostAudioUrl(): String? {
        val parsed = parseKleadUri(trim()) ?: return null
        if (!parsed.scheme.equals("https", ignoreCase = true)) return null
        if (parsed.host?.lowercase()?.removePrefix("www.") != "ilpost.it") return null
        val extension = parsed.rawPath.orEmpty()
            .substringAfterLast('.')
            .lowercase()
        if (parsed.rawQuery != null || extension !in AUDIO_EXTENSIONS) {
            return null
        }
        return parsed.asciiString
    }

    private fun String.validatedIlPostEpisodeUrl(): String? {
        val parsed = parseKleadUri(trim()) ?: return null
        if (!parsed.scheme.equals("https", ignoreCase = true)) return null
        if (parsed.host?.lowercase()?.removePrefix("www.") != "ilpost.it") return null
        val path = parsed.rawPath.orEmpty()
        if (!path.startsWith("/podcasts/") && !path.startsWith("/episodes/")) return null
        return parsed.asciiString
    }

    private fun String.normalizedText(): String = lowercase()
        .replace(Regex("""\s+"""), " ")
        .trim()

    private fun Element.visibleText(): String {
        val clone = clone()
        clone.select("script, style, template, noscript, meta, link").remove()
        return clone.text()
    }

    private data class IlPostArticleData(val summary: String?, val author: String?)
    private data class IlPostEpisodeData(val audioUrl: String?, val pageUrl: String?)

    private val JSON = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }
    private val WORDPRESS_IMAGE_SIZE_PATH = Regex("""/\d+x\d+/([^/?#]+)([?#].*)?$""")
    private val AUDIO_EXTENSIONS = setOf("aac", "m4a", "mp3", "ogg", "wav")
    private val DETAIL_SEPARATOR = Regex("""\s+-\s+""")
}
