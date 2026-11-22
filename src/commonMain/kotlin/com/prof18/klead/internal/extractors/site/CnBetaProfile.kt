package com.prof18.klead.internal.extractors.site

import com.prof18.klead.internal.extractors.DomExtractor

internal object CnBetaProfile : DomExtractor {
    override val id: String = "cnbeta"
    override val domains: Set<String> = setOf("cnbeta.com.tw", "cnbeta.com")

    // The lead paragraph sits in .article-summary, a sibling of the #artibody body.
    override val contentSelectors: List<String> = listOf(".cnbeta-article-body")
    override val postContentRemoveSelectors: List<String> = listOf(
        ".article-summary .topic",
        ".main__left",
        ".article-share-code",
        ".tac",
        ".adsbygoogle",
    )
}
