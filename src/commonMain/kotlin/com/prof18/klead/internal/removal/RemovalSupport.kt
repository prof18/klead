package com.prof18.klead.internal.removal

import com.fleeksoft.ksoup.nodes.Element
import com.fleeksoft.ksoup.select.QueryParser

internal fun isProtected(element: Element, hints: String = partialHaystack(element)): Boolean =
    element.normalName() in PROTECTED_TAGS ||
        element.parents().any { it.normalName() in PROTECTED_TAGS } ||
        element.parents().any { it.hasCalloutProtectionHint() } ||
        element.parents().any { it.hasFootnoteProtectionHint() } ||
        element.selectFirst(FOOTNOTE_PART_EVALUATOR) != null ||
        "footnote" in hints ||
        "footnotes" in hints ||
        "footdef" in hints ||
        "footref" in hints ||
        "callout" in hints ||
        "admonition" in hints

private fun Element.hasCalloutProtectionHint(): Boolean {
    val hints = partialHaystack(this)
    return "callout" in hints || "admonition" in hints
}

internal fun Element.isNestedListContent(): Boolean =
    normalName() in setOf("ul", "ol") && parent()?.normalName() == "li"

internal fun BlockScan.isPlainParagraphWithoutFooterSignal(): Boolean {
    if (element.normalName() != "p") return false

    val text = collapsedText
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

internal fun isLikelyProse(scan: BlockScan): Boolean {
    val paragraphs = scan.element.select("p").count { it.text().split(WHITESPACE_PATTERN).size >= 8 }
    if (paragraphs >= 1) return true
    val text = scan.text
    val words = text.split(WHITESPACE_PATTERN).count { it.isNotBlank() }
    return words >= 35 && text.count { it == '.' || it == ',' } >= 2
}

internal fun String.collapseWhitespace(): String = replace(WHITESPACE_PATTERN, " ")

internal fun String.wordCount(): Int = split(WHITESPACE_PATTERN).count { it.isNotBlank() }

internal fun partialHaystack(element: Element): String = elementHintHaystack(element)

// isProtected runs for every removal candidate and checks each ancestor, so it avoids re-parsing selectors.
private val PROTECTED_TAGS = setOf("pre", "code", "figure", "picture", "table", "math", "blockquote")

// Class and attribute evaluators keep no per-match state, so one instance is safe to share across parses.
private val FOOTNOTE_PART_EVALUATOR = QueryParser.parse(".footdef, .footref, [role=doc-footnote]")
