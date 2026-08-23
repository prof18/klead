package com.prof18.klead.internal.extractors.site

import com.fleeksoft.ksoup.nodes.Element
import com.prof18.klead.RemovalRecord
import com.prof18.klead.extractors.Extractor
import com.prof18.klead.extractors.ExtractorContext
import com.prof18.klead.extractors.ExtractorMetadata
import com.prof18.klead.extractors.ExtractorResult
import com.prof18.klead.internal.dom.isAttachedTo
import com.prof18.klead.internal.extractors.ExtractorPostProcessor
import com.prof18.klead.internal.extractors.document
import com.prof18.klead.internal.removal.recordAndRemove

internal object ElementorArchiveProfile : Extractor, ExtractorPostProcessor {
    override val id: String = "elementor-archive"
    override val contentSelectors: List<String> = listOf(
        """[data-elementor-type="archive"].elementor-location-archive""",
    )
    override val postContentRemoveSelectors: List<String> = listOf(
        ".elementor-widget-jet-ajax-search",
        ".elementor-widget-jet-engine-maps-listing",
        ".elementor-widget-jet-listing-grid",
    )

    override fun matches(context: ExtractorContext): Boolean =
        context.document.body()?.hasClass("elementor-default") == true &&
            context.document.selectFirst("""[data-elementor-type="archive"].elementor-location-archive""") != null

    override fun extract(context: ExtractorContext): ExtractorResult? {
        val content = context.document.selectFirst("""[data-elementor-type="archive"].elementor-location-archive""")
            ?: return null
        return ExtractorResult(
            contentHtml = content.outerHtml(),
            contentSelector = contentSelectors.first(),
            metadata = ExtractorMetadata(title = context.document.title().ifBlank { null }),
        )
    }

    override fun postProcess(content: Element, context: ExtractorContext, debug: MutableList<RemovalRecord>) {
        content.select(".e-con").toList()
            .filter { it.isAttachedTo(content) }
            .filter { it.isOrphanElementorHeadingContainer() }
            .forEach { element ->
                recordAndRemove(
                    element = element,
                    debug = debug,
                    step = "removeElementorOrphanHeading",
                    selector = ".e-con",
                    reason = "heading-only Elementor container after widget cleanup",
                )
            }
    }

    private fun Element.isOrphanElementorHeadingContainer(): Boolean {
        val heading = select("h1, h2, h3, h4, h5, h6").singleOrNull() ?: return false
        if (heading.normalName() == "h1") return false

        val clone = clone()
        clone.select("h1, h2, h3, h4, h5, h6").remove()
        clone.select("br").remove()
        return clone.text().isBlank()
    }
}
