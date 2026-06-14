package dev.defuddle

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CorePipelineOptionsTest {
    @Test
    fun `options default to Defuddle compatible pipeline settings`() {
        val options = DefuddleOptions()

        assertTrue(options.removeExactSelectors)
        assertTrue(options.removePartialSelectors)
        assertTrue(options.removeHiddenElements)
        assertTrue(options.removeLowScoring)
        assertTrue(options.removeSmallImages)
        assertFalse(options.removeImages)
        assertTrue(options.removeContentPatterns)
        assertTrue(options.standardize)
        assertTrue(options.markdown)
        assertTrue(options.separateMarkdown)
        assertFalse(options.debug)
        assertFalse(options.profile)
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
    fun `profile timings are omitted unless requested`() {
        val result = Defuddle.parseHtml(
            html = "<html><body><article><p>Profile off.</p></article></body></html>",
            url = "https://example.com/profile-off",
        )

        assertFalse(result.debug.containsKey("profileTimings"))
    }
}
