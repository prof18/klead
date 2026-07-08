package com.prof18.klead.internal.extractors.site

import com.prof18.klead.RemovalRecord
import com.prof18.klead.extractors.Extractor
import com.prof18.klead.internal.dom.selectSafe
import com.prof18.klead.internal.removal.recordAndRemove
import org.jsoup.nodes.Element

internal object ExtractorRemovalPipeline {
    fun applyPreContentRemovals(root: Element, extractors: List<Extractor>, debug: MutableList<RemovalRecord>) {
        apply(root, extractors, debug, SelectorPhase.PreContent)
    }

    fun applyPostContentRemovals(content: Element, extractors: List<Extractor>, debug: MutableList<RemovalRecord>) {
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
                    recordAndRemove(
                        element = element,
                        debug = debug,
                        step = "${phase.step}:${extractor.id}",
                        selector = selector,
                        reason = "extractor-scoped clutter selector",
                    )
                }
            }
        }
    }

    private enum class SelectorPhase(val step: String, val selectors: (Extractor) -> List<String>) {
        PreContent("removeExtractorPreContentSelectors", Extractor::preContentRemoveSelectors),
        PostContent("removeExtractorSelectors", Extractor::postContentRemoveSelectors),
    }
}
