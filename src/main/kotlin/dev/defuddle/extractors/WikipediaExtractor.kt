package dev.defuddle.extractors

import java.net.URI

object WikipediaExtractor : Extractor {
    override val id: String = "wikipedia"

    override fun matches(context: ExtractorContext): Boolean =
        runCatching { URI(context.url.orEmpty()).host.orEmpty().contains("wikipedia.org") }.getOrDefault(false) &&
            context.document.selectFirst("#mw-content-text") != null

    override suspend fun extract(context: ExtractorContext): ExtractorResult =
        ExtractorResult(
            contentSelector = "#mw-content-text",
            metadata = ExtractorMetadata(site = "Wikipedia"),
        )
}
