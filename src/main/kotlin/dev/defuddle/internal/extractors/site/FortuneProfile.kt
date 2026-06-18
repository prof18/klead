package dev.defuddle.internal.extractors.site

internal object FortuneProfile : dev.defuddle.extractors.Extractor {
    override val id: String = "fortune"
    override val domains: Set<String> = setOf("fortune.com")
    override val postContentRemoveSelectors: List<String> = listOf(
        """[data-cy="trending-top-bar"]""",
        """[data-cy="article-section-eyebrow"]""",
        """[data-cy="article-tag-eyebrow"]""",
        """[data-cy="authors-bio-cards"]""",
        """[data-cy="author-bio"]""",
        """[data-cy="author-see-full-bio"]""",
        """[data-component="headline-block"]""",
        """[data-component="byline-block"]""",
    )
}
