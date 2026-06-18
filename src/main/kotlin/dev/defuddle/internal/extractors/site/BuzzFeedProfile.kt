package dev.defuddle.internal.extractors.site

internal object BuzzFeedProfile : dev.defuddle.extractors.Extractor {
    override val id: String = "buzzfeed"
    override val domains: Set<String> = setOf("buzzfeed.com")
    override val postContentRemoveSelectors: List<String> = listOf(
        ".postHead",
        "[class*=headline-byline]",
    )
}
