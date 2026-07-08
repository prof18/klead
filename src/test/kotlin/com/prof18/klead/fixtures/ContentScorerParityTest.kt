package com.prof18.klead.fixtures

import com.prof18.klead.internal.content.ContentScorer
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import kotlin.math.max
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// ContentScorer.scoreElement collects its signals in a single subtree walk. This test pins
// that walk to the selector-based reference implementation it replaced, both on crafted
// edge cases (selector semantics corners) and across the real FeedFlow reader dumps.
class ContentScorerParityTest {
    @Test
    fun `single-pass scorer matches selector-based reference on crafted edge cases`() {
        val cases = listOf(
            // scored element itself matches the probes (queries include the root)
            """<p class="author" datetime="2020"><a href="#fn1">x</a> readable words here</p>""",
            // nested table where the scored root is the outer table (root counts as ancestor)
            """<table><tr><td><table><tr><td>x</td></tr></table></td></tr></table>""",
            // multi-token rel must NOT count as author signal
            """<div><a rel="author nofollow">x</a><p>words, more words</p></div>""",
            // untrimmed attribute values: exact/prefix must not match, contains must
            """<div><a rel=" author ">x</a><b itemprop=" date ">y</b><a href=" #fn1 ">z</a></div>""",
            // case-insensitive class and attribute values
            """<div class="AUTHOR"><sup id="FNref">1</sup><span class="Footnotes">n</span></div>""",
            // time element and datetime attribute
            """<section><time>Jan</time><p>a, b</p></section>""",
            """<section><span datetime="2021-01-01">Jan</span><p>a, b</p></section>""",
            // scored root is a link (its own text counts toward link density)
            """<a href="/x">only link text</a>""",
            // images and figures including nested figure>img double counting
            """<figure><img src="/a.png"><figcaption>cap</figcaption></figure>""",
            // empty element
            """<div></div>""",
        )
        for (html in cases) {
            val element = Jsoup.parse(html).body().children().first()!!
            assertSameScore(element, "crafted: $html")
        }
    }

    @Test
    fun `single-pass scorer matches selector-based reference across reader dump corpus`() {
        val cases = FeedFlowReaderDumpLoader.loadAll(requireExpectedSnapshots = false)
        assertTrue(cases.isNotEmpty(), "no reader dumps found")
        for (case in cases) {
            val document = Jsoup.parse(case.rawHtml, case.sourceUrl)
            val body = document.body()
            val all = body.select("*")
            val step = max(1, all.size / SAMPLES_PER_DOCUMENT)
            for (index in 0 until all.size step step) {
                assertSameScore(all[index], "${case.name}[$index]")
            }
            assertSameScore(body, "${case.name}[body]")
        }
    }

    private fun assertSameScore(element: Element, label: String) {
        val actual = ContentScorer.scoreElement(element)
        val expected = referenceScore(element)
        assertEquals(expected, actual, "score mismatch for $label")
    }

    // Reference: the original selector-per-signal implementation, kept verbatim.
    private fun referenceScore(element: Element): com.prof18.klead.internal.content.ContentScore {
        val text = element.text()
        val wordCount = WORD_REGEX.findAll(text).count()
        val paragraphCount = element.select("p").count { it.text().isNotBlank() }
        val commaCount = text.count { it == ',' }
        val textLength = text.length
        val linkDensity = if (textLength == 0) {
            0.0
        } else {
            element.select("a").sumOf { it.text().length }.toDouble() / textLength
        }
        val imageCount = element.select("img, picture, figure").size
        val imageDensity = if (imageCount == 0) {
            0.0
        } else {
            imageCount / max(1.0, paragraphCount + wordCount / 120.0)
        }
        val hints = "${element.id()} ${element.className()}".lowercase()
        var bonus = 0.0
        if (CONTENT_HINTS.any { it in hints }) bonus += 35.0
        if (NEGATIVE_HINTS.any { it in hints }) bonus -= 35.0
        if (element.selectFirst("time, [datetime], [itemprop*=date]") != null) bonus += 12.0
        if (element.selectFirst("[rel=author], [itemprop*=author], .author, .byline") != null) bonus += 12.0
        if (element.selectFirst("sup[id*=fn], a[href^=#fn], .footnote, .footnotes") != null) bonus += 8.0
        val penalty = imageDensity * 35.0 +
            element.select("table table").size * 40.0 +
            linkDensity * 80.0
        val base = wordCount.toDouble() + paragraphCount * 12.0 + commaCount * 2.0
        return com.prof18.klead.internal.content.ContentScore(
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

    private companion object {
        const val SAMPLES_PER_DOCUMENT = 150
        val WORD_REGEX = Regex("""[\p{L}\p{N}]+(?:['-][\p{L}\p{N}]+)*""")
        val CONTENT_HINTS = listOf("article", "content", "entry", "post", "story", "markdown-body")
        val NEGATIVE_HINTS = listOf("comment", "footer", "header", "nav", "related", "share", "sidebar")
    }
}
