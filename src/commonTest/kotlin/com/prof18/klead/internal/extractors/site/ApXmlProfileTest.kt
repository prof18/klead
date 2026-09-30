package com.prof18.klead.internal.extractors.site

import com.fleeksoft.ksoup.Ksoup
import com.prof18.klead.internal.extractors.createExtractorContext
import com.prof18.klead.parseHtmlForTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ApXmlProfileTest {
    @Test
    fun `post body is reconstructed with formatting and sanitized through the shared pipeline`() {
        val body = """
            <h2>Model choices</h2><p>Use <strong>unified memory</strong> for local inference.</p>
            <table><tr><th>RAM</th><th>Model</th></tr><tr><td>8GB</td><td>Small</td></tr></table>
            <p onclick="bad()">More useful article prose about running models locally.</p>
            <a href="javascript:bad()">Unsafe link</a><script>bad()</script>
        """.trimIndent()
        val result = parseHtmlForTest(page(body), URL)
        val markdown = result.content.requireMarkdown()
        val html = result.content.requireHtml()

        assertEquals("Mac models", result.metadata.title)
        assertEquals("Ryan A.", result.metadata.author)
        assertEquals("ApX Machine Learning", result.metadata.site)
        assertTrue(markdown.contains("## Model choices"))
        assertTrue(markdown.contains("**unified memory**"))
        assertTrue(markdown.contains("| 8GB | Small |"))
        assertFalse(markdown.contains("Sponsor Content"))
        assertFalse(html.contains("onclick"))
        assertFalse(html.contains("javascript:"))
        assertFalse(html.contains("<script"))
    }

    @Test
    fun `missing or mismatched post data leaves rendered HTML available`() {
        for (script in listOf("", page("<p>Wrong article</p>", slug = "other"), page("\$missing"))) {
            val context = createExtractorContext(
                url = URL,
                host = "apxml.com",
                document = Ksoup.parse("<article><p>Rendered article remains.</p></article>$script"),
            )
            assertNull(ApXmlProfile.extract(context))
            val markdown = parseHtmlForTest(context.document.outerHtml(), URL).content.requireMarkdown()
            assertTrue(markdown.contains("Rendered article"))
        }
    }

    @Test
    fun `flight post data on unrelated hosts does not activate ApX extraction`() {
        val result = parseHtmlForTest(
            "<article><p>Existing article text remains on another publisher.</p></article>" + page("<p>ApX data</p>"),
            "https://example.com/posts/mac-models",
        )

        assertTrue(result.content.requireMarkdown().contains("Existing article text"))
        assertFalse(result.content.requireMarkdown().contains("ApX data"))
    }

    private fun page(content: String, slug: String = "mac-models"): String {
        val post = JsonObject(
            mapOf(
                "title" to JsonPrimitive("Mac models"),
                "slug" to JsonPrimitive(slug),
                "content" to JsonPrimitive(content),
                "author" to JsonObject(mapOf("name" to JsonPrimitive("Ryan A."))),
            ),
        )
        return "<aside>Sponsor Content</aside>" + flightScript("9:{\"post\":$post}\n")
    }

    private companion object {
        const val URL = "https://apxml.com/posts/mac-models"
    }
}
