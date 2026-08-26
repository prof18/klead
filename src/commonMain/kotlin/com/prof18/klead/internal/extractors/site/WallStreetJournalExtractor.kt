package com.prof18.klead.internal.extractors.site

import com.fleeksoft.ksoup.nodes.Element
import com.prof18.klead.extractors.ExtractorMetadata
import com.prof18.klead.extractors.ExtractorResult
import com.prof18.klead.internal.extractors.DomExtractor
import com.prof18.klead.internal.extractors.DomExtractorContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject

internal object WallStreetJournalExtractor : DomExtractor {
    override val id: String = "wall-street-journal"
    override val domains: Set<String> = setOf("wsj.com")

    override fun matches(context: DomExtractorContext): Boolean =
        context.hostMatches(domains) && context.document.selectFirst("script#__NEXT_DATA__") != null

    override fun extract(context: DomExtractorContext): ExtractorResult? {
        val articleData = context.articleData() ?: return null
        val article = articleData.toArticleElement() ?: return null

        return ExtractorResult(
            contentHtml = article.outerHtml(),
            metadata = ExtractorMetadata(
                title = articleData.stringAt("headline", "text"),
                description = articleData.stringAt("standFirst", "content", "text"),
                author = articleData.byline(),
                site = "The Wall Street Journal",
            ),
        )
    }

    private fun DomExtractorContext.articleData(): JsonObject? {
        val script = document.selectFirst("script#__NEXT_DATA__") ?: return null
        val jsonText = script.data().ifBlank { script.html() }.ifBlank { return null }
        val root = runCatching { JSON.parseToJsonElement(jsonText).jsonObject }.getOrNull() ?: return null
        return root.objectAt("props", "pageProps", "articleData")
            ?.takeIf { it.string("type") == "article" }
    }

    private fun JsonObject.toArticleElement(): Element? {
        val body = this["flattenedBody"] as? JsonArray ?: return null
        val article = Element("article")

        body.forEach { item ->
            val block = item as? JsonObject ?: return@forEach
            when (block.string("type")?.lowercase()) {
                "paragraph" -> article.appendRichText("p", block)

                "heading", "subhead" -> article.appendRichText("h2", block)

                "blockquote", "pullquote" -> {
                    val quote = article.appendElement("blockquote")
                    quote.appendRichText("p", block)
                    if (quote.text().isBlank()) quote.remove()
                }
            }
        }

        return article.takeIf { it.text().isNotBlank() }
    }

    private fun Element.appendRichText(tag: String, block: JsonObject) {
        val parts = block["content"] as? JsonArray
            ?: block.objectAt("content")?.get("content") as? JsonArray
            ?: return
        val element = appendElement(tag)

        parts.forEach { item ->
            val inline = item as? JsonObject ?: return@forEach
            val text = inline.rawString("text") ?: return@forEach
            val href = inline.string("uri")
                ?: inline.string("topicUrl")
                ?: inline.string("wsjTopicUrl")
            if (href == null) {
                element.appendText(text)
            } else {
                element.appendElement("a").attr("href", href).text(text)
            }
        }

        if (element.text().isBlank()) element.remove()
    }

    private fun JsonObject.byline(): String? = (this["byline"] as? JsonArray)
        ?.mapNotNull { (it as? JsonObject)?.rawString("text") }
        ?.joinToString("")
        ?.trim()
        ?.replace(BY_PREFIX, "")
        ?.trim()
        ?.ifBlank { null }

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
        return (current as? JsonPrimitive)?.contentOrNull?.trim()?.ifBlank { null }
    }

    private fun JsonObject.string(key: String): String? =
        (this[key] as? JsonPrimitive)?.contentOrNull?.trim()?.ifBlank { null }

    private fun JsonObject.rawString(key: String): String? =
        (this[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }

    private val JSON = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }
    private val BY_PREFIX = Regex("""(?i)^by\s+""")
}
