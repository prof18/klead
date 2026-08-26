package com.prof18.klead.internal.extractors.site

import com.prof18.klead.extractors.Extractor

internal object PhysOrgProfile : Extractor {
    override val id: String = "phys-org"
    override val domains: Set<String> = setOf("phys.org")
    override val contentSelectors: List<String> = listOf(".article-main")
    override val postContentRemoveSelectors: List<String> = listOf(
        ".article-main__more",
        ".author-card",
        ".d-print-block",
        ".icon_open",
    )
}
