package dev.defuddle.extractors

import dev.defuddle.extractors.site.WikipediaExtractor
import dev.defuddle.removal.RemovalRecord
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.net.URI

interface Extractor {
    val id: String
    val domains: Set<String> get() = emptySet()
    val priority: Int get() = 0

    fun matches(context: ExtractorContext): Boolean =
        domains.isNotEmpty() && context.hostMatches(domains)

    val contentSelectors: List<String> get() = emptyList()
    val preContentRemoveSelectors: List<String> get() = emptyList()
    val postContentRemoveSelectors: List<String> get() = emptyList()

    val titleSelectors: List<String> get() = emptyList()
    val authorSelectors: List<String> get() = emptyList()
    val dateSelectors: List<String> get() = emptyList()

    fun extract(context: ExtractorContext): ExtractorResult? = null

    fun postProcess(
        content: Element,
        context: ExtractorContext,
        debug: MutableList<RemovalRecord>,
    ) = Unit
}

data class ExtractorContext(
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
    val variables: Map<String, String> = emptyMap(),
)

data class ExtractorMetadata(
    val title: String? = null,
    val author: String? = null,
    val site: String? = null,
    val description: String? = null,
)

data class AppliedExtractor(
    val result: ExtractorResult,
)

class ExtractorRegistry(
    private val extractors: List<Extractor> = DefaultExtractors.all,
) {
    fun resolve(context: ExtractorContext): List<Extractor> =
        matchingExtractors(context)
            .sortedWith(compareByDescending<Extractor> { it.priority }.thenBy { it.id })
            .toList()

    fun extract(context: ExtractorContext): AppliedExtractor? {
        for (extractor in matchingExtractors(context)) {
            val result = extractor.extract(context)
            if (result != null) {
                return AppliedExtractor(
                    result = result,
                )
            }
        }
        return null
    }

    private fun matchingExtractors(context: ExtractorContext): Sequence<Extractor> =
        extractors
            .asSequence()
            .filter { it.matches(context) }
}

object DefaultExtractors {
    val all: List<Extractor> = listOf(
        WikipediaExtractor,
        _root_ide_package_.dev.defuddle.extractors.site.MotorsportProfile,
        _root_ide_package_.dev.defuddle.extractors.site.MinuteMediaSiProfile,
        _root_ide_package_.dev.defuddle.extractors.site.PhoneArenaProfile,
        _root_ide_package_.dev.defuddle.extractors.site.AndroidAuthorityProfile,
        _root_ide_package_.dev.defuddle.extractors.site.RollingStoneProfile,
        _root_ide_package_.dev.defuddle.extractors.site.PopCultureProfile,
        _root_ide_package_.dev.defuddle.extractors.site.ValnetProfile,
        _root_ide_package_.dev.defuddle.extractors.site.VarietyProfile,
        _root_ide_package_.dev.defuddle.extractors.site.GameSpotProfile,
        _root_ide_package_.dev.defuddle.extractors.site.GamingOnLinuxProfile,
        _root_ide_package_.dev.defuddle.extractors.site.AxiosProfile,
        _root_ide_package_.dev.defuddle.extractors.site.BusinessInsiderProfile,
        _root_ide_package_.dev.defuddle.extractors.site.MashableProfile,
        _root_ide_package_.dev.defuddle.extractors.site.BBCProfile,
        _root_ide_package_.dev.defuddle.extractors.site.BuzzFeedProfile,
        _root_ide_package_.dev.defuddle.extractors.site.FortuneProfile,
        _root_ide_package_.dev.defuddle.extractors.site.EntrepreneurProfile,
        _root_ide_package_.dev.defuddle.extractors.site.FutureProfile,
        _root_ide_package_.dev.defuddle.extractors.site.ArsTechnicaProfile,
        _root_ide_package_.dev.defuddle.extractors.site.RollingStoneLayoutProfile,
        _root_ide_package_.dev.defuddle.extractors.site.BloggerProfile,
        _root_ide_package_.dev.defuddle.extractors.site.JetBrainsBlogProfile,
        _root_ide_package_.dev.defuddle.extractors.site.IlPostProfile,
        _root_ide_package_.dev.defuddle.extractors.site.SubstackProfile,
        _root_ide_package_.dev.defuddle.extractors.site.CitynewsProfile,
        _root_ide_package_.dev.defuddle.extractors.site.TechCrunchProfile,
        _root_ide_package_.dev.defuddle.extractors.site.VoxProfile,
        _root_ide_package_.dev.defuddle.extractors.site.PianetaBasketProfile,
        _root_ide_package_.dev.defuddle.extractors.site.MacRumorsProfile,
        _root_ide_package_.dev.defuddle.extractors.site.NASAProfile,
        _root_ide_package_.dev.defuddle.extractors.site.NineToFiveProfile,
        _root_ide_package_.dev.defuddle.extractors.site.WordPressFamilyProfile,
    )
}
