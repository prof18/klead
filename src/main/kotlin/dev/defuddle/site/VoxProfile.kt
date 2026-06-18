package dev.defuddle.site

object VoxProfile : SiteExtractor {
    override val id: String = "vox"
    override val domains: Set<String> = setOf("theverge.com", "sbnation.com")
    override val postContentRemoveSelectors: List<String> = listOf(
        ".duet--article--lede",
        ".duet--ledes--standard-lede-bottom",
        ".wp-block-query",
        "aside[data-mrf-recirculation]",
        "aside.hawk-root",
    )
}
