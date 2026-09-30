package com.prof18.klead.internal.extractors.site

import com.prof18.klead.internal.extractors.DomExtractor
import com.prof18.klead.internal.extractors.DomExtractorContext

internal object HashnodeProfile : DomExtractor {
    override val id: String = "hashnode"

    // Related-post article cards can be substantial enough to prevent generic main refinement.
    override val contentSelectors: List<String> = listOf("article:has(.prose)")
    override val postContentRemoveSelectors: List<String> = listOf(
        // The author card precedes the prose wrapper in Hashnode's article layout.
        """article > div > div:first-child:has(a[href^="https://hashnode.com/@"]):not(.prose):not(:has(.prose))""",
    )

    override fun matches(context: DomExtractorContext): Boolean =
        context.document.selectFirst("""header a[href^="https://hashnode.com/?"]""") != null &&
            context.document.selectFirst("article .prose") != null
}
