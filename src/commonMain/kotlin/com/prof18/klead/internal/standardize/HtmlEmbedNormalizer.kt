package com.prof18.klead.internal.standardize

import com.fleeksoft.ksoup.nodes.Element
import com.prof18.klead.internal.media.TrustedEmbeds
import com.prof18.klead.internal.media.TrustedMarkdownMedia

// Rewrites trusted embeds and publisher placeholders into uniform iframes carrying the canonical
// watch URL, so downstream output can render or link them consistently.
internal object HtmlEmbedNormalizer {
    fun normalizePublisherPlaceholders(content: Element) {
        content.select(
            """div.ilPostSocial[data-component="ilPostSocial"][data-type="twitter"][data-url]""",
        ).forEach { placeholder ->
            val media = TrustedEmbeds.markdownMediaFromUrl(placeholder.attr("data-url"))
                ?.takeIf { it.markdownLinkLabel != null }
                ?: return@forEach
            val iframe = Element("iframe")
            applyEmbedAttributes(iframe, media, media.defaultTitle)
            placeholder.replaceWith(iframe)
        }

        content.select(
            """blockquote.instagram-media[data-instgrm-permalink]""",
        ).forEach { placeholder ->
            val media = TrustedEmbeds.markdownMediaFromUrl(placeholder.attr("data-instgrm-permalink"))
                ?.takeIf { it.markdownLinkLabel == "Instagram post" }
            if (media == null) {
                placeholder.remove()
                return@forEach
            }
            val iframe = Element("iframe")
            applyEmbedAttributes(iframe, media, media.defaultTitle)
            placeholder.replaceWith(iframe)
        }
    }

    fun normalizeEmbeds(content: Element) {
        content.select("iframe[src]").forEach { iframe ->
            val mediaUrl = iframe.attr("data-klead-video-url").trim().ifBlank { iframe.attr("src") }
            val media = TrustedEmbeds.markdownMediaFromUrl(mediaUrl, iframe.baseUri()) ?: return@forEach
            val title = iframe.attr("title").trim().ifBlank { media.defaultTitle }
            val preserveLeadingSpacer = iframe.hasAttr("data-klead-leading-spacer")
            iframe.clearAttributes()
            applyEmbedAttributes(iframe, media, title)
            if (preserveLeadingSpacer) {
                iframe.attr("data-klead-leading-spacer", "true")
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
            applyEmbedAttributes(iframe, video, video.defaultTitle)
            placeholder.replaceWith(iframe)
        }
    }

    private fun applyEmbedAttributes(iframe: Element, media: TrustedMarkdownMedia, title: String) {
        iframe.attr("src", media.normalizedIframeSrc.orEmpty())
        iframe.attr("title", title.ifBlank { media.defaultTitle })
        iframe.attr("loading", "lazy")
        media.iframeSandbox?.let { sandbox -> iframe.attr("sandbox", sandbox) }
        if (media.allowFullscreen) {
            iframe.attr("allowfullscreen", "")
        }
        if (media.markdownLinkLabel == null) {
            iframe.attr("allowfullscreen", "")
            iframe.attr(
                "allow",
                "accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture; web-share",
            )
        }
        iframe.attr("data-klead-video-url", media.watchUrl)
    }
}
