package dev.defuddle.extractors.site

object GameSpotProfile : dev.defuddle.extractors.Extractor {
    override val id: String = "gamespot"
    override val domains: Set<String> = setOf("gamespot.com")
    override val postContentRemoveSelectors: List<String> = listOf(
        ".right-rail",
        ".single-sidebar",
        """[aria-label="Article sidebar"]""",
    )
}
