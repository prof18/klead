package com.prof18.klead.internal.removal

import com.prof18.klead.RemovalRecord
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

internal object RemovalPipeline {
    fun apply(
        content: Element,
        debug: MutableList<RemovalRecord>,
        metadataImage: String? = null,
        policy: RemovalPolicy = RemovalPolicy(),
    ) {
        if (policy.removeHiddenElements) {
            removeHiddenElements(content, debug)
        }
        if (policy.removeExactSelectors) {
            removeExactSelectors(content, debug)
        }
        if (policy.removePartialSelectors) {
            removePartialSelectors(content, debug)
        }
        if (policy.removeLowScoring) {
            removeLowScoringBlocks(content, debug)
        }
        if (policy.removeContentPatterns) {
            removeContentPatterns(content, debug)
        }
        ImageRemovalPipeline.apply(content, metadataImage, debug, ::partialHaystack)
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
        for (element in content.select("*").toList()) {
            val haystack = partialHaystack(element)
            if (isProtected(element)) continue
            if (isLikelyProse(element) && !isStrongRecirculationChrome(element, haystack)) continue
            if (PARTIAL_PATTERNS.any { it in haystack }) {
                recordAndRemove(element, debug, "removePartialSelectors", null, "partial clutter attribute")
            }
        }
    }

    private fun removeLowScoringBlocks(content: Element, debug: MutableList<RemovalRecord>) {
        for (element in content.select("section, aside, div, ul, ol").toList()) {
            if (isProtected(element) || isLikelyProse(element)) continue
            if (element.isNestedListContent()) continue
            val text = element.text()
            val linkText = element.select("a").sumOf { it.text().length }
            val linkDensity = if (text.isBlank()) 0.0 else linkText.toDouble() / text.length
            val linkCount = element.select("a").size
            if (linkCount >= 3 && linkDensity > 0.55) {
                recordAndRemove(element, debug, "removeLowScoring", null, "link-heavy low scoring block")
            }
        }
    }

    private fun removeContentPatterns(content: Element, debug: MutableList<RemovalRecord>) {
        removeOpeningArticleHeaderBlocks(content, debug)
        removeRecommendationSiblingRuns(content, debug)
        TrailingContentPatterns.remove(content, debug)
        removeNestedArticleFooterBlocks(content, debug)
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
        for (element in content.select("aside, div, p, section, ul, ol, hr").toList()) {
            if (shouldSkipNestedArticleFooterRemoval(element)) continue
            removeNestedArticleFooterBlock(element, content, debug)
        }
    }

    private fun shouldSkipNestedArticleFooterRemoval(element: Element): Boolean =
        isProtected(element) || element.isNestedListContent()

    private fun removeNestedArticleFooterBlock(element: Element, content: Element, debug: MutableList<RemovalRecord>) {
        when {
            isOrphanSeparatorBlock(element) -> {
                recordAndRemove(element, debug, "removeContentPatterns", null, "orphan separator block")
            }

            isTrailingDividerBlock(element, content) -> {
                recordAndRemove(element, debug, "removeContentPatterns", null, "trailing divider")
            }

            isSkeletonRecirculationBlock(element) -> {
                recordAndRemove(element, debug, "removeContentPatterns", null, "skeleton recirculation block")
            }

            isPostedByBylineBlock(element) -> {
                recordAndRemove(element, debug, "removeContentPatterns", null, "posted-by byline strip")
            }

            isBreadcrumbBlock(element) -> {
                recordAndRemove(element, debug, "removeContentPatterns", null, "breadcrumb block")
            }

            isTableOfContentsBlock(element) -> {
                removeTableOfContentsBlock(element, debug, "removeContentPatterns", null)
            }

            isSocialCounterBlock(element) -> {
                recordAndRemove(element, debug, "removeContentPatterns", null, "social counter block")
            }

            isTrailingRecirculationLinkCluster(element) -> {
                removeNestedFooterBlock(element, debug, "trailing recirculation link cluster")
            }

            isRecommendationSectionHeadingBlock(element) -> {
                recordAndRemove(element, debug, "removeContentPatterns", null, "orphan recommendation heading")
            }

            isAboutAuthorFooterBlock(element) -> {
                recordAndRemove(element, debug, "removeContentPatterns", null, "about-author footer block")
            }

            isArticleFooterDetailsBlock(element) -> {
                recordAndRemove(element, debug, "removeContentPatterns", null, "article details footer block")
            }

            isRelatedTermsBlock(element) -> {
                recordAndRemove(element, debug, "removeContentPatterns", null, "related terms footer block")
            }

            isTagListBlock(element) -> {
                recordAndRemove(element, debug, "removeContentPatterns", null, "article footer tag list")
            }

            isCommentCountBlock(element) -> {
                recordAndRemove(element, debug, "removeContentPatterns", null, "article footer comment count")
            }

            isBackToTopBlock(element) -> {
                recordAndRemove(element, debug, "removeContentPatterns", null, "article footer back-to-top control")
            }

            isCommentPromptBlock(element) -> {
                recordAndRemove(element, debug, "removeContentPatterns", null, "article footer comment prompt")
            }

            isReadyForMoreBlock(element) -> {
                removeNestedFooterBlock(element, debug, "article footer subscription call to action")
            }

            isMobileAppPromoBlock(element) -> {
                recordAndRemove(element, debug, "removeContentPatterns", null, "mobile app promo")
            }

            isNewsletterSignupBlock(element) -> {
                recordAndRemove(element, debug, "removeContentPatterns", null, "newsletter signup")
            }

            isDonationWidgetBlock(element) -> {
                recordAndRemove(element, debug, "removeContentPatterns", null, "donation widget")
            }

            isBylineMetadataStrip(element) -> {
                recordAndRemove(element, debug, "removeContentPatterns", null, "byline metadata strip")
            }

            isArticlePackageBlock(element) -> {
                recordAndRemove(element, debug, "removeContentPatterns", null, "article package card")
            }

            isInlineAuthorBioBlock(element) -> {
                recordAndRemove(element, debug, "removeContentPatterns", null, "inline author bio")
            }

            isFollowTopicsBlock(element) -> {
                recordAndRemove(element, debug, "removeContentPatterns", null, "follow topics prompt")
            }

            isStorySuggestionBlock(element) -> {
                recordAndRemove(element, debug, "removeContentPatterns", null, "story suggestion prompt")
            }

            isLocalNewsFollowBlock(element) -> {
                recordAndRemove(element, debug, "removeContentPatterns", null, "local news follow prompt")
            }
        }
    }

    private fun removeNestedFooterBlock(element: Element, debug: MutableList<RemovalRecord>, reason: String) {
        recordAndRemove(element, debug, "removeContentPatterns", null, reason)
    }

    private fun isProtected(element: Element): Boolean {
        val hints = partialHaystack(element)
        return element.`is`("pre, code, figure, picture, table, math, blockquote") ||
            element.parents().any { it.`is`("pre, code, figure, picture, table, math, blockquote") } ||
            element.parents().any { it.hasFootnoteProtectionHint() } ||
            element.select(".footdef, .footref, [role=doc-footnote]").isNotEmpty() ||
            "footnote" in hints ||
            "footnotes" in hints ||
            "footdef" in hints ||
            "footref" in hints ||
            "callout" in hints ||
            "admonition" in hints
    }

    private fun Element.isNestedListContent(): Boolean =
        normalName() in setOf("ul", "ol") && parent()?.normalName() == "li"

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

    private val EXACT_SELECTORS = listOf(
        "nav",
        "footer",
        "#fps",
        "[id*=footer]",
        "form",
        "button",
        "input",
        "select",
        "textarea",
        "[role=navigation]",
        "#comments",
        "#discussion",
        "#article-comments",
        "#comments-loading",
        "#comments-loaded",
        "#viafoura-comments-container",
        "#viafoura-comment-wrapper",
        ".viafoura-twig-component",
        ".viafoura",
        "[data-component-name*=Comments]",
        "[data-component-name*=comments]",
        "[class*=CommentsWrapper]",
        "#reactions-title",
        "[data-component-name*=ScrollUp]",
        "[data-component-name*=scroll]",
        "#author-bio",
        ".more-like-this",
        ":scope > div:first-child > header",
        ".wp-block-post-featured-image__caption",
        ".author-box",
        ".author-profile",
        ".post-author",
        ".byline-box",
        ".writer-contact-block",
        """[class*=WriterContactBlock]""",
        ".entry-meta",
        ".post-meta",
        ".post-meta-infos",
        ".article-meta",
        ".posted-on",
        ".byline",
        ".newsletter-promotion-large",
        ".newsletter-section",
        ".newsletter-form__wrapper",
        ".subscription-widget-wrap",
        ".subscription-widget",
        ".subscribe-widget",
        """[data-inview-type*=newsletter]""",
        """[data-inview-category*=Newsletter]""",
        """[data-component-name*=SubscribeWidget]""",
        ".ad",
        ".ads",
        ".advertisement",
        ".comments",
        ".comment",
        ".top-comment",
        ".share",
        ".sharing",
        ".related",
        ".related-posts",
        ".toc",
        ".table-of-contents",
    )

    private val TABLE_OF_CONTENTS_EXACT_SELECTORS = setOf(".toc", ".table-of-contents")

    private val PARTIAL_PATTERNS = listOf(
        "advert",
        "breadcrumb",
        "promo",
        "recommend",
        "related",
        "share",
        "sidebar",
        "sponsor",
        "subscribe",
        "newsletter",
    )

    private val SUBSCRIBE_PATTERN = Regex(
        """\b(subscribe|newsletter|weekly updates|product announcements)\b""",
        RegexOption.IGNORE_CASE,
    )

    private val RECOMMENDATION_HEADING_PATTERN = Regex(
        """\b(recommended|related|related terms|explore more|keep exploring|discover more|more stories|more from|more on|read more|you may also like|popular stories|most viewed|consigliati|altre storie|i più letti|in evidenza|potrebbe interessarti)\b|^best(?:\s+[\p{L}\p{N}][\p{L}\p{N} .'"’&-]{0,80})?\s+(accessories|deals|offers|prices?|discounts?|sales?)$""",
        RegexOption.IGNORE_CASE,
    )

    private val RECOMMENDATION_SECTION_HEADING_PATTERN = Regex(
        """^(related\s+content|related\s+articles?|related\s+terms|recommended(?:\s+for\s+you)?|explore\s+more|keep\s+exploring|discover\s+more(?:\s+.+)?|what\s+to\s+read\s+next|read\s+more|for\s+more\s+on\s+this\s+topic|popular\s+stories|most\s+viewed|latest\s+articles?|latest\s+in\s+.+|more\s+stories|more\s+from\s+.+|you\s+may\s+also\s+like|best(?:\s+[\p{L}\p{N}][\p{L}\p{N} .'"’&-]{0,80})?\s+(?:accessories|deals|offers|prices?|discounts?|sales?)|consigliati|altre\s+storie|i\s+più\s+letti|potrebbe\s+interessarti)$""",
        RegexOption.IGNORE_CASE,
    )

    private val LOREM_PLACEHOLDER_PATTERN = Regex(
        """\blorem\s+ipsum\s+dolor\s+sit\s+amet\b""",
        RegexOption.IGNORE_CASE,
    )

    private val SKELETON_RECIRCULATION_HEADING_PATTERN = Regex(
        """\b(latest\s+in|most\s+popular|most\s+viewed|popular\s+stories|recommended|related|read\s+more)\b""",
        RegexOption.IGNORE_CASE,
    )

    private val RELATIVE_TIME_AGO_PATTERN = Regex(
        """\b\d+\s+(?:minute|hour|day|week|month|year)s?\s+ago\b""",
        RegexOption.IGNORE_CASE,
    )

    private val AUTHOR_FOLLOW_PATTERN = Regex(
        """^\s*follow\s+[\p{L}\p{N} ._'’-]{1,48}\s*:""",
        RegexOption.IGNORE_CASE,
    )

    private val POSTED_BY_BYLINE_PATTERN = Regex(
        """^posted\s+by\s+[\p{L}\p{N} ._'&/@-]{1,80}$""",
        RegexOption.IGNORE_CASE,
    )

    private val TRAILING_TAG_LABEL_PATTERN = Regex(
        """^\s*(tags?|tagged|etichette?)\s*:""",
        RegexOption.IGNORE_CASE,
    )

    private val COMMENT_COUNT_PATTERN = Regex(
        """^\[?\s*\d+\s+comments?\s*\]?$""",
        RegexOption.IGNORE_CASE,
    )

    private val SOCIAL_COUNTER_PATTERN = Regex(
        """^\d+\s+(?:likes?|shares?|reposts?)$""",
        RegexOption.IGNORE_CASE,
    )

    private val BREADCRUMB_HREF_PATTERN = Regex("""(?:^|\s)/(?:archive|posts?|blog|news|category|tags?)(?:/|\s|$)""")

    private val BACK_TO_TOP_PATTERN = Regex(
        """^back\s+to\s+top$""",
        RegexOption.IGNORE_CASE,
    )

    private val COMMENT_PROMPT_PATTERN = Regex(
        """\b(commenting|join the conversation|display name before commenting)\b""",
        RegexOption.IGNORE_CASE,
    )

    private val READY_FOR_MORE_PATTERN = Regex(
        """^ready\s+for\s+more\??$""",
        RegexOption.IGNORE_CASE,
    )

    private val MOBILE_APP_PROMO_PATTERN = Regex(
        """\b(download our app|scarica l['’]?app|app per rimanere sempre aggiornato|also on mobile|anche su mobile)\b""",
        RegexOption.IGNORE_CASE,
    )

    private val NEWSLETTER_SIGNUP_PATTERN = Regex(
        """\b(subscribe\s+to\s+(?:our|the|a)\s+newsletter|receive\s+newsletter|newsletter\s+signup|subscribe\s+.*\bnewsletter|sign\s+up\s+for\s+.{0,80}\bnewsletters?\b)\b""",
        RegexOption.IGNORE_CASE,
    )

    private val NEWSLETTER_LEGAL_PATTERN = Regex(
        """\b(marketing\s+emails|terms\s+of\s+use|privacy\s+policy|unsubscribe\s+(?:anytime|any\s+time))\b""",
        RegexOption.IGNORE_CASE,
    )

    private val INLINE_NEWSLETTER_PROMO_PATTERN = Regex(
        """\bwant\s+to\s+learn\s+more\s+about\s+getting\s+the\s+best\s+out\s+of\s+your\s+tech\b""",
        RegexOption.IGNORE_CASE,
    )

    private val DONATION_WIDGET_PATTERN = Regex(
        """\b(enjoyed\s+the\s+article|buy\s+me\s+a\s+coffee|support\s+(?:us|our\s+work))\b""",
        RegexOption.IGNORE_CASE,
    )

    private val DONATION_WIDGET_HINTS = listOf(
        "ko-fi",
        "kofi",
        "buy me a coffee",
    )

    private val BYLINE_METADATA_STRIP_PATTERN = Regex(
        """^\s*by\b.+(?:\bedited\s+by\b|\breviewed\s+by\b|\bupdated\s+by\b|[|].+\b(?:jan|feb|mar|apr|may|jun|jul|aug|sep|sept|oct|nov|dec)\p{L}*\.?\s+\d{1,2},\s+\d{4}\b)""",
        RegexOption.IGNORE_CASE,
    )

    private val BYLINE_METADATA_DATE_PATTERN = Regex(
        """\b(?:jan|feb|mar|apr|may|jun|jul|aug|sep|sept|oct|nov|dec)\p{L}*\.?\s+\d{1,2},\s+\d{4}\b|\b\d{4}-\d{2}-\d{2}T""",
        RegexOption.IGNORE_CASE,
    )

    private val TRAILING_BYLINE_DATE_PATTERN = Regex(
        """^by\s+[\p{L}\p{N} ._'&/@-]{1,80}\s+\b(?:jan|feb|mar|apr|may|jun|jul|aug|sep|sept|oct|nov|dec)\p{L}*\.?\s+\d{1,2},\s+\d{4}$""",
        RegexOption.IGNORE_CASE,
    )

    private val ARTICLE_PACKAGE_PATTERN = Regex(
        """^part\s+of\b.+\bsee\s+all\s+updates\b""",
        RegexOption.IGNORE_CASE,
    )

    private val authorRoleAlternation = listOf(
        "contributor",
        "freelance\\s+writer",
        "news\\s+writer",
        "staff\\s+writer",
        "senior\\s+writer",
        "reporter",
        "journalist",
        "editor",
        "reviewer",
        "product\\s+manager",
    ).joinToString("|")

    private val INLINE_AUTHOR_BIO_PATTERN = Regex(
        """\bis\s+an?\s+[\p{L}\p{N} .,&'’/-]{0,80}\b($authorRoleAlternation)\b""",
        RegexOption.IGNORE_CASE,
    )

    private val AUTHOR_ROLE_LABEL_PATTERN = Regex(
        """\b($authorRoleAlternation)\b""",
        RegexOption.IGNORE_CASE,
    )

    private val FOLLOW_TOPICS_PATTERN = Regex(
        """\bfollow\s+topics\s+and\s+authors\b|\bpersonalized\s+homepage\s+feed\b|\breceive\s+email\s+updates\b|\b(?:favorite|preferred)\s+source\s+in\s+google\b|\bgoogle\s+discover\b""",
        RegexOption.IGNORE_CASE,
    )

    private val FOLLOW_TOPICS_STRONG_CONTEXT_PATTERN = Regex(
        """\bfrom\s+this\s+story\b.+\b(receive\s+email\s+updates|personalized\s+homepage)\b""",
        RegexOption.IGNORE_CASE,
    )

    private val STORY_SUGGESTION_PATTERN = Regex(
        """\b(do\s+you\s+have\s+a\s+story\s+suggestion|contact\s+us\s+below)\b""",
        RegexOption.IGNORE_CASE,
    )

    private val LOCAL_NEWS_FOLLOW_PATTERN = Regex(
        """^follow\s+[\p{L}\p{N} .,'’&-]{1,80}\s+news\s+on\b""",
        RegexOption.IGNORE_CASE,
    )

    private val ABOUT_AUTHOR_FOOTER_PATTERN = Regex(
        """^about\s+the\s+authors?\b""",
        RegexOption.IGNORE_CASE,
    )

    private val ARTICLE_FOOTER_DETAILS_PATTERN = Regex(
        """\b(?:share\b.*\bdetails\b.*\b(?:last\s+updated|editor|contact|location)\b|details\b.*\b(?:last\s+updated|editor|contact|location)\b.*\b(?:editor|contact|location)\b)""",
        RegexOption.IGNORE_CASE,
    )

    private val ARTICLE_FOOTER_DETAILS_HEADING_PATTERN = Regex(
        """^(share|details|related\s+terms)$""",
        RegexOption.IGNORE_CASE,
    )

    private val RELATED_TERMS_PATTERN = Regex(
        """^related\s+terms\b""",
        RegexOption.IGNORE_CASE,
    )

    private val PROTECTED_EXACT_SELECTOR_OVERRIDES = setOf(
        ".wp-block-post-featured-image__caption",
        "img.hide-when-no-script",
        """img[aria-label="image unavailable"]""",
        """img[src*="grey-placeholder"]""",
    )

    private val NON_SUBSTANTIVE_OPENING_TAGS = setOf(
        "script",
        "style",
        "template",
        "noscript",
    )

    private val OPENING_ARTICLE_BODY_HINTS = listOf(
        "article-body",
        "article-content",
        "post-content",
        "story-body",
    )

    private val OPENING_ARTICLE_HEADER_HINTS = listOf(
        "article-aux",
        "article-meta",
        "block-header",
        "hero-caption",
        "mega-header",
        "river-score",
        "rumor-score",
        "upper-deck",
    )

    private val COMMENT_LINK_HINTS = listOf(
        "/thread",
        "/comment",
        "#comment",
        "forums.",
    )

    private val RECOMMENDATION_MODULE_HINTS = listOf(
        "article-card",
        "display-card",
        "is-entire-card-clickable",
        "read-next",
        "recommend",
        "recirc",
        "related",
        "river",
        "what-to-read-next",
    )

    private val STRONG_RECIRCULATION_HINTS = listOf(
        "recommend",
        "recirc",
        "related",
        "read-next",
        "what-to-read-next",
    )

    private val ROOT_CONTENT_TAGS = setOf("article", "main")
    private val RECIRCULATION_CLUSTER_TAGS = setOf("aside", "div", "ol", "section", "ul")
    private val ARTICLE_CARD_RECIRCULATION_TAGS = setOf("article", "div", "section")
    private val ARTICLE_CARD_RECIRCULATION_HINTS = listOf(
        "article-card",
        "article-wrapper",
        "display-card",
        "is-entire-card-clickable",
    )

    private val ARTICLE_CARD_METADATA_HINTS = listOf(
        "article-card-date",
        "article-eyebrow",
        "time-ago",
    )

    private val ORPHAN_SEPARATOR_TEXTS = setOf("/", "|")

    private val WHITESPACE_PATTERN = Regex("""\s+""")
    private val HEADING_TAG_PATTERN = Regex("""h[1-6]""")

    private val SKELETON_RECIRCULATION_HINTS = listOf(
        "animate-pulse",
        "bg-helper",
        "skeleton",
        "placeholder",
    )

    private val POSTED_BY_BYLINE_HINTS = listOf(
        "author",
        "byline",
        "post-meta",
        "posted",
        "separator",
    )

    private const val RECOMMENDATION_MIN_LINKS = 2
    private const val RECOMMENDATION_MIN_ARTICLES = 2
    private const val RECOMMENDATION_MIN_IMAGES = 2
    private const val RECOMMENDATION_HEADING_MAX_LENGTH = 90
    private const val SKELETON_RECIRCULATION_MAX_LENGTH = 7_000
    private const val SKELETON_RECIRCULATION_MIN_PLACEHOLDERS = 2
    private const val SKELETON_RECIRCULATION_PROSE_WORD_GUARD = 8
    private const val BREADCRUMB_MAX_LENGTH = 180
    private const val BREADCRUMB_MIN_LINKS = 2
    private const val BREADCRUMB_MAX_LINKS = 6
    private const val TABLE_OF_CONTENTS_MAX_LENGTH = 2_000
    private const val TABLE_OF_CONTENTS_MIN_LINKS = 4
    private const val RECIRCULATION_CLUSTER_MAX_LENGTH = 2_000
    private const val RECIRCULATION_CLUSTER_MIN_LINKS = 2
    private const val RECIRCULATION_CLUSTER_ROW_MIN_LINKS = 1
    private const val RECIRCULATION_CLUSTER_MIN_ROWS = 2
    private const val RECIRCULATION_CLUSTER_MIN_TAG_LINKS = 2
    private const val RECIRCULATION_CLUSTER_MIN_LINK_DENSITY = 0.55
    private const val TRAILING_TAG_MIN_LINKS = 1
    private const val TRAILING_TAG_MAX_WORDS = 16
    private const val COMMENT_COUNT_MAX_LINKS = 2
    private const val COMMENT_PROMPT_MAX_LENGTH = 260
    private const val MOBILE_APP_PROMO_MAX_LENGTH = 180
    private const val NEWSLETTER_SIGNUP_MAX_LENGTH = 700
    private const val DONATION_WIDGET_MAX_LENGTH = 220
    private const val BYLINE_METADATA_STRIP_MAX_LENGTH = 360
    private const val ARTICLE_PACKAGE_MAX_LENGTH = 320
    private const val ARTICLE_CARD_RECIRCULATION_MAX_LENGTH = 900
    private const val ARTICLE_CARD_PROSE_WORD_GUARD = 12
    private const val ARTICLE_CARD_MIN_HEADLINE_WORDS = 4
    private const val ARTICLE_CARD_IMAGE_ONLY_MAX_WORDS = 8
    private const val INLINE_AUTHOR_BIO_MAX_LENGTH = 700
    private const val FOLLOW_TOPICS_MAX_LENGTH = 360
    private const val STORY_SUGGESTION_MAX_LENGTH = 220
    private const val LOCAL_NEWS_FOLLOW_MAX_LENGTH = 360
    private const val OPENING_ARTICLE_HEADER_MAX_LENGTH = 700
    private const val OPENING_ARTICLE_HEADER_MAX_PARAGRAPHS = 2
    private const val AUTHOR_FOLLOW_MAX_LENGTH = 220
    private const val POSTED_BY_BYLINE_MAX_LENGTH = 120
    private const val RECOMMENDATION_TEXT_PREFIX_LENGTH = 80
    private const val ABOUT_AUTHOR_FOOTER_MAX_LENGTH = 900
    private const val ABOUT_AUTHOR_FOOTER_MAX_WORDS = 80
    private const val ABOUT_AUTHOR_PROSE_WORD_GUARD = 16
    private const val ARTICLE_FOOTER_DETAILS_MAX_LENGTH = 1_600
    private const val ARTICLE_FOOTER_DETAILS_PROSE_WORD_GUARD = 18
    private const val RELATED_TERMS_MAX_LENGTH = 1_200
    private const val RELATED_TERMS_PROSE_WORD_GUARD = 14
    private const val RELATED_TERMS_MIN_LINKS = 2
}

private fun Element.isInlineTextButton(): Boolean {
    if (normalName() != "button") return false
    if (parents().none { it.normalName() == "p" }) return false
    if (text().trim().isBlank()) return false
    if (select("svg, img, picture, iframe, input, select, textarea").isNotEmpty()) return false
    return true
}

private fun Element.isAttachedTo(root: Element): Boolean = this === root || parents().any { it === root }

private fun partialHaystack(element: Element): String {
    val attrs = element.attributes().asList().joinToString(" ") { attribute ->
        if (attribute.key.startsWith("data-")) "${attribute.key} ${attribute.value}" else attribute.value
    }
    return "${element.id()} ${element.className()} $attrs".lowercase()
}

private fun Element.isTrailingLinkedListRecommendation(linkCount: Int, minLinks: Int): Boolean {
    if (normalName() !in setOf("ul", "ol")) return false
    if (linkCount < minLinks) return false
    if (select("p, blockquote, pre, code, table, figure, picture").isNotEmpty()) return false
    val items = children().filter { it.normalName() == "li" }
    if (items.size < minLinks) return false
    return items.count { it.select("a[href]").isNotEmpty() } >= minLinks
}
