package com.prof18.klead.internal.extractors.site

internal object BBCProfile : com.prof18.klead.extractors.Extractor {
    override val id: String = "bbc"
    override val domains: Set<String> = setOf("bbc.com")
    override val postContentRemoveSelectors: List<String> = listOf(
        """[data-component="headline-block"]""",
        """[data-component="byline-block"]""",
        "img.hide-when-no-script",
        """img[aria-label="image unavailable"]""",
        """img[src*="grey-placeholder"]""",
        """p:matches((?i)\bdo\s+you\s+have\s+a\s+story\s+suggestion\b)""",
        """p:matches((?i)^follow\s+.{1,80}\s+news\s+on\b)""",
    )
}
