package com.prof18.klead.internal.removal

import com.fleeksoft.ksoup.nodes.Element

internal fun elementHintHaystack(element: Element, includeId: Boolean = true, includeUrls: Boolean = true): String {
    val parts = buildList {
        if (includeId) element.id().takeIf { it.isNotBlank() }?.let(::add)
        element.className().takeIf { it.isNotBlank() }?.let(::add)

        for (attribute in element.attributes()) {
            val key = attribute.key.lowercase()
            val value = attribute.value.asHintValue()?.let { if (includeUrls) it else it.withoutUrls() }
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

// Labels like aria-label="Go to https://…" repeat the link target inside other text.
private fun String.withoutUrls(): String? {
    if (looksLikeUrl()) return null
    return EMBEDDED_URL.replace(this, " ").trim().takeIf { it.isNotBlank() }
}

private val EMBEDDED_URL = Regex("""https?://\S+""", RegexOption.IGNORE_CASE)

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
