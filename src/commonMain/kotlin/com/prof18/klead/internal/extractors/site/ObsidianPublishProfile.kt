package com.prof18.klead.internal.extractors.site

import com.fleeksoft.ksoup.nodes.Element
import com.fleeksoft.ksoup.nodes.TextNode
import com.prof18.klead.extractors.ExtractorMetadata
import com.prof18.klead.extractors.ExtractorResult
import com.prof18.klead.internal.dom.parseKleadUri
import com.prof18.klead.internal.dom.selectFirstSafe
import com.prof18.klead.internal.extractors.DomExtractor
import com.prof18.klead.internal.extractors.DomExtractorContext

internal object ObsidianPublishProfile : DomExtractor {
    override val id: String = "obsidian-publish"
    override val domains: Set<String> = setOf("publish.obsidian.md")

    override fun matches(context: DomExtractorContext): Boolean =
        context.hostMatches(domains) || context.document.selectFirstSafe(OBSIDIAN_CONTENT_SELECTOR) != null

    override fun extract(context: DomExtractorContext): ExtractorResult? {
        val content = context.document.selectFirstSafe(OBSIDIAN_CONTENT_SELECTOR)?.clone() ?: return null
        content.select(".mod-ui, .mod-footer, .backlinks").remove()
        content.normalizeObsidianLinks()
        if (content.text().isBlank()) return null

        val article = Element("article")
        content.childNodes().forEach { node ->
            article.appendChild(node.clone())
        }
        return ExtractorResult(
            contentHtml = article.outerHtml(),
            metadata = ExtractorMetadata(
                title = cleanObsidianTitle(
                    raw = context.document.selectFirst("""meta[property="og:title"]""")
                        ?.attr("content")
                        ?.ifBlank { null }
                        ?: context.document.title().ifBlank { null },
                    site = context.document.obsidianSiteName(),
                ),
                site = context.document.obsidianSiteName(),
            ),
        )
    }

    private fun com.fleeksoft.ksoup.nodes.Document.obsidianSiteName(): String? = selectFirst(
        """meta[property="og:site_name"]""",
    )
        ?.attr("content")
        ?.trim()
        ?.ifBlank { null }

    private fun cleanObsidianTitle(raw: String?, site: String?): String? {
        val trimmed = raw?.trim()?.ifBlank { null } ?: return null
        val hadPlatformSuffix = trimmed.endsWith(OBSIDIAN_PUBLISH_TITLE_SUFFIX)
        val withoutPlatformSuffix = trimmed
            .removeSuffix(OBSIDIAN_PUBLISH_TITLE_SUFFIX)
            .trim()
            .ifBlank { null }
            ?: return null

        if (!hadPlatformSuffix && site != null) {
            return withoutPlatformSuffix
                .removeSuffix(" - $site")
                .trim()
                .ifBlank { null }
        }

        return withoutPlatformSuffix
    }

    private fun Element.normalizeObsidianLinks() {
        select("a[href]").forEach { link ->
            link.attr("href", link.attr("href").toAsciiHref())
            link.addInlineSpacing()
        }
    }

    private fun Element.addInlineSpacing() {
        (previousSibling() as? TextNode)?.let { previous ->
            val text = previous.text()
            if (text.lastOrNull()?.isWhitespace() == false) {
                previous.text("$text ")
            }
        }
        (nextSibling() as? TextNode)?.let { next ->
            val text = next.text()
            if (text.firstOrNull()?.isWhitespace() == false) {
                next.text(" $text")
            }
        }
    }

    private fun String.toAsciiHref(): String {
        val trimmed = trim()
        if (trimmed.isBlank()) return trimmed
        return parseKleadUri(trimmed)?.asciiString ?: trimmed
    }

    private const val OBSIDIAN_CONTENT_SELECTOR = ".markdown-preview-view .markdown-preview-section"
    private const val OBSIDIAN_PUBLISH_TITLE_SUFFIX = " - Obsidian Publish"
}
