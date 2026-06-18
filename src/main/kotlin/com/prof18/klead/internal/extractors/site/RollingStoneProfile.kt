package com.prof18.klead.internal.extractors.site

internal object RollingStoneProfile : com.prof18.klead.extractors.Extractor {
    override val id: String = "rolling-stone"
    override val domains: Set<String> = setOf("rollingstone.com")
    override val postContentRemoveSelectors: List<String> = listOf(
        ".trending-in-article",
    )
}
