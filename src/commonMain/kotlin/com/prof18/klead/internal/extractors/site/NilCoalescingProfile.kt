package com.prof18.klead.internal.extractors.site

import com.fleeksoft.ksoup.nodes.Element
import com.prof18.klead.RemovalRecord
import com.prof18.klead.internal.extractors.DomExtractor
import com.prof18.klead.internal.extractors.DomExtractorContext

internal object NilCoalescingProfile : DomExtractor {
    override val id: String = "nilcoalescing"
    override val domains: Set<String> = setOf("nilcoalescing.com")
    override val postContentRemoveSelectors: List<String> = listOf(".the-swiftui-way-banner")

    override fun postProcess(content: Element, context: DomExtractorContext, debug: MutableList<RemovalRecord>) {
        content.select(".adaptive-phone-images").forEach { wrapper ->
            val images = wrapper.children().filter { it.normalName() == "img" }
            if (images.size != 2 || wrapper.childrenSize() != 2 || wrapper.text().isNotBlank()) return@forEach
            val wide = images.singleOrNull { it.hasClass("wide") } ?: return@forEach
            val narrow = images.singleOrNull { it.hasClass("narrow") } ?: return@forEach
            if (wide.attr("src").isBlank() || narrow.attr("src").isBlank()) return@forEach
            if (wide.attr("alt").isBlank() || wide.attr("alt") != narrow.attr("alt")) return@forEach

            val narrowSrcset = listOf("${narrow.attr("src")} 1x", narrow.attr("srcset").trim())
                .filter(String::isNotBlank)
                .joinToString(", ")
            val picture = Element("picture").attr("data-klead-preserve-fallback-srcset", "")
            picture.appendElement("source")
                .attr("media", "(max-width: 700px)")
                .attr("srcset", narrowSrcset)
            wide.removeClass("wide")
            picture.appendChild(wide)
            wrapper.appendChild(picture)
            narrow.remove()
        }
    }
}
