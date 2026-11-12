package com.prof18.klead

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
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

    // Each shape below made a Ksoup replaceWith run on a node an earlier iteration had detached.
    @Test
    fun `gamespot figure with two video embeds`() {
        val embed = "<div class=\"wp-block-gamespot-blocks-video-embed\" data-jw-id=\"abcdefgh\"></div>"
        assertParses(
            "<html><body><article class=\"type-videos\"><figure>$embed$embed</figure>" +
                "<p>$TAIL_TEXT</p></article></body></html>",
            url = "https://www.gamespot.com/videos/x/",
        )
    }

    @Test
    fun `data-definition footnote reference that is another reference's target`() = assertParses(
        page(
            "<p>$TAIL_TEXT <span data-definition=\"b\">1</span><span id=\"b\" data-definition=\"c\">2</span>" +
                "<span id=\"c\">note</span></p>",
        ),
    )

    @Test
    fun `sidenote label that is the previous label's sidenote`() = assertParses(
        page(
            "<p>$TAIL_TEXT<label class=\"footref\" for=\"a\">1</label>" +
                "<label class=\"footref sidenote\" for=\"b\">2</label></p>",
        ),
    )

    @Test
    fun `lyte wrapper with two placeholders`() = assertParses(
        page(
            "<div class=\"lyte-wrapper\"><div id=\"WYL_dQw4w9WgXcQ\"></div><div id=\"WYL_dQw4w9WgXcQ\"></div></div>" +
                "<p>$TAIL_TEXT</p>",
        ),
    )

    @Test
    fun `code wrapping two pre blocks keeps both`() {
        val html = page("<div><code><pre>first block</pre><pre>second block</pre></code></div><p>$TAIL_TEXT</p>")
        assertParses(html, expected = "first block")
        assertParses(html, expected = "second block")
    }

    @Test
    fun `react segment move without a fallback end marker keeps the following siblings`() = assertParses(
        page(
            "<div><template id=\"t\"></template><p>$TAIL_TEXT after the template.</p></div>" +
                "<div hidden id=\"s\"><p>streamed</p></div><script>\$RC(\"t\",\"s\")</script>",
        ),
    )

    @Test
    fun `hacker news comment with a negative indent keeps the thread`() {
        val html = "<html><body><table class=\"fatitem\"><tr class=\"athing\"><td>" +
            "<span class=\"titleline\"><a href=\"https://example.com/story\">Story</a></span></td></tr>" +
            "<tr><td class=\"subtext\"><a class=\"hnuser\">poster</a></td></tr></table>" +
            "<table class=\"comment-tree\"><tr class=\"athing comtr\" id=\"1\"><td>" +
            "<table><tr><td class=\"ind\"><img src=\"s.gif\" width=\"-40\"></td><td>" +
            "<span class=\"comhead\"><a class=\"hnuser\">commenter</a> " +
            "<span class=\"age\" title=\"2026-01-01T00:00:00\">1 day ago</span></span>" +
            "<div class=\"commtext\">$TAIL_TEXT from the comment.</div></td></tr></table></td></tr></table></body></html>"

        assertParses(html, url = "https://news.ycombinator.com/item?id=1", expected = "Comments")
    }

    @Test
    fun `border width does not make an image small`() {
        val image = "<img src=\"https://example.com/photo.jpg\" alt=\"Photo\" style=\"border-width: 2px; height: 30px\">"
        assertParses(page("<p>$TAIL_TEXT</p>$image"), expected = "photo.jpg")
    }

    @Test
    fun `different next images in one wrapper are both kept`() {
        val images = "<div><img src=\"/_next/image?url=%2Fimages%2Ffirst.jpg&amp;w=640\">" +
            "<img src=\"/_next/image?url=%2Fimages%2Fsecond.jpg&amp;w=640\"></div>"
        val html = page("<p>$TAIL_TEXT</p>$images")
        assertParses(html, expected = "first.jpg")
        assertParses(html, expected = "second.jpg")
    }

    // Nested matching containers each copied the same content into the output once per level.
    @Test
    fun `nested chatgpt message wrappers keep one copy of the message`() {
        val depth = 20
        val html = "<html><head><link rel=\"canonical\" href=\"https://chatgpt.com/share/abc\"></head><body>" +
            "<div data-testid=\"conversation-turn-1\">" + "<div data-message-author-role=\"assistant\">".repeat(depth) +
            "<div class=\"markdown\"><p>$UNIQUE_TEXT</p></div>" + "</div>".repeat(depth) + "</div></body></html>"

        assertOccursOnce(html, url = "https://chatgpt.com/share/abc")
    }

    @Test
    fun `nested x tweet articles keep one copy of the tweet`() {
        val depth = 6
        val html = "<html><body>" + "<article data-testid=\"tweet\">".repeat(depth) +
            "<div data-testid=\"tweetText\"><span>$UNIQUE_TEXT</span></div>" + "</article>".repeat(depth) +
            "</body></html>"

        assertOccursOnce(html, url = "https://x.com/a/status/1")
    }

    @Test
    fun `nested mastodon status wrappers keep one copy of the status`() {
        val depth = 6
        val status = "<div class=\"status__content__text\"><p>$UNIQUE_TEXT</p></div>" +
            "<span class=\"display-name__html\">Bob</span><span class=\"display-name__account\">@bob@example.social</span>" +
            "<a class=\"status__relative-time\" href=\"/@bob/2\"><time datetime=\"2026-01-02T00:00:00Z\">x</time></a>"
        val html = "<html><body><div id=\"mastodon\"><div class=\"detailed-status\">" +
            "<div class=\"status__content__text\"><p>$LEAD_TEXT of the main post with enough words.</p></div>" +
            "<span class=\"display-name__html\">Ann</span><span class=\"display-name__account\">@ann@example.social</span>" +
            "</div>" + "<div class=\"status status--in-thread status--first-in-thread\">".repeat(depth) + status +
            "</div>".repeat(depth) + "</div></body></html>"

        assertOccursOnce(html, url = "https://example.social/@ann/1")
    }

    @Test
    fun `ragged table does not pad every row to the widest one`() {
        val columns = 2_000
        val html = page("<table><tr>" + "<td>x</td>".repeat(columns) + "</tr>" + "<tr><td>y</td></tr>".repeat(columns) +
            "</table><p>$TAIL_TEXT</p>")
        val markdown = runBlocking(Dispatchers.Default) {
            Klead.parseHtml(html, "https://example.com/hostile", testOptions(outputs = setOf(KleadOutput.MARKDOWN)))
        }.content.requireMarkdown()

        assertTrue(markdown.contains(TAIL_TEXT), "Markdown lost the content: ${markdown.take(300)}")
        assertTrue(markdown.length < 100_000, "Markdown grew to ${markdown.length} characters")
    }

    @Test
    fun `nested table text is written once`() {
        val depth = 6
        val html = page(
            "<table><tr><td>a</td><td>".repeat(depth) + UNIQUE_TEXT + "</td></tr></table>".repeat(depth) +
                "<p>$TAIL_TEXT</p>",
        )

        assertOccursOnce(html, url = "https://example.com/hostile")
    }

    private fun assertOccursOnce(html: String, url: String) {
        val markdown = runBlocking(Dispatchers.Default) {
            Klead.parseHtml(html, url, testOptions(outputs = setOf(KleadOutput.MARKDOWN)))
        }.content.requireMarkdown()

        assertEquals(1, markdown.split(UNIQUE_TEXT).size - 1, "Expected one copy in: ${markdown.take(600)}")
    }

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
        const val UNIQUE_TEXT = "Distinctive nested message text"
    }
}
