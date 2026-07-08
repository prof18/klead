package com.prof18.klead.internal.standardize

import com.prof18.klead.internal.dom.replaceWithChildren
import org.jsoup.nodes.Element
import org.jsoup.nodes.TextNode

// Removes headings that duplicate the page title (and the byline/date chrome hugging them) and
// normalizes heading markup: permalink anchors, inline formatting, line breaks, h1 demotion.
internal object HtmlTitleNormalizer {
    fun normalizeHeadings(content: Element, title: String?) {
        removeHeadingPermalinkAnchors(content)
        removeLeadingDuplicateTitleWrapper(content, title)
        removeLeadingDuplicateTitleImage(content, title)
        val firstHeading = content.selectFirst("h1, h2")
        val titleMatch = if (title != null) firstHeading?.text()?.titleMatch(title) else null
        if (titleMatch != null && firstHeading != null) {
            val parent = firstHeading.parent()
            val shouldRemoveHeading = firstHeading.shouldRemoveMatchedTitleHeading(titleMatch, title)
            removeAdjacentHeadingChrome(firstHeading)
            if (shouldRemoveHeading) {
                firstHeading.remove()
                removeEmptyAncestors(parent, content)
            }
        }
        unwrapHeadingFormatting(content)
        flattenHeadingLineBreaks(content)
        content.select("h1").forEach { heading ->
            heading.tagName("h2")
        }
    }

    private fun removeLeadingDuplicateTitleImage(content: Element, title: String?) {
        if (title == null) return
        val first = content.children().firstOrNull { child ->
            child.normalName() != "br" &&
                (child.text().isNotBlank() || child.normalName() == "img" || child.selectFirst("img") != null)
        } ?: return

        val image = when (first.normalName()) {
            "img" -> first
            "a" -> first.children().singleOrNull { it.normalName() == "img" }
            else -> null
        } ?: return

        val label = image.attr("alt").trim().ifBlank { image.attr("title").trim() }
        if (!label.isDuplicateTitle(title)) return
        first.remove()
    }

    private fun removeHeadingPermalinkAnchors(content: Element) {
        content.select(HEADING_TAG_SELECTOR).forEach { heading ->
            heading.select(HEADING_PERMALINK_SELECTOR).remove()
        }
    }

    private fun unwrapHeadingFormatting(content: Element) {
        content.select(HEADING_TAG_SELECTOR).forEach { heading ->
            heading.select("strong, b, em, i").forEach { formatting ->
                formatting.replaceWithChildren()
            }
        }
    }

    private fun flattenHeadingLineBreaks(content: Element) {
        content.select(HEADING_TAG_SELECTOR).forEach { heading ->
            heading.select("br").forEach { lineBreak ->
                lineBreak.replaceWith(TextNode(" "))
            }
        }
    }

    private fun removeLeadingDuplicateTitleWrapper(content: Element, title: String?) {
        if (title == null) return
        for (child in content.children().toList()) {
            if (child.text().isBlank()) {
                continue
            }
            val heading = child.selectFirst("h1, h2") ?: return
            val titleMatch = heading.text().titleMatch(title) ?: return
            if (!child.isCompactTitleMetadataWrapper(heading, title)) return
            if (heading.shouldRemoveMatchedTitleHeading(titleMatch, title)) {
                child.remove()
            } else {
                child.replaceWith(heading.clone())
            }
            return
        }
    }

    private fun Element.isCompactTitleMetadataWrapper(heading: Element, title: String): Boolean {
        if (this === heading) return false
        if (hasNonMetadataSiblingHeading(heading, title)) return false
        val clone = clone()
        clone.select("h1, h2").remove()
        if (clone.select("p, pre, blockquote, table, figure, img, picture, iframe").isNotEmpty()) return false
        val remaining = clone.text().trim()
        return remaining.isBlank() ||
            (remaining.length <= COMPACT_TITLE_METADATA_MAX_LENGTH && remaining.isMetadataChromeText())
    }

    private fun Element.hasNonMetadataSiblingHeading(heading: Element, title: String): Boolean =
        select(HEADING_TAG_SELECTOR).any { sibling ->
            sibling !== heading &&
                sibling.text().trim().isNotBlank() &&
                sibling.text().titleMatch(title) == null &&
                !sibling.text().isMetadataChromeText()
        }

    private fun Element.shouldRemoveMatchedTitleHeading(titleMatch: TitleMatch, title: String?): Boolean {
        if (shouldPreserveMatchedTitleHeading(title)) return false
        return titleMatch == TitleMatch.EXACT ||
            titleMatch == TitleMatch.PREFIX_WITH_SITE_SUFFIX ||
            titleMatch == TitleMatch.SITE_PREFIX_WITH_TITLE
    }

    private fun Element.shouldPreserveMatchedTitleHeading(title: String?): Boolean {
        if (title == null) return false
        if (text().titleSuffix(title)?.let(REFERENCE_TITLE_SUFFIX::matches) == true) return true
        val hasTitleSuffix = text().titleSuffix(title) != null
        val hasSuffixHeadingContext = hasTitleSuffix &&
            (
                nextElementSibling()?.hasBylineHint() == true ||
                    nextElementSibling()?.hasArticleBodyHint() == true ||
                    isFollowedByNumberedSection() ||
                    isFollowedBySectionHeading()
            )
        return isFollowedByTableOfContents() || hasSuffixHeadingContext
    }

    private fun Element.isFollowedByTableOfContents(): Boolean {
        var sibling = nextElementSibling()
        repeat(TABLE_OF_CONTENTS_SCAN_LIMIT) {
            val current = sibling ?: return false
            if (current.normalName() == "ul" && current.isInternalTableOfContentsList()) return true
            if (current.normalName() in HEADING_TAG_NAMES) return false
            sibling = current.nextElementSibling()
        }
        return false
    }

    private fun Element.isInternalTableOfContentsList(): Boolean {
        val links = select("a[href]")
        return links.size >= TABLE_OF_CONTENTS_MIN_LINKS &&
            links.all { it.attr("href").trim().startsWith("#") }
    }

    private fun Element.isFollowedByNumberedSection(): Boolean {
        var sibling = nextElementSibling()
        repeat(NUMBERED_SECTION_SCAN_LIMIT) {
            val current = sibling ?: return false
            if (current.isNumberedSectionHeading()) return true
            sibling = current.nextElementSibling()
        }
        return false
    }

    private fun Element.isNumberedSectionHeading(): Boolean =
        normalName() in HEADING_TAG_NAMES && NUMBERED_SECTION_HEADING.matches(text().trim())

    private fun Element.isFollowedBySectionHeading(): Boolean {
        var sibling = nextElementSibling()
        repeat(NUMBERED_SECTION_SCAN_LIMIT) {
            val current = sibling ?: return false
            if (current.normalName() in HEADING_TAG_NAMES && current.headingLevel() > headingLevel()) return true
            sibling = current.nextElementSibling()
        }
        return false
    }

    private fun Element.headingLevel(): Int = normalName().removePrefix("h").toIntOrNull() ?: Int.MAX_VALUE

    private fun Element.hasBylineHint(): Boolean = listOf(id(), className(), attr("data-testid"), attr("itemprop"))
        .joinToString(" ")
        .lowercase()
        .contains("byline")

    private fun Element.hasArticleBodyHint(): Boolean {
        val hints = componentHintHaystack()
        return ARTICLE_BODY_HINTS.any { it in hints }
    }

    private fun removeAdjacentHeadingChrome(heading: Element) {
        var before = heading.previousElementSibling()
        while (before != null && before.isLeadingHeadingChrome()) {
            val previous = before.previousElementSibling()
            before.remove()
            before = previous
        }

        var after = heading.nextElementSibling()
        while (after != null && after.isTrailingHeadingChrome()) {
            val next = after.nextElementSibling()
            after.remove()
            after = next
        }
    }

    private fun Element.isLeadingHeadingChrome(): Boolean {
        if (hasBlockContentDescendant()) return false
        val text = text().trim()
        if (text.isBlank() || text.length > HEADING_CHROME_MAX_LENGTH) return false
        if (hasMetadataChromeHint()) return true
        return text.split(WHITESPACE_PATTERN).size <= 3 && !SENTENCE_PUNCTUATION.containsMatchIn(text)
    }

    private fun Element.isTrailingHeadingChrome(): Boolean {
        val text = text().trim()
        if (text.isBlank() || text.length > HEADING_CHROME_MAX_LENGTH) return false
        if (text.isMetadataChromeText()) return true
        if (hasBlockContentDescendant()) return false
        return hasMetadataChromeHint() && hasMetadataChromeEvidence()
    }

    private fun Element.hasMetadataChromeEvidence(): Boolean = select(
        "a[rel=author], a[href*=author], address, time, [datetime], [class*=author], [class*=byline], [class*=date]",
    )
        .isNotEmpty()

    private fun String.isDuplicateTitle(title: String): Boolean = titleMatch(title) != null

    private fun String.titleMatch(title: String): TitleMatch? {
        val heading = comparableTitle()
        val pageTitle = title.comparableTitle()
        if (heading == pageTitle) return TitleMatch.EXACT
        if (
            pageTitle.startsWith(heading) &&
            TITLE_SEPARATOR_PATTERN.containsMatchIn(pageTitle.removePrefix(heading).trimStart())
        ) {
            return TitleMatch.PREFIX_WITH_SITE_SUFFIX
        }
        if (
            pageTitle.endsWith(heading) &&
            TITLE_PREFIX_SEPARATOR_PATTERN.containsMatchIn(pageTitle.removeSuffix(heading).trimEnd())
        ) {
            return TitleMatch.SITE_PREFIX_WITH_TITLE
        }
        return null
    }

    private fun String.titleSuffix(title: String): String? {
        val heading = comparableTitle()
        val pageTitle = title.comparableTitle()
        return pageTitle
            .takeIf { it.startsWith(heading) }
            ?.removePrefix(heading)
            ?.trimStart()
            ?.takeIf { TITLE_SEPARATOR_PATTERN.containsMatchIn(it) }
    }

    private fun String.comparableTitle(): String = trim()
        .replace('’', '\'')
        .replace('‘', '\'')
        .replace('“', '"')
        .replace('”', '"')
        .replace(WHITESPACE_PATTERN, " ")
        .lowercase()

    private enum class TitleMatch {
        EXACT,
        PREFIX_WITH_SITE_SUFFIX,
        SITE_PREFIX_WITH_TITLE,
    }

    private val TITLE_SEPARATOR_PATTERN = Regex("""^[|:\-–—]\s+\S+""")
    private val TITLE_PREFIX_SEPARATOR_PATTERN = Regex("""\S\s*:$""")
    private val REFERENCE_TITLE_SUFFIX = Regex("""^[|:\-–—]\s+(?:javascript|typescript|css|html|web api|mdn)\b.*""")
    private val NUMBERED_SECTION_HEADING = Regex("""^\d{1,3}[.)]?\s+\S+.*""")
    private val SENTENCE_PUNCTUATION = Regex("""[.!?]""")
    private val HEADING_TAG_NAMES = setOf("h1", "h2", "h3", "h4", "h5", "h6")
    private val ARTICLE_BODY_HINTS = setOf(
        "article-body",
        "article-content",
        "entry-content",
        "post-content",
    )
    private const val HEADING_PERMALINK_SELECTOR =
        "a[href^=#].anchor, a[href^=#].permalink, a[href^=#].headerlink, " +
            "a[href^=#][aria-hidden=true], a[href^=#][title*=Permanent], " +
            ".permalink-widget, .header-anchor-parent, [class*=permalink], [aria-label=Link]"
    private const val COMPACT_TITLE_METADATA_MAX_LENGTH = 160
    private const val TABLE_OF_CONTENTS_MIN_LINKS = 3
    private const val TABLE_OF_CONTENTS_SCAN_LIMIT = 4
    private const val NUMBERED_SECTION_SCAN_LIMIT = 4
}
