package com.prof18.klead.internal.extractors

import com.prof18.klead.extractors.Extractor
import com.prof18.klead.extractors.ExtractorResult
import com.prof18.klead.internal.extractors.site.AndroidAuthorityProfile
import com.prof18.klead.internal.extractors.site.AndroidPoliceProfile
import com.prof18.klead.internal.extractors.site.ArmNewsroomProfile
import com.prof18.klead.internal.extractors.site.ArsTechnicaProfile
import com.prof18.klead.internal.extractors.site.AxiosProfile
import com.prof18.klead.internal.extractors.site.BBCProfile
import com.prof18.klead.internal.extractors.site.BeehiivProfile
import com.prof18.klead.internal.extractors.site.BloggerProfile
import com.prof18.klead.internal.extractors.site.BusinessInsiderProfile
import com.prof18.klead.internal.extractors.site.BuzzFeedProfile
import com.prof18.klead.internal.extractors.site.ChatGptExtractor
import com.prof18.klead.internal.extractors.site.CitynewsProfile
import com.prof18.klead.internal.extractors.site.DagelijkseStandaardProfile
import com.prof18.klead.internal.extractors.site.DaringFireballProfile
import com.prof18.klead.internal.extractors.site.DwProfile
import com.prof18.klead.internal.extractors.site.ElementorArchiveProfile
import com.prof18.klead.internal.extractors.site.EntrepreneurProfile
import com.prof18.klead.internal.extractors.site.FortuneProfile
import com.prof18.klead.internal.extractors.site.FutureProfile
import com.prof18.klead.internal.extractors.site.GameSpotProfile
import com.prof18.klead.internal.extractors.site.GamingOnLinuxProfile
import com.prof18.klead.internal.extractors.site.GitHubProfile
import com.prof18.klead.internal.extractors.site.GuardianProfile
import com.prof18.klead.internal.extractors.site.HackerNewsProfile
import com.prof18.klead.internal.extractors.site.IlPostProfile
import com.prof18.klead.internal.extractors.site.JetBrainsBlogProfile
import com.prof18.klead.internal.extractors.site.KarakartalProfile
import com.prof18.klead.internal.extractors.site.KurucInfoProfile
import com.prof18.klead.internal.extractors.site.LessWrongProfile
import com.prof18.klead.internal.extractors.site.MacRumorsProfile
import com.prof18.klead.internal.extractors.site.MacStoriesProfile
import com.prof18.klead.internal.extractors.site.MaggieAppletonProfile
import com.prof18.klead.internal.extractors.site.MashableProfile
import com.prof18.klead.internal.extractors.site.MastodonProfile
import com.prof18.klead.internal.extractors.site.MinuteMediaSiProfile
import com.prof18.klead.internal.extractors.site.MotorsportProfile
import com.prof18.klead.internal.extractors.site.NASAProfile
import com.prof18.klead.internal.extractors.site.NineToFiveProfile
import com.prof18.klead.internal.extractors.site.NprProfile
import com.prof18.klead.internal.extractors.site.ObsidianPublishProfile
import com.prof18.klead.internal.extractors.site.OpenNetProfile
import com.prof18.klead.internal.extractors.site.PhoneArenaProfile
import com.prof18.klead.internal.extractors.site.PhysOrgProfile
import com.prof18.klead.internal.extractors.site.PianetaBasketProfile
import com.prof18.klead.internal.extractors.site.PopCultureProfile
import com.prof18.klead.internal.extractors.site.RedditProfile
import com.prof18.klead.internal.extractors.site.RollingStoneLayoutProfile
import com.prof18.klead.internal.extractors.site.RollingStoneProfile
import com.prof18.klead.internal.extractors.site.ScpWikiProfile
import com.prof18.klead.internal.extractors.site.SimonWillisonProfile
import com.prof18.klead.internal.extractors.site.SocketProfile
import com.prof18.klead.internal.extractors.site.StatistaProfile
import com.prof18.klead.internal.extractors.site.SteamPartnerEventExtractor
import com.prof18.klead.internal.extractors.site.StripeDocsProfile
import com.prof18.klead.internal.extractors.site.SubstackProfile
import com.prof18.klead.internal.extractors.site.TechCrunchProfile
import com.prof18.klead.internal.extractors.site.ValnetProfile
import com.prof18.klead.internal.extractors.site.VarietyProfile
import com.prof18.klead.internal.extractors.site.VoxProfile
import com.prof18.klead.internal.extractors.site.WallStreetJournalExtractor
import com.prof18.klead.internal.extractors.site.WikipediaExtractor
import com.prof18.klead.internal.extractors.site.WordPressFamilyProfile
import com.prof18.klead.internal.extractors.site.XProfile

internal class ExtractorRegistry(private val extractors: List<Extractor> = DefaultExtractors.all) {
    fun resolve(context: DomExtractorContext): List<Extractor> = matchingExtractors(context)
        .sortedWith(compareByDescending<Extractor> { it.priority }.thenBy { it.id })
        .toList()

    fun extract(context: DomExtractorContext): ExtractorResult? = extract(context, resolve(context))

    fun extract(context: DomExtractorContext, extractors: List<Extractor>): ExtractorResult? {
        for (extractor in extractors) {
            val result = if (extractor is DomExtractor) {
                extractor.extract(context)
            } else {
                extractor.extract(context.publicContext)
            }
            if (result != null) {
                return result
            }
        }
        return null
    }

    private fun matchingExtractors(context: DomExtractorContext): Sequence<Extractor> = extractors
        .asSequence()
        .filter { extractor ->
            if (extractor is DomExtractor) {
                extractor.matches(context)
            } else {
                extractor.matches(context.publicContext)
            }
        }
}

internal object DefaultExtractors {
    val all: List<Extractor> = listOf(
        WikipediaExtractor,
        MotorsportProfile,
        MinuteMediaSiProfile,
        PhoneArenaProfile,
        PhysOrgProfile,
        ArmNewsroomProfile,
        AndroidAuthorityProfile,
        AndroidPoliceProfile,
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
        NprProfile,
        BuzzFeedProfile,
        FortuneProfile,
        EntrepreneurProfile,
        FutureProfile,
        ArsTechnicaProfile,
        RollingStoneLayoutProfile,
        ChatGptExtractor,
        GitHubProfile,
        GuardianProfile,
        XProfile,
        HackerNewsProfile,
        MastodonProfile,
        RedditProfile,
        StripeDocsProfile,
        SteamPartnerEventExtractor,
        StatistaProfile,
        WallStreetJournalExtractor,
        ObsidianPublishProfile,
        OpenNetProfile,
        SocketProfile,
        ElementorArchiveProfile,
        ScpWikiProfile,
        SimonWillisonProfile,
        LessWrongProfile,
        MaggieAppletonProfile,
        BloggerProfile,
        JetBrainsBlogProfile,
        KarakartalProfile,
        KurucInfoProfile,
        IlPostProfile,
        DaringFireballProfile,
        DwProfile,
        SubstackProfile,
        BeehiivProfile,
        CitynewsProfile,
        DagelijkseStandaardProfile,
        TechCrunchProfile,
        VoxProfile,
        PianetaBasketProfile,
        MacRumorsProfile,
        MacStoriesProfile,
        NASAProfile,
        NineToFiveProfile,
        WordPressFamilyProfile,
    )
}
