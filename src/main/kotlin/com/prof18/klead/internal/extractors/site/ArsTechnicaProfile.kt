package com.prof18.klead.internal.extractors.site

internal object ArsTechnicaProfile : com.prof18.klead.extractors.Extractor {
    override val id: String = "ars-technica"
    override val domains: Set<String> = setOf("arstechnica.com")
    override val postContentRemoveSelectors: List<String> = listOf(
        ".text-settings-dropdown-story",
        ".text-settings",
        ".author-mini-bio",
    )
}
