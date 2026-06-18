package com.prof18.klead

import com.prof18.klead.internal.dom.SelectorDiagnostics
import com.prof18.klead.internal.dom.selectSafe
import org.jsoup.Jsoup
import kotlin.system.measureTimeMillis
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SecurityRobustnessTest {
    @Test
    fun `security sanitizer strips dangerous links src actions events and srcdoc`() {
        val result = parseHtmlForTest(
            html = """
                <article>
                  <p onclick="bad()">Safe text.</p>
                  <a href="javascript:alert(1)">bad href</a>
                  <img src="data:text/html,<script>bad()</script>" alt="bad src">
                  <form action="javascript:bad()" formaction="data:text/html,bad"><button>Go</button></form>
                  <iframe srcdoc="<p>bad</p>"></iframe>
                  <img src="data:image/png;base64,AAAA" alt="safe image">
                </article>
            """.trimIndent(),
            url = "https://example.com/security",
        )

        assertFalse(result.content.requireHtml().contains("javascript:"))
        assertFalse(result.content.requireHtml().contains("data:text/html"))
        assertFalse(result.content.requireHtml().contains("onclick"))
        assertFalse(result.content.requireHtml().contains("srcdoc"))
        assertTrue(result.content.requireHtml().contains("data:image/png"))
    }

    @Test
    fun `malformed html missing head bad url and invalid json ld do not crash`() {
        val result = parseHtmlForTest(
            html = """<article><h1>Broken</h1><p>Still readable<script type="application/ld+json">{bad</script>""",
            url = "not a url",
            options = testOptions(debug = true),
        )

        assertTrue(result.content.requireMarkdown().contains("Still readable"))
        assertTrue(result.debug.toString().contains("Invalid JSON-LD"))
    }

    @Test
    fun `unsupported selector logs debug and continues`() {
        SelectorDiagnostics.clear()
        val document = Jsoup.parse("<article><p>Text</p></article>")

        val result = document.selectFirst("article")?.selectSafe("a:made-up(")

        assertTrue(result?.isEmpty() == true)
        assertTrue(SelectorDiagnostics.unsupportedSelectors().contains("a:made-up("))
    }

    @Test
    fun `repeated parse smoke test does not leak shared debug state`() {
        repeat(25) { index ->
            val result = parseHtmlForTest(
                html = "<article><p>Run $index text.</p><aside hidden>Hidden $index</aside></article>",
                url = "https://example.com/$index",
                options = testOptions(debug = true),
            )
            assertFalse(result.debug.toString().contains("Hidden ${index - 1}"))
        }
    }

    @Test
    fun `benchmark fixtures run within smoke thresholds`() {
        val smallHtml = "<article><p>Small article text.</p></article>"
        val longHtml = buildString {
            append("<article>")
            repeat(300) { append("<p>Long article paragraph $it with enough words for a benchmark smoke test.</p>") }
            append("</article>")
        }

        val smallTime = measureTimeMillis {
            val result = parseHtmlForTest(smallHtml, "https://example.com/small")
            assertTrue(result.content.requireMarkdown().isNotBlank())
        }
        val longTime = measureTimeMillis {
            val result = parseHtmlForTest(longHtml, "https://example.com/long")
            assertTrue(result.content.requireMarkdown().contains("Long article paragraph 299"))
        }

        assertTrue(smallTime < 1_000, "small parse took ${smallTime}ms")
        assertTrue(longTime < 5_000, "long parse took ${longTime}ms")
    }
}
