package com.prof18.klead.internal.extractors.site

import com.fleeksoft.ksoup.nodes.Element
import com.prof18.klead.extractors.Extractor
import com.prof18.klead.extractors.ExtractorContext
import com.prof18.klead.extractors.ExtractorMetadata
import com.prof18.klead.extractors.ExtractorResult
import com.prof18.klead.internal.extractors.document

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
            // A turn can split one assistant message into several message elements, and each
            // may carry content both before and after a Thought section. Take every content
            // fragment from every message element that belongs to this turn (not a nested one).
            val messageBlocks = turn.select("[data-message-author-role=$role]")
                .filter { it.closestConversationTurn() === turn }
                .flatMap { it.messageContentElements() }
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

    private fun Element.closestConversationTurn(): Element? =
        parents().firstOrNull { it.attr("data-testid").startsWith("conversation-turn-") }

    private fun Element.messageContentElements(): List<Element> {
        val candidates = buildList {
            if (hasClass("markdown") || hasClass("whitespace-pre-wrap")) add(this@messageContentElements)
            addAll(select(".markdown, .whitespace-pre-wrap"))
        }
        if (candidates.isEmpty()) return this.takeIf { text().isNotBlank() }?.let(::listOf).orEmpty()
        // Drop any candidate nested inside another candidate so content isn't duplicated.
        return candidates.filter { candidate ->
            candidates.none { other -> other !== candidate && candidate.parents().any { it === other } }
        }
    }

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
