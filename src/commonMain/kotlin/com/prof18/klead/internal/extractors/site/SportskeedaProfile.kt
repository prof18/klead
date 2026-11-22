package com.prof18.klead.internal.extractors.site

internal object SportskeedaProfile : com.prof18.klead.extractors.Extractor {
    override val id: String = "sportskeeda"
    override val domains: Set<String> = setOf("sportskeeda.com")

    // House promos ("Looking for Crossword hints…", "Are you stuck on today's Wordle?") placed inside the body.
    override val postContentRemoveSelectors: List<String> = listOf(".bottom-tagline")
}
