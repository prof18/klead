package com.prof18.klead.internal.extractors.site

internal object AlJazeeraProfile : com.prof18.klead.extractors.Extractor {
    override val id: String = "aljazeera"
    override val domains: Set<String> = setOf("aljazeera.com")
    override val postContentRemoveSelectors: List<String> = listOf(
        ".rich-share",
        "section.more-on",
        ".date-simple span[aria-hidden]",
    )
}
