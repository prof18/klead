package com.prof18.klead.internal.extractors.site

import com.prof18.klead.extractors.Extractor

internal object OpenNetProfile : Extractor {
    override val id: String = "opennet"
    override val domains: Set<String> = setOf("opennet.ru")
    override val contentSelectors: List<String> = listOf("#r_memo")
}
