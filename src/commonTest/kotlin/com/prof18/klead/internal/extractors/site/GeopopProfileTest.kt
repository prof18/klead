package com.prof18.klead.internal.extractors.site

import com.prof18.klead.parseHtmlForTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GeopopProfileTest {
    @Test
    fun `article body keeps metadata and reporting but excludes page chrome and recommendations`() {
        val result = parseHtmlForTest(pageHtml(), "https://www.geopop.it/example/")

        assertEquals("Desert art", result.metadata.title)
        assertEquals("Example Author", result.metadata.author)
        val markdown = result.content.requireMarkdown()
        assertTrue(markdown.contains("The opening paragraph explains"))
        assertTrue(markdown.contains("The final paragraph describes"))
        assertTrue(markdown.contains("Photo: the desert installation"))
        assertTrue(markdown.contains("[source](https://example.com/source)"))
        assertTrue(result.content.requireHtml().contains("https://example.com/art.jpg"))
        assertFalse(markdown.contains("Header controls"))
        assertFalse(markdown.contains("Recommended video sentinel"))
        assertFalse(markdown.contains("Other news sentinel"))
        assertFalse(result.content.requireHtml().contains("<svg"))
    }

    @Test
    fun `recommendation cleanup is domain scoped and missing body selector falls back`() {
        val unrelated = parseHtmlForTest(pageHtml(), "https://example.com/story/")
        assertTrue(unrelated.content.requireMarkdown().contains("Recommended video sentinel"))

        val fallback = parseHtmlForTest(
            pageHtml().replace("cp_article__content", "story-body"),
            "https://geopop.it/example/",
        )
        assertTrue(fallback.content.requireMarkdown().contains("The final paragraph describes"))
    }

    private fun pageHtml() = """
        <html><head><title>Desert art</title><meta name="author" content="Example Author"></head>
        <body><main class="cp_container">
          <header><p>Header controls</p><svg viewBox="0 0 24 24"><path d="M0 0h24v24H0z"/></svg></header>
          <article class="cp_article"><header><h1>Desert art</h1></header>
            <div class="cp_article__content">
              <p>The opening paragraph explains the history of an installation in the desert with enough detail to establish the main article and its context.</p>
              <figure><img src="https://example.com/art.jpg"><figcaption>Photo: the desert installation</figcaption></figure>
              <p>The final paragraph describes the artists and links to a <a href="https://example.com/source">source</a> with more information about their work.</p>
              <a class="rdls rdls--type-video" href="/video"><div>Recommended video sentinel</div></a>
            </div>
          </article>
          <aside>Other news sentinel</aside>
        </main></body></html>
        """.trimIndent()
}
