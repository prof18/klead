package com.prof18.klead.internal.removal

import org.jsoup.nodes.Element

internal fun isProtected(element: Element, hints: String = partialHaystack(element)): Boolean =
    element.`is`("pre, code, figure, picture, table, math, blockquote") ||
        element.parents().any { it.`is`("pre, code, figure, picture, table, math, blockquote") } ||
        element.parents().any { it.hasFootnoteProtectionHint() } ||
        element.select(".footdef, .footref, [role=doc-footnote]").isNotEmpty() ||
        "footnote" in hints ||
        "footnotes" in hints ||
        "footdef" in hints ||
        "footref" in hints ||
        "callout" in hints ||
        "admonition" in hints

internal fun Element.isNestedListContent(): Boolean =
    normalName() in setOf("ul", "ol") && parent()?.normalName() == "li"

internal fun Element.isPlainParagraphWithoutFooterSignal(): Boolean {
    if (normalName() != "p") return false

    val text = text().trim().collapseWhitespace()
    if (text.isBlank() || text.length > PARAGRAPH_FOOTER_SIGNAL_MAX_LENGTH) return true

    return !hasParagraphFooterSignal(text)
}

internal fun hasParagraphFooterSignal(text: String): Boolean = PARAGRAPH_FOOTER_SIGNAL_MATCHERS.any { it(text) }

internal fun Element.hasFootnoteProtectionHint(): Boolean {
    val role = attr("role").lowercase()
    if (role == "doc-footnote" || role == "doc-endnote") return true
    val hints = partialHaystack(this)
    return "footnote" in hints || "footnotes" in hints || "footdef" in hints || "footref" in hints
}

internal fun isLikelyProse(element: Element): Boolean {
    val paragraphs = element.select("p").count { it.text().split(WHITESPACE_PATTERN).size >= 8 }
    if (paragraphs >= 1) return true
    val text = element.text()
    val words = text.split(WHITESPACE_PATTERN).count { it.isNotBlank() }
    return words >= 35 && text.count { it == '.' || it == ',' } >= 2
}

internal fun String.collapseWhitespace(): String = replace(WHITESPACE_PATTERN, " ")

internal fun String.wordCount(): Int = split(WHITESPACE_PATTERN).count { it.isNotBlank() }

internal fun partialHaystack(element: Element): String = elementHintHaystack(element)
