package com.prof18.klead.internal.extractors.site

import com.prof18.klead.extractors.Extractor
import com.prof18.klead.extractors.ExtractorContext

internal object ScpWikiProfile : Extractor {
    override val id: String = "scp-wiki"
    override val domains: Set<String> = setOf("scp-wiki.wikidot.com")
    override val contentSelectors: List<String> = listOf("#page-content")

    override fun matches(context: ExtractorContext): Boolean = super.matches(context) || (
        context.document.selectFirst("#main-content #page-content") != null &&
            context.document.selectFirst("""meta[name=keywords]""")
                ?.attr("content")
                ?.contains("scp", ignoreCase = true) == true
    )
}
