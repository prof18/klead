package dev.defuddle.site

import dev.defuddle.removal.RemovalRecord
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.net.URI

interface SiteExtractor {
    val id: String
    val domains: Set<String>
    val priority: Int get() = 0

    fun matches(context: SiteExtractionContext): Boolean =
        context.hostMatches(domains)

    val contentSelectors: List<String> get() = emptyList()
    val preContentRemoveSelectors: List<String> get() = emptyList()
    val postContentRemoveSelectors: List<String> get() = emptyList()

    val titleSelectors: List<String> get() = emptyList()
    val authorSelectors: List<String> get() = emptyList()
    val dateSelectors: List<String> get() = emptyList()

    fun postProcess(
        content: Element,
        context: SiteExtractionContext,
        debug: MutableList<RemovalRecord>,
    ) = Unit
}

data class SiteExtractionContext(
    val url: String?,
    val host: String?,
    val document: Document,
) {
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
        document.select("""link[rel=canonical][href], meta[property=og:url][content], meta[name=twitter:url][content]""")
            .mapNotNull { element ->
                val url = element.attr("href").ifBlank { element.attr("content") }
                runCatching { URI(url).host?.lowercase() }.getOrNull()
            }
            .forEach { candidate ->
                if (candidate !in result) result.add(candidate)
            }
        return result
    }
}

class SiteExtractorRegistry(
    private val extractors: List<SiteExtractor> = DefaultSiteExtractors.all,
) {
    fun resolve(context: SiteExtractionContext): List<SiteExtractor> =
        extractors
            .asSequence()
            .filter { it.matches(context) }
            .sortedWith(compareByDescending<SiteExtractor> { it.priority }.thenBy { it.id })
            .toList()
}
