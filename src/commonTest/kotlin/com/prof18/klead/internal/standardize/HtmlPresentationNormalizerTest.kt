package com.prof18.klead.internal.standardize

import com.fleeksoft.ksoup.Ksoup
import com.prof18.klead.KleadOutput
import com.prof18.klead.parseHtmlForTest
import com.prof18.klead.testOptions
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HtmlPresentationNormalizerTest {
    @Test
    fun `removes publisher typography and colors throughout nested table HTML`() {
        val document = Ksoup.parse(
            """<body bgcolor="white" color="black" style="COLOR: white !important; margin: 0; background: #fff">
                <table bgcolor="#fff" style="background-color:#fff"><tr><td style="font-family: Arial; FONT-SIZE: 12px; line-height:1.5!important; color: #000; padding: 4px">Text</td></tr></table>
                <font color="red" face="Arial" size="4">Font text</font>
            </body>""",
        )

        HtmlPresentationNormalizer.normalize(document.body())

        assertFalse(document.body().hasAttr("bgcolor"))
        assertFalse(document.body().hasAttr("color"))
        assertEquals("margin: 0", document.body().attr("style"))
        assertFalse(document.selectFirst("table")!!.hasAttr("bgcolor"))
        assertEquals("padding: 4px", document.selectFirst("td")!!.attr("style"))
        assertFalse(document.selectFirst("font")!!.hasAttr("color"))
        assertFalse(document.selectFirst("font")!!.hasAttr("face"))
        assertFalse(document.selectFirst("font")!!.hasAttr("size"))
    }

    @Test
    fun `preserves emphasis image dimensions code whitespace and SVG visual styling`() {
        val document = Ksoup.parse(
            """<div>
                <p><strong><em style="font-style: italic; color: red">Emphasis</em></strong></p>
                <img src="image.png" width="640" height="480" style="width:100%; color:red">
                <pre style="white-space: pre-wrap; font-family: monospace; line-height: 1"> a  b\n</pre>
                <svg style="color: red"><path fill="red" style="stroke: blue"></path></svg>
            </div>""",
        )

        HtmlPresentationNormalizer.normalize(document.selectFirst("div")!!)

        assertContains(document.selectFirst("em")!!.attr("style"), "font-style: italic")
        assertFalse(document.selectFirst("em")!!.attr("style").contains("color", ignoreCase = true))
        assertEquals("640", document.selectFirst("img")!!.attr("width"))
        assertEquals("480", document.selectFirst("img")!!.attr("height"))
        assertEquals("width:100%", document.selectFirst("img")!!.attr("style"))
        assertContains(document.selectFirst("pre")!!.attr("style"), "white-space: pre-wrap")
        assertFalse(document.selectFirst("pre")!!.attr("style").contains("font-family"))
        assertEquals("color: red", document.selectFirst("svg")!!.attr("style"))
        assertEquals("red", document.selectFirst("path")!!.attr("fill"))
        assertEquals("stroke: blue", document.selectFirst("path")!!.attr("style"))
        assertTrue(document.selectFirst("pre")!!.text().contains("a  b"))
    }

    @Test
    fun `keeps semicolons inside data URLs while removing reader owned properties`() {
        val document = Ksoup.parse(
            """<p style="background-image: url(data:image/svg+xml;charset=utf8,%3Csvg%3E;%3C/svg%3E); COLOR: black !important; margin: 0">Text</p>""",
        )

        HtmlPresentationNormalizer.normalize(document.body())

        assertEquals(
            "background-image: url(data:image/svg+xml;charset=utf8,%3Csvg%3E;%3C/svg%3E); margin: 0",
            document.selectFirst("p")!!.attr("style"),
        )
    }

    @Test
    fun `removes declarations after leading CSS comments and preserves unaffected style formatting`() {
        val document = Ksoup.parse(
            """<p style="  margin: 0; /* publisher; theme (night) */ color/* property */: black; padding: 2px  ">Text</p>""",
        )

        HtmlPresentationNormalizer.normalize(document.body())

        assertEquals("margin: 0; padding: 2px", document.selectFirst("p")!!.attr("style"))

        val untouched = Ksoup.parse("""<p style="  margin: 0; padding: 2px  ">Text</p>""")
        val untouchedStyle = untouched.selectFirst("p")!!.attr("style")
        HtmlPresentationNormalizer.normalize(untouched.body())
        assertEquals(untouchedStyle, untouched.selectFirst("p")!!.attr("style"))
    }

    @Test
    fun `font shorthand loses publisher typography but keeps bold and italic emphasis`() {
        val document = Ksoup.parse(
            """<p style="font: italic 700 12px/18px Arial, sans-serif; margin: 0">Text</p>""",
        )

        HtmlPresentationNormalizer.normalize(document.body())

        assertEquals("font-style: italic; font-weight: 700; margin: 0", document.selectFirst("p")!!.attr("style"))
    }

    @Test
    fun `font shorthand expansion still removes publisher font and preserves important emphasis`() {
        val document = Ksoup.parse(
            """<p style="font: bold 16px serif !important; color: white; margin: 0">Text</p>""",
        )

        HtmlPresentationNormalizer.normalize(document.body())

        assertEquals(
            "font-weight: bold !important; margin: 0",
            document.selectFirst("p")!!.attr("style"),
        )
    }

    @Test
    fun `HTML presentation cleanup does not change the Markdown output`() {
        val input = """<!doctype html><html><body bgcolor="white" style="background-color: white; font-family: Arial; color: black">
            <table role="presentation" bgcolor="white"><tr><td style="font-family: Arial; font-size: 14px; color: black; padding: 8px">
            <p>This newsletter paragraph has enough ordinary text for the parser to identify it as the primary article content.</p>
            <p><strong>Bold emphasis remains semantic</strong> and this second paragraph provides enough content for stable parsing.</p>
            </td></tr></table>
            </body></html>"""

        val both = parseHtmlForTest(
            html = input,
            url = "https://example.com/newsletter",
            options = testOptions(outputs = setOf(KleadOutput.HTML, KleadOutput.MARKDOWN)),
        )
        val markdownOnly = parseHtmlForTest(
            html = input,
            url = "https://example.com/newsletter",
            options = testOptions(outputs = setOf(KleadOutput.MARKDOWN)),
        )

        assertEquals(markdownOnly.content.requireMarkdown(), both.content.requireMarkdown())
        assertFalse(both.content.requireHtml().contains("bgcolor", ignoreCase = true))
        assertFalse(both.content.requireHtml().contains("font-family", ignoreCase = true))
    }
}
