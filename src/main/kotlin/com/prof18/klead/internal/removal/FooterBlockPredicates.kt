package com.prof18.klead.internal.removal

import com.prof18.klead.RemovalRecord
import org.jsoup.nodes.Element

internal fun isTrailingRecommendationHeading(element: Element): Boolean {
    if (!HEADING_TAG_PATTERN.matches(element.normalName())) return false
    return RECOMMENDATION_HEADING_PATTERN.containsMatchIn(element.text().trim())
}

internal fun isTrailingRecommendationBlock(element: Element): Boolean {
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

internal fun isRecommendationSectionHeadingBlock(element: Element): Boolean {
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

internal fun isRecommendationSiblingAfterHeading(element: Element): Boolean {
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

internal fun isOrphanSeparatorBlock(element: Element): Boolean {
    val text = element.text().trim().collapseWhitespace()
    if (text !in ORPHAN_SEPARATOR_TEXTS) return false
    if (element.select("a, img, figure, picture, table, pre, code, math").isNotEmpty()) return false
    return element.children().all { child ->
        child.text().trim().collapseWhitespace().let { it.isBlank() || it in ORPHAN_SEPARATOR_TEXTS }
    }
}

internal fun isTrailingDividerBlock(element: Element, content: Element): Boolean {
    if (element.normalName() != "hr") return false

    val siblingContext = element.dividerSiblingContext(content)
    var sibling = siblingContext.nextElementSibling()
    while (sibling != null) {
        if (sibling.hasSubstantiveContent()) return false
        sibling = sibling.nextElementSibling()
    }
    return true
}

internal fun Element.dividerSiblingContext(content: Element): Element {
    val parent = parent()
    return if (parent != null && parent !== content && parent.children().singleOrNull() === this) {
        parent
    } else {
        this
    }
}

internal fun Element.hasSubstantiveContent(): Boolean = text().trim().isNotBlank() ||
    select("img, picture, figure, table, pre, code, math, p, h1, h2, h3, h4, h5, h6").isNotEmpty()

internal fun isSkeletonRecirculationBlock(element: Element): Boolean {
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

internal fun isArticleCardRecirculationBlock(element: Element): Boolean {
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

internal fun Element.isArticleCardRecirculationCandidate(text: String): Boolean =
    normalName() in ARTICLE_CARD_RECIRCULATION_TAGS &&
        text.isNotBlank() &&
        text.length <= ARTICLE_CARD_RECIRCULATION_MAX_LENGTH &&
        select("h1, h2").isEmpty() &&
        select("[data-cy=article-content], [itemprop=articleBody], .article-content").isEmpty() &&
        select("p").none { paragraph ->
            paragraph.text().trim().collapseWhitespace().wordCount() >= ARTICLE_CARD_PROSE_WORD_GUARD
        }

internal fun isPostedByBylineBlock(element: Element): Boolean {
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

internal fun isBreadcrumbBlock(element: Element): Boolean {
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

internal fun isTableOfContentsBlock(element: Element): Boolean {
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

internal fun removeTableOfContentsBlock(
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

internal fun isSocialCounterBlock(element: Element): Boolean {
    val text = element.text().trim().collapseWhitespace()
    if (!SOCIAL_COUNTER_PATTERN.matches(text)) return false
    if (element.select("p, pre, code, table, figure, img, picture, blockquote").isNotEmpty()) return false
    val hints = partialHaystack(element)
    return "social" in hints ||
        "like" in hints ||
        "pencraft" in hints ||
        element.select("a, button").isNotEmpty()
}

internal fun isStrongRecirculationChrome(element: Element, hints: String = partialHaystack(element)): Boolean {
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

internal fun isAuthorFollowBlock(element: Element): Boolean {
    val text = element.text().trim()
    if (text.length > AUTHOR_FOLLOW_MAX_LENGTH || !AUTHOR_FOLLOW_PATTERN.containsMatchIn(text)) return false
    if (element.select("a[href]").isEmpty()) return false

    val hints = partialHaystack(element)
    return element.normalName() == "p" ||
        "follow" in hints ||
        "author" in hints ||
        "social" in hints
}

internal fun isTagListBlock(element: Element): Boolean {
    val text = element.text().trim()
    if (!TRAILING_TAG_LABEL_PATTERN.containsMatchIn(text)) return false

    val linkCount = element.select("a").size
    val tagLinkCount = element.select("""a[href*="/tag/"], a[rel~=tag]""").size
    val wordCount = text.split(WHITESPACE_PATTERN).count { it.isNotBlank() }

    return tagLinkCount >= TRAILING_TAG_MIN_LINKS ||
        (linkCount >= TRAILING_TAG_MIN_LINKS && wordCount <= TRAILING_TAG_MAX_WORDS)
}

internal fun isTrailingRecirculationLinkCluster(element: Element): Boolean =
    isArticleCardRecirculationBlock(element) || isGenericTrailingRecirculationLinkCluster(element)

internal fun isGenericTrailingRecirculationLinkCluster(element: Element): Boolean {
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

internal fun Element.isRecirculationClusterCandidate(): Boolean = normalName() in RECIRCULATION_CLUSTER_TAGS &&
    hasOnlyNonSubstantiveFollowingSiblings() &&
    select("pre, code, table, figure, img, picture, blockquote").isEmpty()

internal fun Element.hasOnlyNonSubstantiveFollowingSiblings(): Boolean {
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

internal fun isAboutAuthorFooterBlock(element: Element): Boolean {
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

internal fun isArticleFooterDetailsBlock(element: Element): Boolean {
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

internal fun isRelatedTermsBlock(element: Element): Boolean {
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

internal fun isCommentCountBlock(element: Element): Boolean {
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

internal fun isBackToTopBlock(element: Element): Boolean {
    val text = element.text().trim()
    if (!BACK_TO_TOP_PATTERN.matches(text)) return false

    val hrefs = element.select("a[href]").map { it.attr("href").trim().lowercase() }
    val hints = partialHaystack(element)
    return hrefs.any { it == "#" || it == "#top" || it.endsWith("#top") } ||
        "scroll" in hints ||
        "back-to-top" in hints
}

internal fun isCommentPromptBlock(element: Element): Boolean {
    val text = element.text().trim()
    if (!COMMENT_PROMPT_PATTERN.containsMatchIn(text)) return false

    val hints = partialHaystack(element)
    return "comment" in hints ||
        "viafoura" in hints ||
        text.length <= COMMENT_PROMPT_MAX_LENGTH
}

internal fun isReadyForMoreBlock(element: Element): Boolean {
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

internal fun isMobileAppPromoBlock(element: Element): Boolean {
    val text = element.text().trim()
    if (text.length > MOBILE_APP_PROMO_MAX_LENGTH) return false
    return MOBILE_APP_PROMO_PATTERN.containsMatchIn(text)
}

internal fun isNewsletterSignupBlock(element: Element): Boolean {
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

internal fun isDonationWidgetBlock(element: Element): Boolean {
    val text = element.text().trim()
    if (text.length > DONATION_WIDGET_MAX_LENGTH) return false

    val hints = partialHaystack(element) + " " +
        element.select("a[href], img[src], img[alt]").joinToString(" ") {
            "${it.attr("href")} ${it.attr("src")} ${it.attr("alt")}"
        }.lowercase()

    return DONATION_WIDGET_PATTERN.containsMatchIn(text) &&
        DONATION_WIDGET_HINTS.any { it in hints }
}

internal fun isBylineMetadataStrip(element: Element): Boolean {
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

internal fun isArticlePackageBlock(element: Element): Boolean {
    val text = element.text().trim().collapseWhitespace()
    if (text.length > ARTICLE_PACKAGE_MAX_LENGTH) return false
    if (!ARTICLE_PACKAGE_PATTERN.containsMatchIn(text)) return false

    val hints = partialHaystack(element)
    return element.select("a[href]").isNotEmpty() ||
        "package" in hints ||
        "series" in hints ||
        "collection" in hints
}

internal fun isInlineAuthorBioBlock(element: Element): Boolean {
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

internal fun isFollowTopicsBlock(element: Element): Boolean {
    val text = element.text().trim().collapseWhitespace()
    if (text.length > FOLLOW_TOPICS_MAX_LENGTH) return false
    if (!FOLLOW_TOPICS_PATTERN.containsMatchIn(text)) return false

    val descendantHints = element.select("*").joinToString(" ") { partialHaystack(it) }
    val hints = "${partialHaystack(element)} $descendantHints"
    return "follow" in hints ||
        element.select("ul, ol, li, a[href], button").isNotEmpty() ||
        FOLLOW_TOPICS_STRONG_CONTEXT_PATTERN.containsMatchIn(text)
}

internal fun isStorySuggestionBlock(element: Element): Boolean {
    val text = element.text().trim().collapseWhitespace()
    if (text.length > STORY_SUGGESTION_MAX_LENGTH) return false
    return STORY_SUGGESTION_PATTERN.containsMatchIn(text)
}

internal fun isLocalNewsFollowBlock(element: Element): Boolean {
    val text = element.text().trim().collapseWhitespace()
    if (text.length > LOCAL_NEWS_FOLLOW_MAX_LENGTH) return false
    if (!LOCAL_NEWS_FOLLOW_PATTERN.containsMatchIn(text)) return false
    return element.select("a[href]").size >= 2
}

internal fun Element.isTrailingLinkedListRecommendation(linkCount: Int, minLinks: Int): Boolean {
    if (normalName() !in setOf("ul", "ol")) return false
    if (linkCount < minLinks) return false
    if (select("p, blockquote, pre, code, table, figure, picture").isNotEmpty()) return false
    val items = children().filter { it.normalName() == "li" }
    if (items.size < minLinks) return false
    return items.count { it.select("a[href]").isNotEmpty() } >= minLinks
}
