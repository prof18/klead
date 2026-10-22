package com.prof18.klead

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.measureTime

// Hostile pages from the second robustness audit. Any page can claim a site extractor through its
// canonical URL, so the site-specific shapes below are reachable from arbitrary pages too.
class HostilePageRegressionTest {
    @Test
    fun `react streaming replay cannot re-nest a reddit thread past the stack`() {
        val moves = 12
        val html = buildString {
            append("<html><head><link rel=\"canonical\" href=\"https://www.reddit.com/r/x/comments/1/t/\">")
            append("</head><body>")
            append("<div class=\"thing link\" data-author=\"a\" data-subreddit=\"x\"><div class=\"entry\">")
            append("<div class=\"usertext-body\"><div class=\"md\"><p>$LEAD_TEXT of the post.</p></div></div>")
            append("</div></div>")
            append("<div class=\"commentarea\"><div class=\"sitetable\"><template id=\"T0\"></template>")
            append("<!--\$?--><p>fallback</p><!--/\$--></div></div>")
            for (move in 0 until moves) {
                var inner = "<template id=\"T${move + 1}\"></template><!--\$?--><p>fallback</p><!--/\$-->"
                repeat(20) { inner = redditComment(inner) }
                append("<div hidden id=\"S$move\">$inner</div>")
            }
            append("<div hidden id=\"S$moves\">${redditComment("")}</div><script>")
            for (move in moves downTo 0) append("\$RC(\"T$move\",\"S$move\");")
            append("</script></body></html>")
        }

        assertParses(html, url = "https://example.com/thread", expected = LEAD_TEXT)
    }

    @Test
    fun `page text in a chatgpt role cannot break a selector`() {
        val html = "<html><head><link rel=\"canonical\" href=\"https://chatgpt.com/share/abc\"></head><body>" +
            "<div data-testid=\"conversation-turn-1\"><div data-message-author-role=\"user]\">" +
            "<div class=\"markdown\"><p>$LEAD_TEXT from the conversation.</p></div></div></div></body></html>"

        assertParses(html, url = "https://example.com/chat", expected = null)
    }

    @Test
    fun `many chatgpt message blocks`() {
        val turn = "<div data-testid=\"conversation-turn-1\"><div data-message-author-role=\"assistant\">" +
            "<div class=\"markdown\"><p>$LEAD_TEXT word</p></div>".repeat(5_000) + "</div></div>"
        val html = "<html><head><link rel=\"canonical\" href=\"https://chatgpt.com/share/abc\"></head>" +
            "<body>$turn</body></html>"

        assertParses(html, url = "https://example.com/chat", expected = LEAD_TEXT)
    }

    @Test
    fun `many share-details footer candidates`() {
        val block = "<div>" + "share details ".repeat(107) + "</div>"
        assertParses(page(block.repeat(200) + "<p>$TAIL_TEXT</p>"))
    }

    @Test
    fun `many heading permalink anchors`() {
        val anchors = (0 until 5_000).joinToString("") { "<a href=\"#h$it\"><svg><path d=\"M0 0\"/></svg></a>" }
        assertParses(page("<div><h2 id=\"h1\">Heading</h2>$anchors<p>$TAIL_TEXT</p></div>"))
    }

    @Test
    fun `footnote definition pointing at the article keeps the article`() {
        val html = "<html><body><article id=\"n1\"><h1>Title</h1>" +
            "<p>$LEAD_TEXT with enough words to be article content for the reader parser today.</p>" +
            "<p>Body text <span data-definition=\"n1\">1</span> more text.</p></article></body></html>"

        assertParses(html, expected = LEAD_TEXT)
    }

    @Test
    fun `many images in one noscript`() {
        val images = (0 until 10_000).joinToString("") { "<img src=\"/i/$it.png\">" }
        assertParses(page("<noscript>$images</noscript><p>$TAIL_TEXT</p>"))
    }

    @Test
    fun `wrapper with many children is unwrapped in linear time`() =
        assertParses(page("<p><span>" + "<i>x</i>".repeat(10_000) + "</span> $TAIL_TEXT</p>"))

    @Test
    fun `single cell table with many children is unwrapped in linear time`() =
        assertParses(page("<table><tr><td>" + "<i>x</i>".repeat(10_000) + "</td></tr></table><p>$TAIL_TEXT</p>"))

    @Test
    fun `many hidden siblings are removed in linear time`() =
        assertParses(page("<div>" + "<span hidden>h</span>".repeat(10_000) + "<p>$TAIL_TEXT</p></div>"))

    @Test
    fun `nested image placeholder wrappers`() {
        val open = "<div class=\"image-wrapper\" style=\"padding-bottom:56.25%\">"
        val body = open.repeat(20) + "<img src=\"/a.png\">" + "<p>w</p>".repeat(10_000) + "</div>".repeat(20)
        assertParses(page("$body<p>$TAIL_TEXT</p>"))
    }

    @Test
    fun `many further reading headings`() =
        // A trailing "Further Reading" section takes everything after it, the tail paragraph included.
        assertParses(page("<h2>Further Reading</h2><p></p>".repeat(3_000) + "<p>$TAIL_TEXT</p>"), expected = LEAD_TEXT)

    private fun redditComment(replies: String) = "<div class=\"thing comment\" data-permalink=\"/r/x/c\">" +
        "<div class=\"entry\"><div class=\"tagline\"><a class=\"author\">user</a>" +
        "<time datetime=\"2024-01-01T00:00:00Z\"></time></div>" +
        "<div class=\"usertext-body\"><div class=\"md\"><p>comment</p></div></div></div>" +
        "<div class=\"child\"><div class=\"sitetable\">$replies</div></div></div>"

    private fun page(body: String) = "<html><body><article><h1>Hostile</h1>" +
        "<p>$LEAD_TEXT with enough words to be article content for the reader parser.</p>" +
        body + "</article></body></html>"

    private fun assertParses(html: String, url: String = "https://example.com/hostile", expected: String? = TAIL_TEXT) {
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
        const val LEAD_TEXT = "Lead paragraph"
        const val TAIL_TEXT = "Readable tail paragraph"
    }
}
