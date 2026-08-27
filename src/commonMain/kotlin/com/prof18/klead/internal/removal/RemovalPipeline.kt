package com.prof18.klead.internal.removal

import com.fleeksoft.ksoup.nodes.Element
import com.prof18.klead.RemovalRecord
import com.prof18.klead.internal.dom.isAttachedTo
import com.prof18.klead.internal.dom.removeSafely

internal fun recordAndRemove(
    element: Element,
    debug: MutableList<RemovalRecord>,
    step: String,
    selector: String?,
    reason: String,
) {
    if (debug !== DiscardedRemovals) {
        debug += RemovalRecord(
            step = step,
            selector = selector,
            reason = reason,
            preview = element.text().take(100),
        )
    }
    element.removeSafely()
}

// Sink used when debug output is disabled: recordAndRemove checks for it by identity and skips
// building the preview text, and any direct appends are dropped, so the pipeline never pays for
// records nobody reads.
internal object DiscardedRemovals : AbstractMutableList<RemovalRecord>() {
    override val size: Int get() = 0
    override fun get(index: Int): RemovalRecord = throw IndexOutOfBoundsException("Discarded removals are empty.")
    override fun set(index: Int, element: RemovalRecord): RemovalRecord = throw IndexOutOfBoundsException("discarded")
    override fun removeAt(index: Int): RemovalRecord = throw IndexOutOfBoundsException("discarded")
    override fun add(index: Int, element: RemovalRecord) = Unit
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
        checkCancelled: () -> Unit = {},
    ) {
        if (policy.removeExactSelectors) {
            measure("removeInteractiveQuizBlocks") {
                removeInteractiveQuizBlocks(content, debug, checkCancelled)
            }
        }
        if (policy.removeHiddenElements) {
            measure("removeHiddenElements") {
                removeHiddenElements(content, debug, checkCancelled)
            }
        }
        if (policy.removeExactSelectors) {
            measure("removeExactSelectors") {
                removeExactSelectors(content, debug, checkCancelled)
            }
        }
        if (policy.removePartialSelectors) {
            measure("removePartialSelectors") {
                removePartialSelectors(content, debug, checkCancelled)
            }
        }
        if (policy.removeLowScoring) {
            measure("removeLowScoringBlocks") {
                removeLowScoringBlocks(content, debug, checkCancelled)
            }
        }
        if (policy.removeContentPatterns) {
            measure("removeContentPatterns") {
                removeContentPatterns(content, debug, measure, checkCancelled)
            }
        }
        measure("imageRemoval") {
            ImageRemovalPipeline.apply(content, metadataImage, debug, ::partialHaystack, checkCancelled)
        }
    }

    private fun removeHiddenElements(content: Element, debug: MutableList<RemovalRecord>, checkCancelled: () -> Unit) {
        HiddenElementRemoval.apply(content, debug, checkCancelled)
    }

    private fun removeInteractiveQuizBlocks(
        content: Element,
        debug: MutableList<RemovalRecord>,
        checkCancelled: () -> Unit,
    ) {
        val quizContainers = mutableListOf<Element>()

        fun addNearestQuizContainer(signal: Element) {
            var candidate: Element? = if (signal.normalName() in INTERACTIVE_QUIZ_CONTAINER_TAGS) {
                signal
            } else {
                signal.parent()
            }
            while (candidate != null && candidate !== content) {
                if (candidate.normalName() in INTERACTIVE_QUIZ_CONTAINER_TAGS) {
                    val optionControlCount = candidate.select(INTERACTIVE_QUIZ_CONTROL_SELECTOR).size
                    if (optionControlCount >= MIN_INTERACTIVE_QUIZ_CONTROLS) {
                        val quizContainer = candidate
                        if (quizContainers.none { it === quizContainer }) quizContainers.add(quizContainer)
                        return
                    }
                }
                candidate = candidate.parent()
            }
        }

        for (heading in content.select("h1, h2, h3, h4, h5, h6, [role=heading]")) {
            checkCancelled()
            if (INTERACTIVE_QUIZ_PATTERN.containsMatchIn(heading.text())) addNearestQuizContainer(heading)
        }
        for (element in content.select(INTERACTIVE_QUIZ_CONTAINER_SELECTOR)) {
            checkCancelled()
            if (INTERACTIVE_QUIZ_PATTERN.containsMatchIn(partialHaystack(element))) addNearestQuizContainer(element)
        }

        // Work from the leaves upward so a quiz is removed without taking surrounding article
        // prose with an ancestor that merely inherits the same text and controls.
        for (element in quizContainers.sortedByDescending { it.parents().size }) {
            if (!element.isAttachedTo(content) || isProtected(element)) continue
            recordAndRemove(
                element,
                debug,
                "removeInteractiveQuizBlocks",
                null,
                "interactive quiz widget",
            )
        }
    }

    private fun removeExactSelectors(content: Element, debug: MutableList<RemovalRecord>, checkCancelled: () -> Unit) {
        val buckets = EXACT_SELECTOR_INDEX.collect(content)
        for (selector in EXACT_SELECTORS) {
            checkCancelled()
            // Filtering at bucket start replicates the per-selector query this replaces: elements
            // detached by earlier selectors disappear, while detachments within this selector's
            // own batch are still processed like the previous query-snapshot was.
            for (element in buckets[selector].orEmpty().filter { it.isAttachedTo(content) }) {
                if (selector in TABLE_OF_CONTENTS_EXACT_SELECTORS) {
                    removeTableOfContentsBlock(element, debug, "removeExactSelectors", selector)
                    continue
                }
                if (selector == "button") {
                    if (element.isInlineTextButton()) continue
                    // Buttons that wrap media (e.g. image-zoom overlays) — lift the media
                    // out before discarding the button so the image isn't lost with it.
                    element.liftButtonMedia()
                }
                if (isProtected(element) && selector !in PROTECTED_EXACT_SELECTOR_OVERRIDES) continue
                recordAndRemove(element, debug, "removeExactSelectors", selector, "exact clutter selector")
            }
        }
    }

    private fun removePartialSelectors(
        content: Element,
        debug: MutableList<RemovalRecord>,
        checkCancelled: () -> Unit,
    ) {
        for (element in content.descendantsSnapshot()) {
            checkCancelled()
            if (!element.isAttachedTo(content)) continue
            if (!element.matchesPartialClutter()) continue
            val scan = BlockScan(element)
            if (isProtected(element, scan.haystack)) continue
            if (isLikelyProse(scan) && !isStrongRecirculationChrome(scan)) continue
            recordAndRemove(element, debug, "removePartialSelectors", null, "partial clutter attribute")
        }
    }

    private fun removeLowScoringBlocks(
        content: Element,
        debug: MutableList<RemovalRecord>,
        checkCancelled: () -> Unit,
    ) {
        for (element in content.select("section, aside, div, ul, ol").toList()) {
            checkCancelled()
            if (!element.isAttachedTo(content)) continue
            // Skip elements inside table cells — a cell's content is structural, not a
            // standalone navigation block, and removing it leaves the table malformed.
            // The table itself isn't scored here, so genuine nav tables are unaffected.
            if (element.isInsideTableCell()) continue
            val links = element.select("a")
            val linkCount = links.size
            if (linkCount < 3) continue
            val scan = BlockScan(element)
            if (isProtected(element, scan.haystack) || isLikelyProse(scan)) continue
            if (element.isNestedListContent()) continue
            val text = scan.text
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
        checkCancelled: () -> Unit,
    ) {
        measure("removeContentPatterns.openingArticleHeaders") {
            removeOpeningArticleHeaderBlocks(content, debug)
        }
        measure("removeContentPatterns.recommendationSiblingRuns") {
            removeRecommendationSiblingRuns(content, debug, checkCancelled)
        }
        measure("removeContentPatterns.trailingContentPatterns") {
            TrailingContentPatterns.remove(content, debug, checkCancelled)
        }
        measure("removeContentPatterns.nestedArticleFooterBlocks") {
            removeNestedArticleFooterBlocks(content, debug, checkCancelled)
        }
        measure("removeContentPatterns.trailingChildren") {
            removeTrailingChildPatterns(content, debug)
        }
    }

    private fun removeTrailingChildPatterns(content: Element, debug: MutableList<RemovalRecord>) {
        for (element in content.children().toList().asReversed()) {
            val scan = BlockScan(element)
            val text = scan.trimmedText
            if (text.isBlank()) continue
            if (isProtected(element, scan.haystack)) break
            if (SUBSCRIBE_PATTERN.containsMatchIn(text) && text.length < 180) {
                recordAndRemove(element, debug, "removeContentPatterns", null, "trailing subscribe call to action")
                continue
            }
            if (isAuthorFollowBlock(scan)) {
                recordAndRemove(element, debug, "removeContentPatterns", null, "trailing author follow links")
                continue
            }
            if (isTrailingRecommendationHeading(scan)) {
                recordAndRemove(element, debug, "removeContentPatterns", null, "trailing recommendation heading")
                continue
            }
            if (isTrailingRecommendationBlock(scan)) {
                recordAndRemove(element, debug, "removeContentPatterns", null, "trailing recommendation block")
                continue
            }
            if (isTagListBlock(scan)) {
                recordAndRemove(element, debug, "removeContentPatterns", null, "trailing tag list")
                continue
            }
            if (isCommentCountBlock(scan)) {
                recordAndRemove(element, debug, "removeContentPatterns", null, "trailing comment count")
                continue
            }
            if (isBackToTopBlock(scan)) {
                recordAndRemove(element, debug, "removeContentPatterns", null, "trailing back-to-top control")
                continue
            }
            if (isCommentPromptBlock(scan)) {
                recordAndRemove(element, debug, "removeContentPatterns", null, "trailing comment prompt")
                continue
            }
            if (isStorySuggestionBlock(scan)) {
                recordAndRemove(element, debug, "removeContentPatterns", null, "trailing story suggestion prompt")
                continue
            }
            if (isLocalNewsFollowBlock(scan)) {
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

    private fun removeRecommendationSiblingRuns(
        content: Element,
        debug: MutableList<RemovalRecord>,
        checkCancelled: () -> Unit,
    ) {
        for (headingBlock in content.select("h1, h2, h3, h4, h5, h6, div, p, section").toList()) {
            checkCancelled()
            if (!headingBlock.isAttachedTo(content)) continue
            if (!isRecommendationSectionHeadingBlock(BlockScan(headingBlock))) continue

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
            while (sibling != null && isRecommendationSiblingAfterHeading(BlockScan(sibling))) {
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
                isTrailingRecommendationBlock(BlockScan(current))
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

    private fun removeNestedArticleFooterBlocks(
        content: Element,
        debug: MutableList<RemovalRecord>,
        checkCancelled: () -> Unit,
    ) {
        val caps = ChromeBlockCaps.compute(content)
        for (element in content.descendantsWithTagNamesSnapshot(NESTED_ARTICLE_FOOTER_TAGS)) {
            checkCancelled()
            if (!element.isAttachedTo(content)) continue
            if (element.isNestedListContent()) continue
            if (caps.exceeds(element)) continue
            val scan = BlockScan(element)
            if (scan.isPlainParagraphWithoutFooterSignal()) continue
            removeNestedArticleFooterBlock(scan, content, debug)
        }
    }

    private fun removeNestedArticleFooterBlock(scan: BlockScan, content: Element, debug: MutableList<RemovalRecord>) {
        val removal = nestedArticleFooterRemoval(scan, content) ?: return
        val element = scan.element
        if (isProtected(element, scan.haystack)) return

        if (removal.tableOfContents) {
            removeTableOfContentsBlock(element, debug, "removeContentPatterns", null)
        } else {
            recordAndRemove(element, debug, "removeContentPatterns", null, removal.reason)
        }
    }

    private fun nestedArticleFooterRemoval(scan: BlockScan, content: Element): NestedFooterRemoval? = when {
        isOrphanSeparatorBlock(scan) -> {
            NestedFooterRemoval("orphan separator block")
        }

        isTrailingDividerBlock(scan.element, content) -> {
            NestedFooterRemoval("trailing divider")
        }

        isSkeletonRecirculationBlock(scan) -> {
            NestedFooterRemoval("skeleton recirculation block")
        }

        isPostedByBylineBlock(scan) -> {
            NestedFooterRemoval("posted-by byline strip")
        }

        isBreadcrumbBlock(scan) -> {
            NestedFooterRemoval("breadcrumb block")
        }

        isTableOfContentsBlock(scan) -> {
            NestedFooterRemoval("table of contents block", tableOfContents = true)
        }

        isSocialCounterBlock(scan) -> {
            NestedFooterRemoval("social counter block")
        }

        isTrailingRecirculationLinkCluster(scan) -> {
            NestedFooterRemoval("trailing recirculation link cluster")
        }

        isRecommendationSectionHeadingBlock(scan) -> {
            NestedFooterRemoval("orphan recommendation heading")
        }

        isAboutAuthorFooterBlock(scan) -> {
            NestedFooterRemoval("about-author footer block")
        }

        isArticleFooterDetailsBlock(scan) -> {
            NestedFooterRemoval("article details footer block")
        }

        isRelatedTermsBlock(scan) -> {
            NestedFooterRemoval("related terms footer block")
        }

        isTagListBlock(scan) -> {
            NestedFooterRemoval("article footer tag list")
        }

        isCommentCountBlock(scan) -> {
            NestedFooterRemoval("article footer comment count")
        }

        isBackToTopBlock(scan) -> {
            NestedFooterRemoval("article footer back-to-top control")
        }

        isCommentPromptBlock(scan) -> {
            NestedFooterRemoval("article footer comment prompt")
        }

        isReadyForMoreBlock(scan) -> {
            NestedFooterRemoval("article footer subscription call to action")
        }

        isMobileAppPromoBlock(scan) -> {
            NestedFooterRemoval("mobile app promo")
        }

        isNewsletterSignupBlock(scan) -> {
            NestedFooterRemoval("newsletter signup")
        }

        isDonationWidgetBlock(scan) -> {
            NestedFooterRemoval("donation widget")
        }

        isBylineMetadataStrip(scan) -> {
            NestedFooterRemoval("byline metadata strip")
        }

        isArticlePackageBlock(scan) -> {
            NestedFooterRemoval("article package card")
        }

        isInlineAuthorBioBlock(scan) -> {
            NestedFooterRemoval("inline author bio")
        }

        isFollowTopicsBlock(scan) -> {
            NestedFooterRemoval("follow topics prompt")
        }

        isStorySuggestionBlock(scan) -> {
            NestedFooterRemoval("story suggestion prompt")
        }

        isLocalNewsFollowBlock(scan) -> {
            NestedFooterRemoval("local news follow prompt")
        }

        else -> null
    }
}

// A delimited id (e.g. "feedback-form") is substring-matched like the other
// attributes, but a delimiter-less id is usually a content anchor concatenated
// from heading words (e.g. "correlatedvariables", "marketshareanalysis") —
// substring matching would wrongly strip it (hitting "related"/"share"), so it
// must equal a selector token outright.
private fun Element.matchesPartialClutter(): Boolean {
    val nonIdHaystack = elementHintHaystack(this, includeId = false)
    if (PARTIAL_PATTERNS.any { it in nonIdHaystack }) return true

    val id = id().trim().lowercase()
    if (id.isEmpty()) return false
    val idHasDelimiter = id.any { it == ' ' || it == '_' || it == '-' || it == ':' || it == '.' }
    return if (idHasDelimiter) PARTIAL_PATTERNS.any { it in id } else id in PARTIAL_PATTERNS
}

private fun Element.isInsideTableCell(): Boolean = parents().any { it.normalName() == "td" || it.normalName() == "th" }

private fun Element.isInlineTextButton(): Boolean {
    if (normalName() != "button") return false
    if (parents().none { it.normalName() in INLINE_BUTTON_CONTEXTS }) return false
    if (text().trim().isBlank()) return false
    if (select("svg, img, picture, iframe, input, select, textarea").isNotEmpty()) return false
    return true
}

private val INLINE_BUTTON_CONTEXTS =
    setOf("p", "li", "td", "th", "span", "h1", "h2", "h3", "h4", "h5", "h6")

// Move an element's media descendants (img/picture/video) to just before it, so a
// wrapper that is about to be removed doesn't take the media with it. Nested media
// (e.g. an <img> inside a <picture>) is skipped — only the outermost is lifted.
private fun Element.liftButtonMedia() {
    val media = select("img, picture, video")
    if (media.isEmpty()) return
    val mediaSet = media.toSet()
    for (item in media) {
        if (item.parents().any { it in mediaSet }) continue
        before(item)
    }
}
