package com.prof18.klead.internal.extractors.site

internal object DDayProfile : com.prof18.klead.extractors.Extractor {
    override val id: String = "dday"
    override val domains: Set<String> = setOf("dday.it")
    override val postContentRemoveSelectors: List<String> = listOf(
        ".google-preferred-source",
        ".like-box",
    )
}
