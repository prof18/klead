package com.prof18.klead.internal.metadata

import com.prof18.klead.internal.dom.attrTrimmedOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import org.jsoup.nodes.Document

internal data class MetaTagItem(val name: String?, val property: String?, val content: String?)

internal data class SchemaOrgResult(val items: List<Map<String, Any?>>, val diagnostics: List<String>) {
    fun firstString(path: String): String? {
        val keys = path.split(".")
        for (item in items) {
            val value = item.findPath(keys)
            if (value is String && value.isNotBlank()) return value
        }
        return null
    }
}

internal object MetadataExtractor {
    fun collectMetaTags(document: Document): List<MetaTagItem> = document.select("meta").mapNotNull { meta ->
        val name = meta.attrTrimmedOrNull("name")
        val property = meta.attrTrimmedOrNull("property")
        val content = meta.attrTrimmedOrNull("content")
        if (content == null || (name == null && property == null)) {
            null
        } else {
            MetaTagItem(name = name, property = property, content = content)
        }
    }

    fun extractSchemaOrg(document: Document, debug: Boolean): SchemaOrgResult {
        val items = mutableListOf<Map<String, Any?>>()
        val diagnostics = mutableListOf<String>()

        document.select("""script[type="application/ld+json"]""").forEachIndexed { index, script ->
            val jsonText = script.data().ifBlank { script.html() }
            val parsed = runCatching {
                JSON.parseToJsonElement(jsonText)
            }.getOrElse { error ->
                if (debug) diagnostics += "Invalid JSON-LD script #$index: ${error.message.orEmpty()}"
                null
            }
            if (parsed != null) {
                flattenJsonLd(parsed, items)
            }
        }

        return SchemaOrgResult(
            items = items,
            diagnostics = diagnostics,
        )
    }

    private fun flattenJsonLd(element: JsonElement, output: MutableList<Map<String, Any?>>) {
        when (element) {
            is JsonArray -> element.forEach { flattenJsonLd(it, output) }

            is JsonObject -> {
                val graph = element["@graph"]
                if (graph != null) {
                    flattenJsonLd(graph, output)
                }
                if (element.keys.any { it != "@graph" && it != "@context" }) {
                    output += element.toKotlinMap()
                }
            }

            else -> Unit
        }
    }

    private fun JsonObject.toKotlinMap(): Map<String, Any?> =
        entries.associate { (key, value) -> key to value.toKotlinValue() }

    private fun JsonElement.toKotlinValue(): Any? = when (this) {
        is JsonNull -> null
        is JsonPrimitive -> contentOrNull
        is JsonArray -> map { it.toKotlinValue() }
        is JsonObject -> toKotlinMap()
    }

    private val JSON = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }
}

private fun Map<String, Any?>.findPath(keys: List<String>): Any? {
    var current: Any? = this
    for (key in keys) {
        current = when (current) {
            is Map<*, *> -> current[key]

            is List<*> -> current.firstNotNullOfOrNull { item ->
                (item as? Map<*, *>)?.get(key)
            }

            else -> return null
        }
    }
    return current
}
