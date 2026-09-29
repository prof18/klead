package com.prof18.klead.internal.extractors.site

import com.fleeksoft.ksoup.Ksoup
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull

class NewsletterDomNormalizerTest {
    @Test
    fun `flattens every presentation cell once in reading order preserving nested data tables`() {
        val content = Ksoup.parse(
            """
            <article><table role="presentation"><tr><td>First</td><td>
            <table role="presentation"><tr><td>Second</td></tr><tr><td>Third</td></tr></table>
            <table><tr><th>Team</th><th>Score</th></tr><tr><td>A</td><td>2</td></tr></table>
            </td></tr><tr><td>Last</td></tr></table></article>
            """.trimIndent(),
        ).selectFirst("article")!!

        NewsletterDomNormalizer.normalize(content)

        assertEquals("First Second Third Team Score A 2 Last", content.text())
        assertEquals(1, content.select("table").size)
        assertEquals(2, content.select("table tr").size)
        assertEquals(2, content.select("table th").size)
        assertEquals(2, content.select("table td").size)
        assertFalse(content.outerHtml().contains("presentation"))
    }

    @Test
    fun `removes newsletter layout but retains links images emphasis and vector artwork`() {
        val content = Ksoup.parse(
            """
            <article style="border:1px solid; padding:30px" width="600">
            <p style="font-weight:bold; font-style:italic; color:black; margin:0">
            <a href="https://example.com/story">Story</a></p>
            <img src="photo.jpg" alt="Editorial photo" width="640" height="480" style="border:2px solid">
            <svg style="color:red"><path style="fill:blue"/></svg></article>
            """.trimIndent(),
        ).selectFirst("article")!!

        NewsletterDomNormalizer.normalize(content)

        assertFalse(content.hasAttr("style"))
        assertFalse(content.hasAttr("width"))
        assertEquals("font-weight:bold;font-style:italic", content.selectFirst("p")!!.attr("style"))
        assertEquals("https://example.com/story", content.selectFirst("a")!!.attr("href"))
        val image = assertNotNull(content.selectFirst("img"))
        assertEquals("photo.jpg", image.attr("src"))
        assertEquals("Editorial photo", image.attr("alt"))
        assertEquals("640", image.attr("width"))
        assertEquals("480", image.attr("height"))
        assertEquals("fill:blue", content.selectFirst("path")!!.attr("style"))
    }
}
