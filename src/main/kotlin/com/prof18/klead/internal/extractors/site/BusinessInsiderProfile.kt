package com.prof18.klead.internal.extractors.site

internal object BusinessInsiderProfile : com.prof18.klead.extractors.Extractor {
    override val id: String = "business-insider"
    override val domains: Set<String> = setOf("businessinsider.com")
    override val postContentRemoveSelectors: List<String> = listOf(
        """[data-component-type="post-byline"]""",
        ".post-byline",
        ".byline-wrapper",
        ".byline-author-container",
        """[data-component-type="timestamp"]""",
        ".post-video-recirc",
        """[data-component-type="post-video-recirc"]""",
        ".back-to-home-container",
        ".back-to-home",
    )
}
