package dev.defuddle.extractors.site

object AxiosProfile : dev.defuddle.extractors.Extractor {
    override val id: String = "axios"
    override val domains: Set<String> = setOf("axios.com")
    override val postContentRemoveSelectors: List<String> = listOf(
        """[data-cy="time-rubric"]""",
        """[data-cy="byline-author"]""",
        """[data-cy="social-share-top"]""",
        """[data-cy="social-share-bottom"]""",
        """[data-vars-event-name="preferred_source_view"]""",
        """[data-cy="preferred-source-top"]""",
        """[data-cy="preferred-source-bottom"]""",
        """[data-cy="what-to-read-next"]""",
        """a[href*="google.com/preferences/source"]""",
        """a[href="#ep-comments"]""",
    )
}
