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

    @Test
    fun `removeImages option removes images from html and markdown`() {
        val result = Defuddle.parseHtml(
            html = """
                <article>
                  <p>Article prose has enough words to keep the default parse result selected while image removal is tested.</p>
                  <p>Another sentence makes this realistic article text stable for the retry controller.</p>
                  <img src="/hero.png" alt="Hero">
                </article>
            """.trimIndent(),
            url = "https://example.com/images",
            options = DefuddleOptions(removeImages = true),
        )

        assertFalse(result.contentHtml.contains("<img"))
        assertFalse(result.contentMarkdown.contains("![Hero]"))
    }

    @Test
    fun `removeSmallImages option removes icon sized images and can be disabled`() {
        val html = """
            <article>
              <p>Article prose has enough words to keep the default parse result selected while small image removal is tested.</p>
              <p>Another sentence makes this realistic article text stable for the retry controller.</p>
              <p><img src="/icon.png" alt="Icon" width="16" height="16"></p>
            </article>
        """.trimIndent()

        val defaultResult = Defuddle.parseHtml(html, "https://example.com/icon")
        val disabledResult = Defuddle.parseHtml(
            html = html,
            url = "https://example.com/icon",
            options = DefuddleOptions(removeSmallImages = false),
        )

        assertFalse(defaultResult.contentHtml.contains("icon.png"))
        assertTrue(disabledResult.contentMarkdown.contains("![Icon](https://example.com/icon.png)"))
    }
}
