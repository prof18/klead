package com.prof18.klead.internal.extractors.site

import com.fleeksoft.ksoup.nodes.Document
import com.fleeksoft.ksoup.nodes.Element
import com.prof18.klead.RemovalRecord
import com.prof18.klead.extractors.ExtractorMetadata
import com.prof18.klead.extractors.ExtractorResult
import com.prof18.klead.internal.extractors.DomExtractor
import com.prof18.klead.internal.extractors.DomExtractorContext
import kotlinx.serialization.json.Json
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
        val jsonText = selectFirst("script#__NEXT_DATA__")
            ?.data()
            ?.ifBlank { selectFirst("script#__NEXT_DATA__")?.html() }
            ?.ifBlank { null }
            ?: return null
        val root = runCatching { JSON.parseToJsonElement(jsonText).jsonObject }.getOrNull() ?: return null
        val article = root.objectAt("props", "pageProps", "data", "data", "main", "data") ?: return null
        return IlPostArticleData(
            summary = article.stringAt("summary") ?: article.stringAt("excerpt"),
            author = article.authorName(),
        )
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

    private fun String.normalizedText(): String = lowercase()
        .replace(Regex("""\s+"""), " ")
        .trim()

    private fun Element.visibleText(): String {
        val clone = clone()
        clone.select("script, style, template, noscript, meta, link").remove()
        return clone.text()
    }

    private data class IlPostArticleData(val summary: String?, val author: String?)

    private val JSON = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }
    private val WORDPRESS_IMAGE_SIZE_PATH = Regex("""/\d+x\d+/([^/?#]+)([?#].*)?$""")
}
