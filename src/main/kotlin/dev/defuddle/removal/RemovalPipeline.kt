package dev.defuddle.removal

import dev.defuddle.DefuddleOptions
import dev.defuddle.dom.removeSafely
import dev.defuddle.dom.selectSafe
import org.jsoup.nodes.Element

data class RemovalRecord(
    val step: String,
    val selector: String?,
    val reason: String,
    val preview: String,
)

object RemovalPipeline {
    fun apply(
        content: Element,
        options: DefuddleOptions,
        debug: MutableList<RemovalRecord>,
        metadataImage: String? = null,
    ) {
        if (options.removeHiddenElements) {
            removeHiddenElements(content, debug)
        }
        if (options.removeExactSelectors) {
            removeExactSelectors(content, debug)
        }
        if (options.removePartialSelectors) {
            removePartialSelectors(content, debug)
        }
        if (options.removeLowScoring) {
            removeLowScoringBlocks(content, debug)
        }
        if (options.removeContentPatterns) {
            removeContentPatterns(content, debug)
        }
        if (options.removeImages) {
            removeImages(content, debug)
        } else {
            if (options.removeSmallImages) {
                removeSmallImages(content, debug)
            }
            deduplicateImages(content, debug)
            removeCoverImage(content, metadataImage, debug)
        }
    }

    private fun removeHiddenElements(
        content: Element,
        debug: MutableList<RemovalRecord>,
    ) {
        for (element in content.select("*").toList()) {
            val reason = hiddenReason(element) ?: continue
            if (isMathWrapper(element)) continue
            debug += RemovalRecord(
                step = "removeHiddenElements",
                selector = hiddenSelector(element),
                reason = reason,
                preview = element.text().take(100),
            )
            element.removeSafely()
        }
    }

    private fun hiddenReason(element: Element): String? {
        if (element.hasAttr("hidden")) return "hidden attribute"
        if (element.attr("aria-hidden").equals("true", ignoreCase = true)) return "aria-hidden"
        val style = element.attr("style").lowercase().replace(" ", "")
        if ("display:none" in style) return "display:none"
        if ("visibility:hidden" in style) return "visibility:hidden"
        if (Regex("""(?:^|;)opacity:0(?:\.0+)?(?:;|$)""").containsMatchIn(style)) return "opacity:0"
        val classes = element.classNames()
        if (classes.any { it == "hidden" || it == "invisible" || it.endsWith(":hidden") || it.endsWith(":invisible") }) {
            return "hidden class"
        }
        return null
    }

    private fun isMathWrapper(element: Element): Boolean {
        val className = element.className().lowercase()
        return element.tagName().equals("math", ignoreCase = true) ||
            element.selectFirst("math, annotation[encoding*=tex]") != null ||
            "math" in className ||
            "katex" in className ||
            "mathjax" in className
    }

    private fun hiddenSelector(element: Element): String =
        when {
            element.id().isNotBlank() -> "#${element.id()}"
            element.className().isNotBlank() -> "${element.tagName()}.${element.classNames().joinToString(".")}"
            else -> element.tagName()
        }

    private fun removeExactSelectors(
        content: Element,
        debug: MutableList<RemovalRecord>,
    ) {
        for (selector in EXACT_SELECTORS) {
            for (element in content.selectSafe(selector).toList()) {
                if (isProtected(element) && selector !in PROTECTED_EXACT_SELECTOR_OVERRIDES) continue
                recordAndRemove(element, debug, "removeExactSelectors", selector, "exact clutter selector")
            }
        }
    }

    private fun removePartialSelectors(
        content: Element,
        debug: MutableList<RemovalRecord>,
    ) {
        for (element in content.select("*").toList()) {
            if (isProtected(element) || isLikelyProse(element)) continue
            val haystack = partialHaystack(element)
            if (PARTIAL_PATTERNS.any { it in haystack }) {
                recordAndRemove(element, debug, "removePartialSelectors", null, "partial clutter attribute")
            }
        }
    }

    private fun removeLowScoringBlocks(
        content: Element,
        debug: MutableList<RemovalRecord>,
    ) {
        for (element in content.select("section, aside, div, ul, ol").toList()) {
            if (isProtected(element) || isLikelyProse(element)) continue
            val text = element.text()
            val linkText = element.select("a").sumOf { it.text().length }
            val linkDensity = if (text.isBlank()) 0.0 else linkText.toDouble() / text.length
            val linkCount = element.select("a").size
            if (linkCount >= 3 && linkDensity > 0.55) {
                recordAndRemove(element, debug, "removeLowScoring", null, "link-heavy low scoring block")
            }
        }
    }

    private fun removeContentPatterns(
        content: Element,
        debug: MutableList<RemovalRecord>,
    ) {
        removeOpeningArticleHeaderBlocks(content, debug)
        removeRecommendationSiblingRuns(content, debug)
        removeNestedArticleFooterBlocks(content, debug)
        for (element in content.children().toList().asReversed()) {
            val text = element.text().trim()
            if (text.isBlank()) continue
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

    private fun removeOpeningArticleHeaderBlocks(
        content: Element,
        debug: MutableList<RemovalRecord>,
    ) {
        for (article in openingArticleCandidates(content)) {
            val header = article.firstSubstantiveChild() ?: continue
            if (header.normalName() != "header") continue
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
    ) {
        for (headingBlock in content.select("h1, h2, h3, h4, h5, h6, div, section").toList()) {
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

    private fun Element.isAttachedTo(root: Element): Boolean =
        this === root || parents().any { it === root }

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

    private fun Element.firstSubstantiveChild(): Element? =
        children().firstOrNull { child ->
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
        return OPENING_ARTICLE_BODY_HINTS.any { it in hints }
    }

    private fun Element.hasOpeningArticleHeaderChromeHint(): Boolean {
        val text = text().trim()
        if (text.isBlank() || text.length > OPENING_ARTICLE_HEADER_MAX_LENGTH) return false
        if (select("p").size > OPENING_ARTICLE_HEADER_MAX_PARAGRAPHS) return false

        val nestedHints = select("*").joinToString(" ") { partialHaystack(it) }
        val hints = "${partialHaystack(this)} $nestedHints"
        return OPENING_ARTICLE_HEADER_HINTS.any { it in hints }
    }

    private fun removeNestedArticleFooterBlocks(
        content: Element,
        debug: MutableList<RemovalRecord>,
    ) {
        for (element in content.select("aside, div, p, section, ul, ol, hr").toList()) {
            if (isProtected(element)) continue
            when {
                isOrphanSeparatorBlock(element) -> {
                    recordAndRemove(element, debug, "removeContentPatterns", null, "orphan separator block")
                }
                isTrailingDividerBlock(element) -> {
                    recordAndRemove(element, debug, "removeContentPatterns", null, "trailing divider")
                }
                isSkeletonRecirculationBlock(element) -> {
                    recordAndRemove(element, debug, "removeContentPatterns", null, "skeleton recirculation block")
                }
                isPostedByBylineBlock(element) -> {
                    recordAndRemove(element, debug, "removeContentPatterns", null, "posted-by byline strip")
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
                    recordAndRemove(element, debug, "removeContentPatterns", null, "article footer subscription call to action")
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
    }

    private fun removeImages(
        content: Element,
        debug: MutableList<RemovalRecord>,
    ) {
        for (element in content.select("picture, img").toList()) {
            if (element.normalName() == "img" && element.parents().any { it.normalName() == "picture" }) continue
            recordAndRemove(element, debug, "removeImages", element.normalName(), "removeImages option")
        }
    }

    private fun removeSmallImages(
        content: Element,
        debug: MutableList<RemovalRecord>,
    ) {
        for (image in content.select("img").toList()) {
            if (image.isSmallImage()) {
                recordAndRemove(image, debug, "removeSmallImages", "img", "small image dimensions")
            }
        }
    }

    private fun deduplicateImages(
        content: Element,
        debug: MutableList<RemovalRecord>,
    ) {
        val seen = mutableSetOf<String>()
        for (image in content.select("img[src]").toList()) {
            val key = image.imageKey() ?: continue
            if (!seen.add(key)) {
                recordAndRemove(image, debug, "deduplicateImages", "img[src]", "duplicate image")
            }
        }
    }

    private fun removeCoverImage(
        content: Element,
        metadataImage: String?,
        debug: MutableList<RemovalRecord>,
    ) {
        val coverKey = metadataImage?.trim()?.takeIf { it.isNotBlank() } ?: return
        for (image in content.select("img[src]").toList()) {
            val key = image.imageKey() ?: continue
            if (key == coverKey) {
                val target = image.coverImageRemovalTarget(content)
                if (!target.hasCoverImageHint(image)) continue
                recordAndRemove(target, debug, "removeCoverImage", coverImageSelector(target), "duplicates metadata image")
            }
        }
    }

    private fun Element.coverImageRemovalTarget(root: Element): Element {
        var target = this
        var current = parent()
        while (current != null && current != root && current.isVisualOnlyImageWrapper()) {
            target = current
            current = current.parent()
        }
        return target
    }

    private fun Element.isVisualOnlyImageWrapper(): Boolean {
        if (normalName() !in VISUAL_IMAGE_WRAPPER_TAGS) return false
        if (text().trim().isNotBlank()) return false
        return children().all { child ->
            child.normalName() in VISUAL_IMAGE_WRAPPER_TAGS ||
                child.normalName() in VISUAL_IMAGE_LEAF_TAGS
        }
    }

    private fun Element.hasCoverImageHint(image: Element): Boolean {
        val hints = listOfNotNull(
            partialHaystack(this),
            parent()?.let(::partialHaystack),
            partialHaystack(image),
        ).joinToString(" ")
        return COVER_IMAGE_HINTS.any { it in hints }
    }

    private fun isProtected(element: Element): Boolean {
        val hints = partialHaystack(element)
        return element.`is`("pre, code, figure, picture, table, math, blockquote") ||
            element.parents().any { it.`is`("pre, code, figure, picture, table, math, blockquote") } ||
            "footnote" in hints ||
            "footnotes" in hints ||
            "callout" in hints ||
            "admonition" in hints
    }

    private fun isLikelyProse(element: Element): Boolean {
        val paragraphs = element.select("p").count { it.text().split(Regex("""\s+""")).size >= 8 }
        if (paragraphs >= 1) return true
        val text = element.text()
        val words = text.split(Regex("""\s+""")).count { it.isNotBlank() }
        return words >= 35 && text.count { it == '.' || it == ',' } >= 2
    }

    private fun partialHaystack(element: Element): String {
        val attrs = element.attributes().asList().joinToString(" ") { attribute ->
            if (attribute.key.startsWith("data-")) "${attribute.key} ${attribute.value}" else attribute.value
        }
        return "${element.id()} ${element.className()} $attrs".lowercase()
    }

    private fun isTrailingRecommendationHeading(element: Element): Boolean {
        if (!element.normalName().matches(Regex("""h[1-6]"""))) return false
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
            (linkCount >= RECOMMENDATION_MIN_LINKS && imageCount >= 1 && !isLikelyProse(element))
    }

    private fun isOrphanSeparatorBlock(element: Element): Boolean {
        val text = element.text().trim().collapseWhitespace()
        if (text !in ORPHAN_SEPARATOR_TEXTS) return false
        if (element.select("a, img, figure, picture, table, pre, code, math").isNotEmpty()) return false
        return element.children().all { child ->
            child.text().trim().collapseWhitespace().let { it.isBlank() || it in ORPHAN_SEPARATOR_TEXTS }
        }
    }

    private fun isTrailingDividerBlock(element: Element): Boolean {
        if (element.normalName() != "hr") return false

        var sibling = element.nextElementSibling()
        while (sibling != null) {
            if (sibling.hasSubstantiveContent()) return false
            sibling = sibling.nextElementSibling()
        }
        return true
    }

    private fun Element.hasSubstantiveContent(): Boolean =
        text().trim().isNotBlank() ||
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
        if (element.select("[data-cy=article-content], article.article-content, .article-content").isNotEmpty()) return false

        val hints = "${partialHaystack(element)} ${element.select("*").joinToString(" ") { partialHaystack(it) }}"
        val hasSkeletonHint = SKELETON_RECIRCULATION_HINTS.any { it in hints }
        return hasSkeletonHint && SKELETON_RECIRCULATION_HEADING_PATTERN.containsMatchIn(text)
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
        val wordCount = text.split(Regex("""\s+""")).count { it.isNotBlank() }

        return tagLinkCount >= TRAILING_TAG_MIN_LINKS ||
            (linkCount >= TRAILING_TAG_MIN_LINKS && wordCount <= TRAILING_TAG_MAX_WORDS)
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
        if (element.select("p").any { it.text().trim().collapseWhitespace().wordCount() >= RELATED_TERMS_PROSE_WORD_GUARD }) {
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
        if (!BYLINE_METADATA_STRIP_PATTERN.containsMatchIn(text)) return false

        val authorLinkCount = element.select("""a[href*="/author/"], a[rel~=author]""").size
        val hasDate = element.select("time, [datetime]").isNotEmpty() ||
            BYLINE_METADATA_DATE_PATTERN.containsMatchIn(text)
        val hints = partialHaystack(element)

        return authorLinkCount >= 1 &&
            hasDate &&
            (
                "byline" in hints ||
                    "author" in hints ||
                    "uppercase" in hints ||
                    "font-sans" in hints ||
                    element.select("""a[href*="google.com/preferences/source"], a[href="#ep-comments"]""").isNotEmpty()
            )
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

    private fun recordAndRemove(
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

    private fun Element.imageKey(): String? =
        absUrl("src").ifBlank { attr("src").trim() }.ifBlank { null }

    private fun coverImageSelector(element: Element): String =
        when {
            element.id().isNotBlank() -> "#${element.id()}"
            element.className().isNotBlank() -> ".${element.classNames().joinToString(".")}"
            else -> element.normalName()
        }

    private fun Element.isSmallImage(): Boolean {
        val width = dimension("width")
        val height = dimension("height")
        return if (width != null && height != null) {
            width > 0 && height > 0 && width <= SMALL_IMAGE_MAX_DIMENSION && height <= SMALL_IMAGE_MAX_DIMENSION
        } else {
            false
        }
    }

    private fun Element.dimension(name: String): Int? =
        attr(name).dimensionValue()
            ?: Regex("""$name\s*:\s*(\d+)px""", RegexOption.IGNORE_CASE)
                .find(attr("style"))
                ?.groupValues
                ?.getOrNull(1)
                ?.toIntOrNull()

    private fun String.dimensionValue(): Int? =
        Regex("""^\s*(\d+)""").find(this)?.groupValues?.getOrNull(1)?.toIntOrNull()

    private fun String.collapseWhitespace(): String =
        replace(Regex("""\s+"""), " ")

    private fun String.wordCount(): Int =
        split(Regex("""\s+""")).count { it.isNotBlank() }

    private val EXACT_SELECTORS = listOf(
        "nav",
        "footer",
        "form",
        "button",
        "input",
        "select",
        "textarea",
        "[role=navigation]",
        "#comments",
        "#discussion",
        "#substack-comments",
        "#viafoura-comments-container",
        "#viafoura-comment-wrapper",
        ".viafoura-twig-component",
        "[data-jwp-carousel]",
        ".van_vid_carousel",
        ".jw-carousel",
        ".jwplayer__wrapper",
        ".jwcarousel__hook",
        ".text-settings-dropdown-story",
        ".text-settings",
        "[data-component-name*=Comments]",
        "[data-component-name*=comments]",
        "[class*=CommentsWrapper]",
        "#reactions-title",
        ".top-comment",
        "[data-component-name*=ScrollUp]",
        "[data-component-name*=scroll]",
        """[data-cy="trending-top-bar"]""",
        """[data-cy="article-section-eyebrow"]""",
        """[data-cy="article-tag-eyebrow"]""",
        """[data-cy="authors-bio-cards"]""",
        """[data-cy="author-bio"]""",
        """[data-cy="author-see-full-bio"]""",
        """[data-component="headline-block"]""",
        """[data-component="byline-block"]""",
        "img.hide-when-no-script",
        """img[aria-label="image unavailable"]""",
        """img[src*="grey-placeholder"]""",
        """p:matches((?i)\bdo\s+you\s+have\s+a\s+story\s+suggestion\b)""",
        """p:matches((?i)^follow\s+.{1,80}\s+news\s+on\b)""",
        "#author-bio",
        ".copy-tooltip",
        ".copy-tooltiptext",
        """div.separator:matchesOwn((?i)^\s*posted\s+by\s+)""",
        "#blog-pager",
        ".blog-pager",
        ".adb-detail > hr:last-child",
        ".blog-pager-newer-link",
        ".blog-pager-older-link",
        ".top-page",
        ".article-section .content > a.tag",
        ".article-section .content > h1:first-of-type",
        ".author-post",
        ".postHead",
        "[class*=headline-byline]",
        ":scope > div:first-child > header",
        ".content__pagination",
        ".toc-opener",
        ".article-section + .section.light-gray-bg",
        "aside[data-mrf-recirculation]",
        "aside.hawk-root",
        "#audioPlayerArticle",
        ".audio-player",
        ".audioplayer",
        "[data-mp3]",
        "[data-audio-src]",
        """[data-component-type="post-byline"]""",
        ".post-byline",
        ".byline-wrapper",
        ".byline-author-container",
        """[data-component-type="timestamp"]""",
        ".post-video-recirc",
        """[data-component-type="post-video-recirc"]""",
        ".back-to-home-container",
        ".back-to-home",
        """a[href*="google.com/preferences/source"]""",
        """a[href="#ep-comments"]""",
        ".classifai-listen-to-post-wrapper",
        ".classifai-post-audio-heading",
        """audio[id^="classifai-post-audio-player"]""",
        ".l-entry__footer",
        ".l-entry__sidebar",
        ".l-entry--infos-square > .l-entry__header",
        ".l-entry__byline",
        ".l-entry__byline--small",
        ".article__meta",
        ".wp-block-post-featured-image__caption",
        ".wp-block-techcrunch-post-authors-list",
        ".wp-block-techcrunch-event-cta",
        ".rightrail-promo",
        ".latest-in-pattern",
        ".duet--article--lede",
        ".duet--ledes--standard-lede-bottom",
        ".wp-block-query",
        ".display-card.article-card",
        "div.article-card[data-nosnippet]",
        ".btn-gpsource-bt-article",
        ".c-story--stack",
        ":scope > article.c-story--stack",
        """[data-section-key^="article-footer"]""",
        """[data-cy="time-rubric"]""",
        """[data-cy="byline-author"]""",
        """[data-cy="social-share-top"]""",
        """[data-cy="social-share-bottom"]""",
        """[data-vars-event-name="preferred_source_view"]""",
        """[data-cy="preferred-source-top"]""",
        """[data-cy="preferred-source-bottom"]""",
        """[data-cy="what-to-read-next"]""",
        ".google-preferred-source-badge",
        ".ad-disclaimer-container",
        ".disclaimer-affiliate",
        ".visitor-promo",
        "#after_disclaimer_placement",
        ".w-article-header-comp",
        ".w-heading-options",
        ".w-sharing-copy",
        "#sharingCopyAlertDiv",
        ".w-article-header-author-img",
        ".article-header-author-img",
        ".bc-complement",
        ".bc-listing-categories",
        ".w-tag-interaction-popup-menu",
        ".article-header > p",
        ".article-header-title",
        ".thumbuser",
        ".author-bio",
        """[class*="about-the-author"]""",
        ".author-box",
        ".author-profile",
        ".author-mini-bio",
        ".slice-author-bio",
        ".slice-container-authorBio",
        """[id^="slice-container-authorBio"]""",
        ".post-author",
        ".byline-box",
        ".entry-meta",
        ".post-meta",
        ".post-meta-infos",
        ".article-meta",
        ".posted-on",
        ".byline",
        """[class*="byline--"]""",
        ".comments-link",
        ".linkback",
        ".big-preview",
        ".avia-copyright",
        ".bm-social-top",
        ".testo > .data.small",
        ".abh_box",
        ".author-bio-box",
        ".article-options",
        ".article-tags",
        """[class*="credits-and-details"]""",
        """[class*="related-articles"]""",
        """[class*="topic-cards"]""",
        ".post-cat-wrap",
        ".post-cat",
        ".post-cats-list",
        ".post-categories",
        ".entry-categories",
        ".cat-links",
        ".category-button",
        ".follow-container",
        "[data-is-follow-choice-button]",
        "[data-is-followed-choice-button]",
        ".newsletter-promotion-large",
        ".newsletter-section",
        ".newsletter-form__wrapper",
        ".slice-container-newsletterForm",
        """[id^="slice-container-newsletterForm"]""",
        ".wp-block-mailchimp-mailchimp",
        ".mc_embed_signup",
        ".mailchimp-signup",
        """[data-inview-type*=newsletter]""",
        """[data-inview-category*=Newsletter]""",
        """[aria-label="Top Posts Footer"]""",
        ".comments-section",
        ".more-comments",
        ".popular-box",
        ".popular-box-slice",
        ".slice-container-popularBox",
        """[id^="slice-container-popularBox"]""",
        ".portable-archive",
        ".portable-archive-list",
        ".portable-archive-empty",
        ".table__instruction",
        ".inline-gallery__count",
        ".inline-gallery__arrows",
        ".image-cont__expand",
        """img[src*="seamless-keep-scrolling"]""",
        """img[alt="Mashable Potato"]""",
        ".ad",
        ".ads",
        ".advertisement",
        ".tcc-badge",
        ".comments",
        ".comment",
        ".share",
        ".sharing",
        ".related",
        ".related-posts",
        ".toc",
        ".table-of-contents",
    )

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
        """\b(recommended|related|related terms|explore more|keep exploring|discover more|more stories|more from|more on|read more|you may also like|popular stories|consigliati|altre storie|i più letti|in evidenza|potrebbe interessarti)\b|^best\s+[\p{L}\p{N}][\p{L}\p{N} .'"’&-]{0,80}\s+(accessories|deals|offers|prices?|discounts?|sales?)$""",
        RegexOption.IGNORE_CASE,
    )

    private val RECOMMENDATION_SECTION_HEADING_PATTERN = Regex(
        """^(related\s+content|related\s+articles?|related\s+terms|recommended(?:\s+for\s+you)?|explore\s+more|keep\s+exploring|discover\s+more(?:\s+.+)?|what\s+to\s+read\s+next|read\s+more|popular\s+stories|latest\s+articles?|latest\s+in\s+.+|more\s+stories|more\s+from\s+.+|you\s+may\s+also\s+like|consigliati|altre\s+storie|i\s+più\s+letti|potrebbe\s+interessarti)$""",
        RegexOption.IGNORE_CASE,
    )

    private val LOREM_PLACEHOLDER_PATTERN = Regex(
        """\blorem\s+ipsum\s+dolor\s+sit\s+amet\b""",
        RegexOption.IGNORE_CASE,
    )

    private val SKELETON_RECIRCULATION_HEADING_PATTERN = Regex(
        """\b(latest\s+in|most\s+popular|popular\s+stories|recommended|related|read\s+more)\b""",
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
        """\b(subscribe\s+to\s+(?:our|the|a)\s+newsletter|receive\s+newsletter|newsletter\s+signup|subscribe\s+.*\bnewsletter)\b""",
        RegexOption.IGNORE_CASE,
    )

    private val NEWSLETTER_LEGAL_PATTERN = Regex(
        """\b(marketing\s+emails|terms\s+of\s+use|privacy\s+policy|unsubscribe\s+(?:anytime|any\s+time))\b""",
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

    private val ARTICLE_PACKAGE_PATTERN = Regex(
        """^part\s+of\b.+\bsee\s+all\s+updates\b""",
        RegexOption.IGNORE_CASE,
    )

    private val INLINE_AUTHOR_BIO_PATTERN = Regex(
        """\bis\s+an?\s+[\p{L}\p{N} .,&'’/-]{0,80}\b(news\s+writer|staff\s+writer|senior\s+writer|reporter|journalist|editor|reviewer)\b""",
        RegexOption.IGNORE_CASE,
    )

    private val AUTHOR_ROLE_LABEL_PATTERN = Regex(
        """\b(contributor|news\s+writer|staff\s+writer|senior\s+writer|reporter|journalist|editor|reviewer)\b""",
        RegexOption.IGNORE_CASE,
    )

    private val FOLLOW_TOPICS_PATTERN = Regex(
        """\bfollow\s+topics\s+and\s+authors\b|\bpersonalized\s+homepage\s+feed\b|\breceive\s+email\s+updates\b""",
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

    private val VISUAL_IMAGE_WRAPPER_TAGS = setOf(
        "a",
        "div",
        "figure",
        "picture",
        "span",
    )

    private val VISUAL_IMAGE_LEAF_TAGS = setOf(
        "img",
        "source",
    )

    private val COVER_IMAGE_HINTS = listOf(
        "post-thumbnail",
        "featured",
        "hero",
        "cover",
        "wp-post-image",
        "image-link",
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

    private val ORPHAN_SEPARATOR_TEXTS = setOf("/", "|")

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
    private const val TRAILING_TAG_MIN_LINKS = 1
    private const val TRAILING_TAG_MAX_WORDS = 16
    private const val COMMENT_COUNT_MAX_LINKS = 2
    private const val COMMENT_PROMPT_MAX_LENGTH = 260
    private const val MOBILE_APP_PROMO_MAX_LENGTH = 180
    private const val NEWSLETTER_SIGNUP_MAX_LENGTH = 700
    private const val DONATION_WIDGET_MAX_LENGTH = 220
    private const val BYLINE_METADATA_STRIP_MAX_LENGTH = 360
    private const val ARTICLE_PACKAGE_MAX_LENGTH = 320
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
    private const val SMALL_IMAGE_MAX_DIMENSION = 64
}
