package dev.defuddle.site

object MacRumorsProfile : SiteExtractor {
    override val id: String = "macrumors"
    override val domains: Set<String> = setOf("macrumors.com")
    override val postContentRemoveSelectors: List<String> = listOf(
        """[class*="byline--"]""",
        ".comments-link",
        ".linkback",
    )
}
