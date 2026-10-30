package com.prof18.klead.internal.extractors.site

internal object MinecraftProfile : com.prof18.klead.extractors.Extractor {
    override val id: String = "minecraft"
    override val domains: Set<String> = setOf("minecraft.net")

    // The "Newest News" grid is filled from an articles API at runtime; only its heading is in the HTML.
    override val postContentRemoveSelectors: List<String> = listOf(""".MC_imageGridA[data-mc-config*="articles"]""")
}
