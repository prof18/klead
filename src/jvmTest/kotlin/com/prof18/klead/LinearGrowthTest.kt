package com.prof18.klead

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.fail
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.measureTime

// Shapes from the 2026-11 audit that grew quadratically. Each is parsed at n and 16n: linear work grows
// about 16×, the old quadratic paths up to 256×. A ratio is used instead of a fixed budget so the check
// holds on fast and slow machines alike. Sizes are kept small but large enough that the code before the
// fixes (1de82a4) grew at least about 48× at that size; the current code grows about 15 to 24×.
//
// These run on the JVM only. The parser is common code with no platform-specific source, so a quadratic
// path is quadratic on every target, and debug Kotlin/Native binaries are too slow to parse inputs large
// enough to tell linear from quadratic in reasonable time. Native-specific risks (stack depth, the selector
// cache, concurrency, crashes and hangs) stay covered in commonTest.
class LinearGrowthTest {
    @Test
    fun `many footnotes under one body wrapper scale linearly`() = assertScalesLinearly(150) { n ->
        page(
            "<div class=\"body\">" + repeatIndexed(n * 4) { "<p>Body paragraph $it $LEAD_TEXT words.</p>" } + "<div>" +
                repeatIndexed(n) { "<p id=\"ftnt$it\"><a href=\"#ftnt_ref$it\">[$it]</a> note</p>" } + "</div></div>",
        )
    }

    @Test
    fun `many share links inside one paragraph scale linearly`() = assertScalesLinearly(500) { n ->
        page("<p>" + repeatIndexed(n) { "<a href=\"/share/$it\">word$it</a> " } + "</p>")
    }

    @Test
    fun `many bare spans in one paragraph scale linearly`() = assertScalesLinearly(2_500) { n ->
        page("<p>" + "<span>word </span>".repeat(n) + "</p>")
    }

    // Line breaks keep the other passes cheap, so the old one-by-one removal of blank top-level text dominates.
    @Test
    fun `many top-level line breaks with newlines scale linearly`() = assertScalesLinearly(5_000) { n ->
        page("<br>\n".repeat(n))
    }

    @Test
    fun `many leading horizontal rules scale linearly`() = assertScalesLinearly(1_000) { n ->
        "<html><head><title>Pathological</title></head><body><article><h1>Pathological</h1>" + "<hr>".repeat(n) +
            "<p>$LEAD_TEXT one.</p><p>$LEAD_TEXT two.</p></article></body></html>"
    }

    @Test
    fun `many code blocks after a large sibling scale linearly`() = assertScalesLinearly(300) { n ->
        page(
            "<div>" + repeatIndexed(n * 4) { "<p>Body paragraph $it $LEAD_TEXT words.</p>" } + "</div><div>" +
                "<pre>x</pre>".repeat(n) + "</div>",
        )
    }

    @Test
    fun `many quiz headings under shared wrappers scale linearly`() = assertScalesLinearly(300) { n ->
        page(nest("<div>", "</div>", depth = 20, inner = "<h2>Quiz time</h2>".repeat(n)))
    }

    @Test
    fun `many images sharing a file name scale linearly`() = assertScalesLinearly(300) { n ->
        page(
            nest(
                "<div>",
                "</div>",
                depth = 30,
                inner = repeatIndexed(n) { "<img src=\"/i/$it/photo.jpg\">" } + "<p>$LEAD_TEXT</p>",
            ),
        )
    }

    @Test
    fun `many recommendation headings in one wrapper scale linearly`() = assertScalesLinearly(250) { n ->
        page("<div>" + "<h2>Related articles</h2>".repeat(n) + "</div>")
    }

    @Test
    fun `many sources in one picture scale linearly`() = assertScalesLinearly(500) { n ->
        page("<picture>" + "<source srcset=\"a.jpg 10w\"><img>".repeat(n) + "</picture>")
    }

    @Test
    fun `many hidden spans under a huge attribute name scale linearly`() = assertScalesLinearly(500) { n ->
        page(
            "<div data-" + "a".repeat(
                n * 20,
            ) + "=\"1\">" + "<span hidden>h</span>".repeat(n) + "<p>$LEAD_TEXT</p></div>",
        )
    }

    @Test
    fun `many dhammatalks notes scale linearly`() = assertScalesLinearly(100) { n ->
        page(
            repeatIndexed(
                n,
            ) { "<div class=\"note\"><p id=\"anote$it\">n</p></div>" } + "<p>" + "<a href=\"x\">l</a> ".repeat(
                n * 10,
            ) + "</p>",
        )
    }

    @Test
    fun `many single cell tables scale linearly`() = assertScalesLinearly(2_000) { n ->
        page("<table><tr><td>x</td></tr></table>".repeat(n))
    }

    // Two short cells per table keep the other passes cheap, so the old per-table sibling insert dominates.
    @Test
    fun `many layout tables scale linearly`() = assertScalesLinearly(2_000) { n ->
        page("<table class=\"layout\"><tr><td>x</td><td>y</td></tr></table>".repeat(n))
    }

    @Test
    fun `many empty articles scale linearly`() = assertScalesLinearly(2_500) { n ->
        page("<article></article>".repeat(n))
    }

    @Test
    fun `many next image noscripts in one figure scale linearly`() = assertScalesLinearly(500) { n ->
        page("<figure>" + repeatIndexed(n) { "<noscript><img data-nimg src=a$it></noscript>" } + "</figure>")
    }

    @Test
    fun `many short divs before the title scale linearly`() = assertScalesLinearly(4_000) { n ->
        "<html><head><title>Pathological</title></head><body><article>" + "<div>x</div>".repeat(n) +
            "<h1>Pathological</h1><p>$LEAD_TEXT one.</p><p>$LEAD_TEXT two.</p></article></body></html>"
    }

    @Test
    fun `many react segment calls without targets scale linearly`() = assertScalesLinearly(500) { n ->
        page("<script>" + "\$RC(\"a\",\"b\");".repeat(n) + "</script>" + "<br>".repeat(n * 3))
    }

    @Test
    fun `long react fallback scales linearly`() = assertScalesLinearly(3_000) { n ->
        page(
            "<div><!--\$?--><template id=\"t\"></template>" + "<p>x</p>".repeat(n) + "<!--/\$--></div>" +
                "<div hidden id=\"s\"><p>$LEAD_TEXT streamed.</p></div><script>\$RC(\"t\",\"s\")</script>",
        )
    }

    @Test
    fun `many paragraphs after further reading scale linearly`() = assertScalesLinearly(2_000) { n ->
        page("<h2>Further Reading</h2>" + "<p>x</p>".repeat(n))
    }

    @Test
    fun `many json-ld image references scale linearly`() = assertScalesLinearly(1_000) { n ->
        val items = "{\"@type\":\"Article\",\"image\":{\"@id\":\"x\"},\"primaryImageOfPage\":{\"@id\":\"x\"}},".repeat(
            n,
        )
        "<html><head><script type=\"application/ld+json\">[$items{}]</script></head><body><article>" +
            "<h1>Pathological</h1><p>$LEAD_TEXT</p></article></body></html>"
    }

    @Test
    fun `many src-less images scale linearly`() = assertScalesLinearly(2_000) { n ->
        page("<div>" + "<img>".repeat(n) + "</div>")
    }

    @Test
    fun `many footnote definition blocks scale linearly`() = assertScalesLinearly(2_500) { n ->
        page("<div class=\"footnote-definition\">x</div>".repeat(n))
    }

    @Test
    fun `many line breaks in one alert scale linearly`() = assertScalesLinearly(7_500) { n ->
        page("<div class=\"alert\">" + "a<br>".repeat(n) + "</div>")
    }

    @Test
    fun `many sibling scripts styles and iframes scale linearly`() = assertScalesLinearly(2_500) { n ->
        page("<script></script><style></style><iframe></iframe>".repeat(n))
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
                "<div class=\"wp-block-gamespot-blocks-video-embed\" data-jw-id=\"abcd${it.toString().padStart(
                    4,
                    '0',
                )}\"></div>"
            } + "<p>$LEAD_TEXT</p></article></body></html>"
    }

    @Test
    fun `many times of india break markers scale linearly`() = assertScalesLinearly(
        2_000,
        url = "https://timesofindia.indiatimes.com/x",
    ) { n -> "<html><body><div data-articlebody>" + "a<span class=\"br\"></span>".repeat(n) + "</div></body></html>" }

    private fun page(body: String) = "<html><body><article><h1>Pathological</h1>" +
        "<p>$LEAD_TEXT with enough words to be article content for the reader parser.</p>" +
        body + "</article></body></html>"

    private fun nest(open: String, close: String, depth: Int, inner: String) = buildString {
        repeat(depth) { append(open) }
        append(inner)
        repeat(depth) { append(close) }
    }

    private fun repeatIndexed(count: Int, item: (Int) -> String) = buildString { repeat(count) { append(item(it)) } }

    private fun assertScalesLinearly(n: Int, url: String = "https://example.com/pathological", html: (Int) -> String) {
        val small = html(n)
        val large = html(n * GROWTH_STEP)
        fun best(page: String, runs: Int) = (1..runs).minOf {
            measureTime {
                runBlocking(Dispatchers.Default) { Klead.parseHtml(page, url, testOptions()) }
            }
        }
        best(small, runs = 1) // Warm-up.
        // Garbage collection and compilation make single timings noisy, so a slow large run gets another
        // attempt instead of a second run every time; a quadratic path fails every attempt.
        val attempts = (1..GROWTH_ATTEMPTS).map {
            val smallTime = maxOf(best(small, runs = 3), MIN_MEASURED_TIME)
            val largeTime = best(large, runs = 1)
            if (largeTime < smallTime * MAX_GROWTH) return
            "${largeTime / smallTime}× ($smallTime → $largeTime)"
        }
        fail("${GROWTH_STEP}× the input took ${attempts.joinToString()} the time")
    }

    private companion object {
        const val GROWTH_STEP = 16

        // Linear work grows about 16× (a little more with garbage collection); the old paths grew about 256×.
        const val MAX_GROWTH = 32
        const val GROWTH_ATTEMPTS = 3
        val MIN_MEASURED_TIME = 10.milliseconds
        const val LEAD_TEXT = "Lead paragraph"
    }
}
