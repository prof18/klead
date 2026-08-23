package com.prof18.klead.internal.extractors.site

import com.prof18.klead.internal.extractors.DomExtractor
import com.prof18.klead.internal.extractors.DomExtractorContext

internal object ScpWikiProfile : DomExtractor {
    override val id: String = "scp-wiki"
    override val domains: Set<String> = setOf("scp-wiki.wikidot.com")
    override val contentSelectors: List<String> = listOf("#page-content")

    override fun matches(context: DomExtractorContext): Boolean = super.matches(context) || (
        context.document.selectFirst("#main-content #page-content") != null &&
            context.document.selectFirst("""meta[name=keywords]""")
                ?.attr("content")
                ?.contains("scp", ignoreCase = true) == true
    )
}
