package com.prof18.klead.internal.extractors.site

import com.fleeksoft.ksoup.nodes.Document
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject

internal fun Document.metaContent(name: String): String? = selectFirst("""meta[property="$name"], meta[name="$name"]""")
    ?.attr("content")
    ?.trim()
    ?.ifBlank { null }

internal fun Document.nextDataJsonObject(): JsonObject? {
    val script = selectFirst("script#__NEXT_DATA__") ?: return null
    val jsonText = script.data().ifBlank { script.html() }.ifBlank { return null }
    return runCatching { NEXT_DATA_JSON.parseToJsonElement(jsonText).jsonObject }.getOrNull()
}

internal fun JsonObject.objectAt(vararg keys: String): JsonObject? {
    var current: JsonElement = this
    for (key in keys) {
        current = (current as? JsonObject)?.get(key) ?: return null
    }
    return current as? JsonObject
}

internal fun JsonObject.stringAt(vararg keys: String): String? {
    var current: JsonElement = this
    for (key in keys) {
        current = (current as? JsonObject)?.get(key) ?: return null
    }
    return (current as? JsonPrimitive)?.contentOrNull?.trim()?.ifBlank { null }
}

private val NEXT_DATA_JSON = Json {
    ignoreUnknownKeys = true
    isLenient = true
}
