package com.prof18.klead

import com.fleeksoft.ksoup.Ksoup
import com.prof18.klead.internal.dom.SelectorDiagnostics
import com.prof18.klead.internal.dom.selectSafe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.measureTime

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
    fun `security sanitizer strips blob non-image data and whitespace-padded schemes`() {
        val result = parseHtmlForTest(
            html = """
                <article>
                  <p>Body text long enough that the article survives the removal pipeline intact.</p>
                  <a href="blob:https://example.com/6f0f0b0e">blob href</a>
                  <a href="data:application/xhtml+xml,<html/>">xhtml data href</a>
                  <a href="java&#9;script:alert(1)">tab-padded scheme</a>
                  <a href="java&#10;script:alert(2)">newline-padded scheme</a>
                  <a href="  JavaScript:alert(3)">padded uppercase scheme</a>
                  <a href="/relative/path">safe relative link</a>
                  <img src="data:image/png;base64,AAAA" alt="safe inline image">
                </article>
            """.trimIndent(),
            url = "https://example.com/security",
        )

        val html = result.content.requireHtml()
        assertFalse(html.contains("blob:"), html)
        assertFalse(html.contains("data:application"), html)
        assertFalse(html.contains("alert("), html)
        assertTrue(html.contains("/relative/path"), html)
        assertTrue(html.contains("data:image/png"), html)
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
        val document = Ksoup.parse("<article><p>Text</p></article>")

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
    fun `parse of pathologically nested html is cancellable`() {
        val depth = 6_000
        val html = buildString {
            append("<article>")
            repeat(depth) { append("<div>") }
            append("<p>Deep text</p>")
            repeat(depth) { append("</div>") }
            append("</article>")
        }

        runBlocking {
            var completed = false
            val parseJob = launch(Dispatchers.Default) {
                Klead.parseHtml(html, "https://example.com/deep", testOptions())
                completed = true
            }
            delay(250)
            val cancelMillis = measureTime { parseJob.cancelAndJoin() }.inWholeMilliseconds

            assertFalse(completed, "parse finished before cancellation; deepen the fixture")
            assertTrue(cancelMillis < 5_000, "cancellation took ${cancelMillis}ms")
        }
    }
}
