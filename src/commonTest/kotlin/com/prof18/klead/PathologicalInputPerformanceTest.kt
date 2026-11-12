package com.prof18.klead

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.measureTime

// Shapes that made a parse take minutes or never finish (exponential re-rendering, quadratic sibling or
// selector scans). Each must now finish well within the budget on every platform, Kotlin/Native included.
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

    // Shapes from the 2026-11 audit that grew quadratically. Each is parsed at n and 16n: linear work grows
    // about 16×, the old quadratic paths up to 256×. A ratio is used instead of a fixed budget so the check
    // holds on fast and slow platforms alike.
    @Test
    fun `many footnotes under one body wrapper scale linearly`() = assertScalesLinearly(150) { n ->
        page(
            "<div class=\"body\">" + repeatIndexed(n * 4) { "<p>Body paragraph $it $LEAD_TEXT words.</p>" } + "<div>" +
                repeatIndexed(n) { "<p id=\"ftnt$it\"><a href=\"#ftnt_ref$it\">[$it]</a> note</p>" } + "</div></div>",
        )
    }

    @Test
    fun `many share links inside one paragraph scale linearly`() = assertScalesLinearly(1_000) { n ->
        page("<p>" + repeatIndexed(n) { "<a href=\"/share/$it\">word$it</a> " } + "</p>")
    }

    @Test
    fun `many bare spans in one paragraph scale linearly`() = assertScalesLinearly(2_500) { n ->
        page("<p>" + "<span>word </span>".repeat(n) + "</p>")
    }

    @Test
    fun `many top-level paragraphs with newlines scale linearly`() = assertScalesLinearly(5_000) { n ->
        "<html><body><article><h1>Pathological</h1>" + "<p>a b c d</p>\n".repeat(n) + "</article></body></html>"
    }

    @Test
    fun `many leading horizontal rules scale linearly`() = assertScalesLinearly(4_000) { n ->
        "<html><head><title>Pathological</title></head><body><article><h1>Pathological</h1>" + "<hr>".repeat(n) +
            "<p>$LEAD_TEXT one.</p><p>$LEAD_TEXT two.</p></article></body></html>"
    }

    @Test
    fun `many code blocks after a large sibling scale linearly`() = assertScalesLinearly(200) { n ->
        page("<div>" + repeatIndexed(n * 4) { "<p>Body paragraph $it $LEAD_TEXT words.</p>" } + "</div><div>" +
            "<pre>x</pre>".repeat(n) + "</div>")
    }

    @Test
    fun `many quiz headings under shared wrappers scale linearly`() = assertScalesLinearly(300) { n ->
        page(nest("<div>", "</div>", depth = 20, inner = "<h2>Quiz time</h2>".repeat(n)))
    }

    @Test
    fun `many images sharing a file name scale linearly`() = assertScalesLinearly(300) { n ->
        page(nest("<div>", "</div>", depth = 30, inner = repeatIndexed(n) { "<img src=\"/i/$it/photo.jpg\">" } + "<p>$LEAD_TEXT</p>"))
    }

    @Test
    fun `many recommendation headings in one wrapper scale linearly`() = assertScalesLinearly(1_000) { n ->
        page("<div>" + "<h2>Related articles</h2>".repeat(n) + "</div>")
    }

    @Test
    fun `many sources in one picture scale linearly`() = assertScalesLinearly(500) { n ->
        page("<picture>" + "<source srcset=\"a.jpg 10w\"><img>".repeat(n) + "</picture>")
    }

    @Test
    fun `many hidden spans under a huge attribute name scale linearly`() = assertScalesLinearly(1_000) { n ->
        page("<div data-" + "a".repeat(n * 20) + "=\"1\">" + "<span hidden>h</span>".repeat(n) + "<p>$LEAD_TEXT</p></div>")
    }

    @Test
    fun `many dhammatalks notes scale linearly`() = assertScalesLinearly(200) { n ->
        page(repeatIndexed(n) { "<div class=\"note\"><p id=\"anote$it\">n</p></div>" } + "<p>" + "<a href=\"x\">l</a> ".repeat(n * 10) + "</p>")
    }

    @Test
    fun `many single cell tables scale linearly`() = assertScalesLinearly(4_000) { n ->
        page("<table><tr><td>x</td></tr></table>".repeat(n))
    }

    @Test
    fun `many layout tables scale linearly`() = assertScalesLinearly(1_500) { n ->
        page(repeatIndexed(n) { "<table><tr><td><p>Layout cell $it $LEAD_TEXT</p></td></tr></table>" })
    }

    @Test
    fun `many empty articles scale linearly`() = assertScalesLinearly(5_000) { n ->
        page("<article></article>".repeat(n))
    }

    @Test
    fun `many next image noscripts in one figure scale linearly`() = assertScalesLinearly(1_000) { n ->
        page("<figure>" + repeatIndexed(n) { "<noscript><img data-nimg src=a$it></noscript>" } + "</figure>")
    }

    @Test
    fun `many short divs before the title scale linearly`() = assertScalesLinearly(4_000) { n ->
        "<html><head><title>Pathological</title></head><body><article>" + "<div>x</div>".repeat(n) +
            "<h1>Pathological</h1><p>$LEAD_TEXT one.</p><p>$LEAD_TEXT two.</p></article></body></html>"
    }

    @Test
    fun `many react segment calls without targets scale linearly`() = assertScalesLinearly(1_000) { n ->
        page("<script>" + "\$RC(\"a\",\"b\");".repeat(n) + "</script>" + "<br>".repeat(n * 3))
    }

    @Test
    fun `long react fallback scales linearly`() = assertScalesLinearly(3_000) { n ->
        page("<div><!--\$?--><template id=\"t\"></template>" + "<p>x</p>".repeat(n) + "<!--/\$--></div>" +
            "<div hidden id=\"s\"><p>$LEAD_TEXT streamed.</p></div><script>\$RC(\"t\",\"s\")</script>")
    }

    @Test
    fun `many paragraphs after further reading scale linearly`() = assertScalesLinearly(4_000) { n ->
        page("<h2>Further Reading</h2>" + "<p>x</p>".repeat(n))
    }

    @Test
    fun `many json-ld image references scale linearly`() = assertScalesLinearly(1_000) { n ->
        val items = "{\"@type\":\"Article\",\"image\":{\"@id\":\"x\"},\"primaryImageOfPage\":{\"@id\":\"x\"}},".repeat(n)
        "<html><head><script type=\"application/ld+json\">[$items{}]</script></head><body><article>" +
            "<h1>Pathological</h1><p>$LEAD_TEXT</p></article></body></html>"
    }

    @Test
    fun `many src-less images scale linearly`() = assertScalesLinearly(4_000) { n ->
        page("<div>" + "<img>".repeat(n) + "</div>")
    }

    @Test
    fun `many footnote definition blocks scale linearly`() = assertScalesLinearly(2_500) { n ->
        page("<div class=\"footnote-definition\">x</div>".repeat(n))
    }

    @Test
    fun `many line breaks in one alert scale linearly`() = assertScalesLinearly(15_000) { n ->
        page("<div class=\"alert\">" + "a<br>".repeat(n) + "</div>")
    }

    @Test
    fun `many sibling scripts styles and iframes scale linearly`() = assertScalesLinearly(5_000) { n ->
        page("<script></script><style></style><iframe></iframe>".repeat(n))
    }

    private fun page(body: String) = "<html><body><article><h1>Pathological</h1>" +
        "<p>$LEAD_TEXT with enough words to be article content for the reader parser.</p>" +
        body + "</article></body></html>"

    private fun nest(open: String, close: String, depth: Int, inner: String) = buildString {
        repeat(depth) { append(open) }
        append(inner)
        repeat(depth) { append(close) }
    }

    @Test
    fun `many gamespot video embeds and ld+json scripts scale linearly`() = assertScalesLinearly(
        60,
        url = "https://www.gamespot.com/videos/x/",
    ) { n ->
        "<html><head>" + repeatIndexed(n) {
            "<script type=\"application/ld+json\">{\"contentUrl\":\"https://cdn.jwplayer.com/videos/x$it\"}</script>"
        } + "</head><body><article class=\"type-videos\">" +
            repeatIndexed(n) {
                "<div class=\"wp-block-gamespot-blocks-video-embed\" data-jw-id=\"abcd${it.toString().padStart(4, '0')}\"></div>"
            } + "<p>$LEAD_TEXT</p></article></body></html>"
    }

    @Test
    fun `many times of india break markers scale linearly`() = assertScalesLinearly(
        2_000,
        url = "https://timesofindia.indiatimes.com/x",
    ) { n -> "<html><body><div data-articlebody>" + "a<span class=\"br\"></span>".repeat(n) + "</div></body></html>" }

    private fun repeatIndexed(count: Int, item: (Int) -> String) = buildString { repeat(count) { append(item(it)) } }

    private fun assertScalesLinearly(n: Int, url: String = "https://example.com/pathological", html: (Int) -> String) {
        val small = html(n)
        val large = html(n * GROWTH_STEP)
        fun best(page: String, runs: Int) = (1..runs).minOf {
            measureTime {
                runBlocking(Dispatchers.Default) { Klead.parseHtml(page, url, testOptions()) }
            }
        }
        best(small, runs = 2) // Warm-up.
        // Garbage collection and compilation make single timings noisy; a quadratic path fails every attempt.
        val attempts = (1..GROWTH_ATTEMPTS).map {
            val smallTime = maxOf(best(small, runs = 3), MIN_MEASURED_TIME)
            val largeTime = best(large, runs = 2)
            if (largeTime < smallTime * MAX_GROWTH) return
            "${largeTime / smallTime}× ($smallTime → $largeTime)"
        }
        fail("${GROWTH_STEP}× the input took ${attempts.joinToString()} the time")
    }

    private fun assertFast(html: String, expected: String? = DEEP_TEXT, url: String = "https://example.com/pathological") {
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
        const val GROWTH_STEP = 16
        // Linear work grows about 16× (a little more with garbage collection); the old paths grew about 256×.
        const val MAX_GROWTH = 32
        const val GROWTH_ATTEMPTS = 3
        val MIN_MEASURED_TIME = 10.milliseconds
        const val DEEP_TEXT = "Readable tail paragraph"
        const val LEAD_TEXT = "Lead paragraph"
        const val NBSP_PARAGRAPH = "<p>&nbsp;</p>"
    }
}
