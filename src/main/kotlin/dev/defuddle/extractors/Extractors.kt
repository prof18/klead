package dev.defuddle.extractors

import org.jsoup.nodes.Document

interface DefuddleHttpClient {
    fun get(url: String): String
}

interface Extractor {
    val name: String

    fun canExtract(
        document: Document,
        url: String,
    ): Boolean

    fun extract(
        document: Document,
        url: String,
        context: ExtractorContext,
    ): ExtractorResult
}

data class ExtractorContext(
    val httpClient: DefuddleHttpClient? = null,
    val disabledExtractors: Set<String> = emptySet(),
)

data class ExtractorResult(
    val contentHtml: String? = null,
    val contentSelector: String? = null,
    val metadata: ExtractorMetadata = ExtractorMetadata(),
    val variables: Map<String, String> = emptyMap(),
)

data class ExtractorMetadata(
    val title: String? = null,
    val author: String? = null,
    val published: String? = null,
    val site: String? = null,
    val description: String? = null,
)

data class AppliedExtractor(
    val name: String,
    val result: ExtractorResult,
)

class ExtractorRegistry(
    private val extractors: List<Extractor> = DefaultExtractors.all,
) {
    fun extract(
        document: Document,
        url: String,
        context: ExtractorContext,
    ): AppliedExtractor? {
        for (extractor in extractors) {
            if (extractor.name in context.disabledExtractors) continue
            if (extractor.canExtract(document, url)) {
                return AppliedExtractor(
                    name = extractor.name,
                    result = extractor.extract(document, url, context),
                )
            }
        }
        return null
    }
}

object DefaultExtractors {
    val all: List<Extractor> = listOf(WikipediaExtractor)
}
