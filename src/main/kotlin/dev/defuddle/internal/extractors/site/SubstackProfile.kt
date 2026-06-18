package dev.defuddle.internal.extractors.site

import dev.defuddle.extractors.ExtractorContext
import dev.defuddle.internal.dom.selectFirstSafe

internal object SubstackProfile : dev.defuddle.extractors.Extractor {
    override val id: String = "substack"
    override val domains: Set<String> = setOf("substack.com", "20percent.berlin")
    override val postContentRemoveSelectors: List<String> = listOf(
        "#substack-comments",
        ".comments-section",
        ".more-comments",
        ".portable-archive",
        ".portable-archive-list",
        ".portable-archive-empty",
        """[aria-label="Top Posts Footer"]""",
    )

    override fun matches(context: ExtractorContext): Boolean =
        context.hostMatches(domains) || SUBSTACK_DOM_SIGNALS.any { context.document.selectFirstSafe(it) != null }

    private val SUBSTACK_DOM_SIGNALS = listOf(
        "#substack-comments",
        ".portable-archive",
        ".portable-archive-list",
        """[aria-label="Top Posts Footer"]""",
        """script[src*="substackcdn.com"]""",
        """link[href*="substackcdn.com"]""",
    )
}
