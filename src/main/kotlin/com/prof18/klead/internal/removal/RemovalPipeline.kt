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
}

private fun Element.isInlineTextButton(): Boolean {
    if (normalName() != "button") return false
    if (parents().none { it.normalName() == "p" }) return false
    if (text().trim().isBlank()) return false
    if (select("svg, img, picture, iframe, input, select, textarea").isNotEmpty()) return false
    return true
}
