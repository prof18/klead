package com.prof18.klead.internal.extractors.site

internal object MacRumorsProfile : com.prof18.klead.extractors.Extractor {
    override val id: String = "macrumors"
    override val domains: Set<String> = setOf("macrumors.com")
    override val postContentRemoveSelectors: List<String> = listOf(
        """[class*="byline--"]""",
        ".comments-link",
        ".linkback",
    )
}
