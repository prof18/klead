package com.prof18.klead.internal.extractors.site

import com.prof18.klead.internal.extractors.DomExtractor

internal object JianshuProfile : DomExtractor {
    override val id: String = "jianshu"
    override val domains: Set<String> = setOf("jianshu.com")

    // The copyright and platform disclaimer sits in a sibling after the <article> body.
    override val contentSelectors: List<String> = listOf("article")
}
