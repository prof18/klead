package com.prof18.klead.internal.extractors.site

import com.prof18.klead.extractors.Extractor

internal object TrtHaberProfile : Extractor {
    override val id: String = "trthaber"
    override val domains: Set<String> = setOf("trthaber.com")
    override val contentSelectors: List<String> = listOf(".news-content-container")
    override val postContentRemoveSelectors: List<String> = listOf(
        ".news-info-bar",
        ".social-links-container",
        ".news-tags",
        ".news-detail-bottom-mobile-ads-container",
    )
}
