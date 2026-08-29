package com.prof18.klead

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CorePipelinePreparationTest {
    @Test
    fun `srcSet attributes normalize to srcset before serialization`() {
        val result = parseHtmlForTest(
            html = """
                <html><body><article>
                  <img src="/small.png" srcSet="/small.png 1x, /large.png 2x">
                </article></body></html>
            """.trimIndent(),
            url = "https://example.com/article",
        )

        assertTrue(result.content.requireHtml().contains("srcset="))
        assertFalse(result.content.requireHtml().contains("srcSet="))
    }

    @Test
    fun `noscript image fallback promotes real image before noscript stripping`() {
        val result = parseHtmlForTest(
            html = """
                <html><body><article>
                  <img src="data:image/svg+xml;base64,placeholder">
                  <noscript><img src="/real.png" alt="Real image"></noscript>
                </article></body></html>
            """.trimIndent(),
            url = "https://example.com/article",
        )

        assertTrue(result.content.requireHtml().contains("""src="/real.png""""))
        assertFalse(result.content.requireHtml().contains("<noscript"))
    }

    @Test
    fun `nextjs noscript image promotion preserves alt caption`() {
        val result = parseHtmlForTest(
            html = """
                <html><body><article>
                  <p>Here is an architecture diagram:</p>
                  <span>
                    <img alt="Architecture diagram." src="data:image/gif;base64,placeholder" data-nimg="intrinsic">
                    <noscript>
                      <img alt="Architecture diagram." src="/images/architecture.png?imwidth=3840" data-nimg="intrinsic">
                    </noscript>
                  </span>
                  <p>That concludes our overview.</p>
                </article></body></html>
            """.trimIndent(),
            url = "https://www.example.com/blog/example-post",
        )

        val markdown = result.content.requireMarkdown()
        assertTrue(
            markdown.contains(
                "![Architecture diagram.](https://www.example.com/images/architecture.png?imwidth=3840)",
            ),
        )
        assertTrue(markdown.contains("\n\nArchitecture diagram.\n\n"))
        assertFalse(result.content.requireHtml().contains("<noscript"))
    }

    @Test
    fun `react streamed segments are moved into placeholders before content detection`() {
        val result = parseHtmlForTest(
            html = """
                <html>
                <head><title>Understanding Widget Architecture | Example Blog</title></head>
                <body>
                  <main>
                    <article>
                      <h1>Understanding Widget Architecture</h1>
                      <p class="byline">Jane Smith · June 15, 2025</p>
                      <div class="article-body">
                        <!--${'$'}?-->
                        <template id="B:0"></template>
                        <div class="skeleton loading"><div class="skeleton-line"></div></div>
                        <!--/${'$'}-->
                      </div>
                    </article>
                  </main>
                  <script>${'$'}RC=function(){}</script>
                  <div hidden id="S:0">
                    <p>Modern widget systems have evolved significantly over the past decade. What started as simple reusable components has grown into sophisticated architectures that handle state management, lifecycle events, and cross-widget communication.</p>
                    <p>At the core of any widget system is the <strong>render pipeline</strong>, which tracks the widget's state, props, and subscriptions.</p>
                  </div>
                  <script>${'$'}RC("B:0","S:0")</script>
                </body>
                </html>
            """.trimIndent(),
            url = "https://example.com/streamed",
        )

        val html = result.content.requireHtml()
        val markdown = result.content.requireMarkdown()
        assertTrue(markdown.startsWith("## Understanding Widget Architecture"))
        assertTrue(markdown.contains("Modern widget systems have evolved"))
        assertFalse(html.contains("skeleton"))
        assertFalse(html.contains("""hidden id="S:0""""))
        assertFalse(html.contains("<template"))
    }

    @Test
    fun `unsafe elements and attributes are stripped from content html`() {
        val result = parseHtmlForTest(
            html = """
                <html><body><article>
                  <p onclick="steal()">Safe text</p>
                  <a href="javascript:alert(1)">bad link</a>
                  <iframe src="https://example.com/embed"></iframe>
                  <form action="data:text/html,<script>bad()</script>"><button formaction="javascript:bad()">Go</button></form>
                  <div srcdoc="<p>bad</p>">Frame content</div>
                  <style>p { color: red; }</style>
                  <script>alert(1)</script>
                </article></body></html>
            """.trimIndent(),
            url = "https://example.com/article",
        )

        assertTrue(result.content.requireHtml().contains("Safe text"))
        assertFalse(result.content.requireHtml().contains("onclick"))
        assertFalse(result.content.requireHtml().contains("javascript:"))
        assertFalse(result.content.requireHtml().contains("data:text/html"))
        assertFalse(result.content.requireHtml().contains("<iframe"))
        assertFalse(result.content.requireHtml().contains("<style"))
        assertFalse(result.content.requireHtml().contains("<script"))
        assertFalse(result.content.requireHtml().contains("srcdoc"))
    }

    @Test
    fun `trusted youtube iframe is preserved as cleaned html and markdown media`() {
        val result = parseHtmlForTest(
            html = """
                <html><body><article>
                  <p>The article introduction should stay because it is normal prose with useful information. It includes enough words, punctuation, and context for the parser to keep the default cleaned result rather than invoking short-page retries.</p>
                  <iframe src="https://www.youtube.com/embed/1hKyYaBzko8" title="Dwarf Fortress trailer" onclick="bad()" srcdoc="<p>bad</p>"></iframe>
                  <iframe src="https://evil.example/embed/1hKyYaBzko8"></iframe>
                </article></body></html>
            """.trimIndent(),
            url = "https://example.com/article",
        )

        assertTrue(
            result.content.requireHtml().contains("""src="https://www.youtube-nocookie.com/embed/1hKyYaBzko8""""),
        )
        assertTrue(
            result.content.requireHtml().contains(
                """data-klead-video-url="https://www.youtube.com/watch?v=1hKyYaBzko8"""",
            ),
        )
        assertFalse(result.content.requireHtml().contains("evil.example"))
        assertFalse(result.content.requireHtml().contains("onclick"))
        assertFalse(result.content.requireHtml().contains("srcdoc"))
        assertTrue(
            result.content.requireMarkdown().contains(
                "![](https://www.youtube.com/watch?v=1hKyYaBzko8)",
            ),
        )
    }

    @Test
    fun `block code token spacing is preserved through cleanup`() {
        val result = parseHtmlForTest(
            html = """
                <html><body><article>
                  <p>This page defines helper functions for working with natural numbers. The code block below uses span elements with generated anchor IDs that happen to match partial selector patterns.</p>
                  <code class="hl lean block" data-lean-context="examples">
                    <span class="keyword token">def</span><span class="inter-text"> </span><span class="const token" id="f-next-next">h1</span><span class="inter-text"> (x : Nat) : Nat :=</span>
                  </code>
                  <p>The function h1 takes a natural number and returns a natural number, and the spacing in the rendered code should stay intact.</p>
                </article></body></html>
            """.trimIndent(),
            url = "https://example.org/docs/type-theory",
        )

        assertTrue(
            result.content.requireMarkdown().contains(
                """
                ```lean
                def h1 (x : Nat) : Nat :=
                ```
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun `trusted social and raw vimeo iframes are preserved safely`() {
        val result = parseHtmlForTest(
            html = """
                <html><body><article>
                  <p>The article introduction should stay because it is normal prose with useful information. It includes enough words, punctuation, and context for the parser to keep the default cleaned result rather than invoking short-page retries.</p>
                  <iframe src="https://platform.twitter.com/embed/Tweet.html?id=1675626836821409792" onclick="bad()" srcdoc="<p>bad</p>"></iframe>
                  <iframe src="https://x.com/kepano/status/1675626836821409792"></iframe>
                  <iframe src="https://www.instagram.com/p/DAbCd_123-4/embed/captioned/" onclick="bad()"></iframe>
                  <iframe src="https://www.instagram.com/stories/example/12345/embed/"></iframe>
                  <iframe src="https://player.vimeo.com/video/45725193?h=a290f71a57" width="100%" height="100%" style="aspect-ratio: 3 / 1.025" frameborder="0" allow="autoplay; fullscreen; picture-in-picture" allowfullscreen=""></iframe>
                  <iframe src="https://example.com/embed/1675626836821409792"></iframe>
                </article></body></html>
            """.trimIndent(),
            url = "https://example.com/article",
        )

        val html = result.content.requireHtml()
        val markdown = result.content.requireMarkdown()
        assertFalse(html.contains("onclick"))
        assertFalse(html.contains("srcdoc"))
        assertFalse(html.contains("example.com/embed"))
        assertTrue(html.contains("""data-klead-video-url="https://x.com/i/status/1675626836821409792""""))
        assertTrue(html.contains("""src="https://www.instagram.com/p/DAbCd_123-4/embed/captioned/""""))
        assertTrue(html.contains("""data-klead-video-url="https://www.instagram.com/p/DAbCd_123-4/""""))
        assertFalse(html.contains("stories/example"))
        assertTrue(html.contains("""src="https://player.vimeo.com/video/45725193?h=a290f71a57""""))
        assertTrue(markdown.contains("[X post](https://x.com/i/status/1675626836821409792)"))
        assertTrue(markdown.contains("[X post](https://x.com/kepano/status/1675626836821409792)"))
        assertTrue(markdown.contains("[Instagram post](https://www.instagram.com/p/DAbCd_123-4/)"))
        assertTrue(
            markdown.contains(
                """<iframe src="https://player.vimeo.com/video/45725193?h=a290f71a57" width="100%" height="100%" frameborder="0" allow="autoplay; fullscreen; picture-in-picture" allowfullscreen=""></iframe>""",
            ),
        )
        assertFalse(markdown.contains("aspect-ratio"))
    }

    @Test
    fun `instagram blockquotes become safe rich embeds with clickable markdown fallbacks`() {
        val result = parseHtmlForTest(
            html = """
                <html><body><article>
                  <p>The article introduction has enough prose to keep content detection stable while the embedded Instagram reel is prepared for reader output.</p>
                  <blockquote class="instagram-media" data-instgrm-captioned data-instgrm-permalink="https://www.instagram.com/reel/DclHZIcM2ss/?utm_source=ig_embed&amp;utm_campaign=loading">
                    <div><div>View this post on Instagram</div></div>
                  </blockquote>
                  <script async src="//www.instagram.com/embed.js"></script>
                  <blockquote class="instagram-media" data-instgrm-permalink="javascript:alert(1)"><div>Unsafe post</div></blockquote>
                  <p>The article continues after the embedded post with more useful prose for the reader.</p>
                </article></body></html>
            """.trimIndent(),
            url = "https://www.ilpost.it/2026/08/29/example/",
        )

        val html = result.content.requireHtml()
        val markdown = result.content.requireMarkdown()
        assertTrue(html.contains("""src="https://www.instagram.com/reel/DclHZIcM2ss/embed/captioned/""""))
        assertTrue(html.contains("""title="Instagram post""""))
        assertTrue(html.contains("allowfullscreen"))
        assertTrue(html.contains("""data-klead-video-url="https://www.instagram.com/reel/DclHZIcM2ss/""""))
        assertFalse(html.contains("embed.js"))
        assertFalse(html.contains("javascript:"))
        assertTrue(markdown.contains("[Instagram post](https://www.instagram.com/reel/DclHZIcM2ss/)"))
    }

    @Test
    fun `il post twitter placeholders survive cleanup as embeds and markdown links`() {
        val result = parseHtmlForTest(
            html = """
                <html><body><article>
                  <p>The article introduction has enough prose to keep content detection stable while the embedded posts are prepared for reader output.</p>
                  <div class="ilPostSocial" data-component="ilPostSocial" data-type="twitter" data-url="https://x.com/afpfr/status/2092163775319159045"></div>
                  <div class="ilPostSocial" data-component="ilPostSocial" data-type="twitter" data-url="javascript:alert(1)"></div>
                  <p>The article continues after the embedded post with more useful prose for the reader.</p>
                </article></body></html>
            """.trimIndent(),
            url = "https://www.ilpost.it/flashes/example/",
        )

        val html = result.content.requireHtml()
        val markdown = result.content.requireMarkdown()
        assertTrue(html.contains("""src="https://platform.twitter.com/embed/Tweet.html?id=2092163775319159045"""))
        assertTrue(html.contains("""title="X post"""))
        assertTrue(html.contains("""data-klead-video-url="https://x.com/afpfr/status/2092163775319159045"""))
        assertFalse(html.contains("javascript:"))
        assertTrue(markdown.contains("[X post](https://x.com/afpfr/status/2092163775319159045)"))
    }

    @Test
    fun `medium code wrappers on custom publication hosts survive safely`() {
        val result = parseHtmlForTest(
            html = """
                <html><body><article>
                  <p>The article introduction has enough prose to keep content detection stable while the embedded code examples are prepared for safe reader output.</p>
                  <iframe src="https://publication.example/media/f3777200497d4a015ceae5d8bde2d2b0" title="APK patch workflow" onclick="bad()" srcdoc="<p>bad</p>" sandbox="allow-same-origin" style="position: fixed"></iframe>
                  <iframe src="https://medium.com/media/0294744cafcb5df1a92253ccdf562865" title="Signature verification"></iframe>
                  <iframe src="http://medium.com/media/cc708732fad37a8dfd4d5d51ff511158"></iframe>
                  <iframe src="https://medium.com/media/short"></iframe>
                  <iframe src="https://medium.com/media/cc708732fad37a8dfd4d5d51ff511158/extra"></iframe>
                  <iframe src="https://evil.example/media/cc708732fad37a8dfd4d5d51ff511158"></iframe>
                  <p>The article continues after the embedded code with more useful prose for the reader.</p>
                </article></body></html>
            """.trimIndent(),
            url = "https://publication.example/example",
        )

        val html = result.content.requireHtml()
        val markdown = result.content.requireMarkdown()
        assertTrue(html.contains("""src="https://publication.example/media/f3777200497d4a015ceae5d8bde2d2b0"""))
        assertTrue(html.contains("""src="https://medium.com/media/0294744cafcb5df1a92253ccdf562865"""))
        assertTrue(html.contains("""sandbox="allow-scripts"""))
        assertFalse(html.contains("allow-same-origin"))
        assertFalse(html.contains("onclick"))
        assertFalse(html.contains("srcdoc"))
        assertFalse(html.contains("position: fixed"))
        assertFalse(html.contains("http://medium.com"))
        assertFalse(html.contains("/short"))
        assertFalse(html.contains("/extra"))
        assertFalse(html.contains("evil.example"))
        assertTrue(
            markdown.contains(
                "[APK patch workflow](https://publication.example/media/f3777200497d4a015ceae5d8bde2d2b0)",
            ),
        )
        assertTrue(
            markdown.contains(
                "[Signature verification](https://medium.com/media/0294744cafcb5df1a92253ccdf562865)",
            ),
        )
    }

    @Test
    fun `parse timing is present when debug is requested`() {
        val result = parseHtmlForTest(
            html = "<html><body><article><p>Profile on.</p></article></body></html>",
            url = "https://example.com/profile-on",
            options = testOptions(debug = true),
        )

        val parseTimeMillis = result.debug["parseTimeMillis"]
        assertNotNull(parseTimeMillis)
        assertTrue(parseTimeMillis is Long)
        assertTrue(parseTimeMillis >= 0)
    }
}
