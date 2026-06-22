package com.prof18.klead.internal.standardize

import com.prof18.klead.internal.dom.replaceWithChildren
import com.prof18.klead.internal.dom.textTrimmedOrNull
import com.prof18.klead.internal.media.TrustedEmbeds
import com.prof18.klead.internal.media.TrustedMarkdownMedia
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode

@Suppress("LargeClass")
internal object HtmlStandardizer {
    fun apply(content: Element, title: String?) {
        normalizeVideoEmbeds(content)
        normalizeCallouts(content)
        normalizeHeadings(content, title)
        normalizeArxivBibliographyCitations(content)
        unwrapArxivCrossReferenceLinks(content)
        normalizeArxivFootnoteMarks(content)
        removeStandaloneTimeChrome(content)
        removeStandaloneDateHeadings(content)
        removeLeadingMetadataChrome(content)
        removeStandaloneLiveUpdateLabels(content)
        removeEdgeDividers(content)
        HtmlCodeNormalizer.normalizeCodeBlocks(content)
        HtmlImageNormalizer.normalizeImages(content)
        HtmlImageNormalizer.normalizeImageAspectPlaceholders(content)
        HtmlFootnoteNormalizer.normalizeFootnotes(content)
        removeTrailingSectionHeadings(content)
        normalizeTables(content)
        removeEmptyWrappers(content)
    }

    private fun normalizeHeadings(content: Element, title: String?) {
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

    private fun unwrapArxivCrossReferenceLinks(content: Element) {
        content.select("a.ltx_ref[href^=#]").forEach { link ->
            link.replaceWithChildren()
        }
    }

    private fun normalizeArxivBibliographyCitations(content: Element) {
        val bibliographyTargets = linkedMapOf<String, String>()
        content.select(".ltx_bibliography").forEach { bibliography ->
            val items = bibliography.select(".ltx_bibitem[id]")
            if (items.isEmpty()) return@forEach

            bibliography.attr("data-footnotes", "true").addClass("footnotes")
            bibliography.select(".ltx_biblist").forEach { list ->
                if (list.normalName() == "ul") {
                    list.tagName("ol")
                }
            }
            items.forEach { item ->
                val footnoteId = "fn${bibliographyTargets.size + 1}"
                bibliographyTargets[item.id()] = footnoteId
                item.attr("id", footnoteId)
                item.select(".ltx_tag_bibitem").remove()
            }
        }

        if (bibliographyTargets.isEmpty()) return

        content.select("cite.ltx_cite").forEach { citation ->
            val targets = citation.select("a[href]").mapNotNull { link ->
                val target = link.hrefFragmentTarget()?.let(bibliographyTargets::get)
                target?.let { it to link.text().trim() }
            }
            if (targets.isEmpty()) return@forEach

            targets.forEachIndexed { index, (target, label) ->
                if (index > 0) {
                    citation.before(TextNode(" "))
                }
                citation.before(
                    Element("sup").appendChild(
                        Element("a")
                            .attr("href", "#$target")
                            .text(label.ifBlank { target.removePrefix("fn") }),
                    ),
                )
            }
            citation.remove()
        }
    }

    private fun normalizeArxivFootnoteMarks(content: Element) {
        content.select(".ltx_role_footnotemark").forEach { mark ->
            mark.select(".ltx_note_outer").remove()
            if (mark.needsLeadingSpaceBeforeInlineFootnoteMark()) {
                mark.before(TextNode(" "))
            }
        }
    }

    private fun Element.needsLeadingSpaceBeforeInlineFootnoteMark(): Boolean {
        val previous = previousSibling() ?: return false
        val previousText = when (previous) {
            is TextNode -> previous.wholeText
            is Element -> previous.text()
            else -> ""
        }
        val previousChar = previousText.lastOrNull { !it.isWhitespace() } ?: return false
        return previousChar.isLetterOrDigit()
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

    private fun removeTrailingSectionHeadings(content: Element) {
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

    private fun removeStandaloneLiveUpdateLabels(content: Element) {
        content.select("div, span, p").forEach { element ->
            if (element.isStandaloneLiveUpdateLabel()) {
                element.remove()
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

    private fun Element.hasBlockContentDescendant(): Boolean = select(BLOCK_CONTENT_SELECTOR).any { it !== this }

    private fun Element.hasMetadataChromeHint(): Boolean {
        val haystack = componentHintHaystack()
        return METADATA_CHROME_HINTS.any { it in haystack }
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

    private fun String.isMetadataChromeText(): Boolean = DATE_TEXT_PATTERN.containsMatchIn(this) ||
        READ_TIME_PATTERN.containsMatchIn(this) ||
        BYLINE_TEXT_PATTERN.containsMatchIn(this)

    private fun normalizeVideoEmbeds(content: Element) {
        content.select("iframe[src]").forEach { iframe ->
            val media = TrustedEmbeds.markdownMediaFromUrl(iframe.attr("src")) ?: return@forEach
            val normalizedSrc = media.normalizedIframeSrc
            if (normalizedSrc == null) {
                iframe.attr("data-klead-video-url", media.watchUrl)
            } else {
                val title = iframe.attr("title").trim().ifBlank { media.defaultTitle }
                val preserveLeadingSpacer = iframe.hasAttr("data-klead-leading-spacer")
                iframe.clearAttributes()
                applyVideoAttributes(iframe, media, title)
                if (preserveLeadingSpacer) {
                    iframe.attr("data-klead-leading-spacer", "true")
                }
            }
        }

        content.select(".hidden_video[data-video-id]").forEach { placeholder ->
            val video = TrustedEmbeds.youtubeVideoFromId(placeholder.attr("data-video-id"))
                ?: TrustedEmbeds.markdownMediaFromUrl(
                    placeholder.selectFirst(
                        """a[href*="youtube.com/watch"], a[href*="youtu.be/"]""",
                    )?.attr("href").orEmpty(),
                )
                ?: return@forEach
            if (video.normalizedIframeSrc == null) return@forEach
            val iframe = Element("iframe")
            applyVideoAttributes(iframe, video, video.defaultTitle)
            placeholder.replaceWith(iframe)
        }
    }

    private fun applyVideoAttributes(iframe: Element, video: TrustedMarkdownMedia, title: String) {
        iframe.attr("src", video.normalizedIframeSrc.orEmpty())
        iframe.attr("title", title.ifBlank { video.defaultTitle })
        iframe.attr("loading", "lazy")
        iframe.attr("allowfullscreen", "")
        iframe.attr(
            "allow",
            "accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture; web-share",
        )
        iframe.attr("data-klead-video-url", video.watchUrl)
    }

    private fun normalizeCallouts(content: Element) {
        normalizeStructuredCallouts(content)
        content.select("blockquote").forEach { blockquote ->
            val firstParagraph = blockquote.selectFirst("p") ?: return@forEach
            val marker = CALLOUT_MARKER.matchEntire(firstParagraph.text().trim()) ?: return@forEach
            val type = marker.groupValues[1].lowercase()
            firstParagraph.remove()
            blockquote.tagName("div")
            blockquote.addClass("callout")
            blockquote.attr("data-callout", type)

            val existingChildren = blockquote.childNodes().toList()
            blockquote.empty()
            blockquote.appendElement("div")
                .addClass("callout-title")
                .appendElement("div")
                .addClass("callout-title-inner")
                .text(type.replaceFirstChar { it.uppercase() })
            val body = blockquote.appendElement("div").addClass("callout-content")
            existingChildren.forEach { body.appendChild(it) }
        }
    }

    private fun normalizeStructuredCallouts(content: Element) {
        content.select(".alert").forEach { alert ->
            if (alert.hasAttr("data-callout")) return@forEach
            val type = alert.classNames()
                .firstOrNull { it.startsWith("alert-") && it != "alert" }
                ?.removePrefix("alert-")
                ?.lowercase()
                ?: "note"
            val titleElement = alert.selectFirst(".alert-heading, .alert-title")
            val title = titleElement?.textTrimmedOrNull() ?: type.replaceFirstChar { it.uppercase() }
            val bodyNodes = alert.childNodes().filterNot { it === titleElement }.toList()
            alert.rebuildCallout(type, title, bodyNodes)
        }

        content.select(".admonition").forEach { admonition ->
            if (admonition.hasAttr("data-callout")) return@forEach
            val type = admonition.classNames()
                .firstOrNull { it !in ADMONITION_CLASS_BLACKLIST }
                ?.lowercase()
                ?: "note"
            val titleElement = admonition.selectFirst(".admonition-title, .details-summary")
            val title = titleElement?.ownText()?.trim()?.ifBlank {
                titleElement.text().trim()
            }?.ifBlank { null } ?: type.replaceFirstChar { it.uppercase() }
            val contentElement = admonition.selectFirst(".admonition-content, .details-content")
            val bodyNodes = contentElement?.childNodes()?.toList()
                ?: admonition.childNodes().filterNot { it === titleElement }.toList()
            admonition.rebuildCallout(type, title, bodyNodes)
        }

        content.select(".callout").forEach { callout ->
            if (!callout.hasAttr("data-callout")) {
                val type = callout.classNames()
                    .firstOrNull { it.startsWith("callout-") && it != "callout" }
                    ?.removePrefix("callout-")
                    ?.lowercase()
                if (type != null) callout.attr("data-callout", type)
            }
        }
    }

    private fun Element.rebuildCallout(type: String, title: String, bodyNodes: List<Node>) {
        bodyNodes.forEach(Node::remove)
        empty()
        tagName("div")
        addClass("callout")
        attr("data-callout", type)
        appendElement("div")
            .addClass("callout-title")
            .appendElement("div")
            .addClass("callout-title-inner")
            .text(title)
        val body = appendElement("div").addClass("callout-content")
        bodyNodes.forEach { body.appendChild(it) }
    }

    private fun normalizeTables(content: Element) {
        content.select("table").forEach { table ->
            val cells = table.directTableCells()
            if (table.hasClass("layout") || cells.size == 1) {
                val nodes = cells.firstOrNull()?.childNodes()?.toList().orEmpty()
                nodes.forEach { table.before(it) }
                table.remove()
            }
        }
    }

    private fun Element.directTableCells(): List<Element> {
        val rows = children().flatMap { child ->
            when (child.normalName()) {
                "tr" -> listOf(child)
                "tbody", "thead", "tfoot" -> child.children().filter { it.normalName() == "tr" }
                else -> emptyList()
            }
        }
        return rows.flatMap { row ->
            row.children().filter { it.normalName() == "td" || it.normalName() == "th" }
        }
    }

    private fun removeEmptyWrappers(content: Element) {
        content.select("span, div").toList().asReversed().forEach { element ->
            if (element.isInsidePreformattedCode()) return@forEach
            if (element.hasInternalKleadAttribute()) return@forEach
            if (element.children().isEmpty() && element.isNonBreakingSpaceWrapper()) {
                element.replaceWith(TextNode(" "))
            } else if (element.children().isEmpty() && element.text().isBlank()) {
                element.remove()
            } else if (element.tagName() == "span" && element.attributes().isEmpty()) {
                element.replaceWithChildren()
            }
        }
        content.childNodes().filterIsInstance<TextNode>().forEach { text ->
            if (text.text().isBlank()) text.remove()
        }
    }

    private fun Element.hasInternalKleadAttribute(): Boolean =
        attributes().asList().any { attribute -> attribute.key.startsWith("data-klead-") }

    private fun Element.isInsidePreformattedCode(): Boolean = normalName() == "code" ||
        normalName() == "pre" ||
        parents().any { it.normalName() == "code" || it.normalName() == "pre" }

    private fun Element.isNonBreakingSpaceWrapper(): Boolean =
        wholeText().isNotEmpty() && wholeText().all { it == '\u00A0' || it == '\u202F' }

    private val ADMONITION_CLASS_BLACKLIST = setOf(
        "admonition",
        "details",
        "open",
    )

    private enum class TitleMatch {
        EXACT,
        PREFIX_WITH_SITE_SUFFIX,
        SITE_PREFIX_WITH_TITLE,
    }

    private val CALLOUT_MARKER = Regex("""\[!(\w+)]""")
    private val TITLE_SEPARATOR_PATTERN = Regex("""^[|:\-–—]\s+\S+""")
    private val TITLE_PREFIX_SEPARATOR_PATTERN = Regex("""\S\s*:$""")
    private val REFERENCE_TITLE_SUFFIX = Regex("""^[|:\-–—]\s+(?:javascript|typescript|css|html|web api|mdn)\b.*""")
    private val NUMBERED_SECTION_HEADING = Regex("""^\d{1,3}[.)]?\s+\S+.*""")
    private val DATE_TEXT_PATTERN = Regex(
        """\b(?:\d{4}-\d{1,2}-\d{1,2}|(?:jan|feb|mar|apr|may|jun|jul|aug|sep|sept|oct|nov|dec)[a-z]*\.?\s+\d{1,2},?\s+\d{4}|\d{1,2}\s+(?:gennaio|febbraio|marzo|aprile|maggio|giugno|luglio|agosto|settembre|ottobre|novembre|dicembre)\s+\d{4})\b""",
        RegexOption.IGNORE_CASE,
    )
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
    private val READ_TIME_PATTERN = Regex("""\b\d+\s+min(?:ute)?s?\s+read\b""", RegexOption.IGNORE_CASE)
    private val BYLINE_TEXT_PATTERN = Regex("""^by\s+\S+""", RegexOption.IGNORE_CASE)
    private val SENTENCE_PUNCTUATION = Regex("""[.!?]""")
    private val HEADING_TAG_NAMES = setOf("h1", "h2", "h3", "h4", "h5", "h6")
    private val ARTICLE_BODY_HINTS = setOf(
        "article-body",
        "article-content",
        "entry-content",
        "post-content",
    )
    private const val HEADING_TAG_SELECTOR = "h1, h2, h3, h4, h5, h6"
    private const val HEADING_PERMALINK_SELECTOR =
        "a[href^=#].anchor, a[href^=#].permalink, a[href^=#].headerlink, " +
            "a[href^=#][aria-hidden=true], a[href^=#][title*=Permanent], " +
            ".permalink-widget, .header-anchor-parent, [class*=permalink], [aria-label=Link]"
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
    private const val COMPACT_TITLE_METADATA_MAX_LENGTH = 160
    private const val HEADING_CHROME_MAX_LENGTH = 120
    private const val TABLE_OF_CONTENTS_MIN_LINKS = 3
    private const val TABLE_OF_CONTENTS_SCAN_LIMIT = 4
    private const val NUMBERED_SECTION_SCAN_LIMIT = 4
    private const val BLOCK_CONTENT_SELECTOR = "p, pre, blockquote, table, figure, img, picture, iframe"
}
