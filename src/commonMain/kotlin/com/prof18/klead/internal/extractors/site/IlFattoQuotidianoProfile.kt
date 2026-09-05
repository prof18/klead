package com.prof18.klead.internal.extractors.site

internal object IlFattoQuotidianoProfile : com.prof18.klead.extractors.Extractor {
    override val id: String = "ilfattoquotidiano"
    override val domains: Set<String> = setOf("ilfattoquotidiano.it")
    override val contentSelectors: List<String> = listOf(
        "article.ifq-post",
    )
    override val postContentRemoveSelectors: List<String> = listOf(
        ".ifq-related-carousel",
        ".ifq-post__utils",
        ".ifq-post__engagement-banner-channels",
    )
}
