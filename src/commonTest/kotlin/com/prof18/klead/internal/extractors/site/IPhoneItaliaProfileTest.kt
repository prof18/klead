package com.prof18.klead.internal.extractors.site

import com.prof18.klead.parseHtmlForTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IPhoneItaliaProfileTest {
    @Test
    fun `removes recommendation heading with widget but preserves editorial headings and links`() {
        val result = parseHtmlForTest(pageHtml(), "https://www.iphoneitalia.com/example")
        val markdown = result.content.requireMarkdown()

        assertTrue(markdown.contains("Editorial heading"))
        assertTrue(markdown.contains("The final paragraph"))
        assertTrue(markdown.contains("[Facebook](https://example.com/source)"))
        assertTrue(result.content.requireHtml().contains("https://example.com/phone.jpg"))
        assertFalse(markdown.contains("Recommendation heading"))
        assertFalse(markdown.contains("Product widget sentinel"))
    }

    @Test
    fun `product cleanup does not apply to other domains`() {
        val result = parseHtmlForTest(pageHtml(), "https://example.com/story")

        assertTrue(result.content.requireMarkdown().contains("Recommendation heading"))
        assertTrue(result.content.requireMarkdown().contains("Product widget sentinel"))
    }

    private fun pageHtml() = """
        <html><head><title>Phone production</title></head><body><article>
        <h1>Phone production</h1>
        <p>The opening paragraph explains the production difficulties facing the new phone,
        with detailed reporting about the display and hinge manufacturing process.</p>
        <img src="https://example.com/phone.jpg" width="770" height="433">
        <h4>Editorial heading</h4>
        <p>The report links to <a href="https://example.com/source">Facebook</a> as an editorial source.</p>
        <h4>Recommendation heading</h4>
        <div class="aawp"><p>Product widget sentinel includes a detailed recommendation
        with information about a different phone and its specifications.</p></div>
        <p>The final paragraph continues the reporting after the product widget and must remain
        available to readers, including all its context about manufacturing.</p>
        </article></body></html>
        """.trimIndent()
}
