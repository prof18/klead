package com.prof18.klead.internal.extractors.site

internal object BloggerProfile : com.prof18.klead.extractors.Extractor {
    override val id: String = "blogger"
    override val domains: Set<String> = setOf("googleblog.com", "blogspot.com")
    override val postContentRemoveSelectors: List<String> = listOf(
        ".copy-tooltip",
        ".copy-tooltiptext",
        ".hidden_message",
        """div.separator:matchesOwn((?i)^\s*posted\s+by\s+)""",
        "#blog-pager",
        ".blog-pager",
        ".adb-detail > hr:last-child",
        ".blog-pager-newer-link",
        ".blog-pager-older-link",
    )
}
