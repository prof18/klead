package com.prof18.klead.internal.content

import com.fleeksoft.ksoup.nodes.Element
import kotlin.math.max

internal data class ContentScore(
    val total: Double,
    val wordCount: Int,
    val paragraphCount: Int,
    val commaCount: Int,
    val linkDensity: Double,
    val imageDensity: Double,
    val bonus: Double,
    val penalty: Double,
)

internal object ContentScorer {
    fun scoreElement(element: Element): ContentScore {
        val text = element.text()
        val wordCount = WORD_REGEX.findAll(text).count()
        val commaCount = text.count { it == ',' }
        val stats = collectSubtreeStats(element)
        val linkDensity = if (text.isEmpty()) 0.0 else stats.linkTextLength.toDouble() / text.length
        val imageDensity = imageDensity(stats.imageCount, wordCount, stats.paragraphCount)
        val bonus = contentHintBonus(element) +
            (if (stats.hasDateSignal) 12.0 else 0.0) +
            (if (stats.hasAuthorSignal) 12.0 else 0.0) +
            (if (stats.hasFootnoteSignal) 8.0 else 0.0)
        val penalty = imageDensity * 35.0 +
            stats.nestedTableCount * 40.0 +
            linkDensity * 80.0
        val base = wordCount.toDouble() +
            stats.paragraphCount * 12.0 +
            commaCount * 2.0
        return ContentScore(
            total = (base + bonus - penalty).coerceAtLeast(0.0),
            wordCount = wordCount,
            paragraphCount = stats.paragraphCount,
            commaCount = commaCount,
            linkDensity = linkDensity,
            imageDensity = imageDensity,
            bonus = bonus,
            penalty = penalty,
        )
    }

    // One walk over the subtree replaces the seven selector queries the scorer used to run
    // per element. Each check replicates the exact jsoup selector semantics it replaces:
    // - queries include the root element itself;
    // - attribute value matching is case-insensitive, [rel=author] is whole-value equality,
    //   hasClass is case-insensitive;
    // - "table table" requires a table ancestor at or below the scored root (jsoup evaluators
    //   never look above the search root), so the root itself can never be a nested table.
    private class SubtreeStats {
        var paragraphCount = 0
        var linkTextLength = 0
        var imageCount = 0
        var nestedTableCount = 0
        var hasDateSignal = false
        var hasAuthorSignal = false
        var hasFootnoteSignal = false
    }

    private fun collectSubtreeStats(root: Element): SubtreeStats {
        val stats = SubtreeStats()
        for (element in root.select("*")) {
            when (element.normalName()) {
                "p" -> if (element.text().isNotBlank()) stats.paragraphCount++

                "a" -> {
                    stats.linkTextLength += element.text().length
                    if (!stats.hasFootnoteSignal && element.attr("href").startsWith("#fn", ignoreCase = true)) {
                        stats.hasFootnoteSignal = true
                    }
                }

                "img", "picture", "figure" -> stats.imageCount++

                "table" -> if (element !== root && element.hasTableAncestorWithin(root)) stats.nestedTableCount++

                "time" -> stats.hasDateSignal = true

                "sup" -> if (element.attr("id").contains("fn", ignoreCase = true)) stats.hasFootnoteSignal = true
            }
            if (!stats.hasDateSignal) {
                stats.hasDateSignal = element.hasAttr("datetime") ||
                    element.attr("itemprop").contains("date", ignoreCase = true)
            }
            if (!stats.hasAuthorSignal) {
                stats.hasAuthorSignal = element.attr("rel").equals("author", ignoreCase = true) ||
                    element.attr("itemprop").contains("author", ignoreCase = true) ||
                    element.hasClass("author") ||
                    element.hasClass("byline")
            }
            if (!stats.hasFootnoteSignal) {
                stats.hasFootnoteSignal = element.hasClass("footnote") || element.hasClass("footnotes")
            }
        }
        return stats
    }

    private fun Element.hasTableAncestorWithin(root: Element): Boolean {
        var ancestor = parent()
        while (ancestor != null) {
            if (ancestor.normalName() == "table") return true
            if (ancestor === root) return false
            ancestor = ancestor.parent()
        }
        return false
    }

    private fun contentHintBonus(element: Element): Double {
        val hints = "${element.id()} ${element.className()}".lowercase()
        var bonus = 0.0
        if (CONTENT_HINTS.any { it in hints }) bonus += 35.0
        if (NEGATIVE_HINTS.any { it in hints }) bonus -= 35.0
        return bonus
    }

    private fun imageDensity(imageCount: Int, wordCount: Int, paragraphCount: Int): Double {
        if (imageCount == 0) return 0.0
        val contentUnits = max(1.0, paragraphCount + wordCount / 120.0)
        return imageCount / contentUnits
    }

    private val WORD_REGEX = Regex("""[\p{L}\p{N}]+(?:['-][\p{L}\p{N}]+)*""")

    private val CONTENT_HINTS = listOf(
        "article",
        "content",
        "entry",
        "post",
        "story",
        "markdown-body",
    )

    private val NEGATIVE_HINTS = listOf(
        "comment",
        "footer",
        "header",
        "nav",
        "related",
        "share",
        "sidebar",
    )
}
