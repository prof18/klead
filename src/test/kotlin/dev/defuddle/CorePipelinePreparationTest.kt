package dev.defuddle

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CorePipelinePreparationTest {
    @Test
    fun `srcSet attributes normalize to srcset before serialization`() {
        val result = parseHtmlForTest(
            html = """
                <html><body><article>
                  <img src="/small.png" srcSet="/small.png 1x, /large.png 2x">
                </article></body></html>
            """.trimIndent(),
            url = "https://example.com/article",
        )

        assertTrue(result.content.requireHtml().contains("srcset="))
        assertFalse(result.content.requireHtml().contains("srcSet="))
    }

    @Test
    fun `noscript image fallback promotes real image before noscript stripping`() {
        val result = parseHtmlForTest(
            html = """
                <html><body><article>
                  <img src="data:image/svg+xml;base64,placeholder">
                  <noscript><img src="/real.png" alt="Real image"></noscript>
                </article></body></html>
            """.trimIndent(),
            url = "https://example.com/article",
        )

        assertTrue(result.content.requireHtml().contains("""src="/real.png""""))
        assertFalse(result.content.requireHtml().contains("<noscript"))
    }

    @Test
    fun `unsafe elements and attributes are stripped from content html`() {
        val result = parseHtmlForTest(
            html = """
                <html><body><article>
                  <p onclick="steal()">Safe text</p>
                  <a href="javascript:alert(1)">bad link</a>
                  <iframe src="https://example.com/embed"></iframe>
                  <form action="data:text/html,<script>bad()</script>"><button formaction="javascript:bad()">Go</button></form>
                  <div srcdoc="<p>bad</p>">Frame content</div>
                  <style>p { color: red; }</style>
                  <script>alert(1)</script>
                </article></body></html>
            """.trimIndent(),
            url = "https://example.com/article",
        )

        assertTrue(result.content.requireHtml().contains("Safe text"))
        assertFalse(result.content.requireHtml().contains("onclick"))
        assertFalse(result.content.requireHtml().contains("javascript:"))
        assertFalse(result.content.requireHtml().contains("data:text/html"))
        assertFalse(result.content.requireHtml().contains("<iframe"))
        assertFalse(result.content.requireHtml().contains("<style"))
        assertFalse(result.content.requireHtml().contains("<script"))
        assertFalse(result.content.requireHtml().contains("srcdoc"))
    }

    @Test
    fun `trusted youtube iframe is preserved as cleaned html and markdown link`() {
        val result = parseHtmlForTest(
            html = """
                <html><body><article>
                  <p>The article introduction should stay because it is normal prose with useful information. It includes enough words, punctuation, and context for the parser to keep the default cleaned result rather than invoking short-page retries.</p>
                  <iframe src="https://www.youtube.com/embed/1hKyYaBzko8" title="Dwarf Fortress trailer" onclick="bad()" srcdoc="<p>bad</p>"></iframe>
                  <iframe src="https://evil.example/embed/1hKyYaBzko8"></iframe>
                </article></body></html>
            """.trimIndent(),
            url = "https://example.com/article",
        )

        assertTrue(
            result.content.requireHtml().contains("""src="https://www.youtube-nocookie.com/embed/1hKyYaBzko8""""),
        )
        assertTrue(
            result.content.requireHtml().contains(
                """data-defuddle-video-url="https://www.youtube.com/watch?v=1hKyYaBzko8"""",
            ),
        )
        assertFalse(result.content.requireHtml().contains("evil.example"))
        assertFalse(result.content.requireHtml().contains("onclick"))
        assertFalse(result.content.requireHtml().contains("srcdoc"))
        assertTrue(
            result.content.requireMarkdown().contains(
                "[Dwarf Fortress trailer](https://www.youtube.com/watch?v=1hKyYaBzko8)",
            ),
        )
    }

    @Test
    fun `parse timing is present when debug is requested`() {
        val result = parseHtmlForTest(
            html = "<html><body><article><p>Profile on.</p></article></body></html>",
            url = "https://example.com/profile-on",
            options = testOptions(debug = true),
        )

        val parseTimeMillis = result.debug["parseTimeMillis"]
        assertNotNull(parseTimeMillis)
        assertTrue(parseTimeMillis is Long)
        assertTrue(parseTimeMillis >= 0)
    }
}
