package com.prof18.klead.internal.extractors.site

import com.prof18.klead.parseHtmlForTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CorriereProfileTest {
    @Test
    fun `content selector keeps article metadata media body and links while removing nested clutter`() {
        val result = parseHtmlForTest(
            html = pageHtml(),
            url = "https://www.corriere.it/cronache/example.shtml",
        )

        val html = result.content.requireHtml()
        val markdown = result.content.requireMarkdown()
        assertEquals("A Corriere author", result.metadata.author)
        assertEquals("A concise article summary for readers.", result.metadata.description)
        assertEquals("https://example.com/cover.jpg", result.metadata.image)
        assertTrue(markdown.contains("A Corriere article summary"))
        assertTrue(markdown.contains("The first article paragraph contains enough reporting detail"))
        assertTrue(markdown.contains("[supporting source](https://example.com/source)"))
        assertTrue(html.contains("https://example.com/cover.jpg"))
        assertTrue(markdown.contains("Photo: Corriere / Example"))
        assertFalse(markdown.contains("External recommendation clutter"))
        assertFalse(markdown.contains("A reader comment nested in the article"))
        assertFalse(markdown.contains("Newsletter promotion"))
    }

    @Test
    fun `corriere cleanup is scoped to the host and article selector can fall back`() {
        val html = pageHtml()
        val unrelated = parseHtmlForTest(html, "https://example.com/cronache/example.shtml")
        val withoutSelector = parseHtmlForTest(
            html = html.replace("id=\"content-to-read\"", "id=\"different-content\""),
            url = "https://www.corriere.it/cronache/example.shtml",
        )

        assertTrue(unrelated.content.requireMarkdown().contains("A reader comment nested in the article"))
        assertTrue(withoutSelector.content.requireMarkdown().contains("The first article paragraph"))
    }

    private fun pageHtml() = """
        <html><head>
          <meta name="author" content="A Corriere author">
          <meta name="description" content="A concise article summary for readers.">
          <meta property="og:image" content="https://example.com/cover.jpg">
        </head><body>
          <div>External recommendation clutter</div>
          <article>
            <div id="content-to-read">
              <p class="summary">A Corriere article summary</p>
              <p>The first article paragraph contains enough reporting detail, context, and explanation to remain a stable article body for the parser.</p>
              <figure><img src="https://example.com/cover.jpg"><figcaption>Photo: Corriere / Example</figcaption></figure>
              <p>The second paragraph links to a <a href="https://example.com/source">supporting source</a> and continues the reported story with useful context for readers.</p>
              <div class="bck-social-nav">Social navigation clutter</div>
              <div class="bck-social-comment-read">A reader comment nested in the article</div>
              <div class="bck-modal-comments"><p>More comments that should be removed</p></div>
              <div class="bck-box-newsletter">Newsletter promotion</div>
            </div>
          </article>
        </body></html>
        """.trimIndent()
}
