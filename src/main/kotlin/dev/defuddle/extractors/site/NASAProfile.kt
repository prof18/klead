package dev.defuddle.extractors.site

object NASAProfile : dev.defuddle.extractors.Extractor {
    override val id: String = "nasa"
    override val domains: Set<String> = setOf("science.nasa.gov")
    override val postContentRemoveSelectors: List<String> = listOf(
        """[class*="credits-and-details"]""",
        """[class*="related-articles"]""",
        """[class*="topic-cards"]""",
        """[class*="about-the-author"]""",
    )
}
