package com.prof18.klead.internal.extractors.site

internal object GameSpotProfile : com.prof18.klead.extractors.Extractor {
    override val id: String = "gamespot"
    override val domains: Set<String> = setOf("gamespot.com")
    override val postContentRemoveSelectors: List<String> = listOf(
        ".right-rail",
        ".single-sidebar",
        """[aria-label="Article sidebar"]""",
    )
}
