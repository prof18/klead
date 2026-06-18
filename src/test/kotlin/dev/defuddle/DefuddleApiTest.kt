package dev.defuddle

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DefuddleApiTest {
    @Test
    fun `empty html parses without crashing`() {
        val result = parseHtmlForTest(
            html = "",
            url = "https://example.com/empty",
            options = DefuddleOptions(outputs = setOf(DefuddleOutput.MARKDOWN)),
        )

        assertEquals("", result.content.requireMarkdown())
        assertFalse(result.debug.containsKey("parseTimeMillis"))
    }

    @Test
    fun `minimal article returns markdown as primary content and html as debug output`() {
        val result = parseHtmlForTest(
            html = """
                <!doctype html>
                <html>
                  <head>
                    <title>Document title</title>
                    <meta name="description" content="A short description">
                  </head>
                  <body>
                    <article>
                      <h1>Readable title</h1>
                      <p>This is the first paragraph.</p>
                      <p>This is the second paragraph.</p>
                    </article>
                  </body>
                </html>
            """.trimIndent(),
            url = "https://example.com/articles/readable",
            options = DefuddleOptions(outputs = setOf(DefuddleOutput.HTML, DefuddleOutput.MARKDOWN)),
        )

        assertEquals("Document title", result.metadata.title)
        assertEquals("A short description", result.metadata.description)
        assertEquals(
            """
            # Readable title

            This is the first paragraph.

            This is the second paragraph.
            """.trimIndent() + "\n",
            result.content.requireMarkdown(),
        )
        assertTrue(result.content.requireHtml().contains("<article>"))
        assertFalse(result.content.requireHtml().contains("<script"))
    }

    @Test
    fun `unsupported browser css behavior is documented and does not crash`() {
        val result = parseHtmlForTest(
            html = """
                <html>
                  <body>
                    <article>
                      <style>.article::before { content: "not executed"; }</style>
                      <p>Visible static text.</p>
                    </article>
                  </body>
                </html>
            """.trimIndent(),
            url = "https://example.com/css",
            options = DefuddleOptions(outputs = setOf(DefuddleOutput.MARKDOWN)),
        )

        assertTrue(result.content.requireMarkdown().contains("Visible static text."))
        assertFalse(result.content.requireMarkdown().contains("not executed"))
        assertEquals(
            "Browser layout, JavaScript execution, and CSS generated content are unsupported.",
            result.debug["unsupportedBrowserBehavior"],
        )
    }

    @Test
    fun `result exposes expected contract fields`() {
        val result = parseHtmlForTest(
            html = "<html><body><p>Body text.</p></body></html>",
            url = "https://example.com",
            options = DefuddleOptions(outputs = setOf(DefuddleOutput.HTML, DefuddleOutput.MARKDOWN)),
        )

        assertNotNull(result.content.markdown)
        assertNotNull(result.content.html)
        assertNotNull(result.metadata)
        assertNotNull(result.debug)
    }

    @Test
    fun `debug mode reports selected content selector`() {
        val result = parseHtmlForTest(
            html = "<html><body><article><p>Debug article.</p></article></body></html>",
            url = "https://example.com/debug",
            options = DefuddleOptions(
                outputs = setOf(DefuddleOutput.MARKDOWN),
                debug = true,
            ),
        )

        assertEquals("article", result.debug["selectedContentSelector"])
        assertNotNull(result.debug["contentCandidates"])
    }

    @Test
    fun `schema article body refines public parser body fallback`() {
        val result = parseHtmlForTest(
            html = """
                <html>
                  <head>
                    <script type="application/ld+json">
                      {
                        "@type": "Article",
                        "articleBody": "Schema body marker belongs to this exact article body"
                      }
                    </script>
                  </head>
                  <body>
                    <header>Site chrome should not be included.</header>
                    <section id="schema-match">
                      <p>Schema body marker belongs to this exact article body and should refine the broad body fallback.</p>
                    </section>
                    <section>
                      <p>Unrelated body content should not be included.</p>
                    </section>
                  </body>
                </html>
            """.trimIndent(),
            url = "https://example.com/schema-body",
            options = DefuddleOptions(
                outputs = setOf(DefuddleOutput.MARKDOWN),
                debug = true,
            ),
        )

        assertTrue(result.content.requireMarkdown().contains("Schema body marker belongs"))
        assertFalse(result.content.requireMarkdown().contains("Unrelated body content"))
        assertEquals("schema-text", result.debug["selectedContentSelector"])
    }
}
