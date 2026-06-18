package dev.defuddle.site

object RollingStoneProfile : SiteExtractor {
    override val id: String = "rolling-stone"
    override val domains: Set<String> = setOf("rollingstone.com")
    override val postContentRemoveSelectors: List<String> = listOf(
        ".trending-in-article",
    )
}
