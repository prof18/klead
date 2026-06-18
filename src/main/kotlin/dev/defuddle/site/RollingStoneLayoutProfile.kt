package dev.defuddle.site

object RollingStoneLayoutProfile : SiteExtractor {
    override val id: String = "rolling-stone-layout"
    override val domains: Set<String> = setOf("rollingstone.com")
    override val postContentRemoveSelectors: List<String> = listOf(
        ".a-article-grid__header",
        ".a-article-grid__author",
        ".recirculation-modules",
    )
}
