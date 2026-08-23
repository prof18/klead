package com.prof18.klead.internal.extractors.site

internal object BuzzFeedProfile : com.prof18.klead.extractors.Extractor {
    override val id: String = "buzzfeed"
    override val domains: Set<String> = setOf("buzzfeed.com")
    override val postContentRemoveSelectors: List<String> = listOf(
        ".postHead",
        "[class*=headline-byline]",
    )
}
