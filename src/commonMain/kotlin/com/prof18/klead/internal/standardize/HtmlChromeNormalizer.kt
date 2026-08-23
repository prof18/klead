package com.prof18.klead.internal.standardize

import com.fleeksoft.ksoup.nodes.Element

// Removes standalone metadata chrome — bare dates, read times, "By" labels, timezone widgets,
// live-update labels, edge dividers — and trailing boilerplate sections like "See also".
internal object HtmlChromeNormalizer {
    fun removeMetadataChrome(content: Element) {
        removeStandaloneTimeChrome(content)
        removeStandaloneDateHeadings(content)
        removeLeadingMetadataChrome(content)
        removeStandaloneLiveUpdateLabels(content)
        removeEdgeDividers(content)
    }

    fun removeTrailingSectionHeadings(content: Element) {
        content.select(HEADING_TAG_SELECTOR).forEach { heading ->
            val text = heading.text().trim()
            if (!TRAILING_REMOVABLE_SECTION_HEADING.matches(text)) return@forEach
            if (text.equals(FURTHER_READING_HEADING, ignoreCase = true)) {
                if (heading.isTrailingRemovableSection()) {
                    heading.removeTrailingRemovableSection()
                }
            } else if (!heading.hasSubstantiveTail()) {
                heading.remove()
            }
        }
    }

    private fun removeStandaloneTimeChrome(content: Element) {
        removeEdgeChrome(content, fromStart = true)
        removeEdgeChrome(content, fromStart = false)
        content.select("span, p, div").forEach { element ->
            if (element.isStandaloneDateReadTimeTextChrome()) {
                element.remove()
            }
        }
    }

    private fun removeStandaloneDateHeadings(content: Element) {
        content.select(HEADING_TAG_SELECTOR).forEach { heading ->
            if (heading.isStandaloneDateHeading()) {
                heading.remove()
            }
        }
    }

    private fun removeEdgeChrome(content: Element, fromStart: Boolean) {
        while (true) {
            val child = if (fromStart) content.children().firstOrNull() else content.children().lastOrNull()
            if (child == null || !child.isStandaloneTimeChrome()) return
            child.remove()
        }
    }

    private fun removeEdgeDividers(content: Element) {
        while (content.children().firstOrNull()?.normalName() == "hr") {
            content.children().first()?.remove()
        }
        while (content.children().lastOrNull()?.normalName() == "hr") {
            content.children().last()?.remove()
        }
    }

    private fun removeLeadingMetadataChrome(content: Element) {
        trimLeadingMetadataChrome(content)
        content.select("article, main, section, header, div").forEach(::trimLeadingMetadataChrome)
    }

    private fun trimLeadingMetadataChrome(container: Element) {
        while (true) {
            val first = container.children().firstOrNull() ?: return
            if (!first.isLeadingMetadataChromeBlock()) return
            if (!container.children().drop(1).any { it.hasSubstantiveContentBlock() }) return
            first.remove()
        }
    }

    private fun removeStandaloneLiveUpdateLabels(content: Element) {
        content.select("div, span, p").forEach { element ->
            if (element.isStandaloneLiveUpdateLabel()) {
                element.remove()
            }
        }
    }

    private fun Element.isStandaloneTimeChrome(): Boolean {
        val text = text().trim()
        if (text.isBlank() || text.length > HEADING_CHROME_MAX_LENGTH) return false
        if (select("time, relative-time, [datetime]").isEmpty()) return false
        if (select("a[href], img, picture, figure, pre, code, table, blockquote, iframe").isNotEmpty()) return false
        return text.isMetadataChromeText() || DAY_MONTH_ENGLISH_DATE_PATTERN.matches(text)
    }

    private fun Element.isStandaloneDateReadTimeTextChrome(): Boolean {
        val text = text().trim()
        if (text.isBlank() || text.length > HEADING_CHROME_MAX_LENGTH) return false
        if (select("a[href], img, picture, figure, pre, code, table, blockquote, iframe").isNotEmpty()) return false
        if (children().any { it.text().trim() != text }) return false
        return DATE_TEXT_PATTERN.containsMatchIn(text) && READ_TIME_PATTERN.containsMatchIn(text)
    }

    private fun Element.isStandaloneDateHeading(): Boolean {
        if (!normalName().matches(HEADING_TAG_PATTERN)) return false
        if (select("a[href], img, picture, figure, pre, code, table, blockquote, iframe").isNotEmpty()) return false
        return FULL_DATE_HEADING_PATTERN.matches(text().trim())
    }

    private fun Element.isStandaloneLiveUpdateLabel(): Boolean {
        if (!text().trim().equals("Pinned", ignoreCase = true)) return false
        if (select("svg").isEmpty()) return false
        return parents().any { it.attr("role") == "feed" || it.attr("role") == "article" }
    }

    private fun Element.isLeadingMetadataChromeBlock(): Boolean {
        if (isLeadingFrontMatterMetadataBlock() || isLeadingTimeZoneWidget() || isLeadingByLabelBeforeDate()) {
            return true
        }
        val text = text().trim()
        if (text.isBlank() || text.length > HEADING_CHROME_MAX_LENGTH) return false
        if (hasBlockContentDescendant()) return false
        return hasMetadataChromeHint() &&
            (
                text.isMetadataChromeText() ||
                    select("a[rel=author], a[href*=author]").isNotEmpty()
            )
    }

    private fun Element.isLeadingByLabelBeforeDate(): Boolean {
        if (!text().trim().equals("By", ignoreCase = true)) return false
        if (select("a[href], img, picture, figure, pre, code, table, blockquote, iframe").isNotEmpty()) return false
        val nextText = nextElementSibling()?.text()?.trim().orEmpty()
        return nextText.length <= HEADING_CHROME_MAX_LENGTH && DATE_TEXT_PATTERN.containsMatchIn(nextText)
    }

    private fun Element.isLeadingFrontMatterMetadataBlock(): Boolean {
        val text = text().trim()
        if (text.isBlank()) return false
        if (!hasFrontMatterHint()) return false
        return select("a[rel=author], address, time, [datetime], hr, [class*=author], [class*=date], [class*=bio]")
            .isNotEmpty()
    }

    private fun Element.isLeadingTimeZoneWidget(): Boolean {
        if (!hasTimeZoneHint()) return false
        val text = text().trim()
        if (text.isBlank()) return false
        return text.contains("current time", ignoreCase = true) || CLOCK_TIME_PATTERN.containsMatchIn(text)
    }

    private fun Element.hasSubstantiveContentBlock(): Boolean {
        if (text().isBlank()) return false
        if (normalName() == "hr" || hasAttr("data-footnotes")) return false
        if (isLeadingMetadataChromeBlock()) return false
        return true
    }

    private fun Element.isHeading(): Boolean = normalName().matches(HEADING_TAG_PATTERN)

    private fun Element.hasSubstantiveTail(): Boolean {
        var sibling = nextElementSibling()
        while (sibling != null) {
            val text = sibling.text().trim()
            if (text.isBlank() || sibling.hasAttr("data-footnotes")) {
                sibling = sibling.nextElementSibling()
                continue
            }
            if (sibling.isHeading() && TRAILING_REMOVABLE_SECTION_HEADING.matches(text)) {
                sibling = sibling.nextElementSibling()
                continue
            }
            return true
        }
        return false
    }

    private fun Element.isTrailingRemovableSection(): Boolean {
        var sibling = nextElementSibling()
        while (sibling != null) {
            val text = sibling.text().trim()
            if (text.isNotBlank() && sibling.isHeading() && !TRAILING_REMOVABLE_SECTION_HEADING.matches(text)) {
                return false
            }
            sibling = sibling.nextElementSibling()
        }
        return true
    }

    private fun Element.removeTrailingRemovableSection() {
        var sibling = nextElementSibling()
        remove()
        while (sibling != null) {
            val next = sibling.nextElementSibling()
            val text = sibling.text().trim()
            if (sibling.hasAttr("data-footnotes")) break
            if (text.isNotBlank() && sibling.isHeading() && !TRAILING_REMOVABLE_SECTION_HEADING.matches(text)) break
            sibling.remove()
            sibling = next
        }
    }

    private fun Element.hasFrontMatterHint(): Boolean {
        val haystack = componentHintHaystack()
            .replace("-", "")
            .replace("_", "")
        return "frontmatter" in haystack
    }

    private fun Element.hasTimeZoneHint(): Boolean {
        val haystack = componentHintHaystack()
            .replace("-", "")
            .replace("_", "")
        return "timezone" in haystack
    }

    private val DAY_MONTH_ENGLISH_DATE_PATTERN = Regex(
        """\d{1,2}\s+(?:jan|feb|mar|apr|may|jun|jul|aug|sep|sept|oct|nov|dec)[a-z]*\.?,?\s+\d{4}""",
        RegexOption.IGNORE_CASE,
    )
    private val CLOCK_TIME_PATTERN = Regex("""\b\d{1,2}:\d{2}\s*(?:a\.m\.|p\.m\.|am|pm)?\b""", RegexOption.IGNORE_CASE)
    private val FULL_DATE_HEADING_PATTERN = Regex(
        """(?:mon|tues|wednes|thurs|fri|satur|sun)day,\s+(?:jan|feb|mar|apr|may|jun|jul|aug|sep|sept|oct|nov|dec)[a-z]*\.?\s+\d{1,2},\s+\d{4}""",
        RegexOption.IGNORE_CASE,
    )
    private val TRAILING_REMOVABLE_SECTION_HEADING = Regex("""(?i)^(further reading|next steps|see also|references)$""")
    private const val FURTHER_READING_HEADING = "Further Reading"
}
