package com.prof18.klead.internal.extractors.site

internal object GsmArenaProfile : com.prof18.klead.extractors.Extractor {
    override val id: String = "gsmarena"
    override val domains: Set<String> = setOf("gsmarena.com")
    override val contentSelectors: List<String> = listOf(
        "#review-body",
    )
}
