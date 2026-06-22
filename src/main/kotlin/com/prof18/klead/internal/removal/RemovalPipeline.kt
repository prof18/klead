package com.prof18.klead.internal.removal

import com.prof18.klead.RemovalRecord
import com.prof18.klead.internal.dom.isAttachedTo
import com.prof18.klead.internal.dom.removeSafely
import com.prof18.klead.internal.dom.selectSafe
import org.jsoup.nodes.Element

internal fun recordAndRemove(
    element: Element,
    debug: MutableList<RemovalRecord>,
    step: String,
    selector: String?,
    reason: String,
) {
    debug += RemovalRecord(
        step = step,
        selector = selector,
        reason = reason,
        preview = element.text().take(100),
    )
    element.removeSafely()
}

internal data class RemovalPolicy(
    val removeExactSelectors: Boolean = true,
    val removePartialSelectors: Boolean = true,
    val removeHiddenElements: Boolean = true,
    val removeLowScoring: Boolean = true,
    val removeContentPatterns: Boolean = true,
)

@Suppress("LargeClass")
internal object RemovalPipeline {
    private data class NestedFooterRemoval(val reason: String, val tableOfContents: Boolean = false)

    fun apply(
        content: Element,
        debug: MutableList<RemovalRecord>,
        metadataImage: String? = null,
        policy: RemovalPolicy = RemovalPolicy(),
        measure: (String, () -> Unit) -> Unit = { _, block -> block() },
    ) {
        if (policy.removeHiddenElements) {
            measure("removeHiddenElements") {
                removeHiddenElements(content, debug)
            }
        }
        if (policy.removeExactSelectors) {
            measure("removeExactSelectors") {
                removeExactSelectors(content, debug)
            }
        }
        if (policy.removePartialSelectors) {
            measure("removePartialSelectors") {
                removePartialSelectors(content, debug)
            }
        }
        if (policy.removeLowScoring) {
            measure("removeLowScoringBlocks") {
                removeLowScoringBlocks(content, debug)
            }
        }
        if (policy.removeContentPatterns) {
            measure("removeContentPatterns") {
                removeContentPatterns(content, debug, measure)
            }
        }
        measure("imageRemoval") {
            ImageRemovalPipeline.apply(content, metadataImage, debug, ::partialHaystack)
        }
    }

    private fun removeHiddenElements(content: Element, debug: MutableList<RemovalRecord>) {
        HiddenElementRemoval.apply(content, debug)
    }

    private fun removeExactSelectors(content: Element, debug: MutableList<RemovalRecord>) {
        for (selector in EXACT_SELECTORS) {
            for (element in content.selectSafe(selector).toList()) {
                if (selector in TABLE_OF_CONTENTS_EXACT_SELECTORS) {
                    removeTableOfContentsBlock(element, debug, "removeExactSelectors", selector)
                    continue
                }
                if (selector == "button" && element.isInlineTextButton()) continue
                if (isProtected(element) && selector !in PROTECTED_EXACT_SELECTOR_OVERRIDES) continue
                recordAndRemove(element, debug, "removeExactSelectors", selector, "exact clutter selector")
            }
        }
    }

    private fun removePartialSelectors(content: Element, debug: MutableList<RemovalRecord>) {
        for (element in content.descendantsSnapshot()) {
            if (!element.isAttachedTo(content)) continue
            val haystack = partialHaystack(element)
            if (PARTIAL_PATTERNS.none { it in haystack }) continue
            if (isProtected(element, haystack)) continue
            if (isLikelyProse(element) && !isStrongRecirculationChrome(element, haystack)) continue
            recordAndRemove(element, debug, "removePartialSelectors", null, "partial clutter attribute")
        }
    }

    private fun removeLowScoringBlocks(content: Element, debug: MutableList<RemovalRecord>) {
        for (element in content.select("section, aside, div, ul, ol").toList()) {
            if (!element.isAttachedTo(content)) continue
            val links = element.select("a")
            val linkCount = links.size
            if (linkCount < 3) continue
            if (isProtected(element) || isLikelyProse(element)) continue
            if (element.isNestedListContent()) continue
            val text = element.text()
            val linkText = links.sumOf { it.text().length }
            val linkDensity = if (text.isBlank()) 0.0 else linkText.toDouble() / text.length
            if (linkDensity > 0.55) {
                recordAndRemove(element, debug, "removeLowScoring", null, "link-heavy low scoring block")
            }
        }
    }

    private fun removeContentPatterns(
        content: Element,
        debug: MutableList<RemovalRecord>,
        measure: (String, () -> Unit) -> Unit,
    ) {
        measure("removeContentPatterns.openingArticleHeaders") {
            removeOpeningArticleHeaderBlocks(content, debug)
        }
        measure("removeContentPatterns.recommendationSiblingRuns") {
            removeRecommendationSiblingRuns(content, debug)
        }
        measure("removeContentPatterns.trailingContentPatterns") {
            TrailingContentPatterns.remove(content, debug)
        }
        measure("removeContentPatterns.nestedArticleFooterBlocks") {
            removeNestedArticleFooterBlocks(content, debug)
        }
        measure("removeContentPatterns.trailingChildren") {
            removeTrailingChildPatterns(content, debug)
        }
    }

    private fun removeTrailingChildPatterns(content: Element, debug: MutableList<RemovalRecord>) {
        for (element in content.children().toList().asReversed()) {
            val text = element.text().trim()
            if (text.isBlank()) continue
            if (isProtected(element)) break
            if (SUBSCRIBE_PATTERN.containsMatchIn(text) && text.length < 180) {
                recordAndRemove(element, debug, "removeContentPatterns", null, "trailing subscribe call to action")
                continue
            }
            if (isAuthorFollowBlock(element)) {
                recordAndRemove(element, debug, "removeContentPatterns", null, "trailing author follow links")
                continue
            }
            if (isTrailingRecommendationHeading(element)) {
                recordAndRemove(element, debug, "removeContentPatterns", null, "trailing recommendation heading")
                continue
            }
            if (isTrailingRecommendationBlock(element)) {
                recordAndRemove(element, debug, "removeContentPatterns", null, "trailing recommendation block")
                continue
            }
            if (isTagListBlock(element)) {
                recordAndRemove(element, debug, "removeContentPatterns", null, "trailing tag list")
                continue
            }
            if (isCommentCountBlock(element)) {
                recordAndRemove(element, debug, "removeContentPatterns", null, "trailing comment count")
                continue
            }
            if (isBackToTopBlock(element)) {
                recordAndRemove(element, debug, "removeContentPatterns", null, "trailing back-to-top control")
                continue
            }
            if (isCommentPromptBlock(element)) {
                recordAndRemove(element, debug, "removeContentPatterns", null, "trailing comment prompt")
                continue
            }
            if (isStorySuggestionBlock(element)) {
                recordAndRemove(element, debug, "removeContentPatterns", null, "trailing story suggestion prompt")
                continue
            }
            if (isLocalNewsFollowBlock(element)) {
                recordAndRemove(element, debug, "removeContentPatterns", null, "trailing local news follow prompt")
                continue
            }
            break
        }
    }

    private fun removeOpeningArticleHeaderBlocks(content: Element, debug: MutableList<RemovalRecord>) {
        for (article in openingArticleCandidates(content)) {
            val header = article.firstSubstantiveChild() ?: continue
            if (!header.isOpeningArticleHeaderCandidate()) continue
            if (!header.hasOpeningArticleHeaderChromeHint()) continue
            if (!article.hasArticleBodySiblingAfter(header)) continue

            recordAndRemove(header, debug, "removeContentPatterns", null, "opening article header chrome")
        }
    }

    private fun openingArticleCandidates(content: Element): List<Element> {
        val candidates = mutableListOf<Element>()

        fun addUnique(element: Element) {
            if (candidates.none { it === element }) candidates.add(element)
        }

        if (content.normalName() == "article" || content.id().equals("post", ignoreCase = true)) {
            addUnique(content)
        }
        content.select("article, #post").forEach(::addUnique)

        return candidates
    }

    private fun removeRecommendationSiblingRuns(content: Element, debug: MutableList<RemovalRecord>) {
        for (headingBlock in content.select("h1, h2, h3, h4, h5, h6, div, p, section").toList()) {
            if (!headingBlock.isAttachedTo(content)) continue
            if (!isRecommendationSectionHeadingBlock(headingBlock)) continue

            val enclosingBlock = headingBlock.enclosingRecommendationBlock(content)
            if (enclosingBlock != null) {
                recordAndRemove(
                    enclosingBlock,
                    debug,
                    "removeContentPatterns",
                    null,
                    "recommendation block containing heading",
                )
                continue
            }

            var sibling = headingBlock.nextElementSibling()
            var removedSibling = false
            while (sibling != null && isRecommendationSiblingAfterHeading(sibling)) {
                val next = sibling.nextElementSibling()
                recordAndRemove(
                    sibling,
                    debug,
                    "removeContentPatterns",
                    null,
                    "recommendation card group after heading",
                )
                removedSibling = true
                sibling = next
            }

            recordAndRemove(
                headingBlock,
                debug,
                "removeContentPatterns",
                null,
                if (removedSibling) "recommendation heading with card group" else "orphan recommendation heading",
            )
        }
    }

    private fun Element.enclosingRecommendationBlock(root: Element): Element? {
        var current = parent()
        while (current != null && current !== root) {
            if (
                current.isAttachedTo(root) &&
                current.startsWithHeadingBlock(this) &&
                isTrailingRecommendationBlock(current)
            ) {
                return current
            }
            current = current.parent()
        }
        return null
    }

    private fun Element.startsWithHeadingBlock(headingBlock: Element): Boolean {
        val firstChild = children().firstOrNull { it.text().trim().isNotBlank() } ?: return false
        return firstChild === headingBlock || headingBlock.parents().any { it === firstChild }
    }

    private fun Element.firstSubstantiveChild(): Element? = children().firstOrNull { child ->
        child.normalName() !in NON_SUBSTANTIVE_OPENING_TAGS &&
            child.text().isNotBlank()
    }

    private fun Element.hasArticleBodySiblingAfter(header: Element): Boolean {
        var afterHeader = false
        for (child in children()) {
            if (child === header) {
                afterHeader = true
                continue
            }
            if (afterHeader && child.containsArticleBodyHint()) return true
        }
        return false
    }

    private fun Element.containsArticleBodyHint(): Boolean =
        hasArticleBodyHint() || select("*").any { it.hasArticleBodyHint() }

    private fun Element.hasArticleBodyHint(): Boolean {
        val hints = partialHaystack(this)
        return OPENING_ARTICLE_BODY_HINTS.any { it in hints } ||
            classNames().any { it.equals("body", ignoreCase = true) }
    }

    private fun Element.isOpeningArticleHeaderCandidate(): Boolean = normalName() == "header" ||
        classNames().any { it.equals("head", ignoreCase = true) }

    private fun Element.hasOpeningArticleHeaderChromeHint(): Boolean {
        val text = text().trim()
        if (text.isBlank() || text.length > OPENING_ARTICLE_HEADER_MAX_LENGTH) return false
        if (select("p").size > OPENING_ARTICLE_HEADER_MAX_PARAGRAPHS) return false

        val nestedHints = select("*").joinToString(" ") { partialHaystack(it) }
        val hints = "${partialHaystack(this)} $nestedHints"
        return OPENING_ARTICLE_HEADER_HINTS.any { it in hints } ||
            (isOpeningArticleHeaderCandidate() && select("h1, h2").isNotEmpty())
    }

    private fun removeNestedArticleFooterBlocks(content: Element, debug: MutableList<RemovalRecord>) {
        for (element in content.descendantsWithTagNamesSnapshot(NESTED_ARTICLE_FOOTER_TAGS)) {
            if (!element.isAttachedTo(content)) continue
            if (element.isNestedListContent()) continue
            if (element.isPlainParagraphWithoutFooterSignal()) continue
            removeNestedArticleFooterBlock(element, content, debug)
        }
    }

    private fun removeNestedArticleFooterBlock(element: Element, content: Element, debug: MutableList<RemovalRecord>) {
        val removal = nestedArticleFooterRemoval(element, content) ?: return
        if (isProtected(element)) return

        if (removal.tableOfContents) {
            removeTableOfContentsBlock(element, debug, "removeContentPatterns", null)
        } else {
            recordAndRemove(element, debug, "removeContentPatterns", null, removal.reason)
        }
    }

    private fun nestedArticleFooterRemoval(element: Element, content: Element): NestedFooterRemoval? = when {
        isOrphanSeparatorBlock(element) -> {
            NestedFooterRemoval("orphan separator block")
        }

        isTrailingDividerBlock(element, content) -> {
            NestedFooterRemoval("trailing divider")
        }

        isSkeletonRecirculationBlock(element) -> {
            NestedFooterRemoval("skeleton recirculation block")
        }

        isPostedByBylineBlock(element) -> {
            NestedFooterRemoval("posted-by byline strip")
        }

        isBreadcrumbBlock(element) -> {
            NestedFooterRemoval("breadcrumb block")
        }

        isTableOfContentsBlock(element) -> {
            NestedFooterRemoval("table of contents block", tableOfContents = true)
        }

        isSocialCounterBlock(element) -> {
            NestedFooterRemoval("social counter block")
        }

        isTrailingRecirculationLinkCluster(element) -> {
            NestedFooterRemoval("trailing recirculation link cluster")
        }

        isRecommendationSectionHeadingBlock(element) -> {
            NestedFooterRemoval("orphan recommendation heading")
        }

        isAboutAuthorFooterBlock(element) -> {
            NestedFooterRemoval("about-author footer block")
        }

        isArticleFooterDetailsBlock(element) -> {
            NestedFooterRemoval("article details footer block")
        }

        isRelatedTermsBlock(element) -> {
            NestedFooterRemoval("related terms footer block")
        }

        isTagListBlock(element) -> {
            NestedFooterRemoval("article footer tag list")
        }

        isCommentCountBlock(element) -> {
            NestedFooterRemoval("article footer comment count")
        }

        isBackToTopBlock(element) -> {
            NestedFooterRemoval("article footer back-to-top control")
        }

        isCommentPromptBlock(element) -> {
            NestedFooterRemoval("article footer comment prompt")
        }

        isReadyForMoreBlock(element) -> {
            NestedFooterRemoval("article footer subscription call to action")
        }

        isMobileAppPromoBlock(element) -> {
            NestedFooterRemoval("mobile app promo")
        }

        isNewsletterSignupBlock(element) -> {
            NestedFooterRemoval("newsletter signup")
        }

        isDonationWidgetBlock(element) -> {
            NestedFooterRemoval("donation widget")
        }

        isBylineMetadataStrip(element) -> {
            NestedFooterRemoval("byline metadata strip")
        }

        isArticlePackageBlock(element) -> {
            NestedFooterRemoval("article package card")
        }

        isInlineAuthorBioBlock(element) -> {
            NestedFooterRemoval("inline author bio")
        }

        isFollowTopicsBlock(element) -> {
            NestedFooterRemoval("follow topics prompt")
        }

        isStorySuggestionBlock(element) -> {
            NestedFooterRemoval("story suggestion prompt")
        }

        isLocalNewsFollowBlock(element) -> {
            NestedFooterRemoval("local news follow prompt")
        }

        else -> null
    }

    private fun isProtected(element: Element, hints: String = partialHaystack(element)): Boolean =
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

    private fun Element.isNestedListContent(): Boolean =
        normalName() in setOf("ul", "ol") && parent()?.normalName() == "li"

    private fun Element.isPlainParagraphWithoutFooterSignal(): Boolean {
        if (normalName() != "p") return false

        val text = text().trim().collapseWhitespace()
        if (text.isBlank() || text.length > PARAGRAPH_FOOTER_SIGNAL_MAX_LENGTH) return true

        return !hasParagraphFooterSignal(text)
    }

    private fun hasParagraphFooterSignal(text: String): Boolean = text in ORPHAN_SEPARATOR_TEXTS ||
        POSTED_BY_BYLINE_PATTERN.matches(text) ||
        RECOMMENDATION_SECTION_HEADING_PATTERN.matches(text) ||
        AUTHOR_FOLLOW_PATTERN.containsMatchIn(text) ||
        TRAILING_TAG_LABEL_PATTERN.containsMatchIn(text) ||
        COMMENT_COUNT_PATTERN.matches(text) ||
        BACK_TO_TOP_PATTERN.matches(text) ||
        COMMENT_PROMPT_PATTERN.containsMatchIn(text) ||
        READY_FOR_MORE_PATTERN.matches(text) ||
        MOBILE_APP_PROMO_PATTERN.containsMatchIn(text) ||
        NEWSLETTER_SIGNUP_PATTERN.containsMatchIn(text) ||
        INLINE_NEWSLETTER_PROMO_PATTERN.containsMatchIn(text) ||
        DONATION_WIDGET_PATTERN.containsMatchIn(text) ||
        BYLINE_METADATA_STRIP_PATTERN.containsMatchIn(text) ||
        TRAILING_BYLINE_DATE_PATTERN.matches(text) ||
        ARTICLE_PACKAGE_PATTERN.containsMatchIn(text) ||
        INLINE_AUTHOR_BIO_PATTERN.containsMatchIn(text) ||
        FOLLOW_TOPICS_PATTERN.containsMatchIn(text) ||
        STORY_SUGGESTION_PATTERN.containsMatchIn(text) ||
        LOCAL_NEWS_FOLLOW_PATTERN.containsMatchIn(text)

    private fun Element.hasFootnoteProtectionHint(): Boolean {
        val role = attr("role").lowercase()
        if (role == "doc-footnote" || role == "doc-endnote") return true
        val hints = partialHaystack(this)
        return "footnote" in hints || "footnotes" in hints || "footdef" in hints || "footref" in hints
    }

    private fun isLikelyProse(element: Element): Boolean {
        val paragraphs = element.select("p").count { it.text().split(WHITESPACE_PATTERN).size >= 8 }
        if (paragraphs >= 1) return true
        val text = element.text()
        val words = text.split(WHITESPACE_PATTERN).count { it.isNotBlank() }
        return words >= 35 && text.count { it == '.' || it == ',' } >= 2
    }

    private fun isTrailingRecommendationHeading(element: Element): Boolean {
        if (!HEADING_TAG_PATTERN.matches(element.normalName())) return false
        return RECOMMENDATION_HEADING_PATTERN.containsMatchIn(element.text().trim())
    }

    private fun isTrailingRecommendationBlock(element: Element): Boolean {
        val heading = element.selectFirst("h1, h2, h3, h4, h5, h6")?.text()
            ?: element.ownText()
        val text = element.text().trim()
        if (
            !RECOMMENDATION_HEADING_PATTERN.containsMatchIn(heading.trim()) &&
            !RECOMMENDATION_HEADING_PATTERN.containsMatchIn(text.take(RECOMMENDATION_TEXT_PREFIX_LENGTH))
        ) {
            return false
        }

        val linkCount = element.select("a").size
        val articleCount = element.select("article").size
        val imageCount = element.select("img, figure, picture").size
        return linkCount >= RECOMMENDATION_MIN_LINKS ||
            articleCount >= RECOMMENDATION_MIN_ARTICLES ||
            imageCount >= RECOMMENDATION_MIN_IMAGES
    }

    private fun isRecommendationSectionHeadingBlock(element: Element): Boolean {
        val text = element.text().trim().collapseWhitespace()
        if (text.isBlank() || text.length > RECOMMENDATION_HEADING_MAX_LENGTH) return false
        if (!RECOMMENDATION_SECTION_HEADING_PATTERN.matches(text)) return false
        if (element.normalName().matches(HEADING_TAG_PATTERN)) return true
        if (element.normalName() == "p" && element.select("a, img, figure, picture, table, pre, code").isEmpty()) {
            return true
        }

        val headings = element.select("h1, h2, h3, h4, h5, h6")
        return headings.size == 1 && headings.first()?.text()?.trim()?.collapseWhitespace() == text
    }

    private fun isRecommendationSiblingAfterHeading(element: Element): Boolean {
        if (isProtected(element)) return false

        val text = element.text().trim().collapseWhitespace()
        if (text.isBlank() || isOrphanSeparatorBlock(element)) return true

        val hints = "${partialHaystack(element)} ${element.select("*").joinToString(" ") { partialHaystack(it) }}"
        if (RECOMMENDATION_MODULE_HINTS.any { it in hints }) return true

        val articleCount = element.select("article").size + if (element.normalName() == "article") 1 else 0
        val linkCount = element.select("a[href]").size
        val imageCount = element.select("img, figure, picture").size

        return (articleCount >= 1 && linkCount >= 1 && imageCount >= 1) ||
            (articleCount >= RECOMMENDATION_MIN_ARTICLES && linkCount >= articleCount) ||
            (linkCount >= RECOMMENDATION_MIN_LINKS && imageCount >= 1 && !isLikelyProse(element)) ||
            element.isTrailingLinkedListRecommendation(linkCount, RECOMMENDATION_MIN_LINKS)
    }

    private fun isOrphanSeparatorBlock(element: Element): Boolean {
        val text = element.text().trim().collapseWhitespace()
        if (text !in ORPHAN_SEPARATOR_TEXTS) return false
        if (element.select("a, img, figure, picture, table, pre, code, math").isNotEmpty()) return false
        return element.children().all { child ->
            child.text().trim().collapseWhitespace().let { it.isBlank() || it in ORPHAN_SEPARATOR_TEXTS }
        }
    }

    private fun isTrailingDividerBlock(element: Element, content: Element): Boolean {
        if (element.normalName() != "hr") return false

        val siblingContext = element.dividerSiblingContext(content)
        var sibling = siblingContext.nextElementSibling()
        while (sibling != null) {
            if (sibling.hasSubstantiveContent()) return false
            sibling = sibling.nextElementSibling()
        }
        return true
    }

    private fun Element.dividerSiblingContext(content: Element): Element {
        val parent = parent()
        return if (parent != null && parent !== content && parent.children().singleOrNull() === this) {
            parent
        } else {
            this
        }
    }

    private fun Element.hasSubstantiveContent(): Boolean = text().trim().isNotBlank() ||
        select("img, picture, figure, table, pre, code, math, p, h1, h2, h3, h4, h5, h6").isNotEmpty()

    private fun isSkeletonRecirculationBlock(element: Element): Boolean {
        val text = element.text().trim().collapseWhitespace()
        if (text.isBlank() || text.length > SKELETON_RECIRCULATION_MAX_LENGTH) return false

        val loremCount = LOREM_PLACEHOLDER_PATTERN.findAll(text).count()
        if (loremCount < SKELETON_RECIRCULATION_MIN_PLACEHOLDERS) return false

        val nonPlaceholderProseCount = element.select("p").count { paragraph ->
            val paragraphText = paragraph.text().trim().collapseWhitespace()
            paragraphText.wordCount() >= SKELETON_RECIRCULATION_PROSE_WORD_GUARD &&
                !LOREM_PLACEHOLDER_PATTERN.containsMatchIn(paragraphText)
        }
        if (nonPlaceholderProseCount > 0) return false
        if (element.select(
                "[data-cy=article-content], article.article-content, .article-content",
            ).isNotEmpty()
        ) {
            return false
        }

        val hints = "${partialHaystack(element)} ${element.select("*").joinToString(" ") { partialHaystack(it) }}"
        val hasSkeletonHint = SKELETON_RECIRCULATION_HINTS.any { it in hints }
        return hasSkeletonHint && SKELETON_RECIRCULATION_HEADING_PATTERN.containsMatchIn(text)
    }

    private fun isArticleCardRecirculationBlock(element: Element): Boolean {
        val text = element.text().trim().collapseWhitespace()
        val links = element.select("a[href]")
        val hints = "${partialHaystack(element)} ${element.select("*").joinToString(" ") { partialHaystack(it) }}"

        val hasHeadlineLink = links.any { link ->
            link.text().trim().collapseWhitespace().wordCount() >= ARTICLE_CARD_MIN_HEADLINE_WORDS
        }
        val hasMetadataHint = ARTICLE_CARD_METADATA_HINTS.any { it in hints } ||
            BYLINE_METADATA_DATE_PATTERN.containsMatchIn(text) ||
            RELATIVE_TIME_AGO_PATTERN.containsMatchIn(text)
        val isImageOnlyMetadataCard = links.size == 1 && text.wordCount() <= ARTICLE_CARD_IMAGE_ONLY_MAX_WORDS

        return element.isArticleCardRecirculationCandidate(text) &&
            links.isNotEmpty() &&
            element.select("img, picture").isNotEmpty() &&
            ARTICLE_CARD_RECIRCULATION_HINTS.any { it in hints } &&
            hasMetadataHint &&
            (hasHeadlineLink || isImageOnlyMetadataCard)
    }

    private fun Element.isArticleCardRecirculationCandidate(text: String): Boolean =
        normalName() in ARTICLE_CARD_RECIRCULATION_TAGS &&
            text.isNotBlank() &&
            text.length <= ARTICLE_CARD_RECIRCULATION_MAX_LENGTH &&
            select("h1, h2").isEmpty() &&
            select("[data-cy=article-content], [itemprop=articleBody], .article-content").isEmpty() &&
            select("p").none { paragraph ->
                paragraph.text().trim().collapseWhitespace().wordCount() >= ARTICLE_CARD_PROSE_WORD_GUARD
            }

    private fun isPostedByBylineBlock(element: Element): Boolean {
        val text = element.text().trim().collapseWhitespace()
        if (text.length > POSTED_BY_BYLINE_MAX_LENGTH) return false
        if (!POSTED_BY_BYLINE_PATTERN.matches(text)) return false
        if (element.select("a, img, figure, picture, table, pre, code, math").isNotEmpty()) return false

        val hints = listOfNotNull(
            partialHaystack(element),
            element.parent()?.let(::partialHaystack),
        ).joinToString(" ")
        return POSTED_BY_BYLINE_HINTS.any { it in hints }
    }

    private fun isBreadcrumbBlock(element: Element): Boolean {
        val text = element.text().trim().collapseWhitespace()
        if (text.isBlank() || text.length > BREADCRUMB_MAX_LENGTH) return false
        if (element.select("pre, code, table, figure, img, picture, blockquote").isNotEmpty()) return false

        val linkCount = element.select("a[href]").size
        if (linkCount < BREADCRUMB_MIN_LINKS) return false

        val hints = partialHaystack(element)
        val hrefs = element.select("a[href]").joinToString(" ") { it.attr("href").lowercase() }
        return "breadcrumb" in hints ||
            "data-block nav" in hints ||
            ("nav" in hints && linkCount <= BREADCRUMB_MAX_LINKS) ||
            BREADCRUMB_HREF_PATTERN.containsMatchIn(hrefs)
    }

    private fun isTableOfContentsBlock(element: Element): Boolean {
        if (element.normalName() !in setOf("ul", "ol", "nav", "div", "section")) return false
        val text = element.text().trim().collapseWhitespace()
        if (text.isBlank() || text.length > TABLE_OF_CONTENTS_MAX_LENGTH) return false
        if (element.select("p, pre, code, table, figure, img, picture, blockquote").isNotEmpty()) return false

        val links = element.select("a[href]")
        if (links.size < TABLE_OF_CONTENTS_MIN_LINKS) return false
        val hashLinks = links.count { it.attr("href").trim().startsWith("#") }
        val hints = partialHaystack(element)
        return hashLinks == links.size ||
            "toc" in hints ||
            "table-of-contents" in hints
    }

    private fun removeTableOfContentsBlock(
        element: Element,
        debug: MutableList<RemovalRecord>,
        step: String,
        selector: String?,
    ) {
        val previousDivider = element.previousElementSibling()?.takeIf { it.normalName() == "hr" }
        val nextDivider = element.nextElementSibling()?.takeIf { it.normalName() == "hr" }

        recordAndRemove(element, debug, step, selector, "table of contents block")
        previousDivider?.let {
            recordAndRemove(it, debug, step, null, "table of contents divider")
        }
        nextDivider?.let {
            recordAndRemove(it, debug, step, null, "table of contents divider")
        }
    }

    private fun isSocialCounterBlock(element: Element): Boolean {
        val text = element.text().trim().collapseWhitespace()
        if (!SOCIAL_COUNTER_PATTERN.matches(text)) return false
        if (element.select("p, pre, code, table, figure, img, picture, blockquote").isNotEmpty()) return false
        val hints = partialHaystack(element)
        return "social" in hints ||
            "like" in hints ||
            "pencraft" in hints ||
            element.select("a, button").isNotEmpty()
    }

    private fun isStrongRecirculationChrome(element: Element, hints: String = partialHaystack(element)): Boolean {
        if (STRONG_RECIRCULATION_HINTS.none { it in hints }) return false
        if (element.normalName() in ROOT_CONTENT_TAGS) return false

        val links = element.select("a[href]")
        val cardLikeChildren = element.children().count { child ->
            val childHints = partialHaystack(child)
            "item" in childHints ||
                "card" in childHints ||
                "article" in childHints ||
                child.normalName() == "article"
        }
        return links.size >= RECOMMENDATION_MIN_LINKS ||
            cardLikeChildren >= RECOMMENDATION_MIN_ARTICLES ||
            RECOMMENDATION_HEADING_PATTERN.containsMatchIn(element.text().take(RECOMMENDATION_TEXT_PREFIX_LENGTH))
    }

    private fun isAuthorFollowBlock(element: Element): Boolean {
        val text = element.text().trim()
        if (text.length > AUTHOR_FOLLOW_MAX_LENGTH || !AUTHOR_FOLLOW_PATTERN.containsMatchIn(text)) return false
        if (element.select("a[href]").isEmpty()) return false

        val hints = partialHaystack(element)
        return element.normalName() == "p" ||
            "follow" in hints ||
            "author" in hints ||
            "social" in hints
    }

    private fun isTagListBlock(element: Element): Boolean {
        val text = element.text().trim()
        if (!TRAILING_TAG_LABEL_PATTERN.containsMatchIn(text)) return false

        val linkCount = element.select("a").size
        val tagLinkCount = element.select("""a[href*="/tag/"], a[rel~=tag]""").size
        val wordCount = text.split(WHITESPACE_PATTERN).count { it.isNotBlank() }

        return tagLinkCount >= TRAILING_TAG_MIN_LINKS ||
            (linkCount >= TRAILING_TAG_MIN_LINKS && wordCount <= TRAILING_TAG_MAX_WORDS)
    }

    private fun isTrailingRecirculationLinkCluster(element: Element): Boolean =
        isArticleCardRecirculationBlock(element) || isGenericTrailingRecirculationLinkCluster(element)

    private fun isGenericTrailingRecirculationLinkCluster(element: Element): Boolean {
        if (!element.isRecirculationClusterCandidate()) return false
        val text = element.text().trim().collapseWhitespace()
        if (text.isBlank() || text.length > RECIRCULATION_CLUSTER_MAX_LENGTH) return false

        val links = element.select("a[href]")
        if (links.size < RECIRCULATION_CLUSTER_MIN_LINKS) return false

        val linkTextLength = links.sumOf { it.text().trim().length }
        val linkDensity = linkTextLength.toDouble() / text.length
        if (linkDensity < RECIRCULATION_CLUSTER_MIN_LINK_DENSITY) return false

        val linkedRows = element.children().count { child ->
            child.select("a[href]").size >= RECIRCULATION_CLUSTER_ROW_MIN_LINKS
        }
        val tagLinks = links.count { link ->
            val href = link.attr("href").lowercase()
            href.contains("/tag/") || href.contains("#")
        }
        return linkedRows >= RECIRCULATION_CLUSTER_MIN_ROWS ||
            tagLinks >= RECIRCULATION_CLUSTER_MIN_TAG_LINKS ||
            isStrongRecirculationChrome(element)
    }

    private fun Element.isRecirculationClusterCandidate(): Boolean = normalName() in RECIRCULATION_CLUSTER_TAGS &&
        hasOnlyNonSubstantiveFollowingSiblings() &&
        select("pre, code, table, figure, img, picture, blockquote").isEmpty()

    private fun Element.hasOnlyNonSubstantiveFollowingSiblings(): Boolean {
        var sibling = nextElementSibling()
        while (sibling != null) {
            if (sibling.normalName() == "hr" || !sibling.hasSubstantiveContent()) {
                sibling = sibling.nextElementSibling()
                continue
            }
            return false
        }
        return true
    }

    private fun isAboutAuthorFooterBlock(element: Element): Boolean {
        val text = element.text().trim().collapseWhitespace()
        if (text.isBlank() || text.length > ABOUT_AUTHOR_FOOTER_MAX_LENGTH) return false
        if (!ABOUT_AUTHOR_FOOTER_PATTERN.containsMatchIn(text)) return false

        val proseParagraphs = element.select("p").count { paragraph ->
            paragraph.text().trim().collapseWhitespace().wordCount() >= ABOUT_AUTHOR_PROSE_WORD_GUARD
        }
        if (proseParagraphs > 0) return false

        val hints = "${partialHaystack(element)} ${element.select("*").joinToString(" ") { partialHaystack(it) }}"
        return "author" in hints ||
            element.select("""a[href*="/author/"], a[rel~=author], img""").isNotEmpty() ||
            text.wordCount() <= ABOUT_AUTHOR_FOOTER_MAX_WORDS
    }

    private fun isArticleFooterDetailsBlock(element: Element): Boolean {
        val text = element.text().trim().collapseWhitespace()
        if (text.isBlank() || text.length > ARTICLE_FOOTER_DETAILS_MAX_LENGTH) return false
        if (!ARTICLE_FOOTER_DETAILS_PATTERN.containsMatchIn(text)) return false

        val proseParagraphs = element.select("p").count { paragraph ->
            paragraph.text().trim().collapseWhitespace().wordCount() >= ARTICLE_FOOTER_DETAILS_PROSE_WORD_GUARD
        }
        if (proseParagraphs > 0) return false

        val headings = element.select("h1, h2, h3, h4, h5, h6")
            .map { it.text().trim().collapseWhitespace() }
        val hasFooterHeading = headings.any { ARTICLE_FOOTER_DETAILS_HEADING_PATTERN.matches(it) }
        val hints = partialHaystack(element)
        return hasFooterHeading ||
            "details" in hints ||
            "credits" in hints ||
            "share" in hints
    }

    private fun isRelatedTermsBlock(element: Element): Boolean {
        val text = element.text().trim().collapseWhitespace()
        if (text.isBlank() || text.length > RELATED_TERMS_MAX_LENGTH) return false
        if (!RELATED_TERMS_PATTERN.containsMatchIn(text)) return false
        if (element.select(
                "p",
            ).any { it.text().trim().collapseWhitespace().wordCount() >= RELATED_TERMS_PROSE_WORD_GUARD }
        ) {
            return false
        }

        val linkCount = element.select("a[href]").size
        val hints = partialHaystack(element)
        return linkCount >= RELATED_TERMS_MIN_LINKS ||
            "tag" in hints ||
            "term" in hints
    }

    private fun isCommentCountBlock(element: Element): Boolean {
        val text = element.text().trim()
        if (!COMMENT_COUNT_PATTERN.matches(text)) return false

        val links = element.select("a[href]")
        if (links.isEmpty() || links.size > COMMENT_COUNT_MAX_LINKS) return false

        val hrefHints = links.joinToString(" ") { it.attr("href") }.lowercase()
        val elementHints = "${partialHaystack(element)} ${element.parent()?.let(::partialHaystack).orEmpty()}"
        return COMMENT_LINK_HINTS.any { it in hrefHints } ||
            "comment" in elementHints ||
            "footer" in elementHints
    }

    private fun isBackToTopBlock(element: Element): Boolean {
        val text = element.text().trim()
        if (!BACK_TO_TOP_PATTERN.matches(text)) return false

        val hrefs = element.select("a[href]").map { it.attr("href").trim().lowercase() }
        val hints = partialHaystack(element)
        return hrefs.any { it == "#" || it == "#top" || it.endsWith("#top") } ||
            "scroll" in hints ||
            "back-to-top" in hints
    }

    private fun isCommentPromptBlock(element: Element): Boolean {
        val text = element.text().trim()
        if (!COMMENT_PROMPT_PATTERN.containsMatchIn(text)) return false

        val hints = partialHaystack(element)
        return "comment" in hints ||
            "viafoura" in hints ||
            text.length <= COMMENT_PROMPT_MAX_LENGTH
    }

    private fun isReadyForMoreBlock(element: Element): Boolean {
        val text = element.text().trim()
        if (!READY_FOR_MORE_PATTERN.matches(text)) return false

        val hints = listOfNotNull(
            partialHaystack(element),
            element.parent()?.let(::partialHaystack),
            element.parent()?.parent()?.let(::partialHaystack),
        ).joinToString(" ")
        return "substack" in hints ||
            "pubinvertedtheme" in hints ||
            "single-post" in hints
    }

    private fun isMobileAppPromoBlock(element: Element): Boolean {
        val text = element.text().trim()
        if (text.length > MOBILE_APP_PROMO_MAX_LENGTH) return false
        return MOBILE_APP_PROMO_PATTERN.containsMatchIn(text)
    }

    private fun isNewsletterSignupBlock(element: Element): Boolean {
        val text = element.text().trim()
        if (text.isBlank() || text.length > NEWSLETTER_SIGNUP_MAX_LENGTH) return false
        if (INLINE_NEWSLETTER_PROMO_PATTERN.containsMatchIn(text)) return true
        if (!NEWSLETTER_SIGNUP_PATTERN.containsMatchIn(text)) return false

        val hints = partialHaystack(element)
        val hasWidgetHint = "newsletter" in hints ||
            "mailinglist" in hints ||
            element.select("form, input, button").isNotEmpty()
        return hasWidgetHint || NEWSLETTER_LEGAL_PATTERN.containsMatchIn(text)
    }

    private fun isDonationWidgetBlock(element: Element): Boolean {
        val text = element.text().trim()
        if (text.length > DONATION_WIDGET_MAX_LENGTH) return false

        val hints = partialHaystack(element) + " " +
            element.select("a[href], img[src], img[alt]").joinToString(" ") {
                "${it.attr("href")} ${it.attr("src")} ${it.attr("alt")}"
            }.lowercase()

        return DONATION_WIDGET_PATTERN.containsMatchIn(text) &&
            DONATION_WIDGET_HINTS.any { it in hints }
    }

    private fun isBylineMetadataStrip(element: Element): Boolean {
        val text = element.text().trim().collapseWhitespace()
        if (text.length > BYLINE_METADATA_STRIP_MAX_LENGTH) return false
        val hasTrailingBylineDate = TRAILING_BYLINE_DATE_PATTERN.matches(text)
        if (!BYLINE_METADATA_STRIP_PATTERN.containsMatchIn(text) && !hasTrailingBylineDate) {
            return false
        }

        val authorLinkCount = element.select("""a[href*="/author/"], a[rel~=author]""").size
        val hasDate = element.select("time, [datetime]").isNotEmpty() ||
            BYLINE_METADATA_DATE_PATTERN.containsMatchIn(text)
        val hints = partialHaystack(element)
        val hasAuthorMarker = authorLinkCount >= 1 ||
            element.select("img").isNotEmpty() ||
            hasTrailingBylineDate
        val hasFooterHint = "byline" in hints ||
            "author" in hints ||
            "uppercase" in hints ||
            "font-sans" in hints ||
            element.select(
                """a[href*="google.com/preferences/source"], a[href="#ep-comments"]""",
            ).isNotEmpty() ||
            hasTrailingBylineDate

        return hasAuthorMarker && hasDate && hasFooterHint
    }

    private fun isArticlePackageBlock(element: Element): Boolean {
        val text = element.text().trim().collapseWhitespace()
        if (text.length > ARTICLE_PACKAGE_MAX_LENGTH) return false
        if (!ARTICLE_PACKAGE_PATTERN.containsMatchIn(text)) return false

        val hints = partialHaystack(element)
        return element.select("a[href]").isNotEmpty() ||
            "package" in hints ||
            "series" in hints ||
            "collection" in hints
    }

    private fun isInlineAuthorBioBlock(element: Element): Boolean {
        val text = element.text().trim().collapseWhitespace()
        if (text.length > INLINE_AUTHOR_BIO_MAX_LENGTH) return false
        if (!INLINE_AUTHOR_BIO_PATTERN.containsMatchIn(text)) return false

        val descendantHints = element.select("*").joinToString(" ") { partialHaystack(it) }
        val hrefHints = element.select("a[href]").joinToString(" ") { it.attr("href") }.lowercase()
        val hints = "${partialHaystack(element)} $descendantHints $hrefHints"
        val hasBioAction = text.contains("read full bio", ignoreCase = true)
        val hasProfileImageAndRole = element.select("img").isNotEmpty() &&
            AUTHOR_ROLE_LABEL_PATTERN.containsMatchIn(text)
        return "author" in hints ||
            "byline" in hints ||
            "writer" in hints ||
            "profile" in hints ||
            hasBioAction ||
            hasProfileImageAndRole
    }

    private fun isFollowTopicsBlock(element: Element): Boolean {
        val text = element.text().trim().collapseWhitespace()
        if (text.length > FOLLOW_TOPICS_MAX_LENGTH) return false
        if (!FOLLOW_TOPICS_PATTERN.containsMatchIn(text)) return false

        val descendantHints = element.select("*").joinToString(" ") { partialHaystack(it) }
        val hints = "${partialHaystack(element)} $descendantHints"
        return "follow" in hints ||
            element.select("ul, ol, li, a[href], button").isNotEmpty() ||
            FOLLOW_TOPICS_STRONG_CONTEXT_PATTERN.containsMatchIn(text)
    }

    private fun isStorySuggestionBlock(element: Element): Boolean {
        val text = element.text().trim().collapseWhitespace()
        if (text.length > STORY_SUGGESTION_MAX_LENGTH) return false
        return STORY_SUGGESTION_PATTERN.containsMatchIn(text)
    }

    private fun isLocalNewsFollowBlock(element: Element): Boolean {
        val text = element.text().trim().collapseWhitespace()
        if (text.length > LOCAL_NEWS_FOLLOW_MAX_LENGTH) return false
        if (!LOCAL_NEWS_FOLLOW_PATTERN.containsMatchIn(text)) return false
        return element.select("a[href]").size >= 2
    }

    private fun String.collapseWhitespace(): String = replace(WHITESPACE_PATTERN, " ")

    private fun String.wordCount(): Int = split(WHITESPACE_PATTERN).count { it.isNotBlank() }
}

private fun Element.isInlineTextButton(): Boolean {
    if (normalName() != "button") return false
    if (parents().none { it.normalName() == "p" }) return false
    if (text().trim().isBlank()) return false
    if (select("svg, img, picture, iframe, input, select, textarea").isNotEmpty()) return false
    return true
}

private fun partialHaystack(element: Element): String = elementHintHaystack(element)

private fun Element.isTrailingLinkedListRecommendation(linkCount: Int, minLinks: Int): Boolean {
    if (normalName() !in setOf("ul", "ol")) return false
    if (linkCount < minLinks) return false
    if (select("p, blockquote, pre, code, table, figure, picture").isNotEmpty()) return false
    val items = children().filter { it.normalName() == "li" }
    if (items.size < minLinks) return false
    return items.count { it.select("a[href]").isNotEmpty() } >= minLinks
}
