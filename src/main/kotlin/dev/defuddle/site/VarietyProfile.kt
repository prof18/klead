package dev.defuddle.site

object VarietyProfile : dev.defuddle.extractors.Extractor {
    override val id: String = "variety"
    override val domains: Set<String> = setOf("variety.com")
    override val postContentRemoveSelectors: List<String> = listOf(
        ".o-comments-link",
    )
}
