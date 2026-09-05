package com.prof18.klead.internal.extractors.site

internal object GeopopProfile : com.prof18.klead.extractors.Extractor {
    override val id: String = "geopop"
    override val domains: Set<String> = setOf("geopop.it")
    override val contentSelectors: List<String> = listOf(".cp_article__content")
    override val postContentRemoveSelectors: List<String> = listOf(".rdls")
}
