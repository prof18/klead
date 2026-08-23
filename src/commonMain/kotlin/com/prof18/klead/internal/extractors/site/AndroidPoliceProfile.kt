package com.prof18.klead.internal.extractors.site

import com.fleeksoft.ksoup.nodes.Document
import com.fleeksoft.ksoup.nodes.Element
import com.prof18.klead.RemovalRecord
import com.prof18.klead.extractors.Extractor
import com.prof18.klead.extractors.ExtractorContext
import com.prof18.klead.internal.extractors.ExtractorPostProcessor
import com.prof18.klead.internal.extractors.document

internal object AndroidPoliceProfile : Extractor, ExtractorPostProcessor {
    override val id: String = "android-police"
    override val domains: Set<String> = setOf("androidpolice.com")
    override val contentSelectors: List<String> = listOf("#article-body", ".article-body")

    override fun postProcess(content: Element, context: ExtractorContext, debug: MutableList<RemovalRecord>) {
        prependFeatureImage(content, context.document)
    }

    private fun prependFeatureImage(content: Element, document: Document) {
        val figure = document.selectFirst(".heading_image[data-is-feature-img=true] figure")
            ?: document.selectFirst(".heading_image figure")
            ?: return
        val image = figure.selectFirst("img[src], img[data-img-url]") ?: return
        val imageKey = image.androidPoliceImageKey() ?: return
        if (content.select("img[src], img[data-img-url]").any { it.androidPoliceImageKey() == imageKey }) return

        val clone = figure.clone()
        clone.select("source").remove()
        content.prependChild(clone)
    }

    private fun Element.androidPoliceImageKey(): String? {
        val source = attr("data-img-url").ifBlank { absUrl("data-img-url") }
            .ifBlank { attr("src") }
            .ifBlank { absUrl("src") }
            .ifBlank { null }
            ?: return null
        return source.substringBefore('#')
            .substringBefore('?')
            .trimEnd('/')
            .substringAfterLast('/')
            .lowercase()
            .ifBlank { null }
    }
}
