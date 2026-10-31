package com.prof18.klead.internal.extractors.site

internal object NintendoEverythingProfile : com.prof18.klead.extractors.Extractor {
    override val id: String = "nintendoeverything"
    override val domains: Set<String> = setOf("nintendoeverything.com")

    // The theme has no article, main or entry-content element, so generic scoring settles on the
    // whole body: the featured-posts carousel, "Submit a news tip" and the Top Posts sidebar included.
    override val contentSelectors: List<String> = listOf(".main .content")

    // Disqus loads the comments at runtime; only the "Leave a Reply" heading is in the HTML.
    override val postContentRemoveSelectors: List<String> = listOf(".comments_head")
}
