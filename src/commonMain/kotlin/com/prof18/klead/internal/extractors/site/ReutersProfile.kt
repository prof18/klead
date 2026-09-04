package com.prof18.klead.internal.extractors.site

internal object ReutersProfile : com.prof18.klead.extractors.Extractor {
    override val id: String = "reuters"
    override val domains: Set<String> = setOf("reuters.com")
    override val postContentRemoveSelectors: List<String> = listOf(
        "[role=tablist]",
    )
}
