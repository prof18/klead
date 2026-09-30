package com.prof18.klead.internal.extractors.site

import com.fleeksoft.ksoup.nodes.Element
import com.prof18.klead.extractors.ExtractorMetadata
import com.prof18.klead.extractors.ExtractorResult
import com.prof18.klead.internal.dom.parseKleadUri
import com.prof18.klead.internal.extractors.DomExtractor
import com.prof18.klead.internal.extractors.DomExtractorContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

internal object ApXmlProfile : DomExtractor {
    override val id: String = "apxml"
    override val domains: Set<String> = setOf("apxml.com")

    override fun extract(context: DomExtractorContext): ExtractorResult? {
        val slug = context.url?.let(::parseKleadUri)?.path
            ?.substringAfter("/posts/", "")?.trimEnd('/')?.takeIf { it.isNotBlank() }
            ?: return null
        val data = context.document.nextFlightData()
        val post = data.models.firstNotNullOfOrNull { it.findPost(slug) } ?: return null
        val html = post.stringAt("content")?.let(data::resolveText)?.takeIf { it.isNotBlank() } ?: return null
        val article = Element("article").html(html)
        if (article.text().isBlank()) return null
        return ExtractorResult(
            contentHtml = article.outerHtml(),
            metadata = ExtractorMetadata(
                title = post.stringAt("title"),
                description = post.stringAt("excerpt"),
                author = post.stringAt("author", "name"),
                site = "ApX Machine Learning",
            ),
        )
    }

    private fun JsonElement.findPost(slug: String): JsonObject? = when (this) {
        is JsonObject -> (this["post"] as? JsonObject)
            ?.takeIf { it.stringAt("slug") == slug && it.stringAt("content") != null }
            ?: values.firstNotNullOfOrNull { it.findPost(slug) }

        is JsonArray -> firstNotNullOfOrNull { it.findPost(slug) }

        else -> null
    }
}
