package com.prof18.klead.internal

import com.prof18.klead.KleadResult
import com.prof18.klead.extractors.Extractor
import com.prof18.klead.extractors.ExtractorContext
import com.prof18.klead.extractors.ExtractorMetadata
import com.prof18.klead.extractors.ExtractorResult
import com.prof18.klead.parseHtmlForTest
import com.prof18.klead.testOptions
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GenericNewsletterLayoutTest {
    private val ktnUrl = "https://kill-the-newsletter.com/feeds/test/entries/layout.html"

    private fun paragraph(n: Int) =
        "<p style=\"margin:0;padding:8px;color:#333;text-transform:uppercase\">Paragraph $n. " +
            "The council approved the harbour redevelopment after a long public consultation, " +
            "and residents described the compromise as workable but far from perfect. ".repeat(3) + "</p>"

    private fun template(): String = """
        <html><head><title>Weekly letter</title></head><body>
        <div style="display:none;max-height:0;overflow:hidden">Hidden preheader text</div>
        <article id="letter" style="width:600px">
        <table role="presentation" width="600" cellpadding="0" cellspacing="0" border="0" style="width:600px;border:1px solid #ccc"><tr><td align="center" style="padding:20px 30px">
        <table role="presentation"><tr><td><h1>Harbour vote</h1>${paragraph(1)}${paragraph(2)}</td></tr></table>
        <img src="https://example.com/photo.jpg" alt="Editorial photo" width="600" height="300" style="border:0">
        <table role="presentation"><tr><td>${paragraph(
        3,
    )}<p><a href="https://example.com/story" style="color:red">Full story</a></p></td></tr></table>
        <table style="width:100%" border="1"><tr><th>Team</th><th>Score</th></tr><tr><td>Reds</td><td>3</td></tr><tr><td>Blues</td><td>1</td></tr></table>
        ${paragraph(4)}
        </td></tr></table></article></body></html>
        """.trimIndent()

    private fun parse(html: String = template(), url: String = ktnUrl, extractors: List<Extractor> = emptyList()) =
        parseHtmlForTest(html, url, testOptions(customExtractors = extractors, debug = true))

    private fun KleadResult.htmlContent(): String = checkNotNull(content.html)

    @Test
    fun `unknown template gets layout cleanup with content and data table intact`() {
        val result = parse()
        val html = result.htmlContent()

        assertFalse(html.contains("cellpadding"), html)
        assertFalse(html.contains("width:600px"), html)
        assertFalse(html.contains("padding:8px"), html)
        assertFalse(html.contains("margin:0"), html)
        assertTrue(html.contains("text-transform:uppercase"), "unknown styles must survive: $html")
        assertFalse(html.contains("Hidden preheader"), html)
        (1..4).forEach { n -> assertEquals(1, Regex("Paragraph $n\\.").findAll(html).count(), "paragraph $n") }
        assertTrue(
            html.indexOf(
                "Paragraph 1.",
            ) < html.indexOf("Paragraph 3.") && html.indexOf("Paragraph 3.") < html.indexOf("Paragraph 4."),
        )
        assertTrue(html.contains("https://example.com/story"))
        assertTrue(html.contains("Editorial photo"))
        assertTrue(html.contains("<th>Team</th>") || html.contains("Team</th>"), html)
        assertTrue(html.contains("Reds") && html.contains("Blues"))
        val markdown = checkNotNull(result.content.markdown)
        assertTrue(markdown.contains("Harbour vote") && markdown.contains("Paragraph 4."))
        assertFalse(markdown.contains("Hidden preheader"))
        val ids = result.debug["extractorIds"]
        assertTrue(ids == null || (ids as List<*>).isEmpty(), "no publisher extractor expected: $ids")
    }

    @Test
    fun `output is deterministic across runs`() {
        assertEquals(parse().htmlContent(), parse().htmlContent())
    }

    @Test
    fun `other hosts keep their layout properties`() {
        listOf("https://example.com/article", "https://kill-the-newsletter.com.example.org/article").forEach { url ->
            val html = parse(url = url).htmlContent()
            assertTrue(html.contains("padding:8px"), "$url: $html")
        }
    }

    @Test
    fun `host matching is case insensitive`() {
        val html = parse(url = "https://KILL-THE-NEWSLETTER.com/feeds/test/entries/layout.html").htmlContent()
        assertFalse(html.contains("padding:8px"), html)
    }

    @Test
    fun `external source with forged canonical url is not treated as a newsletter`() {
        val forged = template().replace(
            "<head>",
            "<head><link rel=\"canonical\" href=\"$ktnUrl\"><meta property=\"og:url\" content=\"$ktnUrl\">",
        )

        val html = parse(html = forged, url = "https://example.com/article").htmlContent()

        assertTrue(html.contains("padding:8px"), html)
    }

    @Test
    fun `no eligible presentation table is a no op`() {
        val plain = """
            <html><body><article><div style="border:1px solid #ccc;padding:4px">${paragraph(
            1,
        )}${paragraph(2)}${paragraph(3)}</div></article></body></html>
        """.trimIndent()

        val html = parse(html = plain).htmlContent()

        assertTrue(html.contains("padding:8px"), html)
    }

    @Test
    fun `presentation table removed by the pipeline leaves nothing to do`() {
        val hidden = """
            <html><body><article>${paragraph(1)}${paragraph(2)}${paragraph(3)}
            <table role="presentation" style="display:none"><tr><td>Gone</td></tr></table></article></body></html>
        """.trimIndent()

        val html = parse(html = hidden).htmlContent()

        assertFalse(html.contains("Gone"))
        assertTrue(html.contains("Paragraph 3."))
        assertTrue(html.contains("padding:8px"), html)
    }

    @Test
    fun `matched extractor returning null still permits fallback cleanup`() {
        val nullExtractor = object : Extractor {
            override val id = "null-ktn"
            override val domains = setOf("kill-the-newsletter.com")
        }

        val html = parse(extractors = listOf(nullExtractor)).htmlContent()

        assertFalse(html.contains("padding:8px"), html)
    }

    @Test
    fun `successful custom extractor bypasses generic cleanup`() {
        val body = "<div style=\"border:1px solid #f00\"><table role=\"presentation\" cellpadding=\"3\"><tr><td>" +
            "${paragraph(1)}${paragraph(2)}</td></tr></table></div>"
        val contentExtractor = object : Extractor {
            override val id = "content-ktn"
            override val domains = setOf("kill-the-newsletter.com")
            override fun extract(context: ExtractorContext) = ExtractorResult(contentHtml = "<article>$body</article>")
        }
        val metadataOnly = object : Extractor {
            override val id = "meta-ktn"
            override val domains = setOf("kill-the-newsletter.com")
            override fun extract(context: ExtractorContext) =
                ExtractorResult(metadata = ExtractorMetadata(title = "Custom title"))
        }

        val fromContent = parse(extractors = listOf(contentExtractor)).htmlContent()
        val fromMetadata = parse(extractors = listOf(metadataOnly))

        assertTrue(fromContent.contains("border:1px solid #f00"), fromContent)
        assertTrue(fromContent.contains("padding:8px"), fromContent)
        assertTrue(
            fromMetadata.htmlContent().contains("padding:8px"),
            fromMetadata.htmlContent(),
        )
    }

    @Test
    fun `small input that triggers retries stays deterministic and duplicate free`() {
        val small = """
            <html><body><article><table role="presentation" cellpadding="2"><tr><td><p>Short note one.</p><p>Short note two.</p></td></tr></table></article></body></html>
        """.trimIndent()

        val first = parse(html = small)
        val second = parse(html = small)

        assertEquals(first.htmlContent(), second.htmlContent())
        assertTrue(
            (first.debug["retryAttempts"] as List<*>).size > 1,
            "expected retries: ${first.debug["retryAttempts"]}",
        )
        assertFalse(first.htmlContent().contains("cellpadding"), first.htmlContent())
        assertEquals(1, Regex("Short note one\\.").findAll(first.htmlContent()).count())
        assertEquals(1, Regex("Short note two\\.").findAll(first.htmlContent()).count())
    }

    @Test
    fun `empty content does not crash`() {
        parse(html = "<html><body></body></html>")
    }
}
