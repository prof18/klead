package dev.defuddle.internal.content

import org.jsoup.Jsoup
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ContentScorerTest {
    @Test
    fun `score records word paragraph and comma counts`() {
        val element = article(
            """
            <article>
              <p>Readable prose has enough words, commas, and structure to be useful.</p>
              <p>Second paragraph keeps the article body substantial.</p>
            </article>
            """.trimIndent(),
        )

        val score = ContentScorer.scoreElement(element)

        assertEquals(18, score.wordCount)
        assertEquals(2, score.paragraphCount)
        assertEquals(2, score.commaCount)
        assertTrue(score.total > 0.0)
    }

    @Test
    fun `content class and id increase score`() {
        val generic = article(
            """<div><p>This paragraph has enough readable words to compare against content hints.</p></div>""",
        )
        val hinted = article(
            """<div id="article-content" class="post-content"><p>This paragraph has enough readable words to compare against content hints.</p></div>""",
        )

        assertTrue(ContentScorer.scoreElement(hinted).total > ContentScorer.scoreElement(generic).total)
    }

    @Test
    fun `date author and footnote signals increase score`() {
        val plain = article(
            """<section><p>Readable words appear in a plain section with no article signals.</p></section>""",
        )
        val signaled = article(
            """
            <section>
              <time datetime="2024-01-02">Jan 2</time>
              <a rel="author">Author Name</a>
              <p>Readable words appear in a signaled section with article metadata.</p>
              <sup id="fnref:1"><a href="#fn:1">1</a></sup>
            </section>
            """.trimIndent(),
        )

        assertTrue(ContentScorer.scoreElement(signaled).total > ContentScorer.scoreElement(plain).total)
    }

    @Test
    fun `image density and nested tables penalize score`() {
        val plain = article(
            """<article><p>This article has readable text and no heavy layout distractions.</p></article>""",
        )
        val noisy = article(
            """
            <article>
              <p>This article has readable text and no heavy layout distractions.</p>
              <img src="/1.png"><img src="/2.png"><img src="/3.png">
              <table><tr><td><table><tr><td>Nested layout</td></tr></table></td></tr></table>
            </article>
            """.trimIndent(),
        )

        assertTrue(ContentScorer.scoreElement(noisy).total < ContentScorer.scoreElement(plain).total)
    }

    @Test
    fun `link density reduces score`() {
        val plain = article(
            """<article><p>This article body has useful words without sending readers elsewhere.</p></article>""",
        )
        val linked = article(
            """
            <article>
              <p><a href="/a">This article body</a> <a href="/b">has useful words</a> <a href="/c">without sending readers elsewhere</a>.</p>
            </article>
            """.trimIndent(),
        )

        assertTrue(ContentScorer.scoreElement(linked).total < ContentScorer.scoreElement(plain).total)
    }

    private fun article(html: String) =
        Jsoup.parse(html).body().children().firstOrNull() ?: error("missing test element")
}
