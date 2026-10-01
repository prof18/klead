package com.prof18.klead.internal.extractors.site

internal object EntrepreneurProfile : com.prof18.klead.extractors.Extractor {
    override val id: String = "entrepreneur"
    override val domains: Set<String> = setOf("entrepreneur.com")
    override val postContentRemoveSelectors: List<String> = listOf(
        """[data-vars-event-name="preferred_source_view"]""",
        ".classifai-listen-to-post-wrapper",
        ".classifai-post-audio-heading",
        """audio[id^="classifai-post-audio-player"]""",
        """a[href*="google.com/preferences/source"]""",
        """a[href="#ep-comments"]""",
    )
}
