package com.prof18.klead.internal.extractors

import com.fleeksoft.ksoup.nodes.Document
import com.fleeksoft.ksoup.nodes.Element
import com.prof18.klead.RemovalRecord
import com.prof18.klead.extractors.Extractor
import com.prof18.klead.extractors.ExtractorContext
import com.prof18.klead.internal.dom.parseKleadUri

internal val ExtractorContext.document: Document
    get() = documentSource as Document

internal fun createExtractorContext(url: String?, host: String?, document: Document): ExtractorContext =
    ExtractorContext(
        url = url,
        host = host,
        documentSource = document,
        candidateHosts = {
            buildList {
                host.normalizedHost()?.let(::add)
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
        },
    )

internal interface ExtractorPostProcessor {
    fun postProcess(content: Element, context: ExtractorContext, debug: MutableList<RemovalRecord>)
}

internal fun Extractor.postProcess(content: Element, context: ExtractorContext, debug: MutableList<RemovalRecord>) {
    (this as? ExtractorPostProcessor)?.postProcess(content, context, debug)
}

private fun String?.normalizedHost(): String? = this
    ?.lowercase()
    ?.trim()
    ?.trim('.')
    ?.takeIf { it.isNotBlank() }
