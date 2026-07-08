package com.prof18.klead.internal.dom

import org.jsoup.nodes.Element

internal fun Element.selectSafe(selector: String): List<Element> {
    val results = mutableListOf<Element>()
    for (part in selector.splitSelectorList()) {
        for (element in selectSingleSafe(part)) {
            if (results.none { it === element }) {
                results.add(element)
            }
        }
    }
    return results
}

internal fun Element.selectFirstSafe(selector: String): Element? = selectSafe(selector).firstOrNull()

internal fun Element.matchesSafe(selector: String): Boolean = selector.splitSelectorList().any { matchesSingleSafe(it) }

internal fun Element.closestSafe(selector: String): Element? {
    var current: Element? = this
    while (current != null) {
        if (current.matchesSafe(selector)) return current
        current = current.parent()
    }
    return null
}

internal fun Element.childrenMatching(selector: String): List<Element> = childrenElements().filter {
    it.matchesSafe(selector)
}

internal object SelectorDiagnostics {
    private const val MAX_TRACKED_SELECTORS = 100
    private val unsupported = linkedSetOf<String>()
    private val lock = Any()

    fun recordUnsupported(selector: String) {
        synchronized(lock) {
            // Cap the process-wide set so user-supplied selectors can't grow it unbounded.
            if (unsupported.size < MAX_TRACKED_SELECTORS) {
                unsupported += selector
            }
        }
    }

    fun unsupportedSelectors(): List<String> = synchronized(lock) {
        unsupported.toList()
    }

    fun clear() {
        synchronized(lock) {
            unsupported.clear()
        }
    }
}

private fun Element.selectSingleSafe(selector: String): List<Element> {
    if (selector.isBlank()) return emptyList()

    knownHasFallback(selector)?.let { return it }
    parseCaseInsensitiveAttributeSelector(selector)?.let { attrSelector ->
        return descendants().filter { it.matches(attrSelector) }
    }
    if (selector.startsWith(":scope >")) {
        return selectScopeDirect(selector)
    }

    return runCatching { select(selector).toList() }
        .getOrElse {
            SelectorDiagnostics.recordUnsupported(selector)
            emptyList()
        }
}

private fun Element.matchesSingleSafe(selector: String): Boolean {
    if (selector.isBlank()) return false

    parseCaseInsensitiveAttributeSelector(selector)?.let { attrSelector ->
        return matches(attrSelector)
    }

    return runCatching { this.`is`(selector) }
        .getOrElse {
            SelectorDiagnostics.recordUnsupported(selector)
            false
        }
}

private fun Element.selectScopeDirect(selector: String): List<Element> {
    val chain = selector.removePrefix(":scope >")
        .split(">")
        .map { it.trim() }
        .filter { it.isNotBlank() }
    if (chain.isEmpty()) return emptyList()

    var current = listOf(this)
    for (segment in chain) {
        current = current.flatMap { parent ->
            parent.childrenElements().filter { child -> child.matchesSingleSafe(segment) }
        }
    }
    return current
}

private fun Element.knownHasFallback(selector: String): List<Element>? = when (selector) {
    "audio:not([src]):not(:has(source))" -> select("audio")
        .filter { !it.hasAttr("src") && it.selectFirst("source") == null }

    "video:not([src]):not(:has(source))" -> select("video")
        .filter { !it.hasAttr("src") && it.selectFirst("source") == null }

    "header:not(:has(p + p)):not(:has(img))" -> select("header")
        .filter { it.selectFirst("p + p") == null && it.selectFirst("img") == null }

    "span:has(img)" -> select("span")
        .filter { it.selectFirst("img") != null }

    """p:has([class*="caption"])""" -> select("p")
        .filter { paragraph ->
            paragraph.descendants().any { descendant ->
                descendant.classNameSafe().contains("caption", ignoreCase = true)
            }
        }

    else -> null
}

private data class CaseInsensitiveAttributeSelector(val attr: String, val operator: String, val value: String)

private fun Element.matches(selector: CaseInsensitiveAttributeSelector): Boolean {
    val attrValue = attr(selector.attr)
    if (attrValue.isBlank()) return false
    return when (selector.operator) {
        "=" -> attrValue.equals(selector.value, ignoreCase = true)
        "*=" -> attrValue.contains(selector.value, ignoreCase = true)
        "^=" -> attrValue.startsWith(selector.value, ignoreCase = true)
        "$=" -> attrValue.endsWith(selector.value, ignoreCase = true)
        else -> false
    }
}

private fun parseCaseInsensitiveAttributeSelector(selector: String): CaseInsensitiveAttributeSelector? {
    val match = CASE_INSENSITIVE_ATTR_REGEX.matchEntire(selector.trim()) ?: return null
    val value = match.groupValues[3]
        .ifBlank { match.groupValues[4] }
        .ifBlank { match.groupValues[5] }
    return CaseInsensitiveAttributeSelector(
        attr = match.groupValues[1],
        operator = match.groupValues[2],
        value = value,
    )
}

private val CASE_INSENSITIVE_ATTR_REGEX =
    Regex("""^\[([\w:-]+)([*^$]?=)(?:"([^"]*)"|'([^']*)'|([^\]\s]+))\s+i]$""")

private fun String.splitSelectorList(): List<String> {
    val parts = mutableListOf<String>()
    var squareDepth = 0
    var parenDepth = 0
    var quote: Char? = null
    var start = 0

    forEachIndexed { index, char ->
        when {
            quote != null && char == quote -> quote = null

            quote != null -> Unit

            char == '"' || char == '\'' -> quote = char

            char == '[' -> squareDepth++

            char == ']' -> squareDepth--

            char == '(' -> parenDepth++

            char == ')' -> parenDepth--

            char == ',' && squareDepth == 0 && parenDepth == 0 -> {
                parts += substring(start, index).trim()
                start = index + 1
            }
        }
    }
    parts += substring(start).trim()
    return parts.filter { it.isNotBlank() }
}
