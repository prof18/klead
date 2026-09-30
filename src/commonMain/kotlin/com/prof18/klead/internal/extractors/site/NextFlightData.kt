package com.prof18.klead.internal.extractors.site

import com.fleeksoft.ksoup.nodes.Document
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

// Read data, never execute the page's JavaScript. Flight text records are framed by
// UTF-8 byte length, so their HTML/newlines cannot be mistaken for another record.
internal class NextFlightData(val models: List<JsonElement>, private val texts: Map<String, String>) {
    fun resolveText(value: String): String? = if (value.startsWith("$")) texts[value.drop(1)] else value
}

internal fun Document.nextFlightData(): NextFlightData {
    val stream = buildString {
        for (script in select("script")) {
            for (encodedChunk in flightChunks(script.data())) {
                val chunk = runCatching { Json.parseToJsonElement(encodedChunk) as? JsonArray }.getOrNull()
                if ((chunk?.getOrNull(0) as? JsonPrimitive)?.contentOrNull == "1") {
                    (chunk.getOrNull(1) as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull?.let(::append)
                }
            }
        }
    }.encodeToByteArray()
    return readFlightRecords(stream)
}

private fun readFlightRecords(stream: ByteArray): NextFlightData {
    val texts = mutableMapOf<String, String>()
    val models = mutableListOf<JsonElement>()
    var offset = 0
    while (offset < stream.size) {
        val colon = stream.indexOfByte(':', offset)
        if (colon < 0) break
        val id = stream.decodeToString(offset, colon)
        if (id.toIntOrNull(16) == null) {
            val end = stream.indexOfByte('\n', offset)
            if (end < 0) break
            offset = end + 1
            continue
        }
        val payload = colon + 1
        if (stream.getOrNull(payload) == 'T'.code.toByte()) {
            val comma = stream.indexOfByte(',', payload + 1)
            if (comma < 0) break
            val length = stream.decodeToString(payload + 1, comma).toIntOrNull(16) ?: break
            val start = comma + 1
            if (length < 0 || length > stream.size - start) break
            texts[id] = stream.decodeToString(start, start + length)
            offset = start + length
        } else {
            val end = stream.indexOfByte('\n', payload).takeIf { it >= 0 } ?: stream.size
            val row = stream.decodeToString(payload, end)
            if (row.startsWith("[") || row.startsWith("{")) {
                runCatching { Json.parseToJsonElement(row) }.getOrNull()?.let(models::add)
            }
            offset = end + 1
        }
    }
    return NextFlightData(models, texts)
}

private fun ByteArray.indexOfByte(character: Char, start: Int): Int {
    for (index in start until size) {
        if (this[index] == character.code.toByte()) return index
    }
    return -1
}

private fun flightChunks(script: String): Sequence<String> = sequence {
    for (match in FLIGHT_PUSH.findAll(script)) {
        val start = match.range.last
        val end = jsonArrayEnd(script, start) ?: continue
        yield(script.substring(start, end))
    }
}

private fun jsonArrayEnd(text: String, start: Int): Int? {
    var depth = 0
    var quoted = false
    var escaped = false
    for (index in start until text.length) {
        val character = text[index]
        if (escaped) {
            escaped = false
        } else if (quoted && character == '\\') {
            escaped = true
        } else if (character == '"') {
            quoted = !quoted
        } else if (!quoted) {
            if (character == '[') depth++
            if (character == ']') depth--
            if (depth == 0) return index + 1
        }
    }
    return null
}

private val FLIGHT_PUSH = Regex("""self\.__next_f\.push\(\s*\[""")
