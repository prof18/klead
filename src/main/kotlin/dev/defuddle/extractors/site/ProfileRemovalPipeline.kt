package dev.defuddle.extractors.site

import dev.defuddle.dom.removeSafely
import dev.defuddle.dom.selectSafe
import dev.defuddle.extractors.Extractor
import dev.defuddle.removal.RemovalRecord
import org.jsoup.nodes.Element

internal object ExtractorRemovalPipeline {
    fun applyPreContentRemovals(
        root: Element,
        extractors: List<Extractor>,
        debug: MutableList<RemovalRecord>,
    ) {
        apply(root, extractors, debug, SelectorPhase.PreContent)
    }

    fun applyPostContentRemovals(
        content: Element,
        extractors: List<Extractor>,
        debug: MutableList<RemovalRecord>,
    ) {
        apply(content, extractors, debug, SelectorPhase.PostContent)
    }

    private fun apply(
        root: Element,
        extractors: List<Extractor>,
        debug: MutableList<RemovalRecord>,
        phase: SelectorPhase,
    ) {
        for (extractor in extractors) {
            for (selector in phase.selectors(extractor)) {
                for (element in root.selectSafe(selector).toList()) {
                    debug += RemovalRecord(
                        step = "${phase.step}:${extractor.id}",
                        selector = selector,
                        reason = "extractor-scoped clutter selector",
                        preview = element.text().take(100),
                    )
                    element.removeSafely()
                }
            }
        }
    }

    private enum class SelectorPhase(
        val step: String,
        val selectors: (Extractor) -> List<String>,
    ) {
        PreContent("removeExtractorPreContentSelectors", Extractor::preContentRemoveSelectors),
        PostContent("removeExtractorSelectors", Extractor::postContentRemoveSelectors),
    }
}
