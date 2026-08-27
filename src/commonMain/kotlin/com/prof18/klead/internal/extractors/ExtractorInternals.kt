package com.prof18.klead.internal.extractors

import com.fleeksoft.ksoup.nodes.Document
import com.fleeksoft.ksoup.nodes.Element
import com.prof18.klead.RemovalRecord
import com.prof18.klead.extractors.Extractor
import com.prof18.klead.extractors.ExtractorContext
import com.prof18.klead.extractors.ExtractorResult
import com.prof18.klead.internal.dom.parseKleadUri

internal class DomExtractorContext(val publicContext: ExtractorContext, val document: Document) {
    val url: String? get() = publicContext.url
    val host: String? get() = publicContext.host

    fun hostMatches(domains: Set<String>): Boolean = publicContext.hostMatches(domains)
}

internal fun createExtractorContext(url: String?, host: String?, document: Document): DomExtractorContext {
    val publicContext = ExtractorContext(url = url, host = host).apply {
        candidateHostsProvider = {
            buildList {
                document.select(
                    """link[rel=canonical][href], meta[property=og:url][content], meta[name=twitter:url][content]""",
                )
                    .mapNotNull { element ->
                        val candidateUrl = element.attr("href").ifBlank { element.attr("content") }
                        parseKleadUri(candidateUrl)?.host?.lowercase()
                    }
                    .forEach { candidate ->
                        if (candidate !in this) add(candidate)
                    }
            }
        }
    }
    return DomExtractorContext(publicContext = publicContext, document = document)
}

internal interface DomExtractor : Extractor {
    fun matches(context: DomExtractorContext): Boolean = matches(context.publicContext)

    fun extract(context: DomExtractorContext): ExtractorResult? = extract(context.publicContext)

    fun preProcess(content: Element, context: DomExtractorContext) = Unit

    fun postProcess(content: Element, context: DomExtractorContext, debug: MutableList<RemovalRecord>) = Unit
}
