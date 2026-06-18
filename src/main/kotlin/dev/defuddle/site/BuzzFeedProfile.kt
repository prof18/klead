package dev.defuddle.site

object BuzzFeedProfile : SiteExtractor {
    override val id: String = "buzzfeed"
    override val domains: Set<String> = setOf("buzzfeed.com")
    override val postContentRemoveSelectors: List<String> = listOf(
        ".postHead",
        "[class*=headline-byline]",
    )
}
