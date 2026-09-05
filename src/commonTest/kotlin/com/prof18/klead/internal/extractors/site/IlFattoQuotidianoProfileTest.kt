package com.prof18.klead.internal.extractors.site

import com.prof18.klead.parseHtmlForTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IlFattoQuotidianoProfileTest {
    @Test
    fun `content selector keeps article body metadata and media while removing nested clutter`() {
        val result = parseHtmlForTest(
            html = pageHtml(),
            url = "https://www.ilfattoquotidiano.it/example/",
        )

        val html = result.content.requireHtml()
        val markdown = result.content.requireMarkdown()
        assertEquals("An Il Fatto author", result.metadata.author)
        assertEquals("A concise Il Fatto article summary for readers.", result.metadata.description)
        assertEquals("https://example.com/cover.jpg", result.metadata.image)
        assertEquals("Il Fatto article heading", result.metadata.title)
        assertTrue(markdown.contains("Reporting section"))
        assertTrue(markdown.contains("A concise Il Fatto article summary for readers."))
        assertTrue(markdown.contains("The final article paragraph remains part of the reported story"))
        assertTrue(markdown.contains("[supporting source](https://example.com/source)"))
        assertTrue(html.contains("https://example.com/cover.jpg"))
        assertTrue(markdown.contains("Photo: Il Fatto / Example"))
        assertFalse(markdown.contains("Related news feed clutter"))
        assertFalse(markdown.contains("Nested related carousel clutter"))
        assertFalse(markdown.contains("Utility controls that should be removed"))
        assertFalse(markdown.contains("Engagement banner that should be removed"))
    }

    @Test
    fun `cleanup is scoped to the host and article selector can fall back`() {
        val html = pageHtml()
        val unrelated = parseHtmlForTest(html, "https://example.com/example/")
        val withoutSelector = parseHtmlForTest(
            html = html.replace("class=\"ifq-post\"", "class=\"different-content\""),
            url = "https://www.ilfattoquotidiano.it/example/",
        )

        assertTrue(unrelated.content.requireMarkdown().contains("Unique nested utility prose sentinel"))
        assertTrue(
            withoutSelector.content.requireMarkdown().contains(
                "The final article paragraph remains part of the reported story",
            ),
        )
    }

    private fun pageHtml() = """
        <html><head>
          <meta name="author" content="An Il Fatto author">
          <meta name="description" content="A concise Il Fatto article summary for readers.">
          <meta property="og:image" content="https://example.com/cover.jpg">
        </head><body>
          <div>Related news feed clutter</div>
          <article class="ifq-post">
            <h1>Il Fatto article heading</h1>
            <p class="excerpt">A concise Il Fatto article summary for readers.</p>
            <h2>Reporting section</h2>
            <p>The first article paragraph contains enough reporting detail, context, and explanation to remain a stable article body for the parser.</p>
            <figure><img src="https://example.com/cover.jpg"><figcaption>Photo: Il Fatto / Example</figcaption></figure>
            <p>The second paragraph links to a <a href="https://example.com/source">supporting source</a> and continues the reported story with useful context for readers.</p>
            <footer class="ifq-post__footer"><div class="ifq-related-carousel">Nested related carousel clutter</div></footer>
            <div class="ifq-post__utils">Utility controls that should be removed<div>Unique nested utility prose sentinel</div></div>
            <div class="ifq-post__engagement-banner-channels">Engagement banner that should be removed</div>
            <p>The final article paragraph remains part of the reported story and should survive cleanup for readers.</p>
          </article>
        </body></html>
        """.trimIndent()
}
