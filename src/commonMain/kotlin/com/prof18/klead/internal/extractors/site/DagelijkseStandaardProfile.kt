package com.prof18.klead.internal.extractors.site

internal object DagelijkseStandaardProfile : com.prof18.klead.extractors.Extractor {
    override val id: String = "dagelijksestandaard"
    override val domains: Set<String> = setOf("dagelijksestandaard.nl")
    override val contentSelectors: List<String> = listOf("#article-blocks")
    override val postContentRemoveSelectors: List<String> = listOf(".raw-html-component--ad")
}
