package dev.defuddle.extractors

import org.jsoup.nodes.Document
import java.net.URI

object WikipediaExtractor : Extractor {
    override val name: String = "wikipedia"

    override fun canExtract(
        document: Document,
        url: String,
    ): Boolean =
        runCatching { URI(url).host.orEmpty().contains("wikipedia.org") }.getOrDefault(false) &&
            document.selectFirst("#mw-content-text") != null

    override fun extract(
        document: Document,
        url: String,
        context: ExtractorContext,
    ): ExtractorResult =
        ExtractorResult(
            contentSelector = "#mw-content-text",
            metadata = ExtractorMetadata(site = "Wikipedia"),
        )
}
