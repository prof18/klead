package dev.defuddle

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DefuddleApiTest {
    @Test
    fun `empty html parses without crashing`() {
        val result = Defuddle.parseHtml(
            html = "",
            url = "https://example.com/empty",
        )

        assertEquals("example.com", result.domain)
        assertEquals("", result.contentMarkdown)
        assertEquals(0, result.wordCount)
        assertTrue(result.parseTimeMillis >= 0)
    }

    @Test
    fun `minimal article returns markdown as primary content and html as debug output`() {
        val result = Defuddle.parseHtml(
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
        )

        assertEquals("Document title", result.title)
        assertEquals("A short description", result.description)
        assertEquals("example.com", result.domain)
        assertEquals(
            """
            # Readable title

            This is the first paragraph.

            This is the second paragraph.
            """.trimIndent() + "\n",
            result.contentMarkdown,
        )
        assertTrue(result.contentHtml.contains("<article>"))
        assertFalse(result.contentHtml.contains("<script"))
        assertEquals(10, result.wordCount)
    }

    @Test
    fun `unsupported browser css behavior is documented and does not crash`() {
        val result = Defuddle.parseHtml(
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
        )

        assertTrue(result.contentMarkdown.contains("Visible static text."))
        assertFalse(result.contentMarkdown.contains("not executed"))
        assertEquals(
            "Browser layout, JavaScript execution, and CSS generated content are unsupported.",
            result.debug["unsupportedBrowserBehavior"],
        )
    }

    @Test
    fun `result exposes expected contract fields`() {
        val result = Defuddle.parseHtml(
            html = "<html><body><p>Body text.</p></body></html>",
            url = "https://example.com",
            options = DefuddleOptions(markdown = true),
        )

        assertNotNull(result.contentMarkdown)
        assertNotNull(result.contentHtml)
        assertNotNull(result.metaTags)
        assertNotNull(result.schemaOrgData)
        assertNotNull(result.debug)
    }

    @Test
    fun `content selector override is used by public parser`() {
        val result = Defuddle.parseHtml(
            html = """
                <html><body>
                  <article><p>Default article should lose.</p></article>
                  <section id="manual"><p>Manual content should win.</p></section>
                </body></html>
            """.trimIndent(),
            url = "https://example.com/manual",
            options = DefuddleOptions(contentSelector = "#manual"),
        )

        assertTrue(result.contentMarkdown.contains("Manual content should win."))
        assertFalse(result.contentMarkdown.contains("Default article should lose."))
    }

    @Test
    fun `debug mode reports selected content selector`() {
        val result = Defuddle.parseHtml(
            html = "<html><body><article><p>Debug article.</p></article></body></html>",
            url = "https://example.com/debug",
            options = DefuddleOptions(debug = true),
        )

        assertEquals("article", result.debug["selectedContentSelector"])
        assertNotNull(result.debug["contentCandidates"])
    }

    @Test
    fun `schema article body refines public parser body fallback`() {
        val result = Defuddle.parseHtml(
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
            options = DefuddleOptions(debug = true),
        )

        assertTrue(result.contentMarkdown.contains("Schema body marker belongs"))
        assertFalse(result.contentMarkdown.contains("Unrelated body content"))
        assertEquals("schema-text", result.debug["selectedContentSelector"])
    }
}
