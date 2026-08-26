package com.prof18.klead.internal.extractors.site

import com.prof18.klead.extractors.Extractor

internal object KurucInfoProfile : Extractor {
    override val id: String = "kuruc-info"
    override val domains: Set<String> = setOf("kuruc.info")
    override val contentSelectors: List<String> = listOf("[itemprop=articleBody]")
}
