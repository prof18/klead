package com.prof18.klead.internal.extractors.site

internal object SupercarBlondieProfile : com.prof18.klead.extractors.Extractor {
    override val id: String = "supercarblondie"
    override val domains: Set<String> = setOf("supercarblondie.com")
    override val contentSelectors: List<String> = listOf(
        ".Detail-Post",
    )
    override val postContentRemoveSelectors: List<String> = listOf(
        ".preferred-source-card",
        ".sb-nl",
        """div:has(> p > a[href*="supercarblondie.com/tag/"])""",
        ".faq-drawer",
    )
}
