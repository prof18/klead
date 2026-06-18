package dev.defuddle.internal.extractors.site

internal object MotorsportProfile : dev.defuddle.extractors.Extractor {
    override val id: String = "motorsport"
    override val domains: Set<String> = setOf("motorsport.com")
    override val contentSelectors: List<String> = listOf(
        ".ms-article-content",
        ".ms-article__body",
        "article.ms-page",
    )
    override val postContentRemoveSelectors: List<String> = listOf(
        ".ms-article-end",
        ".msnt-article-prev-next",
        ".ms-comments-wrapper",
        ".ms-inarticle-widgets",
        ".ms-items-widget",
        "#adblock-content-blocked-tpl",
        ".adblock-content-blocked",
        """h2:matchesOwn((?i)^Photos from .+)""",
    )
}
