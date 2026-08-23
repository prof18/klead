package com.prof18.klead.internal.standardize

import com.fleeksoft.ksoup.nodes.Element
import com.prof18.klead.internal.media.TrustedEmbeds
import com.prof18.klead.internal.media.TrustedMarkdownMedia

// Rewrites trusted video embeds (and lazy video placeholders) into uniform iframes carrying the
// canonical watch URL, so downstream output can render or link them consistently.
internal object HtmlEmbedNormalizer {
    fun normalizeVideoEmbeds(content: Element) {
        content.select("iframe[src]").forEach { iframe ->
            val media = TrustedEmbeds.markdownMediaFromUrl(iframe.attr("src")) ?: return@forEach
            val normalizedSrc = media.normalizedIframeSrc
            if (normalizedSrc == null) {
                iframe.attr("data-klead-video-url", media.watchUrl)
            } else {
                val title = iframe.attr("title").trim().ifBlank { media.defaultTitle }
                val preserveLeadingSpacer = iframe.hasAttr("data-klead-leading-spacer")
                iframe.clearAttributes()
                applyVideoAttributes(iframe, media, title)
                if (preserveLeadingSpacer) {
                    iframe.attr("data-klead-leading-spacer", "true")
                }
            }
        }

        content.select(".hidden_video[data-video-id]").forEach { placeholder ->
            val video = TrustedEmbeds.youtubeVideoFromId(placeholder.attr("data-video-id"))
                ?: TrustedEmbeds.markdownMediaFromUrl(
                    placeholder.selectFirst(
                        """a[href*="youtube.com/watch"], a[href*="youtu.be/"]""",
                    )?.attr("href").orEmpty(),
                )
                ?: return@forEach
            if (video.normalizedIframeSrc == null) return@forEach
            val iframe = Element("iframe")
            applyVideoAttributes(iframe, video, video.defaultTitle)
            placeholder.replaceWith(iframe)
        }
    }

    private fun applyVideoAttributes(iframe: Element, video: TrustedMarkdownMedia, title: String) {
        iframe.attr("src", video.normalizedIframeSrc.orEmpty())
        iframe.attr("title", title.ifBlank { video.defaultTitle })
        iframe.attr("loading", "lazy")
        iframe.attr("allowfullscreen", "")
        iframe.attr(
            "allow",
            "accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture; web-share",
        )
        iframe.attr("data-klead-video-url", video.watchUrl)
    }
}
