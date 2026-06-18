package dev.defuddle.extractors

import dev.defuddle.removal.RemovalRecord
import dev.defuddle.site.AndroidAuthorityProfile
import dev.defuddle.site.ArsTechnicaProfile
import dev.defuddle.site.AxiosProfile
import dev.defuddle.site.BBCProfile
import dev.defuddle.site.BloggerProfile
import dev.defuddle.site.BusinessInsiderProfile
import dev.defuddle.site.BuzzFeedProfile
import dev.defuddle.site.CitynewsProfile
import dev.defuddle.site.EntrepreneurProfile
import dev.defuddle.site.FortuneProfile
import dev.defuddle.site.FutureProfile
import dev.defuddle.site.GameSpotProfile
import dev.defuddle.site.GamingOnLinuxProfile
import dev.defuddle.site.IlPostProfile
import dev.defuddle.site.JetBrainsBlogProfile
import dev.defuddle.site.MacRumorsProfile
import dev.defuddle.site.MashableProfile
import dev.defuddle.site.MinuteMediaSiProfile
import dev.defuddle.site.MotorsportProfile
import dev.defuddle.site.NASAProfile
import dev.defuddle.site.NineToFiveProfile
import dev.defuddle.site.PhoneArenaProfile
import dev.defuddle.site.PianetaBasketProfile
import dev.defuddle.site.PopCultureProfile
import dev.defuddle.site.RollingStoneLayoutProfile
import dev.defuddle.site.RollingStoneProfile
import dev.defuddle.site.SubstackProfile
import dev.defuddle.site.TechCrunchProfile
import dev.defuddle.site.ValnetProfile
import dev.defuddle.site.VarietyProfile
import dev.defuddle.site.VoxProfile
import dev.defuddle.site.WordPressFamilyProfile
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
    fun resolve(
        context: ExtractorContext,
        disabledExtractors: Set<String> = emptySet(),
    ): List<Extractor> =
        matchingExtractors(context, disabledExtractors)
            .sortedWith(compareByDescending<Extractor> { it.priority }.thenBy { it.id })
            .toList()

    fun extract(
        context: ExtractorContext,
        disabledExtractors: Set<String> = emptySet(),
    ): AppliedExtractor? {
        for (extractor in matchingExtractors(context, disabledExtractors)) {
            val result = extractor.extract(context)
            if (result != null) {
                return AppliedExtractor(
                    name = extractor.id,
                    result = result,
                )
            }
        }
        return null
    }

    private fun matchingExtractors(
        context: ExtractorContext,
        disabledExtractors: Set<String>,
    ): Sequence<Extractor> =
        extractors
            .asSequence()
            .filterNot { it.id in disabledExtractors }
            .filter { it.matches(context) }
}

object DefaultExtractors {
    val all: List<Extractor> = listOf(
        WikipediaExtractor,
        MotorsportProfile,
        MinuteMediaSiProfile,
        PhoneArenaProfile,
        AndroidAuthorityProfile,
        RollingStoneProfile,
        PopCultureProfile,
        ValnetProfile,
        VarietyProfile,
        GameSpotProfile,
        GamingOnLinuxProfile,
        AxiosProfile,
        BusinessInsiderProfile,
        MashableProfile,
        BBCProfile,
        BuzzFeedProfile,
        FortuneProfile,
        EntrepreneurProfile,
        FutureProfile,
        ArsTechnicaProfile,
        RollingStoneLayoutProfile,
        BloggerProfile,
        JetBrainsBlogProfile,
        IlPostProfile,
        SubstackProfile,
        CitynewsProfile,
        TechCrunchProfile,
        VoxProfile,
        PianetaBasketProfile,
        MacRumorsProfile,
        NASAProfile,
        NineToFiveProfile,
        WordPressFamilyProfile,
    )
}
