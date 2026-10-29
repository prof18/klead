package com.prof18.klead

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PinterestProfileTest {
    @Test
    fun `pin page keeps the pin image and description without the masonry layout`() {
        val result = parseHtmlForTest(pinHtml(), "https://fr.pinterest.com/pin/123/")
        val html = result.content.requireHtml()

        assertTrue(html.contains("""<img src="https://i.pinimg.com/736x/aa/pin.jpg" alt="A wooden calendar">"""))
        assertTrue(html.contains("Hand painted drawers"))
        assertFalse(html.contains("height:0"))
        assertFalse(html.contains("Comments"))
        assertEquals("Wooden Advent Calendar", result.metadata.title)
    }

    @Test
    fun `pin without a source link still keeps the image`() {
        val markdown = parseHtmlForTest(pinHtml(seeAlso = false), "https://www.pinterest.com/pin/123/")
            .content.requireMarkdown()

        assertTrue(markdown.startsWith("![A wooden calendar](https://i.pinimg.com/736x/aa/pin.jpg)"))
        assertFalse(markdown.contains("https://shop.example.com"))
    }

    @Test
    fun `pin markup is limited to Pinterest domains`() {
        val markdown = parseHtmlForTest(pinHtml(), "https://example.com/pin/123/").content.requireMarkdown()

        assertTrue(markdown.contains("Comments are turned off"))
    }

    private fun pinHtml(seeAlso: Boolean = true): String = """
        <html><head>
        ${if (seeAlso) """<meta property="og:see_also" content="https://shop.example.com/calendar">""" else ""}
        </head><body><div role="main"><div class="Masonry_P" style="height:0;width:100%">
          <div style="overflow:clip"><div data-test-id="pin-closeup-image">
            <img src="https://i.pinimg.com/736x/aa/pin.jpg" alt="A wooden calendar">
          </div></div>
          <div style="container-type:size;min-height:fit-content">
            <div data-test-id="pin-domain-link"><span>shop.example.com</span></div>
            <div data-test-id="pinTitle"><h1>Wooden Advent Calendar</h1></div>
            <div data-test-id="truncated-description"><span>Hand painted drawers topped with a winter village,
            evergreen trees, and softly illuminated details that bring anticipation to the room every day.</span></div>
            <h2>Comments</h2><p>Comments are turned off for this Pin, so nothing else is shown here today.</p>
          </div>
        </div></div></body></html>
        """.trimIndent()
}
