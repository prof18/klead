package com.prof18.klead.internal.extractors.site

internal object IPhoneItaliaProfile : com.prof18.klead.extractors.Extractor {
    override val id: String = "iphoneitalia"
    override val domains: Set<String> = setOf("iphoneitalia.com")
    override val postContentRemoveSelectors: List<String> = listOf(
        ".ipit-native-comments-alert",
        ".ipit-ac-remove-ads",
        ".ipit-tts-player",
        ".ipit-tldr-box",
        ".ipit-source-pill-wrap",
        // Remove the recommendation heading before its adjacent product widget.
        "h4:has(+ .aawp)",
        ".aawp",
        ".amazon-disclaimer",
        "#article-widget-area",
        ".social-sharing",
    )
}
