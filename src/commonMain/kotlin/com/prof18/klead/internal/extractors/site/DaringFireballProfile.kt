package com.prof18.klead.internal.extractors.site

import com.prof18.klead.extractors.ExtractorContext
import com.prof18.klead.extractors.ExtractorMetadata
import com.prof18.klead.extractors.ExtractorResult
import com.prof18.klead.internal.extractors.document

internal object DaringFireballProfile : com.prof18.klead.extractors.Extractor {
    override val id: String = "daring-fireball"
    override val domains: Set<String> = setOf("daringfireball.net")
    override val contentSelectors: List<String> = listOf("#Main .article", ".article")
    override val postContentRemoveSelectors: List<String> = listOf(
        "#PreviousNext",
        ".footnoteBackLink",
    )

    override fun extract(context: ExtractorContext): ExtractorResult? {
        val author = context.document.selectFirst("#Sidebar p strong")
            ?.text()
            ?.trim()
            ?.ifBlank { null }
            ?: return null
        return ExtractorResult(metadata = ExtractorMetadata(author = author))
    }
}
