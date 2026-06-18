package dev.defuddle.extractors.site

object PianetaBasketProfile : dev.defuddle.extractors.Extractor {
    override val id: String = "pianetabasket"
    override val domains: Set<String> = setOf("pianetabasket.com")
    override val postContentRemoveSelectors: List<String> = listOf(
        ".testo > .data.small",
        ".thumbuser",
        ".tcc-badge",
    )
}
