package dev.defuddle

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CorePipelinePreparationTest {
    @Test
    fun `srcSet attributes normalize to srcset before serialization`() {
        val result = Defuddle.parseHtml(
            html = """
                <html><body><article>
                  <img src="/small.png" srcSet="/small.png 1x, /large.png 2x">
                </article></body></html>
            """.trimIndent(),
            url = "https://example.com/article",
        )

        assertTrue(result.contentHtml.contains("srcset="))
        assertFalse(result.contentHtml.contains("srcSet="))
    }

    @Test
    fun `noscript image fallback promotes real image before noscript stripping`() {
        val result = Defuddle.parseHtml(
            html = """
                <html><body><article>
                  <img src="data:image/svg+xml;base64,placeholder">
                  <noscript><img src="/real.png" alt="Real image"></noscript>
                </article></body></html>
            """.trimIndent(),
            url = "https://example.com/article",
        )

        assertTrue(result.contentHtml.contains("""src="/real.png""""))
        assertFalse(result.contentHtml.contains("<noscript"))
    }

    @Test
    fun `unsafe elements and attributes are stripped from content html`() {
        val result = Defuddle.parseHtml(
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

        assertTrue(result.contentHtml.contains("Safe text"))
        assertFalse(result.contentHtml.contains("onclick"))
        assertFalse(result.contentHtml.contains("javascript:"))
        assertFalse(result.contentHtml.contains("data:text/html"))
        assertFalse(result.contentHtml.contains("<iframe"))
        assertFalse(result.contentHtml.contains("<style"))
        assertFalse(result.contentHtml.contains("<script"))
        assertFalse(result.contentHtml.contains("srcdoc"))
    }

    @Test
    fun `profile timings are present only when requested`() {
        val result = Defuddle.parseHtml(
            html = "<html><body><article><p>Profile on.</p></article></body></html>",
            url = "https://example.com/profile-on",
            options = DefuddleOptions(profile = true),
        )

        val timings = result.debug["profileTimings"]
        assertNotNull(timings)
        assertTrue(timings is Map<*, *>)
        assertTrue(timings.containsKey("parseHtml"))
    }
}
