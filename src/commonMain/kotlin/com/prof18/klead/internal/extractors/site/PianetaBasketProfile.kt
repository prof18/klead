package com.prof18.klead.internal.extractors.site

internal object PianetaBasketProfile : com.prof18.klead.extractors.Extractor {
    override val id: String = "pianetabasket"
    override val domains: Set<String> = setOf("pianetabasket.com")
    override val contentSelectors: List<String> = listOf("#article-blocks")
    override val postContentRemoveSelectors: List<String> = listOf(
        ".testo > .data.small",
        ".thumbuser",
        ".tcc-badge",
    )
}
