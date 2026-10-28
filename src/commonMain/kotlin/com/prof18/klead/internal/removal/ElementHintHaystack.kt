package com.prof18.klead.internal.removal

import com.fleeksoft.ksoup.nodes.Element

internal fun elementHintHaystack(element: Element, includeId: Boolean = true, includeUrls: Boolean = true): String {
    val parts = buildList {
        if (includeId) element.id().takeIf { it.isNotBlank() }?.let(::add)
        element.className().takeIf { it.isNotBlank() }?.let(::add)

        for (attribute in element.attributes()) {
            val key = attribute.key.lowercase()
            val value = attribute.value.asHintValue()?.takeIf { includeUrls || !it.looksLikeUrl() }
            if (key.startsWith("data-")) {
                add(key)
                value?.let(::add)
            } else if (key in HINT_ATTRIBUTE_KEYS) {
                value?.let(::add)
            }
        }
    }
    return parts.joinToString(" ").lowercase()
}

private fun String.looksLikeUrl(): Boolean =
    startsWith("/") || startsWith("http://", ignoreCase = true) || startsWith("https://", ignoreCase = true)

private fun String.asHintValue(): String? = trim()
    .takeIf { it.isNotBlank() }
    ?.take(HINT_ATTRIBUTE_VALUE_MAX_LENGTH)

private val HINT_ATTRIBUTE_KEYS = setOf(
    "aria-label",
    "aria-labelledby",
    "href",
    "itemprop",
    "name",
    "property",
    "rel",
    "role",
    "type",
)

private const val HINT_ATTRIBUTE_VALUE_MAX_LENGTH = 160
