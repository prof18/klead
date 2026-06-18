package com.prof18.klead.internal.extractors.site

internal object VarietyProfile : com.prof18.klead.extractors.Extractor {
    override val id: String = "variety"
    override val domains: Set<String> = setOf("variety.com")
    override val postContentRemoveSelectors: List<String> = listOf(
        ".o-comments-link",
    )
}
