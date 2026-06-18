package com.prof18.klead.internal.extractors.site

import com.prof18.klead.extractors.Extractor
import com.prof18.klead.extractors.ExtractorContext
import com.prof18.klead.extractors.ExtractorMetadata
import com.prof18.klead.extractors.ExtractorResult
import java.net.URI

internal object WikipediaExtractor : Extractor {
    override val id: String = "wikipedia"

    override fun matches(context: ExtractorContext): Boolean =
        runCatching { URI(context.url.orEmpty()).host.orEmpty().contains("wikipedia.org") }.getOrDefault(false) &&
            context.document.selectFirst("#mw-content-text") != null

    override fun extract(context: ExtractorContext): ExtractorResult = ExtractorResult(
        contentSelector = "#mw-content-text",
        metadata = ExtractorMetadata(site = "Wikipedia"),
    )
}
