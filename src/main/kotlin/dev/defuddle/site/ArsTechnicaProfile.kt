package dev.defuddle.site

object ArsTechnicaProfile : SiteExtractor {
    override val id: String = "ars-technica"
    override val domains: Set<String> = setOf("arstechnica.com")
    override val postContentRemoveSelectors: List<String> = listOf(
        ".text-settings-dropdown-story",
        ".text-settings",
        ".author-mini-bio",
    )
}
