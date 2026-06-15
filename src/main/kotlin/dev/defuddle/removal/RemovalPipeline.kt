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
                if (isProtected(element)) continue
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
            break
        }
    }

    private fun removeNestedArticleFooterBlocks(
        content: Element,
        debug: MutableList<RemovalRecord>,
    ) {
        for (element in content.select("aside, div, p, section, ul, ol").toList()) {
            if (isProtected(element)) continue
            when {
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
        "[data-component-name*=Comments]",
        "[data-component-name*=comments]",
        ".top-comment",
        "[data-component-name*=ScrollUp]",
        "[data-component-name*=scroll]",
        "aside[data-mrf-recirculation]",
        "aside.hawk-root",
        "#audioPlayerArticle",
        ".audio-player",
        ".audioplayer",
        "[data-mp3]",
        "[data-audio-src]",
        ".l-entry__footer",
        ".l-entry__sidebar",
        ".l-entry--infos-square > .l-entry__header",
        ".l-entry__byline",
        ".l-entry__byline--small",
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
        ".thumbuser",
        ".author-bio",
        ".author-box",
        ".author-profile",
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
        ".comments-link",
        ".big-preview",
        ".avia-copyright",
        ".bm-social-top",
        ".testo > .data.small",
        ".abh_box",
        ".author-bio-box",
        ".article-options",
        ".article-tags",
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
        """\b(recommended|related|more stories|more from|more on|read more|you may also like|popular stories|consigliati|altre storie|i più letti|in evidenza|potrebbe interessarti)\b|^best\s+[\p{L}\p{N}][\p{L}\p{N} .'"’&-]{0,80}\s+(accessories|deals|offers|prices?|discounts?|sales?)$""",
        RegexOption.IGNORE_CASE,
    )

    private val AUTHOR_FOLLOW_PATTERN = Regex(
        """^\s*follow\s+[\p{L}\p{N} ._'’-]{1,48}\s*:""",
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

    private const val RECOMMENDATION_MIN_LINKS = 2
    private const val RECOMMENDATION_MIN_ARTICLES = 2
    private const val RECOMMENDATION_MIN_IMAGES = 2
    private const val TRAILING_TAG_MIN_LINKS = 1
    private const val TRAILING_TAG_MAX_WORDS = 16
    private const val COMMENT_COUNT_MAX_LINKS = 2
    private const val COMMENT_PROMPT_MAX_LENGTH = 260
    private const val MOBILE_APP_PROMO_MAX_LENGTH = 180
    private const val NEWSLETTER_SIGNUP_MAX_LENGTH = 700
    private const val DONATION_WIDGET_MAX_LENGTH = 220
    private const val AUTHOR_FOLLOW_MAX_LENGTH = 220
    private const val RECOMMENDATION_TEXT_PREFIX_LENGTH = 80
    private const val SMALL_IMAGE_MAX_DIMENSION = 64
}
