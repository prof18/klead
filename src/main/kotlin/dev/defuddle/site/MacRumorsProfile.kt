package dev.defuddle.site

object MacRumorsProfile : dev.defuddle.extractors.Extractor {
    override val id: String = "macrumors"
    override val domains: Set<String> = setOf("macrumors.com")
    override val postContentRemoveSelectors: List<String> = listOf(
        """[class*="byline--"]""",
        ".comments-link",
        ".linkback",
    )
}
