package dev.defuddle

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CorePipelineOptionsTest {
    @Test
    fun `options default to Defuddle compatible pipeline settings`() {
        val options = DefuddleOptions()

        assertTrue(options.markdown)
        assertFalse(options.debug)
    }

    @Test
    fun `markdown can be disabled while html remains available`() {
        val result = Defuddle.parseHtml(
            html = "<html><body><article><p>Only HTML output.</p></article></body></html>",
            url = "https://example.com/no-markdown",
            options = DefuddleOptions(markdown = false),
        )

        assertEquals("", result.contentMarkdown)
        assertTrue(result.contentHtml.contains("Only HTML output."))
    }

    @Test
    fun `profile timings are omitted unless debug is requested`() {
        val result = Defuddle.parseHtml(
            html = "<html><body><article><p>Profile off.</p></article></body></html>",
            url = "https://example.com/profile-off",
        )

        assertFalse(result.debug.containsKey("profileTimings"))
    }
}
