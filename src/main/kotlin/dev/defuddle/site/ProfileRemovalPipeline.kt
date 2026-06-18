package dev.defuddle.site

import dev.defuddle.dom.removeSafely
import dev.defuddle.dom.selectSafe
import dev.defuddle.removal.RemovalRecord
import org.jsoup.nodes.Element

object ProfileRemovalPipeline {
    fun applyPreContentRemovals(
        root: Element,
        profiles: List<SiteExtractor>,
        debug: MutableList<RemovalRecord>,
    ) {
        apply(root, profiles, debug, SelectorPhase.PreContent)
    }

    fun applyPostContentRemovals(
        content: Element,
        profiles: List<SiteExtractor>,
        debug: MutableList<RemovalRecord>,
    ) {
        apply(content, profiles, debug, SelectorPhase.PostContent)
    }

    private fun apply(
        root: Element,
        profiles: List<SiteExtractor>,
        debug: MutableList<RemovalRecord>,
        phase: SelectorPhase,
    ) {
        for (profile in profiles) {
            for (selector in phase.selectors(profile)) {
                for (element in root.selectSafe(selector).toList()) {
                    debug += RemovalRecord(
                        step = "${phase.step}:${profile.id}",
                        selector = selector,
                        reason = "site profile clutter selector",
                        preview = element.text().take(100),
                    )
                    element.removeSafely()
                }
            }
        }
    }

    private enum class SelectorPhase(
        val step: String,
        val selectors: (SiteExtractor) -> List<String>,
    ) {
        PreContent("removeSitePreContentSelectors", SiteExtractor::preContentRemoveSelectors),
        PostContent("removeSiteSelectors", SiteExtractor::postContentRemoveSelectors),
    }
}
