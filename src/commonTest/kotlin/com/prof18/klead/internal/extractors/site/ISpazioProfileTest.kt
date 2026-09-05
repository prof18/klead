package com.prof18.klead.internal.extractors.site

import com.prof18.klead.parseHtmlForTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ISpazioProfileTest {
    @Test
    fun `article body keeps metadata paragraphs media and links while excluding page chrome and spots`() {
        val result = parseHtmlForTest(pageHtml(), "https://www.ispazio.net/example/")

        assertEquals("iSpazio article title", result.metadata.title)
        assertEquals("iSpazio Author", result.metadata.author)
        val markdown = result.content.requireMarkdown()
        assertTrue(markdown.contains("The opening paragraph introduces the iSpazio report"))
        assertTrue(markdown.contains("The ending paragraph provides the final context"))
        assertTrue(markdown.contains("[original source](https://example.com/source)"))
        assertTrue(result.content.requireHtml().contains("https://example.com/ispazio.jpg"))
        assertFalse(markdown.contains("AI summary outside the article"))
        assertFalse(markdown.contains("Footer navigation outside the article"))
        assertFalse(markdown.contains("Inline advertising sentinel"))
        assertFalse(markdown.contains("Subscription prompt sentinel"))
    }

    @Test
    fun `profile is domain scoped and missing content selector falls back to article content`() {
        val unrelated = parseHtmlForTest(pageHtml(), "https://example.com/story/")
        assertTrue(unrelated.content.requireMarkdown().contains("Inline advertising sentinel"))

        val fallback = parseHtmlForTest(
            pageHtml().replace("isp-entry-content", "story-body"),
            "https://www.ispazio.net/example/",
        )
        assertTrue(fallback.content.requireMarkdown().contains("The ending paragraph provides the final context"))
    }

    private fun pageHtml() = """
        <html><head><title>iSpazio article title</title><meta name="author" content="iSpazio Author"></head>
        <body>
          <aside><p>AI summary outside the article</p></aside>
          <article>
            <h1>iSpazio article title</h1>
            <div class="isp-entry-content">
              <p>The opening paragraph introduces the iSpazio report with enough detail to establish the article and its context for readers.</p>
              <figure><img src="https://example.com/ispazio.jpg"><figcaption>iSpazio article image</figcaption></figure>
              <div class="isp-single-spot">Inline advertising sentinel</div>
              <p>The middle paragraph explains the reported development and links to the <a href="https://example.com/source">original source</a> for more information.</p>
              <div class="isp-single-spot">Subscription prompt sentinel</div>
              <p>The ending paragraph provides the final context and completes the report with the details readers need to understand the story.</p>
            </div>
          </article>
          <footer>Footer navigation outside the article</footer>
        </body></html>
        """.trimIndent()
}
