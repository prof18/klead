package com.prof18.klead.internal.extractors.site

internal object ISpazioProfile : com.prof18.klead.extractors.Extractor {
    override val id: String = "ispazio"
    override val domains: Set<String> = setOf("ispazio.net")
    override val contentSelectors: List<String> = listOf(".isp-entry-content")
    override val postContentRemoveSelectors: List<String> = listOf(".isp-single-spot")
}
