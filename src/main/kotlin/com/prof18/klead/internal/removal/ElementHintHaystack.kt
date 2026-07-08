package com.prof18.klead.internal.removal

import org.jsoup.nodes.Element

internal fun elementHintHaystack(element: Element, includeId: Boolean = true): String {
    val parts = buildList {
        if (includeId) element.id().takeIf { it.isNotBlank() }?.let(::add)
        element.className().takeIf { it.isNotBlank() }?.let(::add)

        for (attribute in element.attributes()) {
            val key = attribute.key.lowercase()
            if (key.startsWith("data-")) {
                add(key)
                attribute.value.asHintValue()?.let(::add)
            } else if (key in HINT_ATTRIBUTE_KEYS) {
                attribute.value.asHintValue()?.let(::add)
            }
        }
    }
    return parts.joinToString(" ").lowercase()
}

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
