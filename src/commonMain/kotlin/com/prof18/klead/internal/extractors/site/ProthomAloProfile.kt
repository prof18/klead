package com.prof18.klead.internal.extractors.site

internal object ProthomAloProfile : com.prof18.klead.extractors.Extractor {
    override val id: String = "prothomalo"
    override val domains: Set<String> = setOf("prothomalo.com")
    override val contentSelectors: List<String> = listOf(".story-content-wrapper")
    override val preContentRemoveSelectors: List<String> = listOf(
        """.story-content > div:has(> a[href*="google.com/preferences/source"])""",
    )
    override val postContentRemoveSelectors: List<String> = listOf(
        ".story-head .print-entity-section-wrapper",
        ".story-head .story-metadata-wrapper",
        """.story-page-hero [title="Close"]""",
        ".story-element-text-also-read",
        ".print-none",
        ".print-tags",
    )
}
