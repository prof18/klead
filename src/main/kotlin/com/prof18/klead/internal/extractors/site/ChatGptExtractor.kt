package com.prof18.klead.internal.extractors.site

import com.prof18.klead.extractors.Extractor
import com.prof18.klead.extractors.ExtractorContext
import com.prof18.klead.extractors.ExtractorMetadata
import com.prof18.klead.extractors.ExtractorResult
import org.jsoup.nodes.Element

internal object ChatGptExtractor : Extractor {
    override val id: String = "chatgpt"
    override val domains: Set<String> = setOf("chatgpt.com")

    override fun extract(context: ExtractorContext): ExtractorResult? {
        val turns = context.document.select("""[data-testid^="conversation-turn-"]""")
        if (turns.isEmpty()) return null

        val article = Element("article")
        turns.forEachIndexed { index, turn ->
            val role = turn.selectFirst("[data-message-author-role]")
                ?.attr("data-message-author-role")
                ?.trim()
                ?: return@forEachIndexed
            val messageBlocks = turn.select("[data-message-author-role=$role]")
                .mapNotNull { it.messageContentElement() }
            if (messageBlocks.isEmpty()) return@forEachIndexed

            if (index > 0) {
                article.appendElement("hr")
            }
            article.appendLabel(if (role == "user") "You said" else "ChatGPT said")
            messageBlocks.forEach { block ->
                article.appendMessageBlock(block)
            }
        }

        if (article.text().isBlank()) return null
        return ExtractorResult(
            contentHtml = article.outerHtml(),
            metadata = ExtractorMetadata(
                title = context.document.title().ifBlank { null },
                site = "ChatGPT",
            ),
        )
    }

    private fun Element.messageContentElement(): Element? =
        selectFirst(".markdown") ?: selectFirst(".whitespace-pre-wrap") ?: this.takeIf { text().isNotBlank() }

    private fun Element.appendLabel(label: String) {
        appendElement("p").appendElement("strong").text(label)
    }

    private fun Element.appendMessageBlock(block: Element) {
        if (block.hasClass("whitespace-pre-wrap")) {
            block.wholeText().trim().takeIf { it.isNotBlank() }?.let { text ->
                appendElement("p").text(text)
            }
            return
        }

        block.childNodes().forEach { node ->
            appendChild(node.clone())
        }
    }
}
