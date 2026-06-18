package dev.defuddle.standardize

import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class HtmlStandardizerTest {
    @Test
    fun `heading duplicate title is removed`() {
        val article = article("""<article><h1>Article Title</h1><p>Body.</p></article>""")

        HtmlStandardizer.apply(article, title = "Article Title")

        assertFalse(article.outerHtml().contains("<h1>Article Title</h1>"))
        assertTrue(article.text().contains("Body."))
    }

    @Test
    fun `heading duplicate title normalizes smart apostrophes`() {
        val article = article("""<article><h1>SpaceX’s biggest-ever IPO</h1><p>Body.</p></article>""")

        HtmlStandardizer.apply(article, title = "SpaceX's biggest-ever IPO")

        assertFalse(article.outerHtml().contains("<h1>SpaceX’s biggest-ever IPO</h1>"))
        assertTrue(article.text().contains("Body."))
    }

    @Test
    fun `code language retained and line numbers removed`() {
        val article = article(
            """
            <article>
              <pre class="highlight language-kotlin"><span class="lineno">1</span><code><span class="line">val answer = 42</span></code></pre>
            </article>
            """.trimIndent(),
        )

        HtmlStandardizer.apply(article, title = null)

        val code = article.selectFirst("pre > code")
        assertNotNull(code)
        assertEquals("kotlin", code.attr("data-lang"))
        assertTrue(code.hasClass("language-kotlin"))
        assertFalse(article.text().contains("1val"))
        assertTrue(code.text().contains("val answer = 42"))
    }

    @Test
    fun `lazy image promoted and figure caption preserved`() {
        val article = article(
            """
            <article>
              <figure>
                <img src="data:image/svg+xml;base64,placeholder" data-src="/real.png" data-srcset="/real.png 1x, /real@2x.png 2x">
                <figcaption>Useful caption.</figcaption>
              </figure>
            </article>
            """.trimIndent(),
        )

        HtmlStandardizer.apply(article, title = null)

        val image = article.selectFirst("img")
        assertEquals("/real.png", image?.attr("src"))
        assertEquals("/real.png 1x, /real@2x.png 2x", image?.attr("srcset"))
        assertTrue(article.outerHtml().contains("<figcaption>Useful caption.</figcaption>"))
    }

    @Test
    fun `callout normalized`() {
        val article = article("""<article><blockquote><p>[!NOTE]</p><p>Remember this.</p></blockquote></article>""")

        HtmlStandardizer.apply(article, title = null)

        val callout = article.selectFirst(".callout")
        assertEquals("note", callout?.attr("data-callout"))
        assertTrue(article.outerHtml().contains("callout-title-inner"))
        assertTrue(article.outerHtml().contains("Remember this."))
    }

    @Test
    fun `simple footnote normalized`() {
        val article = article("""<article><ol class="footnotes"><li id="fn1">Footnote text</li></ol></article>""")

        HtmlStandardizer.apply(article, title = null)

        val footnotes = article.selectFirst("section[data-footnotes]")
        assertNotNull(footnotes)
        assertTrue(footnotes.text().contains("Footnote text"))
    }

    @Test
    fun `simple data table preserved and layout table flattened`() {
        val data = article(
            """<article><table><tr><th>A</th><th>B</th></tr><tr><td>1</td><td>2</td></tr></table></article>""",
        )
        HtmlStandardizer.apply(data, title = null)
        assertNotNull(data.selectFirst("table"))

        val layout = article(
            """<article><table class="layout"><tr><td><p>Layout text.</p></td></tr></table></article>""",
        )
        HtmlStandardizer.apply(layout, title = null)
        assertFalse(layout.outerHtml().contains("<table"))
        assertTrue(layout.text().contains("Layout text."))
    }

    @Test
    fun `math data latex and readable fallback are preserved`() {
        val article = article(
            """<article><span class="math" data-latex="x^2"><math><mi>x</mi></math></span></article>""",
        )

        HtmlStandardizer.apply(article, title = null)

        assertTrue(article.outerHtml().contains("""data-latex="x^2""""))
        assertTrue(article.outerHtml().contains("<math>"))
    }

    @Test
    fun `youtube consent placeholder normalizes to safe iframe`() {
        val article = article(
            """
            <article>
              <p>See the trailer below</p>
              <div class="hidden_video" data-video-id="1hKyYaBzko8">
                <img alt="YouTube Thumbnail" src="/youtube_cache_default.png">
                <div class="hidden_video_content">
                  YouTube videos require cookies, you must accept their cookies to view.
                  <a href="/index.php?module=cookie_prefs">View cookie preferences</a>.
                  <a class="accept_video" data-video-id="1hKyYaBzko8" href="#">Accept Cookies &amp; Show</a>
                  <a href="https://www.youtube.com/watch?v=1hKyYaBzko8">Direct Link</a>
                </div>
              </div>
            </article>
            """.trimIndent(),
        )

        HtmlStandardizer.apply(article, title = null)

        val iframe = article.selectFirst("iframe")
        assertNotNull(iframe)
        assertEquals("https://www.youtube-nocookie.com/embed/1hKyYaBzko8", iframe.attr("src"))
        assertEquals("https://www.youtube.com/watch?v=1hKyYaBzko8", iframe.attr("data-defuddle-video-url"))
        assertFalse(article.text().contains("YouTube videos require cookies"))
        assertFalse(article.text().contains("Accept Cookies"))
    }

    @Test
    fun `empty wrappers removed without losing text`() {
        val article = article("""<article><div><span>Kept text</span></div><span></span></article>""")

        HtmlStandardizer.apply(article, title = null)

        assertTrue(article.text().contains("Kept text"))
        assertFalse(article.outerHtml().contains("<span></span>"))
    }

    private fun article(html: String): Element =
        Jsoup.parse(html, "https://example.com").selectFirst("article") ?: error("missing article")
}
