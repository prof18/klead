package com.prof18.klead.internal.standardize

import org.jsoup.nodes.Element

internal val HEADING_TAG_PATTERN = Regex("""h[1-6]""")
internal val EMPTY_WRAPPER_TAGS = setOf("div", "section", "header")
internal val WHITESPACE_PATTERN = Regex("""\s+""")

internal fun String.collapseWhitespace(): String = replace(WHITESPACE_PATTERN, " ")

internal fun firstAttr(element: Element, vararg names: String): String? = names.firstNotNullOfOrNull { name ->
    element.attr(name).trim().ifBlank { null }
}

internal fun Element.componentHintHaystack(): String =
    listOf(id(), className(), attr("data-testid"), attr("data-component"), attr("itemprop"))
        .joinToString(" ")
        .lowercase()

internal fun removeEmptyAncestors(start: Element?, boundary: Element) {
    var current = start
    while (current != null && current !== boundary) {
        val parent = current.parent()
        if (
            current.normalName() in EMPTY_WRAPPER_TAGS &&
            current.text().isBlank() &&
            current.children().isEmpty()
        ) {
            current.remove()
        }
        current = parent
    }
}

internal fun Element.hrefFragmentTarget(): String? {
    val href = attr("href").trim()
    if ('#' !in href) return null
    return href.substringAfterLast('#').takeIf { it.isNotBlank() }
}
