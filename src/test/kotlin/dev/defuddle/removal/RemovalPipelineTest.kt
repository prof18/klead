package dev.defuddle.removal

import dev.defuddle.Defuddle
import dev.defuddle.DefuddleOptions
import org.jsoup.Jsoup
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class RemovalPipelineTest {
    @Test
    fun `hidden inline styles and attributes are removed`() {
        val result = Defuddle.parseHtml(
            html = """
                <html><body><article>
                  <p>Visible article text with enough ordinary prose to avoid the short-page retry that intentionally disables hidden-element removal for sparse pages. This paragraph keeps the default removal attempt as the selected parse result while still leaving hidden clutter nearby for the removal pipeline to clean up. Additional visible sentences provide stable article length, realistic punctuation, and enough words for the retry controller to trust the cleaned default result.</p>
                  <p hidden>Hidden attribute text.</p>
                  <p aria-hidden="true">Aria hidden text.</p>
                  <p style="display:none">Display hidden text.</p>
                  <p style="visibility:hidden">Visibility hidden text.</p>
                  <p style="opacity:0">Opacity hidden text.</p>
                  <p class="hidden md:block">Class hidden text.</p>
                  <p class="sm:invisible">Variant invisible text.</p>
                </article></body></html>
            """.trimIndent(),
            url = "https://example.com/hidden",
        )

        assertTrue(result.contentMarkdown.contains("Visible article text"))
        assertFalse(result.contentMarkdown.contains("Hidden attribute text."))
        assertFalse(result.contentMarkdown.contains("Aria hidden text."))
        assertFalse(result.contentMarkdown.contains("Display hidden text."))
        assertFalse(result.contentMarkdown.contains("Visibility hidden text."))
        assertFalse(result.contentMarkdown.contains("Opacity hidden text."))
        assertFalse(result.contentMarkdown.contains("Class hidden text."))
        assertFalse(result.contentMarkdown.contains("Variant invisible text."))
    }

    @Test
    fun `math hidden wrappers are preserved`() {
        val document = Jsoup.parse(
            """
            <article>
              <span aria-hidden="true" class="katex">
                <math><mi>x</mi></math>
              </span>
              <p>Visible prose.</p>
            </article>
            """.trimIndent(),
        )
        val article = document.selectFirst("article") ?: error("missing article")

        val debug = mutableListOf<RemovalRecord>()
        RemovalPipeline.apply(article, DefuddleOptions(), debug)

        assertTrue(article.outerHtml().contains("<math>"))
        assertTrue(article.text().contains("Visible prose."))
    }

    @Test
    fun `debug records identify hidden removals`() {
        val result = Defuddle.parseHtml(
            html = """
                <article>
                  <p>Visible debug text with enough ordinary prose to avoid the short-page retry that disables hidden-element removal. This keeps the removal record from the default parse attempt in the final result and makes the debug assertion deterministic. Additional visible sentences provide stable article length, realistic punctuation, and enough words for the retry controller to trust the cleaned default result.</p>
                  <aside hidden>Hidden debug text.</aside>
                </article>
            """.trimIndent(),
            url = "https://example.com/debug-hidden",
            options = DefuddleOptions(debug = true),
        )

        val removals = result.debug["removals"] as? List<*>
        assertNotNull(removals)
        assertTrue(removals.any { it.toString().contains("removeHiddenElements") })
        assertTrue(removals.any { it.toString().contains("Hidden debug text") })
    }

    @Test
    fun `exact selectors remove obvious nav footer and ad blocks but preserve notes`() {
        val result = Defuddle.parseHtml(
            html = """
                <article>
                  <nav>Navigation should go</nav>
                  <p>Main prose stays with enough words to avoid short-page retry and make removal deterministic for this fixture.</p>
                  <aside class="ad">Advertisement should go</aside>
                  <section class="footnotes"><p>Footnote should stay.</p></section>
                  <footer>Footer should go</footer>
                </article>
            """.trimIndent(),
            url = "https://example.com/exact",
        )

        assertFalse(result.contentMarkdown.contains("Navigation should go"))
        assertFalse(result.contentMarkdown.contains("Advertisement should go"))
        assertFalse(result.contentMarkdown.contains("Footer should go"))
        assertTrue(result.contentMarkdown.contains("Footnote should stay."))
    }

    @Test
    fun `partial selectors do not remove code blocks`() {
        val result = Defuddle.parseHtml(
            html = """
                <article>
                  <p>Main prose stays with enough words to avoid short-page retry and make removal deterministic for this fixture.</p>
                  <pre class="related-code"><code>val related = "content"</code></pre>
                  <section class="related-posts">Related posts should go</section>
                </article>
            """.trimIndent(),
            url = "https://example.com/partial",
        )

        assertTrue(result.contentMarkdown.contains("""val related = "content""""))
        assertFalse(result.contentMarkdown.contains("Related posts should go"))
    }

    @Test
    fun `low scoring removes link heavy related sections and preserves prose`() {
        val result = Defuddle.parseHtml(
            html = """
                <article>
                  <section>
                    <p>This prose section should stay because it has meaningful text, punctuation, and a low link density. It explains the article topic in complete sentences and provides enough substance for the parser to trust the cleaned result.</p>
                    <p>Another prose paragraph gives the block enough substance to avoid being treated as clutter. It adds stable article length, natural punctuation, and more words so short-page retry paths do not disable the low-scoring removal being tested.</p>
                  </section>
                  <section>
                    <a href="/one">Related one</a>
                    <a href="/two">Related two</a>
                    <a href="/three">Related three</a>
                    <a href="/four">Related four</a>
                  </section>
                </article>
            """.trimIndent(),
            url = "https://example.com/low-score",
        )

        assertTrue(result.contentMarkdown.contains("This prose section should stay"))
        assertFalse(result.contentMarkdown.contains("Related one"))
    }

    @Test
    fun `content patterns remove trailing subscribe blocks but preserve final prose`() {
        val result = Defuddle.parseHtml(
            html = """
                <article>
                  <p>The final article paragraph should stay because it is normal prose with useful information. It includes enough words, punctuation, and context for the parser to keep the default cleaned result rather than invoking short-page retries. This makes the trailing subscription pattern removal deterministic while preserving the legitimate article ending. One more sentence keeps the cleaned article comfortably above the retry threshold.</p>
                  <p>Subscribe to our newsletter for weekly updates and product announcements.</p>
                </article>
            """.trimIndent(),
            url = "https://example.com/subscribe",
        )

        assertTrue(result.contentMarkdown.contains("The final article paragraph should stay"))
        assertFalse(result.contentMarkdown.contains("Subscribe to our newsletter"))
    }

    @Test
    fun `duplicate images are removed after first occurrence`() {
        val result = Defuddle.parseHtml(
            html = """
                <article>
                  <p>Article prose has enough words to keep the default cleaned parse result. It describes the image context clearly and avoids short retry paths with stable text.</p>
                  <img src="/image.png" alt="First">
                  <img src="/image.png" alt="Duplicate">
                </article>
            """.trimIndent(),
            url = "https://example.com/images",
        )

        assertTrue(result.contentHtml.contains("""alt="First""""))
        assertFalse(result.contentHtml.contains("""alt="Duplicate""""))
    }

    @Test
    fun `cover image duplicating metadata image is removed`() {
        val result = Defuddle.parseHtml(
            html = """
                <html><head>
                  <meta property="og:image" content="https://example.com/cover.png">
                </head><body>
                  <article>
                    <img src="/cover.png" alt="Cover">
                    <p>Article prose has enough words to keep the default cleaned parse result. It describes the article content clearly and avoids short retry paths with stable text.</p>
                  </article>
                </body></html>
            """.trimIndent(),
            url = "https://example.com/article",
        )

        assertEquals("https://example.com/cover.png", result.image)
        assertFalse(result.contentHtml.contains("""alt="Cover""""))
    }
}
