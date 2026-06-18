package dev.defuddle.extractors.site

object ValnetProfile : dev.defuddle.extractors.Extractor {
    override val id: String = "valnet"
    override val domains: Set<String> = setOf("screenrant.com", "androidpolice.com", "polygon.com")
    override val postContentRemoveSelectors: List<String> = listOf(
        ".display-card.article-card",
        ".display-card[data-include-community-rating]",
        ".display-card[data-show-streamrentbuy-links]",
        "div.article-card[data-nosnippet]",
        ".article-options",
        ".article-tags",
        ".follow-container",
        "[data-is-follow-choice-button]",
        "[data-is-followed-choice-button]",
        ".w-article-header-comp",
        ".w-heading-options",
        ".w-sharing-copy",
        "#sharingCopyAlertDiv",
        ".w-article-header-author-img",
        ".article-header-author-img",
        ".w-tag-interaction-popup-menu",
        ".article-header > p",
        ".article-header-title",
        ".bc-complement",
        ".bc-listing-categories",
    )
}
