package com.prof18.klead.internal.extractors.site

internal object PopCultureProfile : com.prof18.klead.extractors.Extractor {
    override val id: String = "popculture"
    override val domains: Set<String> = setOf("popculture.com")
    override val postContentRemoveSelectors: List<String> = listOf(
        ".entry-footer",
        ".entry-aside",
        ".wp-block-savage-platform-beehiiv-form",
        ".wp-block-savage-platform-primis-video",
        """.section-heading:matches((?i)^\s*Most\s+Viewed\s*$)""",
    )
}
