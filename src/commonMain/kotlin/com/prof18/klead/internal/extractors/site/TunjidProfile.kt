package com.prof18.klead.internal.extractors.site

import com.fleeksoft.ksoup.nodes.Element
import com.prof18.klead.RemovalRecord
import com.prof18.klead.internal.extractors.DomExtractor
import com.prof18.klead.internal.extractors.DomExtractorContext
import com.prof18.klead.internal.removal.recordAndRemove

internal object TunjidProfile : DomExtractor {
    override val id: String = "tunjid"
    override val domains: Set<String> = setOf("tunjid.com")
    override val contentSelectors: List<String> = listOf("main.MuiContainer-root")

    override fun postProcess(content: Element, context: DomExtractorContext, debug: MutableList<RemovalRecord>) {
        val main = if (content.normalName() == "main") content else content.selectFirst("main.MuiContainer-root")
        val header = main?.children()?.firstOrNull { child ->
            child.children().any { it.normalName() == "h3" } && child.selectFirst(".MuiAvatar-root") != null
        } ?: return
        val body = header.nextElementSibling()?.takeIf { it.selectFirst("p") != null } ?: return

        // The cover shares the header with the byline; keep it and the subtitle.
        header.children().filter { it.selectFirst(".MuiAvatar-root") != null }.forEach { byline ->
            recordAndRemove(byline, debug, "removeExtractorChrome:tunjid", null, "author portrait and byline")
        }

        // Tags and both loading and populated comments sit outside the prose wrapper.
        // Use that boundary rather than generated Emotion class names or comment text.
        var footer = body.nextElementSibling()
        while (footer != null) {
            val next = footer.nextElementSibling()
            recordAndRemove(footer, debug, "removeExtractorChrome:tunjid", null, "article footer outside prose")
            footer = next
        }
    }
}
