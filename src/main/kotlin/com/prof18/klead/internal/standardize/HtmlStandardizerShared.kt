package com.prof18.klead.internal.standardize

import com.prof18.klead.internal.dom.attrTrimmedOrNull
import org.jsoup.nodes.Element

internal val HEADING_TAG_PATTERN = Regex("""h[1-6]""")
internal val EMPTY_WRAPPER_TAGS = setOf("div", "section", "header")
internal val WHITESPACE_PATTERN = Regex("""\s+""")

internal fun String.collapseWhitespace(): String = replace(WHITESPACE_PATTERN, " ")

internal fun firstAttr(element: Element, vararg names: String): String? = names.firstNotNullOfOrNull { name ->
    element.attrTrimmedOrNull(name)
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

internal const val HEADING_TAG_SELECTOR = "h1, h2, h3, h4, h5, h6"
internal const val HEADING_CHROME_MAX_LENGTH = 120
internal const val BLOCK_CONTENT_SELECTOR = "p, pre, blockquote, table, figure, img, picture, iframe"

internal fun Element.hasBlockContentDescendant(): Boolean = select(BLOCK_CONTENT_SELECTOR).any { it !== this }

internal fun Element.hasMetadataChromeHint(): Boolean {
    val haystack = componentHintHaystack()
    return METADATA_CHROME_HINTS.any { it in haystack }
}

internal fun String.isMetadataChromeText(): Boolean = DATE_TEXT_PATTERN.containsMatchIn(this) ||
    READ_TIME_PATTERN.containsMatchIn(this) ||
    BYLINE_TEXT_PATTERN.containsMatchIn(this)

internal val DATE_TEXT_PATTERN = Regex(
    """\b(?:\d{4}-\d{1,2}-\d{1,2}|(?:jan|feb|mar|apr|may|jun|jul|aug|sep|sept|oct|nov|dec)[a-z]*\.?\s+\d{1,2},?\s+\d{4}|\d{1,2}\s+(?:gennaio|febbraio|marzo|aprile|maggio|giugno|luglio|agosto|settembre|ottobre|novembre|dicembre)\s+\d{4})\b""",
    RegexOption.IGNORE_CASE,
)
internal val READ_TIME_PATTERN = Regex("""\b\d+\s+min(?:ute)?s?\s+read\b""", RegexOption.IGNORE_CASE)
internal val BYLINE_TEXT_PATTERN = Regex("""^by\s+\S+""", RegexOption.IGNORE_CASE)

private val METADATA_CHROME_HINTS = setOf(
    "author",
    "byline",
    "category",
    "date",
    "eyebrow",
    "kicker",
    "metadata",
    "publish",
    "read-time",
    "timestamp",
)
