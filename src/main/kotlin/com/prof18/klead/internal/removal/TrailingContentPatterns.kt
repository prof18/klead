package com.prof18.klead.internal.removal

import com.prof18.klead.RemovalRecord
import org.jsoup.nodes.Element

internal object TrailingContentPatterns {
    fun remove(content: Element, debug: MutableList<RemovalRecord>) {
        for (element in content.descendantsWithTagNamesSnapshot(TRAILING_CONTENT_TAGS)) {
            if (!element.isAttachedTo(content)) continue
            val reason = footerReason(element) ?: continue
            recordAndRemove(element, debug, "removeContentPatterns", null, reason)
        }
    }

    private fun footerReason(element: Element): String? = when {
        element.isTrailingMarketingCtaBlock() -> "marketing call to action"
        element.isTrailingArticleMetadataList() -> "article metadata list"
        else -> null
    }

    private fun Element.isTrailingMarketingCtaBlock(): Boolean {
        if (normalName() !in TRAILING_MARKETING_CTA_TAGS) return false
        val text = text().trim().collapseWhitespace()
        if (text.isBlank() || text.length > TRAILING_MARKETING_CTA_MAX_LENGTH) return false

        val hints = "${partialHaystack()} ${select("*").joinToString(" ") { it.partialHaystack() }}"
        if (TRAILING_MARKETING_CTA_HINTS.none { it in hints }) return false
        if (!TRAILING_MARKETING_CTA_PATTERN.containsMatchIn(text)) return false

        return select("h1, h2, h3, h4, h5, h6, form, input, button, svg, img").isNotEmpty()
    }

    private fun Element.isTrailingArticleMetadataList(): Boolean {
        if (normalName() !in TRAILING_METADATA_LIST_TAGS) return false
        val text = text().trim().collapseWhitespace()
        if (text.isBlank() || text.length > TRAILING_METADATA_LIST_MAX_LENGTH) return false
        if (!TRAILING_METADATA_PATTERN.containsMatchIn(text)) return false

        val items = children().filter { it.normalName() == "li" }
        if (items.isEmpty() || items.any { it.text().trim().wordCount() > TRAILING_METADATA_ITEM_MAX_WORDS }) {
            return false
        }

        val hints = partialHaystack()
        return "metadata" in hints ||
            "meta" in hints ||
            "byline" in hints ||
            "share" in hints ||
            select("svg, time, [datetime]").isNotEmpty()
    }

    private fun Element.partialHaystack(): String = elementHintHaystack(this)

    private fun String.collapseWhitespace(): String = replace(WHITESPACE_PATTERN, " ")

    private fun String.wordCount(): Int = split(WHITESPACE_PATTERN).count { it.isNotBlank() }

    private val WHITESPACE_PATTERN = Regex("""\s+""")
    private val TRAILING_CONTENT_TAGS = setOf("aside", "div", "ol", "section", "ul")
    private val TRAILING_MARKETING_CTA_TAGS = setOf("aside", "div", "section")
    private val TRAILING_METADATA_LIST_TAGS = setOf("ol", "ul")
    private const val TRAILING_MARKETING_CTA_MAX_LENGTH = 280
    private const val TRAILING_METADATA_LIST_MAX_LENGTH = 140
    private const val TRAILING_METADATA_ITEM_MAX_WORDS = 6

    private val TRAILING_MARKETING_CTA_HINTS = setOf(
        "cta",
        "call-to-action",
        "newsletter",
        "subscribe",
        "signup",
        "promo",
    )

    private val TRAILING_MARKETING_CTA_PATTERN = Regex(
        """\b(help\s+your\s+team|delivered\s+.{0,60}\binbox|newsletter|subscribe|sign\s+up|try\s+.{0,30}\bfree|book\s+a\s+demo|get\s+started)\b""",
        RegexOption.IGNORE_CASE,
    )

    private val TRAILING_METADATA_PATTERN = Regex(
        """\b(\d{4}|jan|feb|mar|apr|may|jun|jul|aug|sep|sept|oct|nov|dec|\d+\s+min|read\s+time|share)\b""",
        RegexOption.IGNORE_CASE,
    )
}
