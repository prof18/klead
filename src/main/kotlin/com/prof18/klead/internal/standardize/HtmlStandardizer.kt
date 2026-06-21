package com.prof18.klead.internal.standardize

import com.prof18.klead.internal.dom.replaceWithChildren
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
        normalizeCodeBlocks(content)
        normalizeImages(content)
        normalizeImageAspectPlaceholders(content)
        normalizeFootnotes(content)
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
        val hints = listOf(id(), className(), attr("data-testid"), attr("data-component"), attr("itemprop"))
            .joinToString(" ")
            .lowercase()
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

    private fun removeEmptyAncestors(start: Element?, boundary: Element) {
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

    private fun Element.isLeadingHeadingChrome(): Boolean {
        if (hasBlockContentDescendant()) return false
        val text = text().trim()
        if (text.isBlank() || text.length > HEADING_CHROME_MAX_LENGTH) return false
        if (hasMetadataChromeHint()) return true
        return text.split(Regex("""\s+""")).size <= 3 && !SENTENCE_PUNCTUATION.containsMatchIn(text)
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
        val haystack = listOf(id(), className(), attr("data-testid"), attr("data-component"), attr("itemprop"))
            .joinToString(" ")
            .lowercase()
        return METADATA_CHROME_HINTS.any { it in haystack }
    }

    private fun Element.hasFrontMatterHint(): Boolean {
        val haystack = listOf(id(), className(), attr("data-testid"), attr("data-component"), attr("itemprop"))
            .joinToString(" ")
            .lowercase()
            .replace("-", "")
            .replace("_", "")
        return "frontmatter" in haystack
    }

    private fun Element.hasTimeZoneHint(): Boolean {
        val haystack = listOf(id(), className(), attr("data-testid"), attr("data-component"), attr("itemprop"))
            .joinToString(" ")
            .lowercase()
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
        .replace(Regex("""\s+"""), " ")
        .lowercase()

    private fun String.isMetadataChromeText(): Boolean = DATE_TEXT_PATTERN.containsMatchIn(this) ||
        READ_TIME_PATTERN.containsMatchIn(this) ||
        BYLINE_TEXT_PATTERN.containsMatchIn(this)

    private fun normalizeCodeBlocks(content: Element) {
        normalizeWritersideCodeBlocks(content)
        normalizeVersoLeanExamples(content)
        normalizeStandalonePreformattedCode(content)
        normalizeCodeTables(content)
        content.select("pre").forEach { pre ->
            normalizeCodeMirrorBlock(pre)
            pre.select(CODE_UI_SELECTOR).remove()
            val code = pre.selectFirst("code") ?: Element("code").also { code ->
                code.text(normalizeCodeText(pre.textWithLineBreaks()))
                pre.empty()
                pre.appendChild(code)
            }
            code.select(CODE_UI_SELECTOR).remove()
            code.text(normalizeCodeText(code.textWithLineBreaks()))
            val language = languageFrom(code) ?: languageFrom(pre) ?: languageFromCodeAncestor(pre)
            if (language != null) {
                code.attr("data-lang", language)
                code.addClass("language-$language")
            }
            removeCodeBlockChromeAround(pre, language)
        }
        content.select("code > pre").forEach { pre ->
            pre.parent()?.replaceWith(pre)
        }
    }

    private fun normalizeVersoLeanExamples(content: Element) {
        content.select(".example").forEach { example ->
            val children = example.children().toList()
            var index = 0
            while (index < children.size) {
                val fragment = children[index]
                if (!fragment.isVersoLeanFragment()) {
                    index += 1
                    continue
                }

                val run = children.drop(index).takeWhile { it.isVersoLeanFragment() }
                run.replaceWithMergedVersoLeanBlock()
                index += run.size
            }
        }
    }

    private fun List<Element>.replaceWithMergedVersoLeanBlock() {
        if (size <= 1) return
        val codeText = mergeVersoLeanText()
        if (codeText.isBlank()) return

        val pre = Element("pre")
        val code = Element("code")
        code.attr("data-lang", "lean")
        code.addClass("language-lean")
        code.text(codeText)
        pre.appendChild(code)
        first().replaceWith(pre)
        drop(1).forEach { it.remove() }
    }

    private fun Element.isVersoLeanFragment(): Boolean =
        (normalName() == "code" && hasClass("lean") && hasClass("block")) ||
            (normalName() == "pre" && hasClass("lean") && hasClass("lean-output"))

    private fun List<Element>.mergeVersoLeanText(): String {
        val builder = StringBuilder()
        for (fragment in this) {
            val text = fragment.versoLeanText()
            if (text.isEmpty()) continue
            if (builder.isNotEmpty() && !builder.endsWith('\n')) {
                builder.append('\n')
            }
            builder.append(text)
        }
        return builder.toString()
            .replace(Regex("""\n{3,}"""), "\n\n")
            .trimEnd()
    }

    private fun Element.versoLeanText(): String {
        val source = if (normalName() == "code") {
            clone().also { clone ->
                clone.select(".hover-container, .hover-info").remove()
            }
        } else {
            this
        }
        return source.textWithLineBreaks().normalizeVersoLeanText()
    }

    private fun String.normalizeVersoLeanText(): String {
        val normalized = replace("\r\n", "\n")
            .replace('\r', '\n')
            .replace(Regex("""\n{3,}"""), "\n\n")
        if (normalized.isBlank()) {
            return if (normalized.contains('\n')) "\n" else ""
        }
        return normalized
            .replace(Regex("""^\n+"""), "")
            .trimEnd(' ', '\t')
    }

    private fun normalizeWritersideCodeBlocks(content: Element) {
        content.select("div.code-block[data-lang], div.code-block[data-language]").forEach { block ->
            if (block.select("pre, code").isNotEmpty()) return@forEach
            val codeText = normalizeCodeText(block.textWithLineBreaks())
            if (codeText.isBlank()) return@forEach

            val language = languageFrom(block)
            val pre = Element("pre")
            val code = Element("code")
            code.text(codeText)
            if (language != null) {
                code.attr("data-lang", language)
                code.addClass("language-$language")
            }
            pre.appendChild(code)
            block.replaceWith(pre)
        }
    }

    private fun removeCodeBlockChromeAround(pre: Element, language: String?) {
        var current: Element? = pre
        repeat(CODE_CHROME_ANCESTOR_DEPTH) {
            val element = current ?: return
            element.previousElementSibling()
                ?.takeIf { it.isCodeChromeSibling(language) }
                ?.remove()
            current = element.parent()
        }
    }

    private fun normalizeStandalonePreformattedCode(content: Element) {
        content.select("code[style*=white-space]").forEach { code ->
            if (code.parents().any { it.normalName() == "pre" }) return@forEach
            if (!code.attr("style").contains("pre", ignoreCase = true)) return@forEach
            val pre = Element("pre")
            code.replaceWith(pre)
            pre.appendChild(code)
        }
    }

    private fun normalizeCodeTables(content: Element) {
        content.select("table.lntable, table.rouge-table, figure.highlight table").forEach { table ->
            val codeSource = table.selectFirst("td.rouge-code pre")
                ?: table.selectFirst("td.code pre")
                ?: table.select("td.lntd pre").lastOrNull()
                ?: return@forEach
            val code = codeSource.selectFirst("code")
            val language = code?.let(::languageFrom)
                ?: languageFrom(codeSource)
                ?: languageFromCodeAncestor(table)
            val pre = Element("pre")
            val codeElement = Element("code")
            val text = normalizeCodeText((code ?: codeSource).textWithLineBreaks())
            codeElement.text(text)
            if (language != null) {
                codeElement.attr("data-lang", language)
                codeElement.addClass("language-$language")
            }
            pre.appendChild(codeElement)
            table.replaceWith(pre)
        }
    }

    private fun normalizeCodeMirrorBlock(pre: Element) {
        val content = pre.selectFirst(".cm-content") ?: return
        val language = pre.selectFirst(".sticky, [class*=header], [class*=toolbar]")
            ?.text()
            ?.split(Regex("""\s+"""))
            ?.firstOrNull { it.length in 1..24 && it.all { char -> char.isLetterOrDigit() || char in "+#_-" } }
        val code = Element("code")
        code.text(normalizeCodeText(content.textWithLineBreaks()))
        language?.lowercase()?.let { normalized ->
            code.attr("data-lang", normalized)
            code.addClass("language-$normalized")
        }
        pre.empty()
        pre.appendChild(code)
    }

    private fun normalizeImages(content: Element) {
        content.select("img").forEach { image ->
            if (image.parent() == null) return@forEach
            image.removeBrowserManagedImageLayoutStyle()
            val pictureSourceSrcset = image.parents().firstOrNull { it.normalName() == "picture" }
                ?.selectFirst("source[srcset], source[srcSet], source[data-srcset]")
                ?.let { firstAttr(it, "srcset", "srcSet", "data-srcset") }
            val replacement = firstAttr(
                image,
                "data-src",
                "data-original",
                "data-original-src",
                "data-lazy-src",
                "data-url",
                "data-image-loader",
            )
            val nextNoscript = image.nextElementSibling()?.takeIf { it.normalName() == "noscript" }
            val noscriptReplacement = nextNoscript?.noscriptImage()
            if (pictureSourceSrcset != null) {
                image.attr("srcset", pictureSourceSrcset)
            }
            if (isPlaceholderImage(image.attr("src"))) {
                when {
                    replacement != null -> image.attr("src", replacement)

                    noscriptReplacement != null && image.hasPriorImageVariant(noscriptReplacement) -> {
                        nextNoscript.remove()
                        image.remove()
                        return@forEach
                    }

                    noscriptReplacement != null -> {
                        firstAttr(noscriptReplacement, "srcset", "srcSet", "data-srcset")?.let {
                            image.attr("srcset", it)
                        }
                        firstAttr(noscriptReplacement, "src", "data-src")?.let {
                            image.attr("src", it)
                        }
                        image.addNoscriptAltCaption(noscriptReplacement)
                        nextNoscript.remove()
                    }
                }
            }
            firstAttr(image, "data-srcset", "data-lazy-srcset")?.let { image.attr("srcset", it) }
            if (isPlaceholderImage(image.attr("src")) && image.attr("srcset").isBlank()) {
                image.remove()
            }
        }

        content.select("a[href] > img").forEach { image ->
            val link = image.parent() ?: return@forEach
            val duplicate = link.nextElementSibling()?.takeIf { it.normalName() == "img" } ?: return@forEach
            val href = link.attr("href").trim()
            val duplicateSrc = duplicate.attr("src").trim()
            if (href.isNotBlank() && href == duplicateSrc) {
                duplicate.remove()
            }
        }
    }

    private fun Element.removeBrowserManagedImageLayoutStyle() {
        val style = attr("style")
        if (style.isBlank()) return
        if (!style.contains("position:absolute", ignoreCase = true) && attr("data-nimg") != "fill") return

        removeAttr("style")
    }

    private fun normalizeImageAspectPlaceholders(content: Element) {
        content.select(
            "div[style], figure[style], picture[style], span[style], p[style], a[style]",
        ).forEach { element ->
            if (!element.hasImageContent()) return@forEach

            val style = element.attr("style")
            if (!style.hasAspectPlaceholderPadding()) return@forEach
            if (!element.hasImageAspectPlaceholderHint() && !element.isImageOnlyWrapper()) return@forEach

            val normalized = style.withoutAspectPlaceholderPadding()
            if (normalized.isBlank()) {
                element.removeAttr("style")
            } else {
                element.attr("style", normalized)
            }
        }
    }

    private fun Element.hasImageContent(): Boolean = normalName() == "picture" || selectFirst("img, picture") != null

    private fun Element.hasImageAspectPlaceholderHint(): Boolean {
        val haystack = listOf(id(), className(), attr("data-testid"), attr("data-component"), attr("itemprop"))
            .joinToString(" ")
            .lowercase()
        return IMAGE_ASPECT_PLACEHOLDER_HINTS.any { it in haystack }
    }

    private fun Element.isImageOnlyWrapper(): Boolean {
        val clone = clone()
        clone.select("img, picture, source, noscript, figcaption, small").remove()
        return clone.text().trim().isBlank()
    }

    private fun String.hasAspectPlaceholderPadding(): Boolean =
        split(';').any { it.trim().isAspectPlaceholderPaddingDeclaration() }

    private fun String.withoutAspectPlaceholderPadding(): String = split(';')
        .map { it.trim() }
        .filter { it.isNotBlank() && !it.isAspectPlaceholderPaddingDeclaration() }
        .joinToString("; ")

    private fun String.isAspectPlaceholderPaddingDeclaration(): Boolean {
        val parts = split(':', limit = 2)
        if (parts.size != 2) return false

        val property = parts[0].trim().lowercase()
        if (property != "padding-bottom" && property != "padding-top") return false

        return ASPECT_PLACEHOLDER_PADDING_VALUE.matches(parts[1].trim())
    }

    private fun Element.noscriptImage(): Element? =
        selectFirst("img[src], img[srcset], img[srcSet], img[data-src], img[data-srcset]")

    private fun Element.hasPriorImageVariant(replacement: Element): Boolean {
        val replacementKey = replacement.imageVariantKey() ?: return false
        val parent = parent() ?: return false
        for (sibling in parent.children()) {
            if (sibling === this) return false
            if (sibling.imageVariantKeys().any { it == replacementKey }) return true
        }
        return false
    }

    private fun Element.imageVariantKeys(): List<String> {
        val candidates = if (normalName() == "img") listOf(this) else select("img")
        return candidates
            .filter { it.hasRealImageSource() }
            .mapNotNull { it.imageVariantKey() }
    }

    private fun Element.hasRealImageSource(): Boolean {
        val src = attr("src").trim()
        return (src.isNotBlank() && !isPlaceholderImage(src)) ||
            firstAttr(this, "srcset", "srcSet", "data-src", "data-srcset") != null
    }

    private fun Element.imageVariantKey(): String? =
        firstAttr(this, "src", "data-src", "srcset", "srcSet", "data-srcset")
            ?.let(::imageVariantKey)

    private fun imageVariantKey(source: String): String? {
        val url = source
            .substringBefore(",")
            .trim()
            .split(Regex("""\s+"""))
            .firstOrNull()
            ?.substringBefore("#")
            ?.substringBefore("?")
            ?.trimEnd('/')
            ?: return null
        return url.substringAfterLast('/').lowercase().ifBlank { null }
    }

    private fun Element.addNoscriptAltCaption(noscriptImage: Element) {
        if (!hasAttr("data-nimg") && !noscriptImage.hasAttr("data-nimg")) return
        if (parents().any { it.normalName() == "figure" && it.selectFirst("figcaption") != null }) return
        if (nextElementSibling()?.normalName() == "figcaption") return

        val caption = attr("alt").trim().ifBlank {
            noscriptImage.attr("alt").trim()
        }
        if (caption.isBlank()) return

        val captionElement = Element("span")
        captionElement.text(caption)
        after(captionElement)
    }

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
            val title = titleElement?.text()?.trim()?.ifBlank { null } ?: type.replaceFirstChar { it.uppercase() }
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

    private fun normalizeFootnotes(content: Element) {
        normalizeDataDefinitionFootnotes(content)
        normalizeInlineFootnoteSpans(content)
        normalizeInlineFootnoteContainers(content)
        normalizeParagraphFootnoteDefinitions(content)
        normalizeNamedAnchorFootnotes(content)
        normalizeSidenoteFootnotes(content)
        normalizeOrgModeFootdefs(content)
        normalizeSubstackFootnotes(content)
        normalizeWikidotFootnotes(content)
        normalizeDhammatalksFootnotes(content)
        normalizeFootnoteDefinitionBlocks(content)
        normalizeAsideFootnoteLists(content)
        normalizeReferenceDivFootnotes(content)
        normalizeReferenceFootnoteLists(content)
        normalizeFootnoteLists(content)
        normalizeLooseFootnoteSections(content)
        normalizeTrailingLooseFootnoteDefinitions(content)
        removeFootnoteDividers(content)
    }

    private fun normalizeDataDefinitionFootnotes(content: Element) {
        val references = content.select("[data-definition]").toList()
        if (references.isEmpty()) return
        val section = lazyFootnoteSection(content)
        references.forEachIndexed { index, reference ->
            val targetId = reference.attr("data-definition").trim()
            val target = content.select("[id]").firstOrNull { it.id() == targetId } ?: return@forEachIndexed
            val number = (index + 1).toString()
            val item = Element("li").attr("id", "fn$number")
            target.childNodes().toList().forEach { item.appendChild(it) }
            section.appendChild(item)

            val sup = Element("sup")
            sup.appendElement("a").attr("href", "#fn$number").text(number)
            reference.replaceWith(sup)
            target.remove()
        }
    }

    private fun normalizeInlineFootnoteSpans(content: Element) {
        val footnotes = content.select(".inline-footnote")
            .filter { it.selectFirst(".footnoteContent") != null }
        if (footnotes.isEmpty()) return

        val section = lazyFootnoteSection(content)
        footnotes.forEachIndexed { index, footnote ->
            val body = footnote.selectFirst(".footnoteContent") ?: return@forEachIndexed
            val number = footnote.ownText().trim().ifBlank { (index + 1).toString() }
            val id = "fn$number"
            val item = Element("li").attr("id", id)
            body.childNodes().toList().forEach { item.appendChild(it) }
            section.appendChild(item)

            val sup = Element("sup")
            sup.appendElement("a").attr("href", "#$id").text(number)
            footnote.replaceWith(sup)
        }
    }

    private fun normalizeInlineFootnoteContainers(content: Element) {
        val footnotes = content.select(".footnote-container")
            .filter { it.selectFirst(".footnote") != null }
        if (footnotes.isEmpty()) return

        val section = lazyFootnoteSection(content)
        footnotes.forEachIndexed { index, footnote ->
            val body = footnote.selectFirst(".footnote") ?: return@forEachIndexed
            val number = footnote.selectFirst("label.footnote-number[for]")
                ?.attr("for")
                ?.trim()
                ?.takeIf { it.isNotBlank() }
                ?: footnote.selectFirst("input.margin-toggle[id]")
                    ?.id()
                    ?.trim()
                    ?.takeIf { it.isNotBlank() }
                ?: (index + 1).toString()
            val id = "fn$number"
            val item = Element("li").attr("id", id)
            body.childNodes().toList().forEach { item.appendChild(it) }
            section.appendChild(item)

            val sup = Element("sup")
            sup.appendElement("a").attr("href", "#$id").text(number)
            footnote.replaceWith(sup)
        }
    }

    private fun normalizeFootnoteDefinitionBlocks(content: Element) {
        val definitions = content.select(".footnote-definition").toList()
        if (definitions.isEmpty()) return
        val section = lazyFootnoteSection(content)
        definitions.forEach { definition ->
            val id = definition.id().ifBlank {
                definition.selectFirst(".footnote-definition-label")?.text()?.trim()?.let { "fn$it" }
                    ?: definition.selectFirst("[id]")?.id()
                    ?: ""
            }
            definition.select(".footnote-definition-label").remove()
            definition.removeFootnoteBackrefs()
            val item = Element("li").attr("id", id.ifBlank { "fn${section.childrenSize() + 1}" })
            definition.childNodes().toList().forEach { item.appendChild(it) }
            section.appendChild(item)
            definition.remove()
        }
    }

    private fun normalizeSidenoteFootnotes(content: Element) {
        content.select("label.footref[for]").forEach { label ->
            val target = label.attr("for").trim()
            if (target.isBlank()) return@forEach
            var sibling = label.nextElementSibling()
            if (sibling?.normalName() == "input" && sibling.id() == target) {
                val next = sibling.nextElementSibling()
                sibling.remove()
                sibling = next
            }
            if (sibling?.hasClass("sidenote") == true) {
                sibling.remove()
            }
            val sup = Element("sup")
            sup.appendElement("a").attr("href", "#$target").text(label.text().trim())
            label.replaceWith(sup)
        }

        content.select("sup.footnote-reference + span.sidenote").remove()
    }

    private fun normalizeOrgModeFootdefs(content: Element) {
        val definitions = content.select(".footdef").toList()
        if (definitions.isEmpty()) return
        val containers = definitions.mapNotNull { it.parent() }.distinct()
        val section = lazyFootnoteSection(content)
        definitions.forEach { definition ->
            val marker = definition.selectFirst(".footnum[id], a[id]") ?: return@forEach
            val body = definition.selectFirst("[role=doc-footnote], .footpara") ?: definition
            val item = Element("li").attr("id", marker.id())
            body.childNodes().toList().forEach { item.appendChild(it) }
            section.appendChild(item)
            definition.remove()
        }
        containers.forEach { container ->
            if (
                container.select(".footdef").isEmpty() &&
                FOOTNOTE_HEADING_PATTERN.containsMatchIn(container.text().trim())
            ) {
                container.remove()
            }
        }
    }

    private fun normalizeSubstackFootnotes(content: Element) {
        val definitions = content.select(".footnote").filter { definition ->
            definition.selectFirst(
                ".footnote-number[id], [data-component-name=FootnoteToDOM] .footnote-number[id]",
            ) != null &&
                definition.selectFirst(".footnote-content") != null
        }
        if (definitions.isEmpty()) return

        val section = lazyFootnoteSection(content)
        definitions.forEachIndexed { index, definition ->
            val marker = definition.selectFirst(".footnote-number[id]")
            val body = definition.selectFirst(".footnote-content") ?: return@forEachIndexed
            val id = marker?.id()?.ifBlank { "footnote-${index + 1}" } ?: "footnote-${index + 1}"

            content.select("a[href]").forEach { link ->
                if (link.hrefFragmentTarget() == id) {
                    link.normalizeFootnoteReferenceLink(id)
                }
            }

            val item = Element("li").attr("id", id)
            body.childNodes().toList().forEach { item.appendChild(it) }
            section.appendChild(item)
            definition.remove()
        }
    }

    private fun normalizeWikidotFootnotes(content: Element) {
        val definitions = content.select(".footnotes-footer .footnote-footer[id]").toList()
        if (definitions.isEmpty()) return

        val definitionIds = definitions.map { it.id() }.toSet()
        content.select("sup.footnoteref a, a.footnoteref").forEach { link ->
            val target = link.wikidotFootnoteTarget()
            if (target != null && target in definitionIds) {
                link.normalizeFootnoteReferenceLink(target)
            }
        }

        val section = lazyFootnoteSection(content)
        definitions.forEach { definition ->
            val item = Element("li").attr("id", definition.id())
            val clone = definition.clone()
            clone.select("> a:first-child").remove()
            clone.removeLeadingFootnoteDefinitionPunctuation()
            clone.childNodes().toList().forEach { item.appendChild(it) }
            section.appendChild(item)
        }
        content.select(".footnotes-footer").remove()
    }

    private fun normalizeDhammatalksFootnotes(content: Element) {
        val notes = content.select("div.note").filter { note ->
            note.select("> p[id]").any { it.id().matches(DHAMMATALKS_NOTE_ID_PATTERN) }
        }
        if (notes.isEmpty()) return

        notes.forEach { note ->
            val definitionIds = note.dhammatalksDefinitionIds()
            if (definitionIds.isEmpty()) return@forEach

            content.normalizeDhammatalksReferences(definitionIds)
            note.moveDhammatalksDefinitionsTo(lazyFootnoteSection(content), definitionIds)
        }
    }

    private fun Element.dhammatalksDefinitionIds(): Set<String> = select("> p[id]")
        .filter { it.id().matches(DHAMMATALKS_NOTE_ID_PATTERN) }
        .map { it.id() }
        .toSet()

    private fun Element.normalizeDhammatalksReferences(definitionIds: Set<String>) {
        select("span.fn a[href], a[href]").forEach { link ->
            val target = link.hrefFragmentTarget()
            if (target != null && target in definitionIds) {
                link.replaceWithFootnoteReference(target)
            }
        }
    }

    private fun Element.moveDhammatalksDefinitionsTo(section: Element, definitionIds: Set<String>) {
        val children = children().toList()
        var index = 0
        while (index < children.size) {
            val definition = children[index]
            if (definition.id() in definitionIds) {
                index = appendDhammatalksDefinition(children, index, definitionIds, section)
            } else {
                index++
            }
        }
        remove()
    }

    private fun appendDhammatalksDefinition(
        children: List<Element>,
        startIndex: Int,
        definitionIds: Set<String>,
        section: Element,
    ): Int {
        val definition = children[startIndex]
        val item = Element("li").attr("id", definition.id())
        definition.removeLeadingNumberMarker()

        var index = startIndex
        while (index < children.size) {
            val current = children[index]
            if (current !== definition && current.id() in definitionIds) break
            current.appendToDhammatalksDefinition(definition, item)
            index++
        }
        section.appendChild(item)
        return index
    }

    private fun Element.appendToDhammatalksDefinition(definition: Element, item: Element) {
        if (hasClass("notetitle")) return
        if (this !== definition) {
            removeTerminalPeriodAfterTrailingLink()
        }
        remove()
        item.appendChild(this)
    }

    private fun Element.wikidotFootnoteTarget(): String? {
        wikidotFootnoteScrollTargetPattern.find(attr("onclick"))?.let { match ->
            return match.groupValues[1]
        }
        wikidotFootnoteRefIdPattern.matchEntire(id())?.let { match ->
            return "footnote-${match.groupValues[1]}"
        }
        val text = text().trim()
        return text.takeIf { it.matches(FOOTNOTE_NUMBER_PATTERN) }?.let { "footnote-$it" }
    }

    private fun Element.removeLeadingFootnoteDefinitionPunctuation() {
        val first = childNodes().firstOrNull() as? TextNode ?: return
        val cleaned = first.wholeText.trimStart().removePrefix(".").trimStart()
        if (cleaned.isBlank()) {
            first.remove()
        } else {
            first.text(cleaned)
        }
    }

    private fun Element.hrefFragmentTarget(): String? {
        val href = attr("href").trim()
        if ('#' !in href) return null
        return href.substringAfterLast('#').takeIf { it.isNotBlank() }
    }

    private fun Element.normalizeFootnoteReferenceLink(targetId: String) {
        attr("href", "#$targetId")
        if (parent()?.normalName() == "sup") return

        val replacement = Element("sup")
        replacement.appendChild(clone())
        replaceWith(replacement)
    }

    private fun Element.replaceWithFootnoteReference(targetId: String) {
        val normalizedLink = clone()
            .attr("href", "#$targetId")
        val replacement = Element("sup")
            .appendChild(normalizedLink)
        val wrapper = parent()?.takeIf { parent ->
            parent.normalName() == "span" &&
                parent.hasClass("fn") &&
                parent.children().size == 1
        }
        if (wrapper != null) {
            wrapper.replaceWith(replacement)
        } else {
            replaceWith(replacement)
        }
    }

    private fun Element.removeLeadingNumberMarker() {
        val first = childNodes().firstOrNull() as? TextNode ?: return
        first.text(FOOTNOTE_NUMBER_DOT_PREFIX_PATTERN.replaceFirst(first.wholeText, ""))
    }

    private fun Element.removeTerminalPeriodAfterTrailingLink() {
        val nodes = childNodes()
        val lastTextIndex = nodes.indexOfLast { node ->
            node !is TextNode || node.wholeText.isNotBlank()
        }
        val lastText = nodes.getOrNull(lastTextIndex) as? TextNode ?: return
        if (!TRAILING_LINK_PERIOD_TEXT_PATTERN.matches(lastText.wholeText)) return

        val previousElement = nodes
            .take(lastTextIndex)
            .lastOrNull { node ->
                when (node) {
                    is TextNode -> node.wholeText.isNotBlank()
                    is Element -> true
                    else -> false
                }
            } as? Element
        if (previousElement?.normalName() != "a") return

        lastText.text(lastText.wholeText.replace(Regex("""\.\s*$"""), ""))
    }

    private fun normalizeParagraphFootnoteDefinitions(content: Element) {
        val definitions = content.select("p[id]").filter { it.id().isParagraphFootnoteId() }
        if (definitions.isEmpty()) return
        val blocks = definitions.map { it.parent()?.takeIf { parent -> parent !== content } ?: it }
        val leadingHeading = blocks.firstOrNull()
            ?.previousElementSibling()
            ?.takeIf {
                it.normalName().matches(HEADING_TAG_PATTERN) &&
                    FOOTNOTE_HEADING_PATTERN.matches(it.text().trim())
            }
        val trailingDivider = blocks.lastOrNull()
            ?.nextElementSibling()
            ?.takeIf { it.normalName() == "hr" }
        val section = lazyFootnoteSection(content)
        definitions.forEach { definition ->
            definition.removeFootnoteBackrefs()
            val oldParent = definition.parent()
            val item = Element("li").attr("id", definition.id())
            definition.remove()
            item.appendChild(definition)
            section.appendChild(item)
            removeEmptyAncestors(oldParent, content)
        }
        leadingHeading?.remove()
        trailingDivider?.remove()
    }

    private fun normalizeNamedAnchorFootnotes(content: Element) {
        val definitions = content.select("p").filter { paragraph ->
            paragraph.selectFirst("a[name]")?.attr("name")?.isNamedFootnoteDefinitionId() == true
        }
        if (definitions.isEmpty()) return
        val section = lazyFootnoteSection(content)
        definitions.forEach { definition ->
            val marker = definition.selectFirst("a[name]") ?: return@forEach
            val id = marker.attr("name")
            marker.remove()
            definition.removeFootnoteBackrefs()
            val oldParent = definition.parent()
            val item = Element("li").attr("id", id)
            definition.remove()
            item.appendChild(definition)
            section.appendChild(item)
            removeEmptyAncestors(oldParent, content)
        }
    }

    private fun normalizeAsideFootnoteLists(content: Element) {
        content.select("aside ol").forEach { list ->
            if (list.parent()?.hasAttr("data-footnotes") == true) return@forEach
            if (list.select("> li").isEmpty()) return@forEach
            val start = list.attr("start").toIntOrNull() ?: 1
            val section = lazyFootnoteSection(content)
            list.select("> li").forEachIndexed { index, item ->
                if (item.id().isBlank()) {
                    item.attr("id", "fn${start + index}")
                }
                item.removeFootnoteBackrefs()
                section.appendChild(item)
            }
            list.remove()
        }
    }

    private fun normalizeReferenceFootnoteLists(content: Element) {
        content.select("ol").forEach { list ->
            if (list.parent()?.hasAttr("data-footnotes") == true) return@forEach
            if (!list.isReferenceFootnoteList()) return@forEach
            list.select("> li").forEachIndexed { index, item ->
                if (item.id().isBlank()) {
                    val nestedId = item.selectFirst("[id]")?.id().orEmpty()
                    item.attr("id", nestedId.ifBlank { "fn${index + 1}" })
                }
                item.select("a[id]").filter { anchor ->
                    anchor.attr("href").isBlank() && anchor.text().isBlank()
                }.forEach { it.remove() }
                item.removeFootnoteBackrefs()
            }
            val section = Element("section").attr("data-footnotes", "true").addClass("footnotes")
            list.before(section)
            section.appendChild(list)
        }
    }

    private fun normalizeReferenceDivFootnotes(content: Element) {
        val references = content.select(".references .reference").filter {
            it.selectFirst(".reference-number[id], [id].reference-number") != null &&
                it.selectFirst(".reference-content") != null
        }
        if (references.isEmpty()) return
        val containers = references.mapNotNull { reference ->
            reference.parents().firstOrNull { it.hasClass("references") }
        }.distinct()
        val section = lazyFootnoteSection(content)
        references.forEachIndexed { index, reference ->
            val marker = reference.selectFirst(".reference-number[id], [id].reference-number")
            val body = reference.selectFirst(".reference-content") ?: return@forEachIndexed
            val item = Element("li").attr("id", marker?.id()?.ifBlank { "ref${index + 1}" } ?: "ref${index + 1}")
            body.childNodes().toList().forEach { item.appendChild(it) }
            section.appendChild(item)
            reference.remove()
        }
        containers.forEach { container ->
            if (container.select(".reference").isEmpty()) {
                container.remove()
            }
        }
    }

    private fun normalizeFootnoteLists(content: Element) {
        content.select(
            "ol.footnotes, ol.references, ol[class*=footnote], ol[id*=footnote], ol[id*=fn], " +
                "section[data-footnotes] ol, .footnotes ol",
        ).forEach { list ->
            if (list.parent()?.hasAttr("data-footnotes") == true) return@forEach
            list.select("li").forEachIndexed { index, item ->
                if (item.id().isBlank()) {
                    val nestedId = item.selectFirst("[id]")?.id().orEmpty()
                    item.attr("id", nestedId.ifBlank { "fn${index + 1}" })
                }
                item.removeFootnoteBackrefs()
            }
            val section = Element("section").attr("data-footnotes", "true").addClass("footnotes")
            list.before(section)
            section.appendChild(list)
        }
    }

    private fun normalizeTrailingLooseFootnoteDefinitions(content: Element) {
        val definitions = content.select("p").filter { it.isLooseFootnoteStart() }
        if (definitions.size < 2) return
        val section = lazyFootnoteSection(content)
        definitions.forEach { definition ->
            val id = "fn${definition.looseFootnoteNumber()}"
            val item = Element("li").attr("id", id)
            definition.removeLooseFootnoteMarker()
            definition.remove()
            item.appendChild(definition)
            section.appendChild(item)
        }
    }

    private fun normalizeLooseFootnoteSections(content: Element) {
        for (marker in content.select("hr, h1, h2, h3, h4, h5, h6").toList()) {
            if (!marker.isAttachedTo(content)) continue
            if (!marker.isLooseFootnoteDelimiter()) continue

            val definitions = mutableListOf<Element>()
            var sibling = marker.nextElementSibling()
            while (sibling != null) {
                if (sibling.isLooseFootnoteStart()) {
                    definitions.add(sibling)
                    sibling = sibling.nextElementSibling()
                    while (sibling != null && !sibling.isLooseFootnoteStart() && !sibling.isLooseFootnoteStop()) {
                        definitions.add(sibling)
                        sibling = sibling.nextElementSibling()
                    }
                } else if (sibling.text().trim().isBlank()) {
                    sibling = sibling.nextElementSibling()
                } else {
                    break
                }
            }
            if (definitions.none { it.isLooseFootnoteStart() }) continue

            val section = Element("section").attr("data-footnotes", "true").addClass("footnotes")
            marker.before(section)
            var currentItem: Element? = null
            for (definition in definitions) {
                if (definition.isLooseFootnoteStart()) {
                    val id = "fn${definition.looseFootnoteNumber()}"
                    currentItem = Element("li").attr("id", id)
                    section.appendChild(currentItem)
                    definition.removeLooseFootnoteMarker()
                }
                val target = currentItem ?: continue
                definition.remove()
                target.appendChild(definition)
            }
            marker.remove()
        }
    }

    private fun lazyFootnoteSection(content: Element): Element {
        content.selectFirst("section[data-footnotes]")?.let { return it }
        return Element("section")
            .attr("data-footnotes", "true")
            .addClass("footnotes")
            .also { content.appendChild(it) }
    }

    private fun Element.removeFootnoteBackrefs() {
        select(
            "a[href*=fnref], a[href*=ftnt_ref], a[href*=_ftnref], a[class*=backref], a[class*=to-top], " +
                "a[href*=FnAnchor], a[href*=-link], a[aria-label*=Back], a[aria-label*=back], " +
                "a[aria-label*=Jump], a[aria-label*=jump], " +
                ".footnote-backref, .footnote-back-link, .data-footnote-backref",
        ).remove()
        select(".easy-footnote-margin-adjust").remove()
    }

    private fun Element.isLooseFootnoteDelimiter(): Boolean =
        normalName() == "hr" || text().trim().matches(FOOTNOTE_HEADING_PATTERN)

    private fun Element.isLooseFootnoteStop(): Boolean =
        normalName().matches(HEADING_TAG_PATTERN) && !text().trim().matches(FOOTNOTE_HEADING_PATTERN)

    private fun Element.isLooseFootnoteStart(): Boolean = startsWithFootnoteMarker() && looseFootnoteNumber() != null

    private fun Element.startsWithFootnoteMarker(): Boolean {
        val firstElement = children().firstOrNull() ?: return false
        return childNodes()
            .takeWhile { it !== firstElement }
            .all { it !is TextNode || it.wholeText.isBlank() }
    }

    private fun Element.looseFootnoteNumber(): String? {
        val firstElement = children().firstOrNull()
        val markerText = when {
            firstElement?.normalName() == "sup" -> firstElement.text().trim()

            firstElement?.normalName() == "span" -> firstElement.selectFirst("sup")?.text()?.trim()

            firstElement != null && firstElement.normalName() in setOf("strong", "b") -> {
                firstElement.selectFirst("sup")?.text()?.trim()
                    ?: FOOTNOTE_NUMBER_PREFIX_PATTERN.find(firstElement.text().trim())?.groupValues?.get(1)
            }

            else -> null
        }
        return markerText?.normalizeFootnoteNumberText()?.takeIf { it.matches(FOOTNOTE_NUMBER_PATTERN) }
    }

    private fun Element.removeLooseFootnoteMarker() {
        val marker = children().firstOrNull()?.takeIf { candidate ->
            val isMarkerElement = candidate.normalName() in FOOTNOTE_MARKER_TAGS ||
                (candidate.normalName() == "span" && candidate.selectFirst("sup") != null)
            val hasMarkerText = candidate.text().trim().matches(FOOTNOTE_NUMBER_PATTERN) ||
                candidate.selectFirst("sup") != null
            isMarkerElement && hasMarkerText
        } ?: return
        if (marker.normalName() == "span") {
            marker.selectFirst("sup")
                ?.takeIf {
                    it.text()
                        .trim()
                        .normalizeFootnoteNumberText()
                        .matches(FOOTNOTE_NUMBER_PATTERN)
                }
                ?.remove()
            if (marker.text().trim().isBlank() && marker.select("a, img, code").isEmpty()) {
                marker.remove()
            }
            return
        }
        if (marker.normalName() in setOf("strong", "b")) {
            marker.selectFirst("sup")
                ?.takeIf {
                    it.text()
                        .trim()
                        .normalizeFootnoteNumberText()
                        .matches(FOOTNOTE_NUMBER_PATTERN)
                }
                ?.remove()
            if (marker.text().trim().matches(FOOTNOTE_NUMBER_PATTERN) || marker.text().trim().isBlank()) {
                marker.remove()
            }
            return
        }
        marker.remove()
    }

    private fun String.normalizeFootnoteNumberText(): String = trim().trim('[', ']')

    private fun Element.isAttachedTo(root: Element): Boolean = this === root || parents().any { it === root }

    private fun removeFootnoteDividers(content: Element) {
        content.select("hr").forEach { divider ->
            val parent = divider.parent()
            val isInsideFootnotes = parent?.hasClass("footnotes") == true ||
                parent?.hasAttr("data-footnotes") == true
            if (isInsideFootnotes) {
                if (divider.isFootnoteSeparatorChrome()) {
                    divider.remove()
                }
                return@forEach
            }

            val next = divider.nextElementSibling()
            val followsFootnoteHint = next == null && divider.previousElementSibling()?.hasFootnoteHint() == true
            val isTrailingBeforeFootnotes = followsFootnoteHint ||
                next?.hasAttr("data-footnotes") == true
            if (isTrailingBeforeFootnotes) {
                divider.remove()
            }
        }
    }

    private fun Element.isFootnoteSeparatorChrome(): Boolean = classNames().any {
        it.contains("separator", ignoreCase = true) ||
            it.contains("separatator", ignoreCase = true)
    }

    private fun Element.hasFootnoteHint(): Boolean {
        val hints = "${id()} ${className()} ${attributes().asList().joinToString(" ") { it.value }}".lowercase()
        return "footnote" in hints || "footnotes" in hints || "ftnt" in hints
    }

    private fun Element.isReferenceFootnoteList(): Boolean {
        if (select("> li [id]").isEmpty()) return false
        val previous = previousElementSibling()
        return previous != null &&
            previous.normalName().matches(HEADING_TAG_PATTERN) &&
            FOOTNOTE_HEADING_PATTERN.matches(previous.text().trim())
    }

    private fun String.isParagraphFootnoteId(): Boolean = matches(PARAGRAPH_FOOTNOTE_ID_PATTERN)

    private fun String.isNamedFootnoteDefinitionId(): Boolean = matches(NAMED_FOOTNOTE_DEFINITION_ID_PATTERN)

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

    private fun languageFrom(element: Element): String? {
        val dataLanguage = firstAttr(element, "data-lang", "data-language", "language")
        if (dataLanguage != null) return dataLanguage.lowercase()
        val classLanguage = LANGUAGE_REGEX.find(element.className())?.groupValues?.getOrNull(1)
        if (classLanguage != null) return classLanguage.lowercase()
        if (element.normalName() == "pre" && element.hasClass("cf")) return "c"
        return genericLanguageClassFrom(element)
    }

    private fun genericLanguageClassFrom(element: Element): String? {
        if (element.classNames().size > MAX_GENERIC_LANGUAGE_CLASSES) return null
        for (className in element.classNames()) {
            className.takeIf {
                it.lowercase() !in CODE_LANGUAGE_CLASS_BLACKLIST &&
                    it.length <= 24 &&
                    it.none(Char::isDigit) &&
                    it.all { char -> char.isLetterOrDigit() || char in "+#_-" }
            }?.let { return it.lowercase() }
        }
        return null
    }

    private fun languageFromCodeAncestor(element: Element): String? {
        var current: Element? = element
        while (current != null) {
            languageFrom(current)?.let { return it }
            current = current.parent()
        }
        return null
    }

    @Suppress("NestedBlockDepth")
    private fun Element.textWithLineBreaks(): String {
        val builder = StringBuilder()

        fun appendNode(node: Node, preserveBlankText: Boolean = false) {
            when (node) {
                is TextNode -> {
                    if (preserveBlankText || node.wholeText.isNotBlank()) {
                        builder.append(node.wholeText)
                    }
                }

                is Element -> {
                    if (node.normalName() == "br") {
                        builder.append('\n')
                    } else if (node.isCodeLineContainer()) {
                        val lineStart = builder.length
                        node.childNodes()
                            .filterNot { it.isFormattingWhitespaceText() }
                            .dropWhile { it.isCodeLineGutter() || it.isLeadingCodeLineNumber() }
                            .forEach { child ->
                                if (child is Element && child.normalName() in CODE_LINE_CELL_TAGS) {
                                    child.childNodes()
                                        .filterNot { it.isFormattingWhitespaceText() }
                                        .forEach { appendNode(it, preserveBlankText = true) }
                                } else {
                                    appendNode(child, preserveBlankText = true)
                                }
                            }
                        while (builder.length > lineStart && builder.last() == '\n') {
                            builder.deleteAt(builder.lastIndex)
                        }
                        builder.append('\n')
                    } else if (node.normalName() == "div") {
                        node.childNodes().forEach { appendNode(it, preserveBlankText = true) }
                        builder.append('\n')
                    } else {
                        node.childNodes().forEach { appendNode(it, preserveBlankText = true) }
                    }
                }
            }
        }

        val hasStructuredLines = hasStructuredCodeLines()
        childNodes()
            .filterNot { hasStructuredLines && it is TextNode && it.wholeText.isBlank() }
            .filterNot { hasStructuredLines && it.isLineBreakAfterCodeLine() }
            .forEach { appendNode(it, preserveBlankText = !hasStructuredLines) }
        return builder.toString().replace("\u00A0", " ")
    }

    private fun Node.isLineBreakAfterCodeLine(): Boolean = this is Element &&
        normalName() == "br" &&
        previousSibling()?.let { it is Element && it.isCodeLineContainer() } == true

    private fun Element.isCodeLineContainer(): Boolean {
        val isExplicitLine = hasAttr("data-line") || classNames().any { it == "line" || it.endsWith("-line") }
        val isImplicitDivLine = normalName() == "div" &&
            childNodes().size >= 2 &&
            childNodes().firstOrNull()?.isLeadingCodeLineNumber() == true
        return normalName() in CODE_LINE_CONTAINER_TAGS && (isExplicitLine || isImplicitDivLine)
    }

    private fun Element.hasStructuredCodeLines(): Boolean =
        childNodes().filterIsInstance<Element>().count { it.isCodeLineContainer() } >= 2

    private fun Node.isLeadingCodeLineNumber(): Boolean {
        if (this !is Element) return false
        val hasLineNumberHint = className().contains("line", ignoreCase = true) ||
            className().contains("gutter", ignoreCase = true) ||
            className().contains("text-end", ignoreCase = true)
        return text().trim().matches(CODE_LINE_NUMBER_PATTERN) && hasLineNumberHint
    }

    private fun Node.isCodeLineGutter(): Boolean = this is Element &&
        className().contains("gutter", ignoreCase = true)

    private fun Node.isFormattingWhitespaceText(): Boolean = this is TextNode &&
        wholeText.isBlank() &&
        wholeText.contains('\n')

    private fun normalizeCodeText(value: String): String = value
        .replace("\r\n", "\n")
        .replace('\r', '\n')
        .replace(Regex("""^\n+"""), "")
        .replace(Regex("""\n{3,}"""), "\n\n")
        .trimEnd()

    private fun Element.isCodeChromeSibling(language: String?): Boolean {
        val normalizedText = text().trim().collapseWhitespace()
        if (normalizedText.isBlank() || normalizedText.length > CODE_CHROME_MAX_LENGTH) return false
        if (select("pre, code, p, blockquote, table, figure, img, picture").isNotEmpty()) return false
        if (language != null && normalizedText.equals(language, ignoreCase = true)) return true
        val hints = partialCodeChromeHaystack()
        return select("button").isNotEmpty() ||
            "copy" in hints ||
            "toolbar" in hints ||
            ("header" in hints && language != null && normalizedText.contains(language, ignoreCase = true))
    }

    private fun Element.partialCodeChromeHaystack(): String =
        "${id()} ${className()} ${attributes().asList().joinToString(" ") { it.value }}".lowercase()

    private fun String.collapseWhitespace(): String = replace(WHITESPACE_PATTERN, " ")

    private fun firstAttr(element: Element, vararg names: String): String? = names.firstNotNullOfOrNull { name ->
        element.attr(name).trim().ifBlank { null }
    }

    private fun isPlaceholderImage(src: String): Boolean = src.isBlank() ||
        src.startsWith("data:image/svg", ignoreCase = true) ||
        src.startsWith("data:image/gif", ignoreCase = true)

    private val LANGUAGE_REGEX = Regex("""(?:^|\s)language-([A-Za-z0-9_+#-]+)(?:\s|$)""")
    private val WHITESPACE_PATTERN = Regex("""\s+""")
    private val ASPECT_PLACEHOLDER_PADDING_VALUE = Regex(
        """(?:\d+(?:\.\d+)?|\.\d+)%\s*(?:!important)?""",
        RegexOption.IGNORE_CASE,
    )
    private val CODE_LINE_NUMBER_PATTERN = Regex("""\d{1,5}""")
    private val CODE_LINE_CELL_TAGS = setOf("div", "span")
    private val CODE_LINE_CONTAINER_TAGS = setOf("div", "span")
    private const val CODE_CHROME_ANCESTOR_DEPTH = 4
    private const val CODE_CHROME_MAX_LENGTH = 80
    private const val MAX_GENERIC_LANGUAGE_CLASSES = 3
    private const val CODE_UI_SELECTOR =
        ".lineno, .linenumber, .line-number, .line-numbers-rows, [class*=line-number], " +
            "[class*=linenumber], [aria-hidden=true], [style*=user-select], " +
            ".code__header, .code__copy-button, button, svg"
    private val CODE_LANGUAGE_CLASS_BLACKLIST = setOf(
        "box-root",
        "chroma",
        "code",
        "codeblock",
        "codeblock-code",
        "codeblock-content",
        "codeblock-numbered",
        "codetabgroup",
        "container",
        "document",
        "highlight",
        "highlighter-rouge",
        "hljs",
        "language",
        "line",
        "lntable",
        "lntd",
        "mx-auto",
        "page-container",
        "flex",
        "flex-col",
        "font-mono",
        "gutter",
        "highlight-wrap",
        "markdown-body",
        "pe-xs",
        "plain",
        "plaintext",
        "problem-content",
        "problem-description",
        "rouge-code",
        "rouge-gutter",
        "rouge-table",
        "section",
        "section--numbered",
        "section-content",
        "shiki",
        "text",
        "text-code-snippet",
        "typeset",
    )
    private val ADMONITION_CLASS_BLACKLIST = setOf(
        "admonition",
        "details",
        "open",
    )
    private val IMAGE_ASPECT_PLACEHOLDER_HINTS = setOf(
        "article-image",
        "article-img",
        "aspect-ratio",
        "aspectratio",
        "body-img",
        "image-aspect",
        "image-container",
        "image-expandable",
        "image-wrapper",
        "img-article-item",
        "intrinsic",
        "ratio-box",
        "ratio-container",
        "responsive-img",
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
    private val FOOTNOTE_HEADING_PATTERN = Regex(
        """(?i)^(notes|footnotes|endnotes|sidenotes|references(?:\s+and\s+notes)?)$""",
    )
    private val TRAILING_REMOVABLE_SECTION_HEADING = Regex("""(?i)^(further reading|next steps|see also|references)$""")
    private const val FURTHER_READING_HEADING = "Further Reading"
    private val FOOTNOTE_NUMBER_PATTERN = Regex("""\d{1,4}""")
    private val FOOTNOTE_NUMBER_PREFIX_PATTERN = Regex("""^(\d{1,4})(?:$|[\].):]|\s)""")
    private val FOOTNOTE_NUMBER_DOT_PREFIX_PATTERN = Regex("""^\s*\d{1,4}\.\s*""")
    private val TRAILING_LINK_PERIOD_TEXT_PATTERN = Regex("""^\s*[)\]]?\.\s*$""")
    private val PARAGRAPH_FOOTNOTE_ID_PATTERN = Regex("""(?i)^(?:ftnt|_ftn)\d+$""")
    private val NAMED_FOOTNOTE_DEFINITION_ID_PATTERN = Regex("""(?i)^(?:Footnote|_ftn)\D*\d+$""")
    private val DHAMMATALKS_NOTE_ID_PATTERN = Regex("""(?i).+note\d{1,4}$""")
    private val HEADING_TAG_PATTERN = Regex("""h[1-6]""")
    private val READ_TIME_PATTERN = Regex("""\b\d+\s+min(?:ute)?s?\s+read\b""", RegexOption.IGNORE_CASE)
    private val BYLINE_TEXT_PATTERN = Regex("""^by\s+\S+""", RegexOption.IGNORE_CASE)
    private val SENTENCE_PUNCTUATION = Regex("""[.!?]""")
    private val EMPTY_WRAPPER_TAGS = setOf("div", "section", "header")
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
    private val FOOTNOTE_MARKER_TAGS = setOf("sup", "strong", "b")
    private val wikidotFootnoteRefIdPattern = Regex("""footnoteref-(\d+)""")
    private val wikidotFootnoteScrollTargetPattern = Regex("""scrollToReference\(['"]([^'"]+)['"]\)""")
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
