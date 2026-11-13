package com.prof18.klead

import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.nodes.Element
import com.prof18.klead.internal.dom.MAX_DOM_DEPTH
import com.prof18.klead.internal.standardize.HtmlStandardizer
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

    // 63 levels stay under the parse-time cap, but each alert gains a callout-content wrapper.
    @Test
    fun `nested alerts`() = assertSurvives(nest("<div class=\"alert\">", "</div>", depth = 63))

    @Test
    fun `nested alerts stay within the depth cap after standardizing`() {
        val content = Ksoup.parse(
            "<article><p>Lead paragraph</p>" + nest("<div class=\"alert\">", "</div>", depth = 63) + "</article>",
        ).selectFirst("article")!!

        HtmlStandardizer.apply(content, title = null)

        assertTrue(content.maxDepth() <= MAX_DOM_DEPTH, "depth ${content.maxDepth()} passed the cap")
        assertTrue(content.text().contains(DEEP_TEXT))
    }

    private fun Element.maxDepth(): Int {
        var deepest = 0
        val pending = ArrayDeque(listOf(this to 0))
        while (pending.isNotEmpty()) {
            val (element, depth) = pending.removeAt(pending.lastIndex)
            deepest = maxOf(deepest, depth)
            element.children().forEach { pending.addLast(it to depth + 1) }
        }
        return deepest
    }

    private fun nest(open: String, close: String, inner: String = DEEP_TEXT, depth: Int = DEPTH) = buildString {
        repeat(depth) { append(open) }
        append(inner)
        repeat(depth) { append(close) }
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
