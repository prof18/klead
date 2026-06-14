package dev.defuddle.content

import org.jsoup.nodes.Element
import kotlin.math.max

data class ContentScore(
    val total: Double,
    val wordCount: Int,
    val paragraphCount: Int,
    val commaCount: Int,
    val linkDensity: Double,
    val imageDensity: Double,
    val bonus: Double,
    val penalty: Double,
)

object ContentScorer {
    fun scoreElement(element: Element): ContentScore {
        val text = element.text()
        val wordCount = WORD_REGEX.findAll(text).count()
        val paragraphCount = element.select("p").count { it.text().isNotBlank() }
        val commaCount = text.count { it == ',' }
        val linkDensity = linkDensity(element)
        val imageDensity = imageDensity(element, wordCount, paragraphCount)
        val bonus = contentHintBonus(element) +
            dateSignalBonus(element) +
            authorSignalBonus(element) +
            footnoteSignalBonus(element)
        val penalty = imageDensity * 35.0 +
            nestedTablePenalty(element) +
            linkDensity * 80.0
        val base = wordCount.toDouble() +
            paragraphCount * 12.0 +
            commaCount * 2.0
        return ContentScore(
            total = (base + bonus - penalty).coerceAtLeast(0.0),
            wordCount = wordCount,
            paragraphCount = paragraphCount,
            commaCount = commaCount,
            linkDensity = linkDensity,
            imageDensity = imageDensity,
            bonus = bonus,
            penalty = penalty,
        )
    }

    private fun contentHintBonus(element: Element): Double {
        val hints = "${element.id()} ${element.className()}".lowercase()
        var bonus = 0.0
        if (CONTENT_HINTS.any { it in hints }) bonus += 35.0
        if (NEGATIVE_HINTS.any { it in hints }) bonus -= 35.0
        return bonus
    }

    private fun dateSignalBonus(element: Element): Double =
        if (element.selectFirst("time, [datetime], [itemprop*=date]") == null) 0.0 else 12.0

    private fun authorSignalBonus(element: Element): Double =
        if (element.selectFirst("[rel=author], [itemprop*=author], .author, .byline") == null) 0.0 else 12.0

    private fun footnoteSignalBonus(element: Element): Double =
        if (element.selectFirst("sup[id*=fn], a[href^=#fn], .footnote, .footnotes") == null) 0.0 else 8.0

    private fun nestedTablePenalty(element: Element): Double =
        element.select("table table").size * 40.0

    private fun linkDensity(element: Element): Double {
        val textLength = element.text().length
        if (textLength == 0) return 0.0
        val linkTextLength = element.select("a").sumOf { it.text().length }
        return linkTextLength.toDouble() / textLength
    }

    private fun imageDensity(
        element: Element,
        wordCount: Int,
        paragraphCount: Int,
    ): Double {
        val imageCount = element.select("img, picture, figure").size
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
