package com.prof18.klead

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IlSole24OreProfileTest {
    @Test
    fun `ilsole24ore profile removes clutter across separated article sections`() {
        val result = parseHtmlForTest(
            html = """
                <html><body>
                  <article>
                    <div class="aentry-container">
                      <div class="ahead"><div class="meta">Editorial policy chrome</div></div>
                      <p>Article opening paragraph with the essential context.</p>
                      <div class="rstrip">Trailing recommendation clutter</div>
                      <div class="agoogle-pref">Google preference controls</div>
                      <img src="/images/market.png" alt="Market chart">
                      <p>Read the <a href="/analysis">full analysis</a> for more details.</p>
                      <div class="ainfotool">Information tool clutter</div>
                      <div class="gpt24-suggest">Suggested prompt clutter</div>
                    </div>
                    <div class="aentry-container">
                      <h2>What happens next</h2>
                      <p>The final paragraph explains what readers should watch next.</p>
                      <div class="abox">Promotional box clutter</div>
                      <div class="acor--mkt">Market correlation clutter</div>
                      <div class="afoot-info">Footer information clutter</div>
                      <div class="rel--brandconn">Brand connection clutter</div>
                      <div class="d-print-none">Loading...</div>
                      <div class="d-print-none">This online-only explanation belongs to the article.</div>
                    </div>
                  </article>
                </body></html>
            """.trimIndent(),
            url = "https://www.ilsole24ore.com/art/example",
        )

        val markdown = result.content.requireMarkdown()
        assertTrue(markdown.contains("Article opening paragraph with the essential context."))
        assertTrue(markdown.contains("full analysis"))
        assertTrue(markdown.contains("What happens next"))
        assertTrue(markdown.contains("The final paragraph explains what readers should watch next."))
        assertTrue(markdown.contains("![Market chart](https://www.ilsole24ore.com/images/market.png)"))
        assertFalse(markdown.contains("chrome"))
        assertFalse(markdown.contains("clutter"))
        assertFalse(markdown.contains("Loading..."))
        assertTrue(markdown.contains("This online-only explanation belongs to the article."))
    }

    @Test
    fun `ilsole24ore selectors do not remove matching prose on unrelated hosts`() {
        val result = parseHtmlForTest(
            html = """
                <html><body><article>
                  <p class="rstrip">This is meaningful prose on another publisher.</p>
                  <p class="abox">This box contains an important explanation.</p>
                  <p class="d-print-none">This paragraph is also part of the story.</p>
                </article></body></html>
            """.trimIndent(),
            url = "https://example.com/story",
        )

        val markdown = result.content.requireMarkdown()
        assertTrue(markdown.contains("This is meaningful prose on another publisher."))
        assertTrue(markdown.contains("This box contains an important explanation."))
        assertTrue(markdown.contains("This paragraph is also part of the story."))
    }
}
