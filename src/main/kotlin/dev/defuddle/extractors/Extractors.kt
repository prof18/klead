package dev.defuddle.extractors

import dev.defuddle.RemovalRecord
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
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
    fun hostMatches(domains: Set<String>): Boolean {
        val normalizedHosts = candidateHosts().map { it.lowercase().trim('.') }
        if (normalizedHosts.isEmpty()) return false
        return normalizedHosts.any { normalizedHost ->
            domains.any { domain ->
                val normalizedDomain = domain.lowercase().trim().trim('.')
                normalizedHost == normalizedDomain || normalizedHost.endsWith(".$normalizedDomain")
            }
        }
    }

    private fun candidateHosts(): List<String> {
        val result = mutableListOf<String>()
        host?.takeIf { it.isNotBlank() }?.let(result::add)
        document.select(
            """link[rel=canonical][href], meta[property=og:url][content], meta[name=twitter:url][content]""",
        )
            .mapNotNull { element ->
                val candidateUrl = element.attr("href").ifBlank { element.attr("content") }
                runCatching { URI(candidateUrl).host?.lowercase() }.getOrNull()
            }
            .forEach { candidate ->
                if (candidate !in result) result.add(candidate)
            }
        return result
    }
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
