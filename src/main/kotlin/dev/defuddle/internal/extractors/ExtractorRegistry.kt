package dev.defuddle.internal.extractors

import dev.defuddle.extractors.Extractor
import dev.defuddle.extractors.ExtractorContext
import dev.defuddle.extractors.ExtractorResult
import dev.defuddle.internal.extractors.site.AndroidAuthorityProfile
import dev.defuddle.internal.extractors.site.ArsTechnicaProfile
import dev.defuddle.internal.extractors.site.AxiosProfile
import dev.defuddle.internal.extractors.site.BBCProfile
import dev.defuddle.internal.extractors.site.BloggerProfile
import dev.defuddle.internal.extractors.site.BusinessInsiderProfile
import dev.defuddle.internal.extractors.site.BuzzFeedProfile
import dev.defuddle.internal.extractors.site.CitynewsProfile
import dev.defuddle.internal.extractors.site.EntrepreneurProfile
import dev.defuddle.internal.extractors.site.FortuneProfile
import dev.defuddle.internal.extractors.site.FutureProfile
import dev.defuddle.internal.extractors.site.GameSpotProfile
import dev.defuddle.internal.extractors.site.GamingOnLinuxProfile
import dev.defuddle.internal.extractors.site.IlPostProfile
import dev.defuddle.internal.extractors.site.JetBrainsBlogProfile
import dev.defuddle.internal.extractors.site.MacRumorsProfile
import dev.defuddle.internal.extractors.site.MashableProfile
import dev.defuddle.internal.extractors.site.MinuteMediaSiProfile
import dev.defuddle.internal.extractors.site.MotorsportProfile
import dev.defuddle.internal.extractors.site.NASAProfile
import dev.defuddle.internal.extractors.site.NineToFiveProfile
import dev.defuddle.internal.extractors.site.PhoneArenaProfile
import dev.defuddle.internal.extractors.site.PianetaBasketProfile
import dev.defuddle.internal.extractors.site.PopCultureProfile
import dev.defuddle.internal.extractors.site.RollingStoneLayoutProfile
import dev.defuddle.internal.extractors.site.RollingStoneProfile
import dev.defuddle.internal.extractors.site.SubstackProfile
import dev.defuddle.internal.extractors.site.TechCrunchProfile
import dev.defuddle.internal.extractors.site.ValnetProfile
import dev.defuddle.internal.extractors.site.VarietyProfile
import dev.defuddle.internal.extractors.site.VoxProfile
import dev.defuddle.internal.extractors.site.WikipediaExtractor
import dev.defuddle.internal.extractors.site.WordPressFamilyProfile

internal class ExtractorRegistry(private val extractors: List<Extractor> = DefaultExtractors.all) {
    fun resolve(context: ExtractorContext): List<Extractor> = matchingExtractors(context)
        .sortedWith(compareByDescending<Extractor> { it.priority }.thenBy { it.id })
        .toList()

    fun extract(context: ExtractorContext): ExtractorResult? {
        for (extractor in resolve(context)) {
            val result = extractor.extract(context)
            if (result != null) {
                return result
            }
        }
        return null
    }

    private fun matchingExtractors(context: ExtractorContext): Sequence<Extractor> = extractors
        .asSequence()
        .filter { it.matches(context) }
}

internal object DefaultExtractors {
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
