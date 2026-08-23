package com.prof18.klead.internal.extractors.site

internal object TechCrunchProfile : com.prof18.klead.extractors.Extractor {
    override val id: String = "techcrunch"
    override val domains: Set<String> = setOf("techcrunch.com")
    override val postContentRemoveSelectors: List<String> = listOf(
        ".article__meta",
        ".wp-block-techcrunch-post-authors-list",
        ".wp-block-techcrunch-event-cta",
        ".rightrail-promo",
        ".latest-in-pattern",
    )
}
