package com.prof18.klead.internal.extractors.site

internal object MacStoriesProfile : com.prof18.klead.extractors.Extractor {
    override val id: String = "macstories"
    override val domains: Set<String> = setOf("macstories.net")
    override val postContentRemoveSelectors: List<String> = listOf(
        ".view-full-size",
    )
}
