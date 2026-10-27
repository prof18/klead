package com.prof18.klead.internal.extractors.site

import com.prof18.klead.extractors.ExtractorResult
import com.prof18.klead.internal.extractors.DomExtractor
import com.prof18.klead.internal.extractors.DomExtractorContext

internal object GolemProfile : DomExtractor {
    override val id: String = "golem"
    override val domains: Set<String> = setOf("golem.de")

    // Without a consent cookie golem.de serves its consent dialog in place of the article.
    override fun matches(context: DomExtractorContext): Boolean = context.hostMatches(domains) &&
        context.document.selectFirst("#gspmessage") != null &&
        context.document.selectFirst("article") == null

    override fun extract(context: DomExtractorContext): ExtractorResult = ExtractorResult(contentHtml = "")
}
