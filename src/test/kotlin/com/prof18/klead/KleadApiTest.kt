package com.prof18.klead

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class KleadApiTest {
    @Test
    fun `empty html parses without crashing`() {
        val result = parseHtmlForTest(
            html = "",
            url = "https://example.com/empty",
            options = KleadOptions(outputs = setOf(KleadOutput.MARKDOWN)),
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
            options = KleadOptions(outputs = setOf(KleadOutput.HTML, KleadOutput.MARKDOWN)),
        )

        assertEquals("Document title", result.metadata.title)
        assertEquals("A short description", result.metadata.description)
        assertEquals(
            """
            ## Readable title

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
            options = KleadOptions(outputs = setOf(KleadOutput.MARKDOWN)),
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
            options = KleadOptions(outputs = setOf(KleadOutput.HTML, KleadOutput.MARKDOWN)),
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
            options = KleadOptions(
                outputs = setOf(KleadOutput.MARKDOWN),
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
            options = KleadOptions(
                outputs = setOf(KleadOutput.MARKDOWN),
                debug = true,
            ),
        )

        assertTrue(result.content.requireMarkdown().contains("Schema body marker belongs"))
        assertFalse(result.content.requireMarkdown().contains("Unrelated body content"))
        assertEquals("schema-text", result.debug["selectedContentSelector"])
    }

    @Test
    fun `external footnote blocks are merged when article contains matching references`() {
        val result = parseHtmlForTest(
            html = """
                <html>
                  <body>
                    <article>
                      <p>This release includes several improvements to image processing.</p>
                      <ul>
                        <li>Higher resolution is available to every account.<sup class="caption post__sup">1</sup></li>
                        <li>Better accuracy was measured in internal evaluations.<sup class="caption post__sup">2</sup></li>
                      </ul>
                      <p>Users can start using these features immediately.</p>
                    </article>
                    <div class="page-wrapper">
                      <div class="PostDetail__abc123__footnotes">
                        <h4>Footnotes</h4>
                        <p><sup>1</sup> This is a <a href="https://example.com/docs/vision">configuration change</a>.</p>
                        <p><sup>2</sup> Based on standardized test harnesses.</p>
                      </div>
                    </div>
                  </body>
                </html>
            """.trimIndent(),
            url = "https://example.com/external-footnotes",
        )

        val markdown = result.content.requireMarkdown()
        assertTrue(markdown.contains("Higher resolution is available to every account.[^1]"))
        assertTrue(markdown.contains("[^1]: This is a [configuration change](https://example.com/docs/vision)"))
        assertFalse(markdown.contains("[^1]: This is a [configuration change](https://example.com/docs/vision)."))
        assertTrue(markdown.contains("[^2]: Based on standardized test harnesses."))
        assertFalse(markdown.contains("<sup"))
    }
}
