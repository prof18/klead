package com.prof18.klead.internal.extractors.site

internal object MashableProfile : com.prof18.klead.extractors.Extractor {
    override val id: String = "mashable"
    override val domains: Set<String> = setOf("mashable.com")
    override val postContentRemoveSelectors: List<String> = listOf(
        """[aria-label="Author Bio Flyout"]""",
        """[role="tooltip"][aria-label*="Author Bio"]""",
        """div:matchesOwn((?i)^\s*All products featured here are independently selected)""",
        """img[src*="seamless-keep-scrolling"]""",
        """img[alt="Mashable Potato"]""",
    )
}
