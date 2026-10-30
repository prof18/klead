package com.prof18.klead.internal.extractors.site

internal object DexertoProfile : com.prof18.klead.extractors.Extractor {
    override val id: String = "dexerto"
    override val domains: Set<String> = setOf("dexerto.com")
    override val postContentRemoveSelectors: List<String> = listOf("#article-note")
}
