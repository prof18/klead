package com.prof18.klead.internal.extractors.site

import com.prof18.klead.extractors.Extractor

internal object AnsaProfile : Extractor {
    override val id: String = "ansa"
    override val domains: Set<String> = setOf("ansa.it")
    override val contentSelectors: List<String> = listOf(".article-detail")
    override val postContentRemoveSelectors: List<String> = listOf(
        ".section-heading",
        ".section-side",
        ".article-side",
        ".article-widget",
        ".enlarge",
        "#ansa-check",
    )
}
