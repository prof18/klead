package com.prof18.klead.internal.extractors.site

import com.fleeksoft.ksoup.nodes.Element
import com.prof18.klead.RemovalRecord
import com.prof18.klead.internal.extractors.DomExtractor
import com.prof18.klead.internal.extractors.DomExtractorContext

internal object Motor1Profile : DomExtractor {
    override val id: String = "motor1"
    override val domains: Set<String> = setOf("motor1.com")
    override val postContentRemoveSelectors: List<String> = listOf(
        ".msnt-photo-thumb-gallery-count",
    )

    override fun postProcess(content: Element, context: DomExtractorContext, debug: MutableList<RemovalRecord>) {
        content.select(".msnt-photo-thumb-gallery-photos-list").forEach { list ->
            val photos = list.children().mapNotNull { photo ->
                val imageUrl = photo.selectFirst("meta[itemprop=contentUrl]")?.attr("content")
                    ?.takeIf { it.startsWith("https://cdn.motor1.com/images/") }
                    ?: return@mapNotNull null
                imageUrl to photo
            }
            if (photos.isEmpty()) return@forEach

            list.empty()
            photos.forEach { (imageUrl, photo) ->
                val paragraph = list.appendElement("p")
                val galleryUrl = photo.attr("data-url")
                val imageParent = if (galleryUrl.isNotBlank()) {
                    paragraph.appendElement("a").attr("href", galleryUrl)
                } else {
                    paragraph
                }
                imageParent.appendElement("img")
                    .attr("src", imageUrl)
                    .attr("loading", "lazy")
                    .attr("alt", photo.attr("data-caption"))
                    .apply {
                        photo.selectFirst("meta[itemprop=width]")?.attr("content")
                            ?.toIntOrNull()?.takeIf { it > 0 }?.let { attr("width", it.toString()) }
                        photo.selectFirst("meta[itemprop=height]")?.attr("content")
                            ?.toIntOrNull()?.takeIf { it > 0 }?.let { attr("height", it.toString()) }
                    }
            }
        }
    }
}
