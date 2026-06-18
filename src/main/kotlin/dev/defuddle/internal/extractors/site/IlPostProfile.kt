package dev.defuddle.internal.extractors.site

internal object IlPostProfile : dev.defuddle.extractors.Extractor {
    override val id: String = "ilpost"
    override val domains: Set<String> = setOf("ilpost.it")
    override val postContentRemoveSelectors: List<String> = listOf(
        "#audioPlayerArticle",
        ".audio-player",
        ".audioplayer",
        "[data-mp3]",
        "[data-audio-src]",
    )
}
