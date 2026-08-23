package com.prof18.klead.internal.removal

import com.fleeksoft.ksoup.nodes.Element
import com.prof18.klead.RemovalRecord
import com.prof18.klead.internal.dom.isAttachedTo

internal object TrailingContentPatterns {
    fun remove(content: Element, debug: MutableList<RemovalRecord>, checkCancelled: () -> Unit = {}) {
        // Both predicates guard on text length far below the chrome caps, so this skip is
        // exact — it just avoids building the text of large containers.
        val caps = ChromeBlockCaps.compute(content)
        for (element in content.descendantsWithTagNamesSnapshot(TRAILING_CONTENT_TAGS)) {
            checkCancelled()
            if (!element.isAttachedTo(content)) continue
            if (caps.exceeds(element)) continue
            val reason = footerReason(BlockScan(element)) ?: continue
            recordAndRemove(element, debug, "removeContentPatterns", null, reason)
        }
    }

    private fun footerReason(scan: BlockScan): String? = when {
        scan.isTrailingMarketingCtaBlock() -> "marketing call to action"
        scan.isTrailingArticleMetadataList() -> "article metadata list"
        else -> null
    }

    private fun BlockScan.isTrailingMarketingCtaBlock(): Boolean {
        if (element.normalName() !in TRAILING_MARKETING_CTA_TAGS) return false
        val text = collapsedText
        if (text.isBlank() || text.length > TRAILING_MARKETING_CTA_MAX_LENGTH) return false

        val hints = selfAndSubtreeHints
        if (TRAILING_MARKETING_CTA_HINTS.none { it in hints }) return false
        if (!TRAILING_MARKETING_CTA_PATTERN.containsMatchIn(text)) return false

        return element.select("h1, h2, h3, h4, h5, h6, form, input, button, svg, img").isNotEmpty()
    }

    private fun BlockScan.isTrailingArticleMetadataList(): Boolean {
        if (element.normalName() !in TRAILING_METADATA_LIST_TAGS) return false
        val text = collapsedText
        if (text.isBlank() || text.length > TRAILING_METADATA_LIST_MAX_LENGTH) return false
        if (!TRAILING_METADATA_PATTERN.containsMatchIn(text)) return false

        val items = element.children().filter { it.normalName() == "li" }
        if (items.isEmpty() || items.any { it.text().trim().wordCount() > TRAILING_METADATA_ITEM_MAX_WORDS }) {
            return false
        }

        val hints = haystack
        return "metadata" in hints ||
            "meta" in hints ||
            "byline" in hints ||
            "share" in hints ||
            element.select("svg, time, [datetime]").isNotEmpty()
    }

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
