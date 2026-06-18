package dev.defuddle

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CorePipelineOptionsTest {
    @Test
    fun `options require explicit outputs and default debug off`() {
        val options = DefuddleOptions(outputs = setOf(DefuddleOutput.MARKDOWN))

        assertEquals(setOf(DefuddleOutput.MARKDOWN), options.outputs)
        assertFalse(options.debug)
    }

    @Test
    fun `html can be requested without markdown`() {
        val result = Defuddle.parseHtml(
            html = "<html><body><article><p>Only HTML output.</p></article></body></html>",
            url = "https://example.com/no-markdown",
            options = DefuddleOptions(outputs = setOf(DefuddleOutput.HTML)),
        )

        assertNull(result.content.markdown)
        assertTrue(result.content.requireHtml().contains("Only HTML output."))
        assertFailsWith<IllegalStateException> {
            result.content.requireMarkdown()
        }
    }

    @Test
    fun `markdown can be requested without html`() {
        val result = Defuddle.parseHtml(
            html = "<html><body><article><p>Only Markdown output.</p></article></body></html>",
            url = "https://example.com/no-html",
            options = DefuddleOptions(outputs = setOf(DefuddleOutput.MARKDOWN)),
        )

        assertNull(result.content.html)
        assertTrue(result.content.requireMarkdown().contains("Only Markdown output."))
        assertFailsWith<IllegalStateException> {
            result.content.requireHtml()
        }
    }

    @Test
    fun `at least one output must be requested`() {
        assertFailsWith<IllegalArgumentException> {
            DefuddleOptions(outputs = emptySet())
        }
    }

    @Test
    fun `parse timing is omitted unless debug is requested`() {
        val result = Defuddle.parseHtml(
            html = "<html><body><article><p>Profile off.</p></article></body></html>",
            url = "https://example.com/profile-off",
            options = DefuddleOptions(outputs = setOf(DefuddleOutput.MARKDOWN)),
        )

        assertFalse(result.debug.containsKey("parseTimeMillis"))
    }
}
