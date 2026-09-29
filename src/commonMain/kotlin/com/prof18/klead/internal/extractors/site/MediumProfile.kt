package com.prof18.klead.internal.extractors.site

import com.fleeksoft.ksoup.nodes.Element
import com.prof18.klead.RemovalRecord
import com.prof18.klead.internal.extractors.DomExtractor
import com.prof18.klead.internal.extractors.DomExtractorContext
import com.prof18.klead.internal.removal.recordAndRemove

internal object MediumProfile : DomExtractor {
    override val id: String = "medium"
    override val domains: Set<String> = setOf("medium.com")

    override fun matches(context: DomExtractorContext): Boolean {
        if (context.hostMatches(domains)) return true
        if (context.document.selectFirst("h1.pw-post-title") == null) return false

        val siteName = context.document.selectFirst("meta[property=og:site_name]")?.attr("content")
        val appPackage = context.document.selectFirst("meta[property=al:android:package]")?.attr("content")
        return siteName.equals("Medium", ignoreCase = true) || appPackage == "com.medium.reader"
    }

    override fun postProcess(content: Element, context: DomExtractorContext, debug: MutableList<RemovalRecord>) {
        // Medium nests its byline and action toolbar beside the title. Metadata has already been
        // extracted, so the whole header can go without leaving reaction counts or icon links.
        content.selectFirst("h1.pw-post-title")?.parent()?.let { header ->
            if (header != content && header.select("figure, h2, h3, p.pw-post-body-paragraph").isEmpty()) {
                recordAndRemove(header, debug, "postProcess:medium", "h1.pw-post-title", "Medium article header")
            }
        }

        // The image control's instruction is visually hidden by Medium's CSS, but otherwise
        // becomes visible text in cleaned HTML. Keep the picture and any figcaption intact.
        content.select("figure.paragraph-image [role=button]").forEach { imageControl ->
            imageControl.select("span").toList().forEach { span ->
                if (span.text().trim() == IMAGE_PROMPT) {
                    recordAndRemove(
                        span,
                        debug,
                        "postProcess:medium",
                        "figure.paragraph-image span",
                        "Medium image control hint",
                    )
                }
            }
            imageControl.removeAttr("role")
            imageControl.removeAttr("tabindex")
        }
    }

    private const val IMAGE_PROMPT = "Press enter or click to view image in full size"
}
