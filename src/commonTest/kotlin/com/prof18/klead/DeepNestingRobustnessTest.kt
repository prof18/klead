package com.prof18.klead

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertTrue

// Each shape overflowed a recursive normalizer or renderer on Kotlin/Native before the DOM depth cap,
// crashing the host app. Parsing runs on Dispatchers.Default, whose Darwin threads have small stacks.
class DeepNestingRobustnessTest {
    @Test
    fun `nested divs`() = assertSurvives(nest("<div>", "</div>"))

    @Test
    fun `nested sections`() = assertSurvives(nest("<section><p>x</p>", "</section>"))

    @Test
    fun `nested blockquotes`() = assertSurvives(nest("<blockquote>", "</blockquote>"))

    @Test
    fun `nested lists`() = assertSurvives(nest("<ul><li>", "</li></ul>"))

    @Test
    fun `nested emphasis`() = assertSurvives("<p>" + nest("<b><i>", "</i></b>") + "</p>")

    @Test
    fun `nested spans`() = assertSurvives("<p>" + nest("<span>", "</span>") + "</p>")

    @Test
    fun `nested spans in code block`() = assertSurvives("<pre><code>" + nest("<span>", "</span>") + "</code></pre>")

    @Test
    fun `nested spans in link`() =
        assertSurvives("<p><a href=\"https://example.com/x\">" + nest("<span>", "</span>") + "</a></p>")

    @Test
    fun `nested tables`() = assertSurvives(nest("<table><tr><td>", "</td></tr></table>"))

    @Test
    fun `nested json ld objects`() = assertSurvives(
        body = "",
        head = "<script type=\"application/ld+json\">" + nest("{\"a\":", "}", inner = "1") + "</script>",
        expected = "Lead paragraph",
    )

    private fun nest(open: String, close: String, inner: String = DEEP_TEXT) = buildString {
        repeat(DEPTH) { append(open) }
        append(inner)
        repeat(DEPTH) { append(close) }
    }

    private fun assertSurvives(body: String, head: String = "", expected: String = DEEP_TEXT) {
        val html = "<html><head>$head</head><body><article><h1>Deep nesting</h1>" +
            "<p>Lead paragraph with enough words to be article content for the reader parser.</p>" +
            body + "</article></body></html>"
        for (output in listOf(KleadOutput.HTML, KleadOutput.MARKDOWN)) {
            val result = runBlocking(Dispatchers.Default) {
                Klead.parseHtml(html, "https://example.com/deep", testOptions(outputs = setOf(output)))
            }
            val rendered = when (output) {
                KleadOutput.HTML -> result.content.requireHtml()
                KleadOutput.MARKDOWN -> result.content.requireMarkdown()
            }
            assertTrue(rendered.contains(expected), "$output output lost the nested text: ${rendered.take(500)}")
        }
    }

    private companion object {
        const val DEPTH = 3_000
        const val DEEP_TEXT = "Deepest nested text"
    }
}
