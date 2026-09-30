package com.prof18.klead.internal.extractors.site

import com.prof18.klead.extractors.Extractor

internal object LucumrProfile : Extractor {
    override val id: String = "lucumr"
    override val domains: Set<String> = setOf("lucumr.pocoo.org")
    override val contentSelectors: List<String> = listOf("div.body")
    override val postContentRemoveSelectors: List<String> = listOf(".tags", ".markdown-links")
}
