package com.prof18.klead.internal.extractors.site

internal object GamingBoltProfile : com.prof18.klead.extractors.Extractor {
    override val id: String = "gamingbolt"
    override val domains: Set<String> = setOf("gamingbolt.com")

    // The page has no article element, so generic scoring settles on the whole body,
    // header logo and "Keep On Reading!" river included.
    override val contentSelectors: List<String> = listOf(".post-content-container")
}
