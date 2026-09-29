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
            // On some publications the subtitle and the byline/actions share the next sibling.
            // Remove only the controls so the subtitle remains part of the article.
            header.nextElementSibling()?.let { details ->
                if (details.select("p.pw-post-body-paragraph, figure").isEmpty()) {
                    details.children()
                        .firstOrNull { child ->
                            child.selectFirst("[data-testid=storyReadTime]") != null &&
                                child.selectFirst("[data-testid=headerClapButton]") != null
                        }
                        ?.let { controls ->
                            recordAndRemove(
                                controls,
                                debug,
                                "postProcess:medium",
                                "[data-testid=storyReadTime]",
                                "Medium byline and action toolbar",
                            )
                        }
                }
            }
            if (header != content && header.select("figure, h2, h3, p.pw-post-body-paragraph").isEmpty()) {
                recordAndRemove(header, debug, "postProcess:medium", "h1.pw-post-title", "Medium article header")
            }
        }

        content.select("aside").toList().forEach { aside ->
            if (aside.text().trim() == "Top highlight") {
                recordAndRemove(aside, debug, "postProcess:medium", "aside", "Medium highlight control")
            }
        }

        content.select("h2").toList().forEach { heading ->
            val text = heading.text().trim()
            if (text.startsWith("Get ") && text.endsWith("stories in your inbox")) {
                heading.parent()?.parent()?.let { signup ->
                    if (signup.select("p").any { it.text().startsWith("Join Medium for free") }) {
                        recordAndRemove(signup, debug, "postProcess:medium", "h2", "Medium signup prompt")
                    }
                }
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
