package com.prof18.klead

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.measureTime

// Shapes that made a parse take minutes or never finish (exponential re-rendering, quadratic sibling or
// selector scans). Each must now finish well within the budget on every platform, Kotlin/Native included.
// The growth-ratio checks for the shapes that grew quadratically are in the JVM-only LinearGrowthTest.
class PathologicalInputPerformanceTest {
    @Test
    fun `nested blank asides render in linear time`() =
        assertFast(page(nest("<aside>", "</aside>", depth = 60, inner = NBSP_PARAGRAPH)), expected = LEAD_TEXT)

    @Test
    fun `nested blank definition list wrappers render in linear time`() =
        assertFast(page(nest("<dl><div>", "</div></dl>", depth = 30, inner = NBSP_PARAGRAPH)), expected = LEAD_TEXT)

    @Test
    fun `nested callouts stay within the stack at the depth cap`() =
        assertFast(page(nest("<div class=\"callout\">", "</div>", depth = 3_000, inner = "<p>$DEEP_TEXT</p>")))

    @Test
    fun `many horizontal rules before the tail`() =
        assertFast(page("<div>" + "<hr>".repeat(5_000) + "<p>$DEEP_TEXT</p></div>"))

    @Test
    fun `many hidden siblings after a heading`() =
        assertFast(page("<div><h2>Heading</h2>" + "<p hidden>x</p>".repeat(5_000) + "<p>$DEEP_TEXT</p></div>"))

    @Test
    fun `many empty sibling wrappers`() =
        assertFast(page("<div>" + "<div></div>".repeat(5_000) + "<p>$DEEP_TEXT</p></div>"))

    @Test
    fun `huge schema article body`() {
        val needle = "a".repeat(50_000) + "b"
        val head = "<script type=\"application/ld+json\">{\"@type\":\"Article\",\"articleBody\":\"$needle\"}</script>"
        assertFast(
            html = "<html><head>$head</head><body><div><div><div><div>" + "a".repeat(200_000) +
                "</div></div></div></div><p>$DEEP_TEXT</p></body></html>",
            expected = null,
        )
    }

    @Test
    fun `duplicate images in nested figures`() {
        val images = "<img src=\"https://example.com/i.png\">".repeat(2_000)
        assertFast(page(nest("<figure>", "</figure>", depth = 60, inner = images)), expected = LEAD_TEXT)
    }

    @Test
    fun `many data-definition footnotes`() {
        val references = (1..1_000).joinToString("") { "<span data-definition=\"d$it\">$it</span>" }
        val ids = (1..8_000).joinToString("") { "<span id=\"z$it\">.</span>" }
        assertFast(page("<p>$DEEP_TEXT</p><p>$references</p><p>$ids</p>"))
    }

    @Test
    fun `blockquote with many paragraphs`() =
        assertFast(page("<blockquote>" + "<p>para</p>".repeat(10_000) + "<p>$DEEP_TEXT</p></blockquote>"))

    @Test
    fun `many leading byline blocks`() {
        val bylines = "<p class=\"byline\">By John Doe</p>".repeat(5_000)
        assertFast(page("<div>$bylines<p>$DEEP_TEXT with more words.</p></div>"))
    }

    @Test
    fun `nested external footnote sections`() {
        val filler = "<p>" + "word ".repeat(200) + "</p>"
        val section = "<section class=\"footnotes\"><p><sup>1</sup> note</p>$filler"
        val sections = nest(section, "</section>", depth = 60, inner = "")
        val article = "<main><p>$DEEP_TEXT<sup>1</sup> with more words here to count.</p></main>"
        assertFast(
            html = "<html><body>$article$sections</body></html>",
            // Content detection prefers the wordier footnote sections here; only the time budget is under test.
            expected = null,
        )
    }

    // Shapes from the 2026-11 audit that hung a parse for minutes on small inputs.
    @Test
    fun `long run of zeros in a max-height declaration`() =
        assertFast(page("<div style=\"max-height:" + "0".repeat(50_000) + "!\"><p>$DEEP_TEXT</p></div>"))

    @Test
    fun `footnote definition made of link openers`() = assertFast(
        page(
            "<p>$DEEP_TEXT<sup><a href=\"#fn1\">1</a></sup></p><ol class=\"footnotes\"><li id=\"fn1\">" +
                "](".repeat(40_000) + ".</li></ol>",
        ),
    )

    @Test
    fun `nested links kept apart by marquee`() {
        val depth = 24
        val links = (1..depth).joinToString("") { "<marquee><a href=\"https://e.com/$it\">" }
        assertFast(
            page("<p><a href=\"https://e.com/0\">$links$DEEP_TEXT" + "</a></marquee>".repeat(depth) + "</a></p>"),
        )
    }

    @Test
    fun `reddit comment id cannot inject a selector regex`() {
        val thingId = "x,:matches((.*a){10}b),#y"
        val html = "<html><body><shreddit-post><div slot=\"text-body\"><div property=\"schema:articleBody\">" +
            "<p>$DEEP_TEXT of the post.</p></div></div></shreddit-post>" +
            "<shreddit-comment author=\"a\" created=\"2026-01-01T00:00:00Z\" thingid=\"$thingId\">" +
            "<div id=\"$thingId-comment-rtjson-content\"><p>${"a".repeat(40)}!</p></div></shreddit-comment>" +
            "</body></html>"
        assertFast(html, url = "https://www.reddit.com/r/a/comments/1/")
    }

    @Test
    fun `many github comment bodies in one comment`() {
        val bodies = "<div class=\"comment-body markdown-body\">x</div>".repeat(20_000)
        assertFast(
            "<html><body><div class=\"pull-discussion-timeline\"><div class=\"js-comment\">" +
                "<div class=\"comment-body markdown-body\"><p>$DEEP_TEXT</p></div>$bodies</div></div></body></html>",
            url = "https://github.com/a/b/pull/1",
        )
    }

    @Test
    fun `many guardian newsletter cells in one module`() {
        val cells = "<tr><td class=\"text\">lorem ipsum dolor sit amet</td></tr>".repeat(10_000)
        assertFast(
            "<html><head><title>X | The Guardian</title></head><body>" +
                "<img alt=\"Football Daily\" src=\"https://i.guim.co.uk/a.png\">" +
                "<div aria-roledescription=\"email\" role=\"article\"><h1>T</h1><div style=\"max-width:600px\"><table>" +
                "<tr><td class=\"text\">$DEEP_TEXT</td></tr>$cells</table></div></div></body></html>",
            url = "https://kill-the-newsletter.com/feeds/x.xml",
        )
    }

    @Test
    fun `steam event body with many unclosed paragraph tags`() = assertFast(
        "<html><body><div id=\"application_config\" data-partnereventstore='[{\"announcement_body\":{\"body\":\"" +
            "$DEEP_TEXT " + "[p]".repeat(80_000) + "\"}}]'></div></body></html>",
        url = "https://example.com/steam",
    )

    @Test
    fun `substack note title with many handle openers`() = assertFast(
        "<html><head><link href=\"https://substackcdn.com/a.css\">" +
            "<meta property=\"og:url\" content=\"https://substack.com/@a/note/c-1\">" +
            "<meta property=\"og:title\" content=\"x" + " (@".repeat(80_000) + "\"></head>" +
            "<body><div class=\"feedCommentBody\"><p>$DEEP_TEXT</p></div></body></html>",
        url = "https://example.com/s",
        expected = null,
    )

    private fun page(body: String) = "<html><body><article><h1>Pathological</h1>" +
        "<p>$LEAD_TEXT with enough words to be article content for the reader parser.</p>" +
        body + "</article></body></html>"

    private fun nest(open: String, close: String, depth: Int, inner: String) = buildString {
        repeat(depth) { append(open) }
        append(inner)
        repeat(depth) { append(close) }
    }

    private fun assertFast(
        html: String,
        expected: String? = DEEP_TEXT,
        url: String = "https://example.com/pathological",
    ) {
        for (output in KleadOutput.entries) {
            lateinit var result: KleadResult
            val elapsed = measureTime {
                result = runBlocking(Dispatchers.Default) {
                    Klead.parseHtml(html, url, testOptions(outputs = setOf(output)))
                }
            }
            assertTrue(elapsed.inWholeSeconds < BUDGET_SECONDS, "$output parse took $elapsed")
            if (expected != null) {
                val rendered = when (output) {
                    KleadOutput.HTML -> result.content.requireHtml()
                    KleadOutput.MARKDOWN -> result.content.requireMarkdown()
                }
                assertTrue(rendered.contains(expected), "$output output lost the content: ${rendered.take(300)}")
            }
        }
    }

    private companion object {
        // Generous for slow CI runners executing debug native binaries; the guarded shapes used to take minutes.
        const val BUDGET_SECONDS = 60
        const val DEEP_TEXT = "Readable tail paragraph"
        const val LEAD_TEXT = "Lead paragraph"
        const val NBSP_PARAGRAPH = "<p>&nbsp;</p>"
    }
}
