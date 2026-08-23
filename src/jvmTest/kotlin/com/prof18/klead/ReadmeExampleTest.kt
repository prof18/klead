package com.prof18.klead

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ReadmeExampleTest {
    @Test
    fun `README basic API example compiles and returns markdown`() = runTest {
        val html = "<article><h1>Example</h1><p>Body text.</p></article>"
        val url = "https://example.com/article"

        val result = Klead.parseHtml(
            html = html,
            url = url,
            options = KleadOptions(outputs = setOf(KleadOutput.MARKDOWN)),
        )

        assertTrue(result.content.requireMarkdown().contains("Body text."))
    }

    @Test
    fun `README debug example compiles`() = runTest {
        val html = """
            <article>
              <p>Visible debug text with enough ordinary prose to keep the default parse result selected. This avoids the sparse-page retry that intentionally preserves hidden elements when a document is too short to trust. Additional sentences provide a realistic article body, stable word count, and enough punctuation for the removal diagnostics to remain deterministic.</p>
              <aside hidden>Hidden</aside>
            </article>
        """.trimIndent()
        val url = "https://example.com/debug"

        val result = Klead.parseHtml(
            html = html,
            url = url,
            options = KleadOptions(
                outputs = setOf(KleadOutput.MARKDOWN),
                debug = true,
            ),
        )

        val removals = result.debug["removals"] as? List<*>
        assertNotNull(removals)
    }
}
