package com.prof18.klead.internal.extractors.site

import com.fleeksoft.ksoup.nodes.Element
import com.prof18.klead.internal.extractors.DomExtractor
import com.prof18.klead.internal.extractors.DomExtractorContext

internal object NprProfile : DomExtractor {
    override val id: String = "npr"
    override val domains: Set<String> = setOf("npr.org")

    override fun preProcess(content: Element, context: DomExtractorContext) {
        normalizeImageCaptions(content)
        normalizeRelatedStoryCallouts(content)
    }

    private fun normalizeImageCaptions(content: Element) {
        content.select("div.bucketwrap.image").forEach { wrapper ->
            if (wrapper.selectFirst("img, picture") == null) return@forEach
            val creditCaption = wrapper.children().firstOrNull { it.hasClass("credit-caption") }
                ?: return@forEach

            creditCaption.select(".hide-caption, .toggle-caption").remove()
            if (creditCaption.children().any { it.normalName() == "span" && it.hasClass("credit") }) {
                creditCaption.select(".caption-wrap .credit").remove()
            }
            creditCaption.select(".caption-wrap").toList().forEach { captionWrapper ->
                if (captionWrapper.text().isBlank()) captionWrapper.remove()
            }
            wrapper.tagName("figure")
            creditCaption.tagName("figcaption")
        }
    }

    private fun normalizeRelatedStoryCallouts(content: Element) {
        content.select("div.bucketwrap.internallink.insettwocolumn, div.bucketwrap.internallink.inset2col")
            .forEach { relatedStory ->
                if (relatedStory.selectFirst("a[href]") == null) return@forEach
                if (relatedStory.previousElementSibling()?.normalName() != "p") return@forEach
                if (relatedStory.nextElementSibling()?.normalName() != "p") return@forEach
                relatedStory.tagName("aside")
                relatedStory.addClass("callout")
                relatedStory.attr("data-callout", "note")

                val calloutContent = relatedStory.children().firstOrNull { it.hasClass("bucket") }
                    ?: return@forEach
                calloutContent.addClass("callout-content")
                calloutContent.children().firstOrNull { it.hasClass("imagewrap") }
                    ?.addClass("callout-media")
                calloutContent.children().firstOrNull { it.hasClass("bucketblock") }
                    ?.let { calloutBody ->
                        calloutBody.addClass("callout-body")
                        calloutBody.select("h3.slug").addClass("callout-label")
                        calloutBody.children().firstOrNull {
                            it.normalName() == "h3" && !it.hasClass("slug")
                        }?.addClass("callout-title")
                    }
            }
    }
}
