package com.prof18.klead.extractors

import com.fleeksoft.ksoup.nodes.Document
import com.fleeksoft.ksoup.nodes.Element
import com.prof18.klead.RemovalRecord
import java.net.URI

interface Extractor {
    val id: String
    val domains: Set<String> get() = emptySet()
    val priority: Int get() = 0

    fun matches(context: ExtractorContext): Boolean = domains.isNotEmpty() && context.hostMatches(domains)

    val contentSelectors: List<String> get() = emptyList()
    val preContentRemoveSelectors: List<String> get() = emptyList()
    val postContentRemoveSelectors: List<String> get() = emptyList()

    fun extract(context: ExtractorContext): ExtractorResult? = null

    fun postProcess(content: Element, context: ExtractorContext, debug: MutableList<RemovalRecord>) = Unit
}

data class ExtractorContext(val url: String?, val host: String?, val document: Document) {
    private val normalizedHost: String? = host.normalizedHost()

    private val candidateHosts: List<String> by lazy(LazyThreadSafetyMode.NONE) {
        buildList {
            normalizedHost?.let(::add)
            document.select(
                """link[rel=canonical][href], meta[property=og:url][content], meta[name=twitter:url][content]""",
            )
                .mapNotNull { element ->
                    val candidateUrl = element.attr("href").ifBlank { element.attr("content") }
                    runCatching { URI(candidateUrl).host?.lowercase() }.getOrNull()
                }
                .forEach { candidate ->
                    if (candidate !in this) add(candidate)
                }
        }
    }

    fun hostMatches(domains: Set<String>): Boolean {
        val normalizedDomains = domains.mapNotNull { it.normalizedHost() }
        if (normalizedDomains.isEmpty()) return false
        if (normalizedHost != null && normalizedHost.matchesDomain(normalizedDomains)) return true

        if (candidateHosts.isEmpty()) return false
        return candidateHosts.any { candidate -> candidate.matchesDomain(normalizedDomains) }
    }

    private fun String?.normalizedHost(): String? = this
        ?.lowercase()
        ?.trim()
        ?.trim('.')
        ?.takeIf { it.isNotBlank() }

    private fun String.matchesDomain(domains: List<String>): Boolean =
        domains.any { domain -> this == domain || endsWith(".$domain") }
}

data class ExtractorResult(
    val contentHtml: String? = null,
    val contentSelector: String? = null,
    val metadata: ExtractorMetadata = ExtractorMetadata(),
)

data class ExtractorMetadata(
    val title: String? = null,
    val author: String? = null,
    val site: String? = null,
    val description: String? = null,
)
