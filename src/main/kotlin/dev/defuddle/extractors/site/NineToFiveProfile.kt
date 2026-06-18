package dev.defuddle.extractors.site

object NineToFiveProfile : dev.defuddle.extractors.Extractor {
    override val id: String = "nine-to-five"
    override val domains: Set<String> = setOf("9to5google.com", "9to5mac.com", "9to5linux.com")
    override val postContentRemoveSelectors: List<String> = listOf(
        ".google-preferred-source-badge",
        ".ad-disclaimer-container",
        ".disclaimer-affiliate",
        ".bm-social-top",
        ".visitor-promo",
        "#after_disclaimer_placement",
        ".btn-gpsource-bt-article",
        ".top-comment",
        """h2:matchesOwn((?i)^More on .+:$) + ul""",
        """h2:matchesOwn((?i)^More on .+:$)""",
        """h3:matchesOwn((?i)^Best .+ accessories$) + ul""",
        """h3:matchesOwn((?i)^Best .+ accessories$)""",
        """p:matches((?i)Follow\s+[^:]{1,48}:)""",
    )
}
