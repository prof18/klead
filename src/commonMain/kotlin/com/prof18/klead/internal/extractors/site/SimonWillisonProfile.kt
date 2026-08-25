package com.prof18.klead.internal.extractors.site

import com.prof18.klead.extractors.Extractor

internal object SimonWillisonProfile : Extractor {
    override val id: String = "simon-willison"
    override val domains: Set<String> = setOf("simonwillison.net")
    override val contentSelectors: List<String> = listOf(".entry.entryPage")
    override val postContentRemoveSelectors: List<String> = listOf(".entryFooter")
}
