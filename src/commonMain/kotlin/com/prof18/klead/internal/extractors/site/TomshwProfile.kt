package com.prof18.klead.internal.extractors.site

internal object TomshwProfile : com.prof18.klead.extractors.Extractor {
    override val id: String = "tomshw"
    override val domains: Set<String> = setOf("tomshw.it")
    override val postContentRemoveSelectors: List<String> = listOf(
        """a[href*="google.com/preferences/source"]""",
        """[x-data="miniArticlePaginate()"]""",
    )
}
