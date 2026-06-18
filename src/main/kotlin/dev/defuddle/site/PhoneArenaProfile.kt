package dev.defuddle.site

object PhoneArenaProfile : dev.defuddle.extractors.Extractor {
    override val id: String = "phonearena"
    override val domains: Set<String> = setOf("phonearena.com")
    override val contentSelectors: List<String> = listOf(
        "article",
        ".content-body",
    )
    override val postContentRemoveSelectors: List<String> = listOf(
        ".content-header-widgets",
        ".content-disclaimer",
        ".content-after-content-row",
        ".content-author-byline",
        ".discussions-latest",
        ".phone-links",
    )
}
