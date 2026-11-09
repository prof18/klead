package com.prof18.klead.internal.extractors.site

internal object AbcNewsProfile : com.prof18.klead.extractors.Extractor {
    override val id: String = "abcnews"
    override val domains: Set<String> = setOf("abc.net.au")

    // Sport articles put a score rail of ~200 fixture <article> cards inside <main>, ahead of the story,
    // so generic scoring settles on <main> and the fixtures list comes first.
    override val contentSelectors: List<String> = listOf("""[data-component="ArticleWeb"]""")

    override val postContentRemoveSelectors: List<String> = listOf(
        // The visible timestamp is aria-hidden and already dropped; keep the screen-reader copy, not the print one.
        """[data-component="PrintTimestamp"]""",
        // Audio embeds load at runtime; only their "Loading..." spinner is in the HTML.
        """[data-component="Loading"]""",
    )
}
