package com.prof18.klead.internal.extractors.site

import com.prof18.klead.parseHtmlForTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SkyTg24ProfileTest {
    @Test
    fun `inline suggestions become compact links without changing article media`() {
        val result = parseHtmlForTest(pageHtml(), "https://tg24.sky.it/politica/story")
        val html = result.content.requireHtml()
        val markdown = result.content.requireMarkdown()

        assertTrue(markdown.contains("Vedi anche: [A video & its context](https://tg24.sky.it/video/story)"))
        assertTrue(html.contains("<p>Vedi anche: <a href=\"/video/story\">"), html)
        assertFalse(html.contains("preview.jpg"))
        assertFalse(html.contains("play-icon"))
        assertTrue(html.contains("article-photo.jpg"))
        assertTrue(html.contains("article-diagram"))
        assertTrue(markdown.indexOf("Before the suggestion") < markdown.indexOf("Vedi anche:"))
        assertTrue(markdown.indexOf("Vedi anche:") < markdown.indexOf("After the suggestion"))
    }

    @Test
    fun `normalization is scoped to Sky TG24 and leaves incomplete cards alone`() {
        val otherHost = parseHtmlForTest(pageHtml(), "https://example.com/story").content.requireHtml()
        assertTrue(otherHost.contains("preview.jpg"))
        val noTitle = parseHtmlForTest(
            pageHtml().replace("c-inline-card__title", "different-title"),
            "https://tg24.sky.it/story",
        ).content.requireHtml()
        assertTrue(noTitle.contains("preview.jpg"))
    }

    @Test
    fun `unlabelled suggestions keep their title without inventing a label`() {
        val markdown = parseHtmlForTest(
            pageHtml().replace("<h2 class=\"c-section-title\">Vedi anche</h2>", ""),
            "https://tg24.sky.it/story",
        ).content.requireMarkdown()
        assertTrue(markdown.contains("[A video & its context](https://tg24.sky.it/video/story)"))
        assertFalse(markdown.contains("Vedi anche"))
        assertFalse(markdown.contains("preview.jpg"))
    }

    private fun pageHtml() = """
        <html><body><article>
          <h1>Article title</h1>
          <p>Before the suggestion, this article contains enough reporting, explanation, and context to preserve its main content in the reader.</p>
          <img src="https://example.com/article-photo.jpg">
          <svg width="200" height="200" viewBox="0 0 200 200"><path id="article-diagram" d="M0 0 L200 200"/></svg>
          <a class="c-inline-card" href="/video/story">
            <div><img src="https://example.com/preview.jpg">
              <svg width="80" height="80" viewBox="0 0 80 80"><path id="play-icon" d="M0 0 L80 40 L0 80 Z"/></svg>
            </div>
            <h2 class="c-section-title">Vedi anche</h2>
            <h3 class="c-inline-card__title">A video &amp; its context</h3>
          </a>
          <p>After the suggestion, the original reporting continues with detailed information and further explanation for readers of the article.</p>
        </article></body></html>
        """.trimIndent()
}
