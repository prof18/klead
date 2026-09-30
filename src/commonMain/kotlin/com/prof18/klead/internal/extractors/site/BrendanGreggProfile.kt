package com.prof18.klead.internal.extractors.site

import com.prof18.klead.extractors.Extractor

internal object BrendanGreggProfile : Extractor {
    override val id: String = "brendangregg"
    override val domains: Set<String> = setOf("brendangregg.com")
    override val contentSelectors: List<String> = listOf(".site .post")
}
