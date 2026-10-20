package com.prof18.klead.internal.json

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

// kotlinx.serialization reads arrays recursively, and callers walk the tree recursively too. On
// Kotlin/Native a deeply nested page payload overflows the small stack of a background thread,
// which kills the process instead of throwing, so it must be rejected before parsing.
internal const val MAX_JSON_NESTING_DEPTH = 64

internal fun Json.parseToJsonElementOrNull(text: String): JsonElement? {
    if (text.exceedsJsonNestingDepth(MAX_JSON_NESTING_DEPTH)) return null
    return runCatching { parseToJsonElement(text) }.getOrNull()
}

internal fun String.exceedsJsonNestingDepth(limit: Int): Boolean {
    var depth = 0
    var inString = false
    var escaped = false
    for (char in this) {
        when {
            escaped -> escaped = false
            inString && char == '\\' -> escaped = true
            char == '"' -> inString = !inString
            inString -> Unit
            char == '[' || char == '{' -> if (++depth > limit) return true
            char == ']' || char == '}' -> depth--
        }
    }
    return false
}
