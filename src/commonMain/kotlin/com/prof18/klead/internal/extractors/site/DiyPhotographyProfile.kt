package com.prof18.klead.internal.extractors.site

internal object DiyPhotographyProfile : com.prof18.klead.extractors.Extractor {
    override val id: String = "diyphotography"
    override val domains: Set<String> = setOf("diyphotography.net")
    override val contentSelectors: List<String> = listOf(".bb-post-content")
    override val postContentRemoveSelectors: List<String> = listOf(".diyp-preferred-source-and-banner")
}
