package com.prof18.klead.internal.extractors.site

import com.fleeksoft.ksoup.nodes.Element
import com.prof18.klead.extractors.ExtractorMetadata
import com.prof18.klead.extractors.ExtractorResult
import com.prof18.klead.internal.dom.parseFragment
import com.prof18.klead.internal.dom.textTrimmedOrNull
import com.prof18.klead.internal.extractors.DomExtractor
import com.prof18.klead.internal.extractors.DomExtractorContext

// bsky.app renders posts client-side; the server only ships the post text in a <noscript> summary.
internal object BlueskyProfile : DomExtractor {
    override val id: String = "bluesky"
    override val domains: Set<String> = setOf("bsky.app")

    override fun extract(context: DomExtractorContext): ExtractorResult? {
        val summary = context.document.select("noscript")
            .asSequence()
            .mapNotNull { noscript -> noscript.parsedChildren().selectFirst("#bsky_post_summary") }
            .firstOrNull()
            ?: return null
        val postText = summary.selectFirst("#bsky_post_text")?.wholeText()?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: return null

        val article = Element("article")
        postText.split(BLANK_LINE)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .forEach { paragraph ->
                val element = article.appendElement("p")
                paragraph.lines().forEachIndexed { index, line ->
                    if (index > 0) element.appendElement("br")
                    element.appendText(line.trim())
                }
            }

        val handle = summary.selectFirst("#bsky_handle")?.textTrimmedOrNull()
        val displayName = summary.selectFirst("#bsky_display_name")?.textTrimmedOrNull()
        return ExtractorResult(
            contentHtml = article.outerHtml(),
            metadata = ExtractorMetadata(
                title = displayName ?: handle?.let { "@$it" },
                author = displayName ?: handle,
                site = SITE_NAME,
            ),
        )
    }

    private fun Element.parsedChildren(): Element {
        val root = Element("fragment")
        parseFragment(html(), baseUri()).forEach { root.appendChild(it) }
        return root
    }

    private val BLANK_LINE = Regex("""\n\s*\n""")
    private const val SITE_NAME = "Bluesky"
}
