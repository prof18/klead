package dev.defuddle.internal.extractors.site

internal object EntrepreneurProfile : dev.defuddle.extractors.Extractor {
    override val id: String = "entrepreneur"
    override val domains: Set<String> = setOf("entrepreneur.com")
    override val postContentRemoveSelectors: List<String> = listOf(
        """[data-cy="time-rubric"]""",
        """[data-cy="byline-author"]""",
        """[data-cy="social-share-top"]""",
        """[data-cy="social-share-bottom"]""",
        """[data-vars-event-name="preferred_source_view"]""",
        """[data-cy="preferred-source-top"]""",
        """[data-cy="preferred-source-bottom"]""",
        """[data-cy="what-to-read-next"]""",
        ".classifai-listen-to-post-wrapper",
        ".classifai-post-audio-heading",
        """audio[id^="classifai-post-audio-player"]""",
        """a[href*="google.com/preferences/source"]""",
        """a[href="#ep-comments"]""",
    )
}
