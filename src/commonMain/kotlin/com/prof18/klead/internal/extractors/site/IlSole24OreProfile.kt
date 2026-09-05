package com.prof18.klead.internal.extractors.site

internal object IlSole24OreProfile : com.prof18.klead.extractors.Extractor {
    override val id: String = "ilsole24ore"
    override val domains: Set<String> = setOf("ilsole24ore.com")

    // The article can continue in a separate aentry-container; do not select only its first body.
    override val preContentRemoveSelectors: List<String> = listOf(
        ".rstrip",
        ".ahead > .meta",
        ".agoogle-pref",
        ".ainfotool",
        ".gpt24-suggest",
        ".abox",
        ".acor--mkt",
        ".afoot-info",
        ".rel--brandconn",
        """div.d-print-none:matchesOwn(^Loading\.\.\.$)""",
    )
}
