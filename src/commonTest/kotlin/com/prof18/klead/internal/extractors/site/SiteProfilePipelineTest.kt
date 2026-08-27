package com.prof18.klead.internal.extractors.site

import com.prof18.klead.extractors.Extractor
import com.prof18.klead.parseHtmlForTest
import com.prof18.klead.testOptions
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SiteProfilePipelineTest {
    @Test
    fun `profile content selector beats noisy generic main`() {
        val profile = TestExtractor(
            contentSelectors = listOf(".preferred-story"),
            domains = setOf("example.com"),
        )

        val result = parseHtmlForTest(
            html = """
                <main>
                  <section class="preferred-story">
                    <p>This preferred article paragraph contains enough natural language, punctuation, and context to pass the profile selector guard cleanly.</p>
                    <p>The second preferred article paragraph keeps the article substantial while unrelated content elsewhere makes the generic container noisy.</p>
                  </section>
                  <section class="related">
                    <p>Unrelated recommendation text should remain outside the selected content even though it sits inside the broad main container.</p>
                    <p>More unrelated recommendation text makes the main element attractive to generic scoring but unsuitable as the focused article body.</p>
                  </section>
                </main>
            """.trimIndent(),
            url = "https://example.com/story",
            options = testOptions(customExtractors = listOf(profile), debug = true),
        )

        assertTrue(result.content.requireMarkdown().contains("This preferred article paragraph"))
        assertFalse(result.content.requireMarkdown().contains("Unrelated recommendation text"))
        assertEquals(".preferred-story", result.debug["selectedContentSelector"])
        assertEquals(".preferred-story", result.debug["extractorContentSelector"])
    }

    @Test
    fun `profile content selector chooses strongest matching element`() {
        val profile = TestExtractor(
            contentSelectors = listOf("article"),
            domains = setOf("example.com"),
        )

        val result = parseHtmlForTest(
            html = """
                <main>
                  <article>
                    <p>Teaser article paragraph contains enough natural language and punctuation to pass the profile selector guard.</p>
                    <p>The second teaser paragraph keeps this short item plausible, but it is still not the primary story on the page.</p>
                  </article>
                  <article>
                    <h1>Primary investigation</h1>
                    <p>The primary article paragraph contains substantially more natural prose, details, and context than the teaser above so it should be chosen when the profile selector matches multiple article elements.</p>
                    <p>The second primary paragraph adds more useful article text, enough words, and stable punctuation to make this the strongest guarded content candidate for the same preferred selector.</p>
                  </article>
                </main>
            """.trimIndent(),
            url = "https://example.com/story",
            options = testOptions(customExtractors = listOf(profile), debug = true),
        )

        assertTrue(result.content.requireMarkdown().contains("The primary article paragraph"))
        assertFalse(result.content.requireMarkdown().contains("Teaser article paragraph"))
        assertEquals("article", result.debug["selectedContentSelector"])
        assertEquals("article", result.debug["extractorContentSelector"])
    }

    @Test
    fun `weak profile content selector falls back to generic scoring`() {
        val profile = TestExtractor(
            contentSelectors = listOf(".weak-preferred"),
            domains = setOf("example.com"),
        )

        val result = parseHtmlForTest(
            html = """
                <main>
                  <section class="weak-preferred">Tiny promo</section>
                  <article>
                    <p>This generic article paragraph contains enough natural language, punctuation, and context to beat a weak profile selector.</p>
                    <p>The second generic article paragraph keeps the article substantial and should be selected by the normal detector fallback.</p>
                  </article>
                </main>
            """.trimIndent(),
            url = "https://example.com/story",
            options = testOptions(customExtractors = listOf(profile), debug = true),
        )

        assertTrue(result.content.requireMarkdown().contains("This generic article paragraph"))
        assertFalse(result.content.requireMarkdown().contains("Tiny promo"))
        assertEquals("article", result.debug["selectedContentSelector"])
        assertFalse(result.debug.containsKey("extractorContentSelector"))
    }

    @Test
    fun `profile selectors only run for matching host and are reported in debug`() {
        val profile = TestExtractor(
            postContentRemoveSelectors = listOf(".site-chrome"),
            domains = setOf("example.com"),
        )
        val html = """
            <article>
              <p>The actual article body should stay because it contains enough realistic prose for deterministic parsing.</p>
              <div class="site-chrome">Site-specific chrome should only disappear on matching hosts.</div>
            </article>
        """.trimIndent()

        val matching = parseHtmlForTest(
            html = html,
            url = "https://example.com/story",
            options = testOptions(customExtractors = listOf(profile), debug = true),
        )
        val unrelated = parseHtmlForTest(
            html = html,
            url = "https://unrelated.test/story",
            options = testOptions(customExtractors = listOf(profile), debug = true),
        )

        assertFalse(matching.content.requireMarkdown().contains("Site-specific chrome"))
        assertTrue(unrelated.content.requireMarkdown().contains("Site-specific chrome"))
        assertEquals(listOf("test-profile"), matching.debug["extractorIds"])
        assertNotNull(matching.debug["extractorRemovals"])
        assertFalse(unrelated.debug.containsKey("extractorIds"))
        assertFalse(unrelated.debug.containsKey("extractorRemovals"))
    }

    @Test
    fun `built in site profile selectors do not run on unrelated hosts`() {
        val html = """
            <article>
              <p>The actual article body should stay because it contains enough realistic prose for deterministic parsing.</p>
              <p>A second paragraph keeps the article body stable while a MacRumors-style linkback footer appears below it.</p>
              <div class="linkback">Related Roundup: <a href="/roundup/example">Example Product</a></div>
            </article>
        """.trimIndent()

        val matching = parseHtmlForTest(
            html = html,
            url = "https://www.macrumors.com/example",
            options = testOptions(debug = true),
        )
        val unrelated = parseHtmlForTest(
            html = html,
            url = "https://example.com/not-macrumors",
            options = testOptions(debug = true),
        )

        assertFalse(matching.content.requireMarkdown().contains("Related Roundup"))
        assertTrue(unrelated.content.requireMarkdown().contains("Related Roundup"))
        assertEquals(listOf("macrumors"), matching.debug["extractorIds"])
        assertFalse(unrelated.debug.containsKey("extractorIds"))
    }

    @Test
    fun `npr profile turns image credits into figure captions`() {
        val html = """
            <article>
              <p>The opening article paragraph contains enough natural language, punctuation, and context for deterministic content selection.</p>
              <div class="bucketwrap image large">
                <div class="imagewrap"><picture><img src="/performance.jpg" alt="Dolly performs"></picture></div>
                <div class="credit-caption">
                  <div class="caption-wrap"><div class="caption"></div></div>
                  <span class="credit"><a href="https://video.example/performance">The Carter Family Channel</a>/Screenshot by NPR</span>
                  <b class="hide-caption">hide caption</b>
                  <b class="toggle-caption">toggle caption</b>
                </div>
              </div>
              <p>The closing article paragraph keeps the selected story substantial and verifies that prose around the image remains intact.</p>
            </article>
        """.trimIndent()

        val matching = parseHtmlForTest(
            html = html,
            url = "https://www.npr.org/2026/08/27/example",
            options = testOptions(debug = true),
        )
        val unrelated = parseHtmlForTest(
            html = html,
            url = "https://example.com/not-npr",
            options = testOptions(debug = true),
        )

        val htmlResult = matching.content.requireHtml()
        val markdown = matching.content.requireMarkdown()
        assertTrue(htmlResult.contains("<figure class=\"bucketwrap image large\">"), htmlResult)
        assertTrue(htmlResult.contains("<figcaption class=\"credit-caption\">"), htmlResult)
        assertTrue(markdown.contains("*The Carter Family Channel/Screenshot by NPR*"), markdown)
        assertFalse(markdown.contains("hide caption"), markdown)
        assertFalse(markdown.contains("toggle caption"), markdown)
        assertEquals(listOf("npr"), matching.debug["extractorIds"])
        assertFalse(unrelated.content.requireHtml().contains("<figcaption"))
        assertFalse(unrelated.debug.containsKey("extractorIds"))
    }

    @Test
    fun `npr profile preserves related stories as callouts`() {
        val result = parseHtmlForTest(
            html = """
                <article>
                  <p>The opening article paragraph contains enough natural language, punctuation, and context for deterministic content selection.</p>
                  <div class="bucketwrap internallink insettwocolumn inset2col">
                    <div class="bucket img">
                      <a class="imagewrap" href="/related-story"><img src="/related.jpg" alt="Related story"></a>
                      <div class="bucketblock">
                        <h3 class="slug"><a href="/series/only-on-npr">Only on NPR</a></h3>
                        <h3><a href="/related-story">How Dolly Parton wrote the story of a changing America</a></h3>
                      </div>
                    </div>
                  </div>
                  <p>The closing article paragraph keeps the selected story substantial and verifies that prose after the related story remains intact.</p>
                </article>
            """.trimIndent(),
            url = "https://www.npr.org/2026/08/27/example",
            options = testOptions(debug = true),
        )

        val html = result.content.requireHtml()
        val markdown = result.content.requireMarkdown()
        assertTrue(
            html.contains(
                "<aside class=\"bucketwrap internallink insettwocolumn inset2col callout\"",
            ),
            html,
        )
        assertTrue(html.contains("data-callout=\"note\""), html)
        assertTrue(html.contains("class=\"bucket img callout-content\""), html)
        assertTrue(html.contains("class=\"imagewrap callout-media\""), html)
        assertTrue(html.contains("class=\"bucketblock callout-body\""), html)
        assertTrue(html.contains("class=\"slug callout-label\""), html)
        assertTrue(html.contains("class=\"callout-title\""), html)
        assertFalse(html.contains("npr-related-content"), html)
        assertTrue(markdown.contains("> [!note]"), markdown)
        assertTrue(markdown.contains("Only on NPR"), markdown)
        assertTrue(markdown.contains("How Dolly Parton wrote the story of a changing America"), markdown)
        assertTrue(markdown.contains("The closing article paragraph"), markdown)
    }

    @Test
    fun `guardian profile removes header chrome while preserving lead media and standfirst`() {
        val html = """
            <article>
              <div data-gu-name="media">
                <figure>
                  <img src="https://media.guim.co.uk/lead.jpg" alt="Lead image">
                  <span>
                    <label for="the-checkbox">Info</label>
                    <input id="the-checkbox" type="checkbox">
                    <figcaption>Duplicate responsive caption.</figcaption>
                  </span>
                  <figcaption><span><svg width="18" height="13"><path></path></svg></span>Lead caption should stay.</figcaption>
                </figure>
              </div>
              <aside data-gu-name="title"><a href="/film">Film</a></aside>
              <div data-gu-name="standfirst">
                <p>The useful article standfirst should remain in the cleaned reader output.</p>
              </div>
              <aside data-gu-name="meta">
                <svg aria-hidden="true" focusable="false" width="100%" height="13"><line></line></svg>
                <time>Wed 26 Aug 2026 17.47 CEST</time>
                <a href="https://www.google.com/preferences/source?q=theguardian.com">
                  Prefer the Guardian on Google
                  <svg viewBox="-3 -3 30 30"><path></path></svg>
                </a>
              </aside>
              <div data-gu-name="body">
                <p>The actual article body should stay because it contains enough realistic prose, punctuation, and context for deterministic parsing.</p>
                <p>A second article paragraph keeps the selected story stable while Guardian-specific header chrome is removed from the reader output.</p>
              </div>
              <svg aria-hidden="true" focusable="false" width="100%" height="13"><line></line></svg>
            </article>
        """.trimIndent()

        val matching = parseHtmlForTest(
            html = html,
            url = "https://www.theguardian.com/film/example",
            options = testOptions(debug = true),
        )
        val unrelated = parseHtmlForTest(
            html = html,
            url = "https://example.com/film/example",
            options = testOptions(debug = true),
        )

        val matchingMarkdown = matching.content.requireMarkdown()
        assertTrue(matchingMarkdown.contains("Lead caption should stay."))
        assertTrue(matchingMarkdown.contains("The useful article standfirst should remain"))
        assertTrue(matchingMarkdown.contains("The actual article body should stay"))
        assertFalse(matchingMarkdown.contains("Duplicate responsive caption."))
        assertFalse(matchingMarkdown.contains("Film"))
        assertFalse(matchingMarkdown.contains("Wed 26 Aug 2026"))
        assertFalse(matchingMarkdown.contains("Prefer the Guardian on Google"))
        val matchingHtml = matching.content.requireHtml()
        assertFalse(matchingHtml.contains("the-checkbox"))
        assertFalse(matchingHtml.contains("<svg"))
        assertEquals(listOf("guardian"), matching.debug["extractorIds"])

        val unrelatedMarkdown = unrelated.content.requireMarkdown()
        assertTrue(unrelatedMarkdown.contains("Duplicate responsive caption."))
        assertTrue(unrelatedMarkdown.contains("Prefer the Guardian on Google"))
        assertFalse(unrelated.debug.containsKey("extractorIds"))
    }

    @Test
    fun `macrumors profile selects a short article body instead of nearby guides`() {
        val result = parseHtmlForTest(
            html = """
                <main id="maincontent">
                  <article class="article--hash js-article">
                    <header><h1>A short MacRumors story</h1></header>
                    <div class="content--hash js-content">
                      <div class="ugc--hash" data-io-article-url="/2026/08/26/example/">
                        <p>The first article paragraph contains the concise report and a small amount of essential context for interested readers.</p>
                        <p>The second article paragraph adds the remaining important detail while keeping this deliberately short news post complete and understandable.</p>
                      </div>
                    </div>
                  </article>
                  <section class="guides">
                    <h2>Guides</h2>
                    <p><a href="/roundup/ios-26/">iOS 26 Features</a></p>
                    <p>This comprehensive guide highlights every major addition and links to more unrelated product coverage.</p>
                  </section>
                </main>
            """.trimIndent(),
            url = "https://www.macrumors.com/2026/08/26/example/",
            options = testOptions(debug = true),
        )

        val markdown = result.content.requireMarkdown()
        assertTrue(markdown.contains("The first article paragraph"))
        assertFalse(markdown.contains("iOS 26 Features"))
        assertEquals("article.js-article .js-content", result.debug["selectedContentSelector"])
        assertEquals("article.js-article .js-content", result.debug["extractorContentSelector"])
        assertEquals(listOf("macrumors"), result.debug["extractorIds"])
    }

    @Test
    fun `kuruc info profile selects schema article body instead of fixed width page chrome`() {
        val html = """
            <body>
              <nav><a href="/latest">Latest stories and unrelated navigation links</a></nav>
              <div style="width:981px">
                <aside>Sidebar poll and exchange rates should stay outside reader content.</aside>
                <div id="cikkcontent">
                  <h1>Article title rendered separately by the reader</h1>
                  <div itemprop="articleBody">
                    <p>The actual article body contains enough natural language, punctuation, and context to pass the profile selector guard cleanly.</p>
                    <p>A second paragraph keeps the selected body substantial while the fixed-width shell and unrelated sidebar remain outside it.</p>
                  </div>
                </div>
              </div>
            </body>
        """.trimIndent()

        val matching = parseHtmlForTest(
            html = html,
            url = "https://kuruc.info/r/7/123456/",
            options = testOptions(debug = true),
        )
        val unrelated = parseHtmlForTest(
            html = html,
            url = "https://example.com/r/7/123456/",
            options = testOptions(debug = true),
        )

        val markdown = matching.content.requireMarkdown()
        assertTrue(markdown.contains("The actual article body"))
        assertFalse(markdown.contains("Sidebar poll"))
        assertFalse(markdown.contains("Article title rendered separately"))
        assertEquals("[itemprop=articleBody]", matching.debug["selectedContentSelector"])
        assertEquals(listOf("kuruc-info"), matching.debug["extractorIds"])
        assertTrue(unrelated.content.requireMarkdown().contains("Sidebar poll"))
    }

    @Test
    fun `beehiiv profile extracts the post body without page chrome and publisher padding`() {
        val result = parseHtmlForTest(
            html = """
                <html>
                  <body>
                    <main>
                      <div class="mt-8">
                        <ul><li><a href="/">Publication</a></li><li>Posts</li><li>Issue</li></ul>
                      </div>
                      <div class="rendered-post">
                        <div id="web-header">
                          <h2>Publication</h2>
                          <h2>Issue title</h2>
                          <div class="bh__byline_wrapper">Author and date</div>
                          <div class="bh__byline_social_wrapper">
                            <a href="https://twitter.com/intent/tweet"><svg height="100%"></svg></a>
                          </div>
                        </div>
                        <img src="https://example.com/duplicate-hero.jpg">
                        <div id="content-blocks">
                          <div style="padding-bottom:12px;padding-left:15px;padding-right:15px;padding-top:12px;">
                            <p style="font-size:16px;line-height:1.5">The actual newsletter paragraph contains enough natural language, punctuation, and context for the preferred Beehiiv content selector.</p>
                          </div>
                          <div style="font-size:0px;line-height:0px;padding:30px 0px 30px;">
                            <div style="border-top:3px solid #365f82"></div>
                          </div>
                          <div style="padding-bottom:12px;padding-left:15px;padding-right:15px;padding-top:12px;">
                            <p></p>
                          </div>
                          <div style="padding-bottom:12px;padding-left:15px;padding-right:15px;padding-top:12px;">
                            <p>A second substantial paragraph keeps the body selectable and proves all meaningful prose remains after the Beehiiv layout chrome is removed.</p>
                          </div>
                        </div>
                      </div>
                    </main>
                  </body>
                </html>
            """.trimIndent(),
            url = "https://publication.beehiiv.com/p/example",
            options = testOptions(debug = true),
        )

        val markdown = result.content.requireMarkdown()
        val html = result.content.requireHtml()
        assertTrue(markdown.startsWith("The actual newsletter paragraph"), markdown)
        assertTrue(markdown.contains("A second substantial paragraph"), markdown)
        assertFalse(markdown.contains("Posts"), markdown)
        assertFalse(markdown.contains("Author and date"), markdown)
        assertFalse(markdown.contains("<svg"), markdown)
        assertFalse(html.contains("duplicate-hero.jpg"), html)
        assertFalse(html.contains("padding"), html)
        assertFalse(html.contains("<p></p>"), html)
        assertEquals("#content-blocks", result.debug["selectedContentSelector"])
        assertTrue((result.debug["extractorIds"] as List<*>).contains("beehiiv"))
    }

    @Test
    fun `karakartal profile isolates responsive article body`() {
        val result = parseHtmlForTest(
            html = """
                <main style="width: 994px">
                  <div id="haberBody">
                    <div style="float:right; margin: 5px">
                      <img src="/story.jpg" alt="Story image" width="301" height="227">
                    </div>
                    <span id="contextual">
                      <p>The first article paragraph contains the report itself, with enough natural language and punctuation to identify this focused news body reliably.</p>
                      <p>The second article paragraph adds the supporting details readers need while keeping the domain-specific selector safely above its content guard.</p>
                      <a href="https://www.karakartal.com/mobil">Install the mobile app</a>
                    </span>
                  </div>
                  <aside>Unrelated sidebar recommendations must stay outside the article.</aside>
                </main>
            """.trimIndent(),
            url = "https://www.karakartal.com/futbol/example",
            options = testOptions(debug = true),
        )

        val markdown = result.content.requireMarkdown()
        val html = result.content.requireHtml()
        assertTrue(markdown.contains("The first article paragraph"))
        assertTrue(markdown.contains("Story image"))
        assertFalse(markdown.contains("Install the mobile app"))
        assertFalse(markdown.contains("Unrelated sidebar recommendations"))
        assertFalse(html.contains("float:right"))
        assertEquals("#haberBody", result.debug["selectedContentSelector"])
        assertTrue((result.debug["extractorIds"] as List<*>).contains("karakartal"))
    }

    @Test
    fun `macstories profile removes full size image icon while preserving image`() {
        val result = parseHtmlForTest(
            html = """
                <article>
                  <div class="media-wrapper">
                    <img src="https://cdn.macstories.net/article.jpg" alt="Vision Pro running visionOS 27">
                    <p class="image-caption">
                      <a class="view-full-size" href="https://cdn.macstories.net/article.jpg">
                        <svg viewBox="0 0 120 120"><path d="M71.2 48.8"></path></svg>
                      </a>
                    </p>
                  </div>
                  <p>The actual article body should stay because it contains enough realistic prose for deterministic parsing after the full-size image control is removed.</p>
                  <p>A second paragraph keeps the selected article stable and proves the decorative link icon is not treated as meaningful article content.</p>
                </article>
            """.trimIndent(),
            url = "https://www.macstories.net/stories/example/",
            options = testOptions(debug = true),
        )

        val markdown = result.content.requireMarkdown()
        assertTrue(
            markdown.contains(
                "![Vision Pro running visionOS 27](https://cdn.macstories.net/article.jpg)",
            ),
            markdown,
        )
        assertFalse(markdown.contains("<svg"), markdown)
        assertFalse(result.content.requireHtml().contains("view-full-size"))
        assertTrue((result.debug["extractorIds"] as List<*>).contains("macstories"))
    }

    @Test
    fun `dw profile removes article chrome and restores templated image`() {
        val result = parseHtmlForTest(
            html = """
                <article>
                  <section data-tracking-name="sharing-icons-inline">
                    <a href="https://x.com/share"><svg viewBox="-10 -10 40 40"><path d="M6 0"></path></svg></a>
                    <div><svg viewBox="0 0 25 25"><path d="m22.5 8.75-10 7.5"></path></svg></div>
                  </section>
                  <p>The actual article body should stay because it contains enough realistic prose for deterministic parsing after the sharing toolbar is removed.</p>
                  <blockquote class="tweet embed" data-id="123"></blockquote>
                  <figure class="placeholder-image master_landscape big">
                    <img
                      data-format="MASTER_LANDSCAPE"
                      data-url="https://static.dw.com/image/74954369_${'$'}{formatId}.jpg"
                      alt="Dortmund fans celebrating">
                    <figcaption>Borussia Dortmund fans in the stadium.</figcaption>
                  </figure>
                  <p>A second paragraph keeps the selected article stable and proves meaningful prose remains around the repaired media.</p>
                </article>
            """.trimIndent(),
            url = "https://www.dw.com/en/example/a-123",
            options = testOptions(debug = true),
        )

        val markdown = result.content.requireMarkdown()
        assertFalse(markdown.contains("<svg"), markdown)
        assertFalse(markdown.contains("https://x.com/share"), markdown)
        assertFalse(markdown.lines().any { it == ">" }, markdown)
        assertTrue(
            markdown.contains(
                "![Dortmund fans celebrating](https://static.dw.com/image/74954369_605.jpg)",
            ),
            markdown,
        )
        assertTrue(markdown.contains("Borussia Dortmund fans in the stadium."), markdown)
        assertFalse(result.content.requireHtml().contains("blockquote"))
        assertTrue((result.debug["extractorIds"] as List<*>).contains("dw"))
    }

    @Test
    fun `dw profile removes video headline icon while preserving video`() {
        val result = parseHtmlForTest(
            html = """
                <article>
                  <p>The first article paragraph contains enough realistic prose to keep this focused news body stable during deterministic content selection.</p>
                  <div class="vjs-wrapper embed big">
                    <h2 class="headline">
                      <svg viewBox="0 0 20 20"><path d="M14.114 7.599H13.5l.002 4.706h.601z"></path></svg>
                      Malaria research in Germany targets parasite's life cycle
                    </h2>
                    <video id="video-74740912" controls>
                      <source src="https://example.com/malaria.mp4" type="video/mp4">
                    </video>
                  </div>
                  <p>The second article paragraph proves that removing the decorative camera icon leaves the surrounding story and embedded media intact.</p>
                </article>
            """.trimIndent(),
            url = "https://www.dw.com/en/example/a-123",
            options = testOptions(debug = true),
        )

        val html = result.content.requireHtml()
        assertFalse(html.contains("<svg"), html)
        assertTrue(html.contains("Malaria research in Germany targets parasite's life cycle"), html)
        assertTrue(html.contains("<video"), html)
        assertTrue(html.contains("https://example.com/malaria.mp4"), html)
        assertTrue((result.debug["extractorIds"] as List<*>).contains("dw"))
    }

    @Test
    fun `android police profile keeps feature image that sits outside article body`() {
        val result = parseHtmlForTest(
            html = """
                <html>
                  <head>
                    <meta property="og:image" content="https://static0.anpoimages.com/wordpress/wp-content/uploads/2026/06/custom-launcher.png?w=1600&h=900&fit=crop">
                  </head>
                  <body>
                    <header>
                      <div class="heading_image" data-is-feature-img="true">
                        <figure>
                          <picture>
                            <source srcset="https://static0.anpoimages.com/wordpress/wp-content/uploads/2026/06/custom-launcher.png?q=70&fit=crop&w=1600&h=900&dpr=1">
                            <img
                              src="https://static0.anpoimages.com/wordpress/wp-content/uploads/2026/06/custom-launcher.png?&fit=crop&w=1600&h=900"
                              data-img-url="https://static0.anpoimages.com/wordpress/wp-content/uploads/2026/06/custom-launcher.png?&fit=crop&w=1600&h=900"
                              alt="Two Android phones side by side showing a busy home screen and a custom launcher layout.">
                          </picture>
                          <small class="item-img-caption">Credit: Example / Android Police</small>
                        </figure>
                      </div>
                    </header>
                    <section id="article-body" class="article-body">
                      <p>The actual article body should stay because it contains enough realistic prose for deterministic parsing after the separate feature image has been merged back into the article content.</p>
                      <p>A second article paragraph keeps the selected body stable while proving the Valnet profile can preserve the leading image that lives in the page header instead of inside the article body.</p>
                    </section>
                  </body>
                </html>
            """.trimIndent(),
            url = "https://www.androidpolice.com/replaced-samsung-home-screen-with-custom-launcher-never-going-back/",
            options = testOptions(debug = true),
        )

        val markdown = result.content.requireMarkdown()
        assertTrue(
            markdown.startsWith(
                "![Two Android phones side by side showing a busy home screen and a custom launcher layout.]" +
                    "(https://static0.anpoimages.com/wordpress/wp-content/uploads/2026/06/custom-launcher.png?&fit=crop&w=1600&h=900)",
            ),
            markdown,
        )
        assertTrue(markdown.contains("The actual article body should stay"))
        assertFalse(markdown.contains("Credit: Example"))
        assertTrue((result.debug["extractorIds"] as List<*>).contains("android-police"))
        assertEquals("#article-body", result.debug["extractorContentSelector"])
    }

    @Test
    fun `valnet profile keeps main gallery images and removes presentation duplicates`() {
        val result = parseHtmlForTest(
            html = """
                <html>
                  <body>
                    <section id="article-body" class="article-body">
                      <p>The actual article body contains enough realistic prose to keep content selection stable while the Valnet gallery is cleaned for reader output.</p>
                      <div class="valnet-gallery">
                        <div class="article__gallery">
                          <div class="w-gallery-carousel">
                            <section class="splide splide-gallery">
                              <div class="splide__track">
                                <div class="splide__list">
                                  <div class="splide__slide gallery-main-img">
                                    <img src="https://static.example.com/one.jpg?w=705" alt="First gallery image">
                                  </div>
                                  <div class="splide__slide gallery-main-img">
                                    <img src="https://static.example.com/two.jpg?w=705" alt="Second gallery image">
                                  </div>
                                </div>
                              </div>
                            </section>
                            <section class="splide gallery-thumbnails">
                              <img src="https://static.example.com/one.jpg?w=120" alt="First thumbnail">
                              <img src="https://static.example.com/two.jpg?w=120" alt="Second thumbnail">
                            </section>
                          </div>
                          <div class="w-gallery-carousel-fullscreen">
                            <img src="https://static.example.com/one.jpg?w=1920" alt="First fullscreen image">
                            <img src="https://static.example.com/two.jpg?w=1920" alt="Second fullscreen image">
                          </div>
                        </div>
                      </div>
                      <p>A second substantial article paragraph verifies that removing alternate carousel representations preserves the surrounding story content.</p>
                    </section>
                  </body>
                </html>
            """.trimIndent(),
            url = "https://www.androidpolice.com/example-gallery/",
            options = testOptions(debug = true),
        )

        val html = result.content.requireHtml()
        val markdown = result.content.requireMarkdown()
        assertEquals(2, Regex("https://static\\.example\\.com/(one|two)\\.jpg").findAll(markdown).count(), markdown)
        assertTrue(markdown.contains("one.jpg?w=705"), markdown)
        assertTrue(markdown.contains("two.jpg?w=705"), markdown)
        assertFalse(markdown.contains("w=120"), markdown)
        assertFalse(markdown.contains("w=1920"), markdown)
        assertFalse(html.contains("gallery-thumbnails"), html)
        assertFalse(html.contains("w-gallery-carousel-fullscreen"), html)
        assertTrue((result.debug["extractorIds"] as List<*>).contains("valnet"))
    }

    @Test
    fun `il post profile restores api summary before article body`() {
        val result = parseHtmlForTest(
            html = """
                <html>
                  <head>
                    <title>Il calcio dei Mondiali è diventato uno sport in quattro tempi?</title>
                    <meta property="og:site_name" content="Il Post">
                    <meta name="description" content="Le pause per bere sono obbligatorie al 22esimo e al 67esimo minuto, cosa che secondo alcuni spezzetta troppo il gioco">
                  </head>
                  <body>
                    <main>
                      <article>
                        <figure>
                          <img src="https://www.ilpost.it/wp-content/uploads/2026/06/15/hero.jpg" alt="I calciatori del Marocco durante una pausa">
                          <figcaption>I calciatori del Marocco durante una pausa</figcaption>
                        </figure>
                        <h2 class="index_author__TBkbf">di Alberto Chiumento</h2>
                        <div id="audioPlayerArticle" data-mp3="https://audio.example.com/story.mp3">Caricamento player</div>
                        <p>Tra le nuove regole introdotte ai Mondiali di calcio quella più evidente riguarda gli hydration break, cioè due pause obbligatorie in tutte le partite.</p>
                        <p>Fino a prima dei Mondiali, nel calcio internazionale queste pause non erano obbligatorie e avvenivano soltanto quando la temperatura superava i 32 gradi.</p>
                      </article>
                    </main>
                    <script id="__NEXT_DATA__" type="application/json">
                      {
                        "props": {
                          "pageProps": {
                            "data": {
                              "data": {
                                "main": {
                                  "data": {
                                    "summary": "Le pause per bere sono obbligatorie al 22esimo e al 67esimo minuto, cosa che secondo alcuni spezzetta troppo il gioco",
                                    "author": {
                                      "first_name": "Alberto",
                                      "last_name": "Chiumento"
                                    }
                                  }
                                }
                              }
                            }
                          }
                        }
                      }
                    </script>
                  </body>
                </html>
            """.trimIndent(),
            url = "https://www.ilpost.it/2026/06/15/cooling-break-mondiali-calcio-pause/",
            options = testOptions(debug = true),
        )

        val markdown = result.content.requireMarkdown()
        assertTrue(
            markdown.startsWith(
                "## Le pause per bere sono obbligatorie al 22esimo e al 67esimo minuto, cosa che secondo alcuni spezzetta troppo il gioco\n\n" +
                    "![I calciatori del Marocco durante una pausa]",
            ),
            markdown,
        )
        assertFalse(markdown.contains("Caricamento player"))
        assertFalse(markdown.contains("di Alberto Chiumento"))
        assertEquals("Alberto Chiumento", result.metadata.author)
        assertEquals(listOf("ilpost"), result.debug["extractorIds"])
    }

    @Test
    fun `daring fireball profile selects article and removes navigation footnote chrome`() {
        val result = parseHtmlForTest(
            html = """
                <body>
                  <header>
                    <a href="/"><img src="/graphics/logos/" alt="Daring Fireball"></a>
                  </header>
                  <div id="Main">
                    <div class="article">
                      <p>The article body should stay because it has enough realistic prose to beat the header and any surrounding site chrome.</p>
                      <p>A second article paragraph keeps this fixture stable while a footnote marker appears here.<sup><a href="#fn1">1</a></sup></p>
                      <div id="footnotes">
                        <p><sup>1</sup> Footnote text should stay. <a class="footnoteBackLink" href="#fnr1">↩︎</a></p>
                      </div>
                      <div id="PreviousNext">
                        <table><tr><td>Previous:</td><td>Older site navigation</td></tr></table>
                      </div>
                    </div>
                  </div>
                </body>
            """.trimIndent(),
            url = "https://daringfireball.net/2025/02/example",
            options = testOptions(debug = true),
        )

        val markdown = result.content.requireMarkdown()
        assertTrue(markdown.startsWith("The article body should stay"))
        assertTrue(markdown.contains("Footnote text should stay."))
        assertFalse(markdown.contains("Daring Fireball"))
        assertFalse(markdown.contains("Previous:"))
        assertFalse(markdown.contains("↩"))
        assertEquals("#Main .article", result.debug["selectedContentSelector"])
        assertEquals("#Main .article", result.debug["extractorContentSelector"])
        assertEquals(listOf("daring-fireball"), result.debug["extractorIds"])
    }

    @Test
    fun `simon willison profile selects entry and removes repeated posting footer`() {
        val result = parseHtmlForTest(
            html = """
                <body>
                  <div id="smallhead"><h1><a href="/">Simon Willison’s Weblog</a></h1></div>
                  <div id="wrapper">
                    <div id="primary">
                      <div class="entry entryPage">
                        <p class="mobile-date-eyebrow">23rd August 2026 - Link Blog</p>
                        <div data-permalink-context="/2026/Aug/23/example/">
                          <p><strong><a href="https://example.com/report">The linked report title</a></strong> introduces the article with enough natural prose and context for the preferred content selector.</p>
                          <p>A second paragraph keeps this representative entry substantial while the surrounding site chrome remains outside the selected reader content.</p>
                        </div>
                        <div class="entryFooter">Posted 23rd August 2026 at 8:24 pm</div>
                      </div>
                      <div class="recent-articles"><h2>Recent articles</h2><p>Older post link</p></div>
                    </div>
                    <div id="secondary"><p>This is a link post by Simon Willison.</p></div>
                  </div>
                </body>
            """.trimIndent(),
            url = "https://simonwillison.net/2026/Aug/23/example/",
            options = testOptions(debug = true),
        )

        val markdown = result.content.requireMarkdown()
        assertTrue(markdown.startsWith("23rd August 2026 - Link Blog"))
        assertTrue(markdown.contains("The linked report title"))
        assertFalse(markdown.contains("Simon Willison’s Weblog"))
        assertFalse(markdown.contains("Posted 23rd August 2026"))
        assertFalse(markdown.contains("Recent articles"))
        assertFalse(markdown.contains("This is a link post"))
        assertEquals(".entry.entryPage", result.debug["selectedContentSelector"])
        assertEquals(listOf("simon-willison"), result.debug["extractorIds"])
    }

    @Test
    fun `nasa profile removes social icon list before contents`() {
        val result = parseHtmlForTest(
            html = """
                <article>
                  <p>The mission article body starts with enough natural language to keep the selected content stable before the social links.</p>
                  <ul class="social-icons social-icons-round">
                    <li class="social-icon social-icon-rss">
                      <a href="/feed/" aria-label="Link to RSS Feed.">
                        <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 800 800" aria-hidden="true">
                          <path d="M493 652H392c0-134-111-244-244-244V307c189 0 345 156 345 345z"></path>
                          <circle cx="219" cy="581" r="71"></circle>
                        </svg>
                      </a>
                    </li>
                  </ul>
                  <div class="usa-article-scroll-wrapper">
                    <h2>Contents</h2>
                    <ul><li><a href="#visual-description">Visual Description</a></li></ul>
                  </div>
                  <p>The science result should remain readable after the social icon list is removed from the article body.</p>
                </article>
            """.trimIndent(),
            url = "https://science.nasa.gov/missions/chandra/example",
            options = testOptions(debug = true),
        )

        val markdown = result.content.requireMarkdown()
        assertTrue(markdown.contains("## Contents"))
        assertTrue(markdown.contains("Visual Description"))
        assertTrue(markdown.contains("The science result should remain readable"))
        assertFalse(markdown.contains("<svg"))
        assertFalse(markdown.contains("RSS Feed"))
        assertEquals(listOf("nasa"), result.debug["extractorIds"])
    }

    @Test
    fun `lesswrong profile selects post body without page chrome`() {
        val result = parseHtmlForTest(
            html = """
                <body>
                  <header>
                    <a href="/">LESSWRONG</a>
                    <a href="/">LW</a>
                  </header>
                  <div class="PostsPage-splashHeaderImage">
                    <img src="https://example.com/splash.jpg" alt="Background Image">
                  </div>
                  <main>
                    <div class="PostsPage-title"><h1>Simulators</h1></div>
                    <div class="PostsPage-postContent instapaper_body ContentStyles-base content ContentStyles-postBody">
                      <div class="commentOnSelection">
                        <div id="postContent">
                          <p><i>Thanks to reviewers for feedback on drafts.</i></p>
                          <p>
                            The article body should stay because it has enough natural language to pass
                            the LessWrong profile selector guard while a footnote appears here.
                            <span role="doc-noteref" class="footnote-reference">
                              <sup><a href="#fnabc123">[1]</a></sup>
                            </span>
                          </p>
                          <ol role="doc-endnotes" class="footnotes">
                            <li role="doc-endnote" id="fnabc123" class="footnote-item">
                              <span class="footnote-back-link"><sup><strong><a href="#fnrefabc123">^</a></strong></sup></span>
                              <div class="footnote-content"><p>The footnote definition should stay without the back-link marker.</p></div>
                            </li>
                          </ol>
                        </div>
                      </div>
                    </div>
                    <div class="PostsPage-commentsSection">Comments should not be selected.</div>
                  </main>
                </body>
            """.trimIndent(),
            url = "https://www.lesswrong.com/posts/example/simulators",
            options = testOptions(debug = true),
        )

        val markdown = result.content.requireMarkdown()
        assertTrue(markdown.startsWith("*Thanks to reviewers for feedback on drafts.*"))
        assertTrue(markdown.contains("[^1]"))
        assertTrue(markdown.contains("[^1]: The footnote definition should stay without the back-link marker."))
        assertFalse(markdown.contains("LESSWRONG"))
        assertFalse(markdown.contains("Background Image"))
        assertFalse(markdown.contains("Comments should not be selected"))
        assertFalse(markdown.contains("marker. ^"))
        assertEquals(".PostsPage-postContent", result.debug["selectedContentSelector"])
        assertEquals(listOf("lesswrong"), result.debug["extractorIds"])
    }

    @Test
    fun `maggie appleton profile removes astro chrome and keeps inline footnotes`() {
        val result = parseHtmlForTest(
            html = """
                <body>
                  <article class="prose-wrapper">
                    <div class="desktop-container"><h4>Table of Contents</h4></div>
                    <p>
                      The article paragraph should stay and its note should become a real footnote.
                      <span class="footnote-container">
                        <label for="1" class="margin-toggle footnote-number"></label>
                        <input type="checkbox" id="1" class="margin-toggle">
                        <span class="footnote">
                          The note points to
                          <span class="tooltip-trigger">
                            <a href="https://example.com/source">a source</a>
                            <template class="tooltip-content">
                              <a class="external-url" href="https://example.com/source">https://example.com/source</a>
                            </template>
                          </span>.
                        </span>
                      </span>
                    </p>
                    <div class="book-card">
                      <img src="/cover.jpg" alt="Book cover">
                      <div class="metadata">Preview card title that should disappear.</div>
                    </div>
                    <figure class="container framed">
                      <img src="/framed.jpg" alt="Framed image caption">
                    </figure>
                    <figure class="container">
                      <img src="/plain.jpg" alt="Unframed image caption">
                    </figure>
                    <div class="tweet-embed">Embedded tweet that should disappear.</div>
                    <p>The second paragraph keeps the preferred selector substantial enough for extraction.</p>
                  </article>
                </body>
            """.trimIndent(),
            url = "https://maggieappleton.com/xanadu-patterns",
            options = testOptions(debug = true),
        )

        val markdown = result.content.requireMarkdown()
        assertTrue(markdown.startsWith("The article paragraph should stay"))
        assertTrue(markdown.contains("[^1]"))
        assertTrue(markdown.contains("![Book cover](https://maggieappleton.com/cover.jpg)"))
        assertTrue(markdown.contains("Framed image caption"))
        assertTrue(markdown.contains("Unframed image caption"))
        assertTrue(markdown.contains("[^1]: The note points to [a source](https://example.com/source)"))
        assertFalse(markdown.contains("Table of Contents"))
        assertFalse(markdown.contains("Preview card title"))
        assertFalse(markdown.contains("Embedded tweet"))
        assertFalse(markdown.contains("https://example.com/source](https://example.com/source)"))
        assertEquals("article.prose-wrapper", result.debug["selectedContentSelector"])
        assertEquals(listOf("maggie-appleton"), result.debug["extractorIds"])
    }

    @Test
    fun `custom extractors do not replace built in defaults`() {
        val custom = TestExtractor(domains = setOf("custom.example"))
        val result = parseHtmlForTest(
            html = """
                <article>
                  <p>The actual article body should stay because it contains enough realistic prose for deterministic parsing.</p>
                  <p>A second paragraph keeps the article body stable while a MacRumors-style linkback footer appears below it.</p>
                  <div class="linkback">Related Roundup: <a href="/roundup/example">Example Product</a></div>
                </article>
            """.trimIndent(),
            url = "https://www.macrumors.com/example",
            options = testOptions(customExtractors = listOf(custom), debug = true),
        )

        assertFalse(result.content.requireMarkdown().contains("Related Roundup"))
        assertEquals(listOf("macrumors"), result.debug["extractorIds"])
    }

    @Test
    fun `citynews profile selectors do not run on unrelated hosts`() {
        val html = """
            <main>
              <article>
                <p>The actual article body should stay because it contains enough realistic prose for deterministic parsing.</p>
                <p>A second paragraph keeps the article body stable while a Citynews-style footer appears below it.</p>
                <section class="l-entry__footer">
                  <p>Riproduzione riservata</p>
                </section>
              </article>
            </main>
        """.trimIndent()

        val matching = parseHtmlForTest(
            html = html,
            url = "https://www.veneziatoday.it/example",
            options = testOptions(debug = true),
        )
        val unrelated = parseHtmlForTest(
            html = html,
            url = "https://example.com/not-citynews",
            options = testOptions(debug = true),
        )

        assertFalse(matching.content.requireMarkdown().contains("Riproduzione riservata"))
        assertTrue(unrelated.content.requireMarkdown().contains("Riproduzione riservata"))
        assertEquals(listOf("citynews"), matching.debug["extractorIds"])
        assertFalse(unrelated.debug.containsKey("extractorIds"))
    }

    @Test
    fun `chatgpt share extractor preserves post thought assistant content`() {
        val result = parseHtmlForTest(
            html = """
                <main>
                  <div data-testid="conversation-turn-1">
                    <h5 class="sr-only">You said:</h5>
                    <div data-message-author-role="user">
                      <div class="whitespace-pre-wrap">Please help me plan a simple weekend picnic.</div>
                    </div>
                  </div>
                  <div data-testid="conversation-turn-2">
                    <h5 class="sr-only">ChatGPT said:</h5>
                    <div data-message-author-role="assistant">
                      <div class="markdown">
                        <p>Start with a simple checklist before choosing the location.</p>
                      </div>
                    </div>
                    <button type="button"><span>Thought for 12s</span></button>
                    <div data-message-author-role="assistant">
                      <div class="markdown">
                        <p>Pick a nearby park, check the weather, and choose food that travels well.</p>
                      </div>
                    </div>
                  </div>
                </main>
            """.trimIndent(),
            url = "https://chatgpt.com/share/example-post-thought-content",
            options = testOptions(debug = true),
        )

        assertEquals("ChatGPT", result.metadata.site)
        assertEquals(listOf("chatgpt"), result.debug["extractorIds"])
        assertEquals(
            """
            **You said**

            Please help me plan a simple weekend picnic.

            ---

            **ChatGPT said**

            Start with a simple checklist before choosing the location.

            Pick a nearby park, check the weather, and choose food that travels well.
            """.trimIndent() + "\n",
            result.content.requireMarkdown(),
        )
    }

    @Test
    fun `elementor archive profile selects archive body without listing chrome`() {
        val result = parseHtmlForTest(
            html = """
                <html>
                  <head><title>Apartments for Sale | Updated 2025</title></head>
                  <body class="archive post-type-archive elementor-default elementor-template-full-width">
                    <header data-elementor-type="header" class="elementor elementor-location-header">
                      <a href="/"><img src="/logo.png" alt="Site Logo"></a>
                    </header>
                    <div data-elementor-type="archive" class="elementor elementor-location-archive">
                      <h1>PREMIUM APARTMENTS FOR SALE</h1>
                      <div class="elementor-widget elementor-widget-jet-ajax-search">
                        <form><input type="search" placeholder="Search..."></form>
                      </div>
                      <div class="e-con">
                        <div class="elementor-widget elementor-widget-heading">
                          <h2>Current Premium Apartment Projects</h2>
                        </div>
                        <div class="elementor-widget elementor-widget-jet-engine-maps-listing">
                          <div class="jet-map-listing">Map marker text</div>
                        </div>
                      </div>
                      <div class="elementor-widget elementor-widget-jet-listing-grid">
                        <div class="jet-listing-grid__item">Listing card text</div>
                      </div>
                      <div class="elementor-widget elementor-widget-text-editor">
                        <p>Address: 123 River Road<br>Price: 6.4 - 57.5 billion<br>Status: Handed over</p>
                      </div>
                    </div>
                    <footer data-elementor-type="footer" class="elementor elementor-location-footer">
                      Contact us for expert consultation.
                    </footer>
                  </body>
                </html>
            """.trimIndent(),
            url = "https://example.com/apartments/",
            options = testOptions(debug = true),
        )

        val markdown = result.content.requireMarkdown()
        assertTrue(markdown.contains("PREMIUM APARTMENTS FOR SALE"))
        assertTrue(markdown.contains("Address: 123 River Road  \nPrice: 6.4 - 57.5 billion"))
        assertFalse(markdown.contains("Site Logo"))
        assertFalse(markdown.contains("Current Premium Apartment Projects"))
        assertFalse(markdown.contains("Map marker text"))
        assertFalse(markdown.contains("Listing card text"))
        assertFalse(markdown.contains("Contact us"))
        assertEquals(listOf("elementor-archive"), result.debug["extractorIds"])
    }

    @Test
    fun `x longform extractor preserves article content and media quality`() {
        val result = parseHtmlForTest(
            html = """
                <main>
                  <div data-testid="twitterArticleRichTextView">
                    <div data-testid="twitter-article-title">Lorem Ipsum Dolor Sit Amet</div>
                    <div itemprop="author">
                      <meta itemprop="name" content="Jane Doe">
                      <meta itemprop="additionalName" content="janedoe">
                    </div>
                    <div class="longform-unstyled">
                      <div class="public-DraftStyleDefault-block">
                        Lorem ipsum dolor sit amet, consectetur adipiscing elit.
                      </div>
                    </div>
                    <div class="longform-unstyled">
                      <div class="public-DraftStyleDefault-block">
                        <span style="font-weight: bold">Ut enim ad minim:</span> Veniam quis nostrud exercitation.
                      </div>
                    </div>
                    <div class="longform-unstyled">
                      <div class="public-DraftStyleDefault-block">
                        Keep <div><a href="//CLAUDE.md">CLAUDE.md</a></div> inline.
                      </div>
                    </div>
                    <div data-testid="tweetPhoto">
                      <img src="https://example.com/media/placeholder.jpg?format=jpg&name=medium" alt="Placeholder image">
                    </div>
                    <div class="longform-unstyled">
                      <div class="public-DraftStyleDefault-block">
                        Duis aute irure dolor in reprehenderit.
                      </div>
                    </div>
                  </div>
                </main>
            """.trimIndent(),
            url = "https://x.com/example/article/1",
            options = testOptions(debug = true),
        )

        assertEquals("X (Twitter)", result.metadata.site)
        assertEquals(listOf("x"), result.debug["extractorIds"])
        assertEquals(
            """
            Lorem Ipsum Dolor Sit Amet



            Lorem ipsum dolor sit amet, consectetur adipiscing elit.

            **Ut enim ad minim:** Veniam quis nostrud exercitation.

            Keep [CLAUDE.md](https://claude.md/) inline.

            ![Placeholder image](https://example.com/media/placeholder.jpg?format=jpg&name=large)

            Duis aute irure dolor in reprehenderit.
            """.trimIndent() + "\n",
            result.content.requireMarkdown(),
        )
    }

    @Test
    fun `x conversation extractor keeps main post and reply comments`() {
        val result = parseHtmlForTest(
            html = """
                <main>
                  <section aria-label="时间线：对话">
                    <div data-testid="cellInnerDiv">
                      <article data-testid="tweet">
                        <div data-testid="User-Name">
                          <a href="/main_user">Main User</a>
                          <a href="/main_user">@main_user</a>
                        </div>
                        <a href="/main_user/status/1234567890">
                          <time datetime="2026-05-16T12:00:00.000Z">May 16</time>
                        </a>
                        <div data-testid="tweetText">Main post from a localized X interface.</div>
                      </article>
                    </div>
                    <div data-testid="cellInnerDiv">
                      <div role="separator"></div>
                    </div>
                    <div data-testid="cellInnerDiv">
                      <article data-testid="tweet">
                        <div data-testid="User-Name">
                          <a href="/reply_user">Reply User</a>
                          <a href="/reply_user">@reply_user</a>
                        </div>
                        <a href="/reply_user/status/1234567891">
                          <time datetime="2026-05-16T12:05:00.000Z">May 16</time>
                        </a>
                        <div data-testid="tweetText">Reply that should be extracted from the localized timeline.</div>
                      </article>
                    </div>
                  </section>
                </main>
            """.trimIndent(),
            url = "https://x.com/main_user/status/1234567890",
            options = testOptions(debug = true),
        )

        assertEquals("X (Twitter)", result.metadata.site)
        assertEquals(listOf("x"), result.debug["extractorIds"])
        assertEquals(
            """
            Main post from a localized X interface.

            ---

            ## Comments

            > **Reply User @reply\_user** · [2026-05-16](https://x.com/reply_user/status/1234567891)
            >
            > Reply that should be extracted from the localized timeline.
            """.trimIndent() + "\n",
            result.content.requireMarkdown(),
        )
    }

    @Test
    fun `obsidian publish extractor preserves markdown body without publish chrome`() {
        val result = parseHtmlForTest(
            html = """
                <html>
                  <head><meta property="og:site_name" content="Obsidian 中文帮助"></head>
                  <body>
                    <div class="published-container">
                      <div class="markdown-preview-view markdown-rendered">
                        <div class="markdown-preview-sizer markdown-preview-section">
                          <div class="mod-header mod-ui"><h1 class="page-header">开发者</h1></div>
                          <div class="el-p">
                            <p dir="auto">如果你熟悉 TypeScript 或 CSS，你可以开发自己的<a href="https://publish.obsidian.md/help-zh/扩展+Obsidian/第三方插件">第三方插件</a>和<a href="https://publish.obsidian.md/help-zh/扩展+Obsidian/主题">主题</a>。</p>
                          </div>
                          <div class="el-p">
                            <p dir="auto">了解更多信息，请访问<a href="https://docs.obsidian.md">Obsidian 开发者文档</a>。</p>
                          </div>
                          <div class="mod-footer mod-ui">
                            <div class="backlinks"><a href="https://publish.obsidian.md/help-zh/帮助与支持">帮助与支持</a></div>
                          </div>
                        </div>
                      </div>
                    </div>
                  </body>
                </html>
            """.trimIndent(),
            url = "https://publish.obsidian.md/help-zh/助力+Obsidian/开发者",
            options = testOptions(debug = true),
        )

        assertEquals("Obsidian 中文帮助", result.metadata.site)
        assertEquals(listOf("obsidian-publish"), result.debug["extractorIds"])
        assertEquals(
            """
            如果你熟悉 TypeScript 或 CSS，你可以开发自己的 [第三方插件](https://publish.obsidian.md/help-zh/%E6%89%A9%E5%B1%95+Obsidian/%E7%AC%AC%E4%B8%89%E6%96%B9%E6%8F%92%E4%BB%B6) 和 [主题](https://publish.obsidian.md/help-zh/%E6%89%A9%E5%B1%95+Obsidian/%E4%B8%BB%E9%A2%98) 。

            了解更多信息，请访问 [Obsidian 开发者文档](https://docs.obsidian.md/) 。
            """.trimIndent() + "\n",
            result.content.requireMarkdown(),
        )
    }

    @Test
    fun `wikipedia extractor removes article chrome while preserving reference sections`() {
        val result = parseHtmlForTest(
            html = """
                <div id="bodyContent">
                  <div id="siteSub">From Wikipedia, the free encyclopedia</div>
                  <div id="mw-content-text">
                    <div class="mw-parser-output">
                      <table class="infobox"><tr><th>Developer(s)</th><td>Example Inc.</td></tr></table>
                      <div id="toc" class="toc">Contents History References</div>
                      <p><b>Example</b> body text with a source.<sup id="cite_ref-one_1-0" class="reference"><a href="#cite_note-one-1">[1]</a></sup></p>
                      <h2><span class="mw-headline">History</span><span class="mw-editsection">[edit]</span></h2>
                      <p>History paragraph stays readable after Wikipedia chrome is removed.</p>
                      <h2><span class="mw-headline">See also</span><span class="mw-editsection">[edit]</span></h2>
                      <ul><li><a href="/wiki/Related_topic" title="Related topic">Related topic</a></li></ul>
                      <h2><span class="mw-headline">References</span><span class="mw-editsection">[edit]</span></h2>
                      <div class="reflist">
                        <ol class="references">
                          <li id="cite_note-one-1"><span class="mw-cite-backlink"><b><a href="#cite_ref-one_1-0">^</a></b></span> <span class="reference-text">Reference text.</span></li>
                        </ol>
                      </div>
                    </div>
                  </div>
                </div>
            """.trimIndent(),
            url = "https://en.wikipedia.org/wiki/Example",
            options = testOptions(debug = true),
        )

        val markdown = result.content.requireMarkdown()
        assertTrue(markdown.startsWith("**Example** body text with a source.[^1]"))
        assertTrue(markdown.contains("## History"))
        assertTrue(
            markdown.contains("- [Related topic](https://en.wikipedia.org/wiki/Related_topic)"),
        )
        assertTrue(markdown.contains("[^1]: Reference text."))
        assertFalse(markdown.contains("From Wikipedia"))
        assertFalse(markdown.contains("Developer(s)"))
        assertFalse(markdown.contains("[edit]"))
        assertFalse(markdown.contains("[^](#cite_ref"))
        assertEquals("Wikipedia", result.metadata.site)
        assertEquals(listOf("wikipedia"), result.debug["extractorIds"])
    }

    @Test
    fun `wikipedia extractor removes duplicate citation paragraph before appendix sections`() {
        val result = parseHtmlForTest(
            html = """
                <html>
                  <head><meta property="og:site_name" content="Wikipedia"></head>
                  <body class="mediawiki">
                    <div id="mw-content-text">
                      <div class="mw-parser-output">
                        <p>Opening paragraph with a source.<sup class="reference"><a href="#cite_note-one-1">[1]</a></sup></p>
                        <div class="mw-heading mw-heading2"><h2 id="Applications">Applications</h2></div>
                        <p>Section paragraph adds a reused source.<sup class="reference"><a href="#cite_note-shared-2">[2]</a></sup></p>
                        <p>Repeated citation paragraph before the appendix.<sup class="reference"><a href="#cite_note-shared-2">[2]</a></sup></p>
                        <div class="mw-heading mw-heading2"><h2 id="See_also">See also</h2></div>
                        <ul><li><a href="/wiki/Related_topic">Related topic</a></li></ul>
                        <div class="mw-heading mw-heading2"><h2 id="References">References</h2></div>
                        <ol class="references">
                          <li id="cite_note-one-1">First reference.</li>
                          <li id="cite_note-shared-2">Shared reference.</li>
                        </ol>
                      </div>
                    </div>
                  </body>
                </html>
            """.trimIndent(),
            url = "https://wikipedia-references",
            options = testOptions(debug = true),
        )

        val markdown = result.content.requireMarkdown()
        assertTrue(markdown.contains("Opening paragraph with a source.[^1]"))
        assertTrue(markdown.contains("Section paragraph adds a reused source.[^2]"))
        assertFalse(markdown.contains("Repeated citation paragraph before the appendix"))
        assertTrue(markdown.contains("[^2]: Shared reference."))
        assertEquals(listOf("wikipedia"), result.debug["extractorIds"])
    }

    @Test
    fun `hacker news extractor preserves single comment pages`() {
        val result = parseHtmlForTest(
            html = """
                <table class="fatitem">
                  <tr class="athing" id="12345678">
                    <td class="default">
                      <div>
                        <span class="comhead">
                          <a href="user?id=testuser" class="hnuser">testuser</a>
                          <span class="age" title="2025-06-15T12:00:00"><a href="item?id=12345678">1 day ago</a></span>
                          <span class="navs"> | <a href="item?id=12345000">parent</a></span>
                        </span>
                      </div>
                      <div class="comment">
                        <div class="commtext">
                          This is the main comment text that should be extracted.
                          <p>It has multiple paragraphs.</p>
                          <p>And a link: <a href="https://example.com" rel="nofollow">https://example.com</a></p>
                        </div>
                      </div>
                    </td>
                  </tr>
                </table>
            """.trimIndent(),
            url = "https://news.ycombinator.com/item?id=12345678",
            options = testOptions(debug = true),
        )

        assertEquals("Hacker News", result.metadata.site)
        assertEquals(listOf("hacker-news"), result.debug["extractorIds"])
        assertEquals(
            """
            **testuser** · 2025-06-15

            This is the main comment text that should be extracted.

            It has multiple paragraphs.

            And a link: [https://example.com](https://example.com/)
            """.trimIndent() + "\n",
            result.content.requireMarkdown(),
        )
    }

    @Test
    fun `hacker news extractor preserves story comment trees`() {
        val result = parseHtmlForTest(
            html = """
                <table class="fatitem">
                  <tr class="athing" id="12345678">
                    <td class="title">
                      <span class="titleline"><a href="https://example.com/article">A Sample Article</a></span>
                    </td>
                  </tr>
                </table>
                <table class="comment-tree">
                  <tr class="comtr" id="12345679">
                    <td class="ind"><img src="s.gif" width="0" height="1"></td>
                    <td>
                      <div class="comhead">
                        <a class="hnuser" href="user?id=commenter_one">commenter_one</a>
                        <span class="age" title="2025-01-15T10:00:00"><a href="item?id=12345679">2 hours ago</a></span>
                        <span class="score">25 points</span>
                      </div>
                      <div class="comment"><span class="commtext"><p>Top-level comment.</p></span></div>
                    </td>
                  </tr>
                  <tr class="comtr" id="12345680">
                    <td class="ind"><img src="s.gif" width="40" height="1"></td>
                    <td>
                      <div class="comhead">
                        <a class="hnuser" href="user?id=commenter_two">commenter_two</a>
                        <span class="age" title="2025-01-15T10:30:00"><a href="item?id=12345680">90 minutes ago</a></span>
                      </div>
                      <div class="comment"><span class="commtext"><p>Nested reply.</p></span></div>
                    </td>
                  </tr>
                  <tr class="comtr" id="12345681">
                    <td class="ind"><img src="s.gif" width="0" height="1"></td>
                    <td>
                      <div class="comhead">
                        <a class="hnuser" href="user?id=commenter_three">commenter_three</a>
                        <span class="age" title="2025-01-15T11:00:00"><a href="item?id=12345681">1 hour ago</a></span>
                      </div>
                      <div class="comment"><span class="commtext"><p>Second root comment.</p></span></div>
                    </td>
                  </tr>
                </table>
            """.trimIndent(),
            url = "https://news.ycombinator.com/item?id=12345678",
            options = testOptions(debug = true),
        )

        assertEquals("Hacker News", result.metadata.site)
        assertEquals(listOf("hacker-news"), result.debug["extractorIds"])
        assertEquals(
            """
            [https://example.com/article](https://example.com/article)

            ---

            ## Comments

            > **commenter\_one** · [2025-01-15](https://news.ycombinator.com/item?id=12345679) · 25 points
            >
            > Top-level comment.
            >
            > > **commenter\_two** · [2025-01-15](https://news.ycombinator.com/item?id=12345680)
            > >
            > > Nested reply.

            > **commenter\_three** · [2025-01-15](https://news.ycombinator.com/item?id=12345681)
            >
            > Second root comment.
            """.trimIndent() + "\n",
            result.content.requireMarkdown(),
        )
    }

    @Test
    fun `reddit extractor preserves post body and nested comment trees`() {
        val result = parseHtmlForTest(
            html = """
                <div class="content" role="main">
                  <div class="thing link" data-permalink="/r/test/comments/abc123/test_post/">
                    <a class="title" href="/r/test/comments/abc123/test_post/">Test Post</a>
                    <div class="entry">
                      <div class="usertext-body"><div class="md"><p>This is the post body with some content.</p></div></div>
                    </div>
                  </div>
                  <div class="commentarea">
                    <div class="sitetable nestedlisting">
                      <div class="thing comment" data-permalink="/r/test/comments/abc123/test_post/comment1/">
                        <div class="entry">
                          <p class="tagline">
                            <a class="author" href="/user/user_alpha">user_alpha</a>
                            <span class="score unvoted">42 points</span>
                            <time datetime="2025-01-15T10:30:00Z">2 days ago</time>
                          </p>
                          <div class="usertext-body"><div class="md"><p>This is a top-level comment with some thoughts on the topic.</p></div></div>
                        </div>
                        <div class="child">
                          <div class="sitetable">
                            <div class="thing comment" data-permalink="/r/test/comments/abc123/test_post/comment2/">
                              <div class="entry">
                                <p class="tagline">
                                  <a class="author" href="/user/user_beta">user_beta</a>
                                  <span class="score unvoted">15 points</span>
                                  <time datetime="2025-01-15T11:00:00Z">2 days ago</time>
                                </p>
                                <div class="usertext-body"><div class="md"><p>Great point! I agree with this.</p></div></div>
                              </div>
                              <div class="child"></div>
                            </div>
                          </div>
                        </div>
                      </div>
                    </div>
                  </div>
                </div>
            """.trimIndent(),
            url = "https://old.reddit.com/r/test/comments/abc123/test_post",
            options = testOptions(debug = true),
        )

        assertEquals("Reddit", result.metadata.site)
        assertEquals(listOf("reddit"), result.debug["extractorIds"])
        assertEquals(
            """
            This is the post body with some content.

            ---

            ## Comments

            > **user\_alpha** · [2025-01-15](https://reddit.com/r/test/comments/abc123/test_post/comment1/) · 42 points
            >
            > This is a top-level comment with some thoughts on the topic.
            >
            > > **user\_beta** · [2025-01-15](https://reddit.com/r/test/comments/abc123/test_post/comment2/) · 15 points
            > >
            > > Great point! I agree with this.
            """.trimIndent() + "\n",
            result.content.requireMarkdown(),
        )
    }

    @Test
    fun `mastodon extractor preserves status thread and reply comments`() {
        val result = parseHtmlForTest(
            html = """
                <div id="mastodon" class="app-holder">
                  <div class="detailed-status">
                    <a href="/@alice" class="detailed-status__display-name">
                      <span class="display-name">
                        <bdi><strong class="display-name__html">Alice</strong></bdi>
                        <span class="display-name__account">@alice@mastodon.example</span>
                      </span>
                    </a>
                    <div class="status__content">
                      <div class="status__content__text status__content__text--visible translate">
                        <p>This is a sample post about something interesting. Check out this link!</p>
                        <p><a href="https://example.com/interesting" title="https://example.com/interesting"><span class="invisible">https://</span><span>example.com/interesting</span></a></p>
                      </div>
                    </div>
                    <div class="media-gallery">
                      <a class="media-gallery__item-thumbnail" href="https://cdn.mastodon.example/media/original/sample-image.png">
                        <img src="https://cdn.mastodon.example/media/small/sample-image.png" alt="A sample screenshot showing the project">
                      </a>
                    </div>
                  </div>
                  <div class="status status-public status-reply status--in-thread" data-id="12345679">
                    <div class="status__info">
                      <a href="/@alice/12345679" class="status__relative-time"><time datetime="2026-04-20T12:05:00.000Z">5m</time></a>
                      <a href="/@alice" class="status__display-name">
                        <span class="display-name"><bdi><strong class="display-name__html">Alice</strong></bdi><span class="display-name__account">@alice</span></span>
                      </a>
                    </div>
                    <div class="status__content"><div class="status__content__text status__content__text--visible translate"><p>Here is some more context about the project</p></div></div>
                  </div>
                  <div class="status status-public status-reply status--in-thread status--first-in-thread" data-id="12345680">
                    <div class="status__info">
                      <a href="/@bob@other.social/12345680" class="status__relative-time"><time datetime="2026-04-20T12:15:00.000Z">15m</time></a>
                      <a href="/@bob@other.social" class="status__display-name">
                        <span class="display-name"><bdi><strong class="display-name__html">Bob</strong></bdi><span class="display-name__account">@bob@other.social</span></span>
                      </a>
                    </div>
                    <div class="status__content"><div class="status__content__text status__content__text--visible translate"><p><span class="h-card"><a href="/@alice" class="u-url mention">@<span>alice</span></a></span> This is really cool! Great work.</p></div></div>
                  </div>
                  <div class="status status-public status-reply status--in-thread" data-id="12345681">
                    <div class="status__info">
                      <a href="/@alice/12345681" class="status__relative-time"><time datetime="2026-04-20T12:35:00.000Z">35m</time></a>
                      <a href="/@alice" class="status__display-name">
                        <span class="display-name"><bdi><strong class="display-name__html">Alice</strong></bdi><span class="display-name__account">@alice</span></span>
                      </a>
                    </div>
                    <div class="status__content"><div class="status__content__text status__content__text--visible translate"><p><span class="h-card"><a href="/@bob@other.social" class="u-url mention">@<span>bob</span></a></span> Thanks for the reply!</p></div></div>
                  </div>
                  <div class="status status-public status-reply status--in-thread status--first-in-thread" data-id="12345682">
                    <div class="status__info">
                      <a href="/@dave/12345682" class="status__relative-time"><time datetime="2026-04-20T13:00:00.000Z">1h</time></a>
                      <a href="/@dave" class="status__display-name">
                        <span class="display-name"><bdi><strong class="display-name__html">Dave</strong></bdi><span class="display-name__account">@dave@mastodon.example</span></span>
                      </a>
                    </div>
                    <div class="status__content"><div class="status__content__text status__content__text--visible translate"><p><span class="h-card"><a href="/@alice" class="u-url mention">@<span>alice</span></a></span> Related project here</p></div></div>
                    <a href="https://example.com/related" class="status-card">
                      <div class="status-card__image"><img src="https://cdn.mastodon.example/cards/related-preview.png" alt=""></div>
                      <strong class="status-card__title">Related Project</strong>
                      <span class="status-card__description">A similar project with different goals</span>
                    </a>
                  </div>
                </div>
            """.trimIndent(),
            url = "https://mastodon.example/@alice/12345678",
            options = testOptions(debug = true),
        )

        assertEquals("mastodon.example", result.metadata.site)
        assertEquals(listOf("mastodon"), result.debug["extractorIds"])
        assertEquals(
            """
            This is a sample post about something interesting. Check out this link!

            [example.com/interesting](https://example.com/interesting "https://example.com/interesting")

            ![A sample screenshot showing the project](https://cdn.mastodon.example/media/original/sample-image.png)

            ---

            Here is some more context about the project

            ---

            ## Comments

            > **Bob @bob@other.social** · [2026-04-20](https://mastodon.example/@bob@other.social/12345680)
            >
            > [@alice](https://mastodon.example/@alice) This is really cool! Great work.
            >
            > > **Alice @alice** · [2026-04-20](https://mastodon.example/@alice/12345681)
            > >
            > > [@bob](https://mastodon.example/@bob@other.social) Thanks for the reply!

            > **Dave @dave@mastodon.example** · [2026-04-20](https://mastodon.example/@dave/12345682)
            >
            > [@alice](https://mastodon.example/@alice) Related project here
            >
            > [![Related Project](https://cdn.mastodon.example/cards/related-preview.png)](https://example.com/related)
            >
            > [Related Project](https://example.com/related)
            >
            > A similar project with different goals
            """.trimIndent() + "\n",
            result.content.requireMarkdown(),
        )
    }

    @Test
    fun `stripe docs profile removes code tab chrome and duplicate title`() {
        val result = parseHtmlForTest(
            html = """
                <meta property="og:site_name" content="Stripe Documentation">
                <main>
                  <article id="content">
                    <div>
                      <h1>x402 payments</h1>
                      <h2>Use x402 for machine-to-machine payments.</h2>
                    </div>
                    <div role="toolbar" aria-label="Actions"><button>Ask about this page</button></div>
                    <div class="Document">
                      <p>x402 is a protocol for internet payments. When a client requests a paid resource, your server returns a <code>402 Payment Required</code> response with payment details.</p>
                      <div class="CodeTabGroup">
                        <div role="listbox"><div role="option">Node.js</div><div role="option">Python</div><div>No results</div></div>
                        <div class="CodeBlock">
                          <div class="CodeBlock-header"><div class="CodeBlock-filename">Command Line</div></div>
                          <pre class="CodeBlock-content"><code class="CodeBlock-code">curl http://localhost:3000/paid</code></pre>
                        </div>
                      </div>
                    </div>
                  </article>
                </main>
            """.trimIndent(),
            url = "https://docs.stripe.com/x402",
            options = testOptions(debug = true),
        )

        assertEquals(listOf("stripe-docs"), result.debug["extractorIds"])
        assertEquals(
            """
            ## Use x402 for machine-to-machine payments.

            x402 is a protocol for internet payments. When a client requests a paid resource, your server returns a `402 Payment Required` response with payment details.

            ```
            curl http://localhost:3000/paid
            ```
            """.trimIndent() + "\n",
            result.content.requireMarkdown(),
        )
    }

    @Test
    fun `hacker news extractor preserves listing pages`() {
        val result = parseHtmlForTest(
            html = """
                <table>
                  <tr class="athing" id="10000001">
                    <td class="title"><span class="rank">1.</span></td>
                    <td class="title">
                      <span class="titleline">
                        <a href="https://example.com/building-a-database-from-scratch">Building a Database from Scratch in Rust</a>
                        <span class="sitebit comhead"> (<a href="from?site=example.com"><span class="sitestr">example.com</span></a>)</span>
                      </span>
                    </td>
                  </tr>
                  <tr>
                    <td class="subtext">
                      <span class="score">384 points</span> by <a class="hnuser" href="user?id=dev_user">dev_user</a>
                      <span class="age"><a href="item?id=10000001">5 hours ago</a></span> |
                      <a href="item?id=10000001">142&nbsp;comments</a>
                    </td>
                  </tr>
                  <tr><td class="title"><a href="news?p=2" class="morelink" rel="next">More</a></td></tr>
                </table>
            """.trimIndent(),
            url = "https://news.ycombinator.com/news",
            options = testOptions(debug = true),
        )

        assertEquals("Hacker News", result.metadata.site)
        assertEquals(listOf("hacker-news"), result.debug["extractorIds"])
        assertEquals(
            """
            1. [Building a Database from Scratch in Rust](https://example.com/building-a-database-from-scratch) (example.com)%BR%
            %TAB%384 points · by dev\_user · [142 comments](https://news.ycombinator.com/item?id=10000001)

            [More](https://news.ycombinator.com/news?p=2)
            """.trimIndent()
                .replace("%BR%", "  ")
                .replace("%TAB%", "\t") + "\n",
            result.content.requireMarkdown(),
        )
    }

    @Test
    fun `github pull request extractor preserves body and discussion comments`() {
        val result = parseHtmlForTest(
            html = """
                <div class="pull-discussion-timeline">
                  <div class="js-discussion">
                    <div id="pullrequest-1" class="comment js-comment">
                      <h3>
                        <a class="author" href="/author-one">author-one</a>
                        <relative-time datetime="2026-01-15T10:30:00Z">Jan 15, 2026</relative-time>
                      </h3>
                      <div class="comment-body markdown-body">
                        <h2>Summary</h2>
                        <p>This fixes a regression where content was clipped.</p>
                      </div>
                    </div>
                    <div id="discussion_r1" class="comment js-comment">
                      <h3>
                        <a class="author" href="/reviewer-bot">reviewer-bot</a>
                        <relative-time datetime="2026-01-15T10:45:00Z">Jan 15, 2026</relative-time>
                      </h3>
                      <div class="comment-body markdown-body">
                        <p>Consider removing just the image element.</p>
                      </div>
                    </div>
                  </div>
                </div>
            """.trimIndent(),
            url = "https://github.com/test-owner/test-repo/pull/42",
            options = testOptions(debug = true),
        )

        assertEquals("GitHub - test-owner/test-repo", result.metadata.site)
        assertEquals(listOf("github"), result.debug["extractorIds"])
        assertEquals(
            """
            ## Summary

            This fixes a regression where content was clipped.

            ---

            ## Comments

            > **reviewer-bot** · 2026-01-15
            >
            > Consider removing just the image element.
            """.trimIndent() + "\n",
            result.content.requireMarkdown(),
        )
    }

    @Test
    fun `github issue extractor preserves issue body without activity chrome`() {
        val result = parseHtmlForTest(
            html = """
                <div data-testid="issue-body">
                  <a data-testid="issue-body-header-author" href="/issue-author">issue-author</a>
                  <span data-testid="comment-author-association">Contributor</span>
                  <div data-testid="issue-body-viewer">
                    <div data-testid="markdown-body" class="markdown-body">
                      <p>Example repo here: <a href="https://github.com/example/repo">https://github.com/example/repo</a></p>
                      <p>When running I get an error:</p>
                      <div class="snippet-clipboard-content notranslate position-relative overflow-auto">
                        <pre class="notranslate"><code class="notranslate">Defuddle: Error evaluating media queries</code></pre>
                      </div>
                      <p>This is due to linkedom not implementing <code class="notranslate">doc.styleSheets</code>:</p>
                      <div class="Box Box--condensed my-2">
                        <div class="Box-header f6">
                          <p><a href="https://github.com/example/repo/blob/abc/src/defuddle.ts#L213">src/defuddle.ts</a></p>
                          <p>Line 213</p>
                        </div>
                        <div class="Box-body">
                          <table>
                            <tbody>
                              <tr>
                                <td class="blob-code blob-code-inner js-file-line"> <span>const</span> <span>sheets</span> <span>=</span> <span>Array</span><span>.</span><span>from</span><span>(</span><span>doc</span><span>.</span><span>styleSheets</span><span>)</span><span>.</span><span>filter</span><span>(</span><span>sheet</span> <span>=&gt;</span> <span>{</span> </td>
                              </tr>
                            </tbody>
                          </table>
                        </div>
                      </div>
                      <p>This could be silenced by falling back to <code class="notranslate">[]</code>.</p>
                      <div class="highlight highlight-source-ts notranslate position-relative overflow-auto">
                        <pre class="notranslate"><span>const</span> <span>sheets</span> <span>=</span> <span>Array</span><span>.</span><span>from</span><span>(</span><span>doc</span><span>.</span><span>styleSheets</span> <span>??</span> <span>[</span><span>]</span><span>)</span></pre>
                        <div class="zeroclipboard-container">Copy</div>
                      </div>
                    </div>
                  </div>
                </div>
                <div data-testid="issue-viewer-comments-container">Activity chrome should not appear.</div>
            """.trimIndent(),
            url = "https://github.com/example/repo/issues/56",
            options = testOptions(debug = true),
        )

        assertEquals("GitHub", result.metadata.site)
        assertEquals("issue-author", result.metadata.author)
        assertEquals(listOf("github"), result.debug["extractorIds"])
        assertEquals(
            """
            [issue-author](https://github.com/issue-author)

            Contributor

            Example repo here: [https://github.com/example/repo](https://github.com/example/repo)

            When running I get an error:

            ```
            Defuddle: Error evaluating media queries
            ```

            This is due to linkedom not implementing `doc.styleSheets`:

            ```
            const sheets = Array.from(doc.styleSheets).filter(sheet => {
            ```

            This could be silenced by falling back to `[]`.

            ```
            const sheets = Array.from(doc.styleSheets ?? [])
            ```
            """.trimIndent() + "\n",
            result.content.requireMarkdown(),
        )
    }

    @Test
    fun `steam partner event extractor converts bbcode data to article content`() {
        val result = parseHtmlForTest(
            html = """
                <html>
                  <head><title>Example Game - Patch 1.2.3 is now LIVE! - Example Store News</title></head>
                  <body>
                    <div id="application_config"
                      data-partnereventstore='[{"event_name":"Patch 1.2.3 is now LIVE!","announcement_body":{"headline":"Patch 1.2.3 is now LIVE!","body":"[p]Patch 1.2.3 is now live! This is build 500, network compatible. Available on all platforms! You can read the [url=\"https://docs.example.com/patch-123\"]full patch notes here[/url].\n\nOr, watch the video patch notes below:\n\n[/p][previewyoutube=\"dQw4w9WgXcQ;full\"][/previewyoutube]"}}]'
                      data-groupvanityinfo='[{"group_name":"Example Game"}]'></div>
                    <div id="application_root"></div>
                  </body>
                </html>
            """.trimIndent(),
            url = "https://store.example.com/news/app/123456/view/987654321",
            options = testOptions(debug = true),
        )

        assertEquals("Patch 1.2.3 is now LIVE!", result.metadata.title)
        assertEquals("Example Game", result.metadata.author)
        assertEquals("", result.metadata.site)
        assertEquals(
            """
            Patch 1.2.3 is now live! This is build 500, network compatible. Available on all platforms! You can read the [full patch notes here](https://docs.example.com/patch-123).

            Or, watch the video patch notes below:


            ![](https://www.youtube.com/watch?v=dQw4w9WgXcQ)
            """.trimIndent() + "\n",
            result.content.requireMarkdown(),
        )
    }

    @Test
    fun `techcrunch profile selectors do not run on unrelated hosts`() {
        val html = """
            <article>
              <p>The actual article body should stay because it contains enough realistic prose for deterministic parsing.</p>
              <p>A second paragraph keeps the article body stable while a TechCrunch-specific event promo appears below it.</p>
              <div class="wp-block-techcrunch-event-cta">TechCrunch event promo should only disappear on matching hosts.</div>
            </article>
        """.trimIndent()

        val matching = parseHtmlForTest(
            html = html,
            url = "https://techcrunch.com/example",
            options = testOptions(debug = true),
        )
        val unrelated = parseHtmlForTest(
            html = html,
            url = "https://example.com/not-techcrunch",
            options = testOptions(debug = true),
        )

        assertFalse(matching.content.requireMarkdown().contains("TechCrunch event promo"))
        assertTrue(unrelated.content.requireMarkdown().contains("TechCrunch event promo"))
        assertEquals(listOf("techcrunch"), matching.debug["extractorIds"])
        assertFalse(unrelated.debug.containsKey("extractorIds"))
    }

    @Test
    fun `socket profile selects article body without blog chrome`() {
        val html = """
            <div class="css-wrapper">
              <a class="css-banner" href="/blog/event">You're Invited: Meet the Socket Team</a>
              <div class="css-article-container">
                <a class="chakra-link chakra-button css-backlink" href="/blog">Blog</a>
                <div class="css-article">
                  <div class="chakra-wrap"><span class="chakra-badge">Product</span></div>
                  <h1>Introducing Socket Firewall</h1>
                  <p>We are excited to announce Socket Firewall, a new feature that blocks supply chain attacks before they reach your infrastructure.</p>
                  <p>This second paragraph keeps the Socket article body substantial enough to pass the profile selector guard while surrounding blog chrome stays outside the selected content.</p>
                </div>
                <div class="chakra-stack css-newsletter">Get notified when we publish new security blog posts!</div>
              </div>
              <span class="chakra-theme dark css-cta"><a href="/pricing">Start using Socket</a></span>
            </div>
        """.trimIndent()

        val matching = parseHtmlForTest(
            html = html,
            url = "https://socket.dev/blog/introducing-socket-firewall",
            options = testOptions(debug = true),
        )
        val unrelated = parseHtmlForTest(
            html = html,
            url = "https://example.com/not-socket",
            options = testOptions(debug = true),
        )

        val matchingMarkdown = matching.content.requireMarkdown()
        assertTrue(matchingMarkdown.contains("We are excited to announce Socket Firewall"))
        assertFalse(matchingMarkdown.contains("Introducing Socket Firewall"))
        assertFalse(matchingMarkdown.contains("You're Invited"))
        assertFalse(matchingMarkdown.contains("Get notified"))
        assertFalse(matchingMarkdown.contains("Start using Socket"))
        assertEquals(listOf("socket"), matching.debug["extractorIds"])
        assertFalse(unrelated.debug.containsKey("extractorIds"))
    }

    @Test
    fun `businessinsider profile removes bottom author module only on matching hosts`() {
        val html = """
            <article>
              <p>The actual article body should stay because it contains enough realistic prose for deterministic parsing.</p>
              <p>A second paragraph keeps the article body stable while the Business Insider author module appears below it.</p>
              <div class="post-bottom-authors" data-component-type="post-bottom-authors">
                <div class="post-bottom-author">
                  <a class="author-link" href="/author/example">Example Author</a>
                  <span class="follow-button">You're currently following this author!</span>
                  <div class="author-bio">
                    <div class="author-description">Business Insider biography and popular articles.</div>
                  </div>
                </div>
              </div>
            </article>
        """.trimIndent()

        val matching = parseHtmlForTest(
            html = html,
            url = "https://www.businessinsider.com/example",
            options = testOptions(debug = true),
        )
        val unrelated = parseHtmlForTest(
            html = html,
            url = "https://example.com/not-businessinsider",
            options = testOptions(debug = true),
        )

        assertTrue(matching.content.requireMarkdown().contains("The actual article body should stay"))
        assertFalse(matching.content.requireMarkdown().contains("Business Insider biography"))
        assertFalse(matching.content.requireMarkdown().contains("You're currently following"))
        assertTrue(unrelated.content.requireMarkdown().contains("Business Insider biography"))
        assertTrue(unrelated.content.requireMarkdown().contains("You're currently following"))
        assertEquals(listOf("business-insider"), matching.debug["extractorIds"])
        assertFalse(unrelated.debug.containsKey("extractorIds"))
    }

    @Test
    fun `arm newsroom profile removes article chrome while preserving excerpt and body`() {
        val result = parseHtmlForTest(
            html = """
                <div id="single_post" class="c-container __post_type-post">
                  <div class="single_post__breadcrumbs">Tech Newsroom / Blog</div>
                  <div class="single_post__intro_box ads-news-intro-box">
                    <ads-breadcrumbs><ads-breadcrumb label="Blog">Blog</ads-breadcrumb></ads-breadcrumbs>
                    <h1 class="single_post__title c-heading-1">Sample Article Title</h1>
                    <div class="single_post__excerpt">Brief subtitle describing the article.</div>
                  </div>
                  <div class="single_post__content">
                    <p>The company announced a new generation of processors designed for data center inference workloads, with sustained throughput across thousands of parallel execution threads.</p>
                    <p>Engineers increased memory bandwidth available to each core, reducing stalls when serving inference requests that repeatedly access large model weight tensors.</p>
                  </div>
                  <div class="single_post__final_box">By <a href="/author/jane-smith">Jane Smith</a></div>
                  <div class="CopyContent">Copy Text</div>
                  <p>Any re-use permitted for informational and non-commercial or personal use only.</p>
                  <div class="single_post__editorial_contact">Editorial Contact</div>
                  <div class="TwiBlock">Latest on X</div>
                </div>
            """.trimIndent(),
            url = "https://newsroom.arm.com/blog/sample",
            options = testOptions(debug = true),
        )

        val markdown = result.content.requireMarkdown()
        assertTrue(markdown.contains("Brief subtitle describing the article."))
        assertTrue(markdown.contains("The company announced a new generation"))
        assertFalse(markdown.contains("Sample Article Title"))
        assertFalse(markdown.contains("Tech Newsroom / Blog"))
        assertFalse(markdown.contains("Copy Text"))
        assertFalse(markdown.contains("Editorial Contact"))
        assertFalse(markdown.contains("Latest on X"))
        assertEquals(listOf("arm-newsroom"), result.debug["extractorIds"])
    }

    @Test
    fun `ars technica profile selectors do not run on unrelated hosts`() {
        val html = """
            <article>
              <p>The actual article body should stay because it contains enough realistic prose for deterministic parsing.</p>
              <p>A second paragraph keeps the article body stable while an Ars-style reader control appears below it.</p>
              <div class="text-settings-dropdown-story">
                <div class="text-settings">Story text Size Links</div>
              </div>
            </article>
        """.trimIndent()

        val matching = parseHtmlForTest(
            html = html,
            url = "https://arstechnica.com/example",
            options = testOptions(debug = true),
        )
        val unrelated = parseHtmlForTest(
            html = html,
            url = "https://example.com/not-ars",
            options = testOptions(debug = true),
        )

        assertFalse(matching.content.requireMarkdown().contains("Story text Size Links"))
        assertTrue(unrelated.content.requireMarkdown().contains("Story text Size Links"))
        assertEquals(listOf("ars-technica"), matching.debug["extractorIds"])
        assertFalse(unrelated.debug.containsKey("extractorIds"))
    }

    @Test
    fun `blogger profile selectors do not run on unrelated hosts`() {
        val html = """
            <main>
              <p>The actual article body should stay because it contains enough realistic prose for deterministic parsing.</p>
              <p>A second paragraph keeps the article body stable while Blogger navigation appears below it.</p>
              <div class="copy-tooltip"><span class="copy-tooltiptext">Link copied to clipboard</span></div>
              <div class="blog-pager" id="blog-pager">Older post</div>
            </main>
        """.trimIndent()

        val matching = parseHtmlForTest(
            html = html,
            url = "https://android-developers.googleblog.com/example",
            options = testOptions(debug = true),
        )
        val unrelated = parseHtmlForTest(
            html = html,
            url = "https://example.com/not-blogger",
            options = testOptions(debug = true),
        )

        assertFalse(matching.content.requireMarkdown().contains("Link copied to clipboard"))
        assertFalse(matching.content.requireMarkdown().contains("Older post"))
        assertTrue(unrelated.content.requireMarkdown().contains("Link copied to clipboard"))
        assertTrue(unrelated.content.requireMarkdown().contains("Older post"))
        assertEquals(listOf("blogger"), matching.debug["extractorIds"])
        assertFalse(unrelated.debug.containsKey("extractorIds"))
    }

    @Test
    fun `scp wiki profile selects page content without sidebar title chrome`() {
        val html = """
            <div id="side-bar">
              <a href="https://www.facebook.com/scpfoundation"><img src="https://example.com/social.png" alt="Facebook"></a>
            </div>
            <div id="main-content">
              <div id="page-title">SCP-9935</div>
              <div id="page-content">
                <p>The actual article body should stay because it contains enough realistic prose for deterministic parsing.</p>
                <p>A second paragraph keeps the article body stable while page chrome exists outside the selected content, with enough additional wording to satisfy the preferred selector guard used by the detector.</p>
                <p>A third paragraph describes containment procedures, anomalous records, archival notes, and review details so this synthetic SCP page behaves like the real fixture shape.</p>
              </div>
            </div>
        """.trimIndent()

        val result = parseHtmlForTest(
            html = html,
            url = "https://scp-wiki.wikidot.com/scp-9935",
            options = testOptions(debug = true),
        )

        val markdown = result.content.requireMarkdown()
        assertTrue(markdown.contains("The actual article body should stay"))
        assertFalse(markdown.contains("Facebook"))
        assertFalse(markdown.contains("SCP-9935"))
        assertEquals(listOf("scp-wiki"), result.debug["extractorIds"])
    }

    @Test
    fun `gamingonlinux profile selectors do not run on unrelated hosts`() {
        val html = """
            <article>
              <p>The actual article body should stay because it contains enough realistic prose for deterministic parsing.</p>
              <p>A second paragraph keeps the article body stable while GamingOnLinux footer chrome appears below it.</p>
              <span class="hidden_message">Article taken from GamingOnLinux.com.</span>
              <div class="article_likes">4 Likes</div>
            </article>
        """.trimIndent()

        val matching = parseHtmlForTest(
            html = html,
            url = "https://www.gamingonlinux.com/example",
            options = testOptions(debug = true),
        )
        val unrelated = parseHtmlForTest(
            html = html,
            url = "https://example.com/not-gamingonlinux",
            options = testOptions(debug = true),
        )

        assertFalse(matching.content.requireMarkdown().contains("Article taken from"))
        assertFalse(matching.content.requireMarkdown().contains("4 Likes"))
        assertTrue(unrelated.content.requireMarkdown().contains("Article taken from"))
        assertTrue(unrelated.content.requireMarkdown().contains("4 Likes"))
        assertEquals(listOf("gamingonlinux"), matching.debug["extractorIds"])
        assertFalse(unrelated.debug.containsKey("extractorIds"))
    }

    @Test
    fun `substack profile matches hosted and custom domain pages`() {
        val html = """
            <article>
              <p>The actual article body should stay because it contains enough realistic prose for deterministic parsing.</p>
              <p>A second paragraph keeps the article body stable while Substack footer and comment chrome appears below it.</p>
              <figure>
                <a class="image-link">
                  <img src="https://example.com/article-image.png" alt="Article image">
                  <div class="image-link-expand">
                    <button class="restack-image"></button>
                    <button class="view-image"><svg><path d="M0 0"></path></svg></button>
                  </div>
                </a>
              </figure>
              <script src="https://substackcdn.com/embed.js"></script>
              <section id="substack-comments">Substack comments chrome</section>
              <section aria-label="Top Posts Footer">Substack top posts chrome</section>
            </article>
        """.trimIndent()
        val urls = listOf(
            "https://publication.substack.com/p/example",
            "https://independent.example/p/example",
        )

        for (url in urls) {
            val result = parseHtmlForTest(
                html = html,
                url = url,
                options = testOptions(debug = true),
            )

            assertTrue(result.content.requireMarkdown().contains("The actual article body should stay"), url)
            assertFalse(result.content.requireMarkdown().contains("Substack comments chrome"), url)
            assertFalse(result.content.requireMarkdown().contains("Substack top posts chrome"), url)
            assertTrue(result.content.requireHtml().contains("article-image.png"), url)
            assertFalse(result.content.requireHtml().contains("image-link-expand"), url)
            assertFalse(result.content.requireHtml().contains("restack-image"), url)
            assertFalse(result.content.requireHtml().contains("view-image"), url)
            assertTrue((result.debug["extractorIds"] as List<*>).contains("substack"), url)
        }
    }

    @Test
    fun `substack profile removes transformed top image that duplicates metadata`() {
        val result = parseHtmlForTest(
            html = """
                <html>
                <head>
                  <meta property="og:image" content="https://substackcdn.com/image/fetch/f_auto/https%3A%2F%2Fmedia.example%2Fcover.png">
                </head>
                <body>
                  <article>
                    <div class="captioned-image-container">
                      <figure>
                        <a class="image-link">
                          <img
                            src="https://substackcdn.com/image/fetch/w_1456,f_auto/https%3A%2F%2Fmedia.example%2Fcover.png"
                            data-attrs="{&quot;topImage&quot;:true}"
                            alt="Cover"
                          >
                        </a>
                      </figure>
                    </div>
                    <p>Article prose has enough words to keep the default cleaned parse result. It describes the article content clearly and avoids short retry paths with stable text.</p>
                    <figure><img src="https://media.example/body.png" alt="Body image"></figure>
                  </article>
                </body>
                </html>
            """.trimIndent(),
            url = "https://publication.substack.com/p/example",
            options = testOptions(debug = true),
        )

        assertFalse(result.content.requireHtml().contains("cover.png"))
        assertFalse(result.content.requireHtml().contains("captioned-image-container"))
        assertTrue(result.content.requireHtml().contains("body.png"))
        assertTrue((result.debug["extractorIds"] as List<*>).contains("substack"))
    }

    @Test
    fun `substack profile preserves a captioned top image`() {
        val result = parseHtmlForTest(
            html = """
                <html>
                <head>
                  <meta property="og:image" content="https://substackcdn.com/image/fetch/f_auto/https%3A%2F%2Fmedia.example%2Fcover.png">
                </head>
                <body>
                  <article>
                    <div class="captioned-image-container">
                      <figure>
                        <a class="image-link">
                          <img
                            src="https://substackcdn.com/image/fetch/w_1456,f_auto/https%3A%2F%2Fmedia.example%2Fcover.png"
                            data-attrs="{&quot;topImage&quot;:true}"
                            alt="Cover"
                          >
                        </a>
                        <figcaption>Context that should remain with the cover.</figcaption>
                      </figure>
                    </div>
                    <p>Article prose has enough words to keep the default cleaned parse result. It describes the article content clearly and avoids short retry paths with stable text.</p>
                  </article>
                </body>
                </html>
            """.trimIndent(),
            url = "https://publication.substack.com/p/example",
        )

        assertTrue(result.content.requireHtml().contains("cover.png"))
        assertTrue(result.content.requireMarkdown().contains("Context that should remain with the cover."))
    }

    @Test
    fun `substack note extractor keeps the matching note body and metadata image`() {
        val result = parseHtmlForTest(
            html = """
                <html>
                <head>
                  <meta property="og:title" content="Test User (@testuser)">
                  <meta property="og:description" content="This is the main note content on the permalink page.">
                  <meta property="og:image" content="https://example.com/image/main-note.jpg">
                  <meta property="og:site_name" content="Substack">
                </head>
                <body>
                  <div id="entry">
                    <div class="reader-nav-root">
                      <div class="pencraft pc-display-flex pc-flexDirection-column pc-reset">
                        <div class="pencraft pc-display-flex pc-flexDirection-column pc-reset feedCommentBody-abc123">
                          <div class="ProseMirror FeedProseMirror"><p>This is a different note from the feed sidebar.</p></div>
                        </div>
                        <div class="feedPermalinkUnit-abc123">
                          <div class="pencraft pc-display-flex pc-gap-8 pc-alignItems-center pc-reset">
                            <a href="/@testuser"><img src="https://example.com/avatar.jpg" alt="Test User's avatar"></a>
                            <a href="/@testuser">Test User</a>
                          </div>
                          <div class="pencraft pc-display-flex pc-flexDirection-column pc-reset feedCommentBody-abc123">
                            <div class="ProseMirror FeedProseMirror">
                              <p>This is the main note content on the permalink page.</p>
                              <p>It has multiple paragraphs with important information.</p>
                            </div>
                          </div>
                          <div class="imageGrid-TadIyX"><img src="https://example.com/image/320w.jpg" srcset="https://example.com/image/640w.webp 640w"></div>
                        </div>
                        <div class="pencraft pc-display-flex pc-flexDirection-column pc-reset feedCommentBody-abc123">
                          <div class="ProseMirror FeedProseMirror"><p>Yet another unrelated feed note after the main content.</p></div>
                        </div>
                      </div>
                    </div>
                  </div>
                </body>
                </html>
            """.trimIndent(),
            url = "https://substack.com/@testuser/note/c-999999999",
            options = testOptions(debug = true),
        )

        val markdown = result.content.requireMarkdown()
        assertTrue(markdown.startsWith("This is the main note content on the permalink page."))
        assertTrue(markdown.contains("![](https://example.com/image/main-note.jpg)"))
        assertFalse(markdown.contains("different note from the feed sidebar"))
        assertFalse(markdown.contains("Yet another unrelated feed note"))
        assertFalse(markdown.contains("Test User's avatar"))
        assertTrue((result.debug["extractorIds"] as List<*>).contains("substack"))
    }

    @Test
    fun `substack note extractor uses canonical note url on app shell pages`() {
        val result = parseHtmlForTest(
            html = """
                <html>
                <head>
                  <meta property="og:title" content="Rich Holmes (@richholmes)">
                  <meta property="og:description" content="Google's former CEO says traditional user interfaces &quot;are going to go away.&quot;

It sounds far-fetched but Google is already rolling out the technology to make it happen.">
                  <meta property="og:image" content="https://example.com/preview.png">
                  <meta property="og:site_name" content="Substack">
                  <link rel="canonical" href="https://substack.com/@richholmes/note/c-202380205">
                </head>
                <body>
                  <div id="entry">
                    <div class="feedCommentBody-abc123">
                      <div class="ProseMirror FeedProseMirror">
                        <p>Wrote a piece about unrelated app-shell feed content.</p>
                      </div>
                    </div>
                    <div class="feedPermalinkUnit-abc123">
                      <div class="feedCommentBody-abc123">
                        <div class="feedCommentBodyInner-abc123">
                          <div class="ProseMirror FeedProseMirror">
                            <p>Google's former CEO says traditional user interfaces "are going to go away."</p>
                            <p>It sounds far-fetched but Google is already rolling out the technology to make it happen.</p>
                          </div>
                        </div>
                      </div>
                      <div class="imageGrid-TadIyX"><img src="https://example.com/preview.png"></div>
                    </div>
                    <div>14 Replies</div>
                  </div>
                </body>
                </html>
            """.trimIndent(),
            url = "https://substack-app",
            options = testOptions(debug = true),
        )

        val markdown = result.content.requireMarkdown()
        assertTrue(markdown.startsWith("Google's former CEO says traditional user interfaces"))
        assertTrue(markdown.contains("It sounds far-fetched"))
        assertFalse(markdown.contains("unrelated app-shell feed content"))
        assertFalse(markdown.contains("preview.png"))
        assertFalse(markdown.contains("14 Replies"))
        assertTrue((result.debug["extractorIds"] as List<*>).contains("substack"))
    }

    @Test
    fun `statista extractor promotes anonymous chart preview image`() {
        val result = parseHtmlForTest(
            html = """
                <html>
                <head>
                  <meta property="og:site_name" content="Statista">
                  <meta property="og:description" content="Sales by segment description.">
                </head>
                <body>
                  <main>
                    <h2 id="statisticSectionTitle">Sales by segment (in million U.S. dollars)</h2>
                    <div data-statistic-chart aria-hidden="true">
                      <div data-chart-preview class="hide"
                           data-src="/Statistic/365000/369297-blank-754.png"
                           data-alt="Statistic: Sales by segment"
                           data-width="754"
                           data-height="560"></div>
                    </div>
                    <article>
                      <h2 id="statisticTitle">Global sales by segment</h2>
                      <div id="readingAidText"><p>The public statistic description remains available.</p></div>
                    </article>
                    <aside>Pricing and unrelated recommendation chrome.</aside>
                  </main>
                </body>
                </html>
            """.trimIndent(),
            url = "https://www.statista.com/statistics/369297/example/",
            options = testOptions(debug = true),
        )

        val html = result.content.requireHtml()
        val markdown = result.content.requireMarkdown()
        assertTrue(html.contains("369297-blank-754.png"))
        assertTrue(html.contains("width=\"754\""))
        assertTrue(markdown.contains("![Statistic: Sales by segment]"))
        assertTrue(markdown.contains("The public statistic description remains available."))
        assertFalse(markdown.contains("Pricing and unrelated recommendation chrome."))
        assertTrue((result.debug["extractorIds"] as List<*>).contains("statista"))
    }

    @Test
    fun `older migrated profile selectors do not run on unrelated hosts`() {
        val cases = listOf(
            ProfileIsolationCase(
                url = "https://www.androidcentral.com/example",
                selectorHtml = """<div class="slice-container-authorBio">Future author bio chrome</div>""",
                removedText = "Future author bio chrome",
                profileId = "future",
            ),
            ProfileIsolationCase(
                url = "https://www.rollingstone.com/example",
                selectorHtml = """<div class="a-article-grid__header">Rolling Stone header chrome</div>""",
                removedText = "Rolling Stone header chrome",
                profileId = "rolling-stone-layout",
            ),
            ProfileIsolationCase(
                url = "https://blog.jetbrains.com/kotlin/example",
                selectorHtml = """<div class="author-post">JetBrains author chrome</div>""",
                removedText = "JetBrains author chrome",
                profileId = "jetbrains-blog",
            ),
            ProfileIsolationCase(
                url = "https://www.ilpost.it/example",
                selectorHtml = """<div id="audioPlayerArticle">Il Post audio player</div>""",
                removedText = "Il Post audio player",
                profileId = "ilpost",
            ),
            ProfileIsolationCase(
                url = "https://www.theverge.com/example",
                selectorHtml = """<aside data-mrf-recirculation="related">Vox recirculation chrome</aside>""",
                removedText = "Vox recirculation chrome",
                profileId = "vox",
            ),
            ProfileIsolationCase(
                url = "https://science.nasa.gov/example",
                selectorHtml = """<section class="related-articles">NASA related article chrome</section>""",
                removedText = "NASA related article chrome",
                profileId = "nasa",
            ),
            ProfileIsolationCase(
                url = "https://9to5google.com/example",
                selectorHtml = """<div class="google-preferred-source-badge">9to5 preferred source chrome</div>""",
                removedText = "9to5 preferred source chrome",
                profileId = "nine-to-five",
            ),
            ProfileIsolationCase(
                url = "https://berlinomagazine.com/example",
                selectorHtml = """<div class="entry-footer">WordPress footer chrome</div>""",
                removedText = "WordPress footer chrome",
                profileId = "wordpress-family",
            ),
            ProfileIsolationCase(
                url = "https://variety.com/example",
                selectorHtml = """<a class="o-comments-link">Variety comment jump chrome</a>""",
                removedText = "Variety comment jump chrome",
                profileId = "variety",
            ),
        )

        for (case in cases) {
            val html = """
                <article>
                  <p>The actual article body should stay because it contains enough realistic prose for deterministic parsing.</p>
                  <p>A second paragraph keeps the article body stable while migrated site chrome appears below it.</p>
                  ${case.selectorHtml}
                </article>
            """.trimIndent()

            val matching = parseHtmlForTest(
                html = html,
                url = case.url,
                options = testOptions(debug = true),
            )
            val unrelated = parseHtmlForTest(
                html = html,
                url = "https://example.com/not-profile",
                options = testOptions(debug = true),
            )

            assertFalse(matching.content.requireMarkdown().contains(case.removedText), case.profileId)
            assertTrue(unrelated.content.requireMarkdown().contains(case.removedText), case.profileId)
            assertTrue((matching.debug["extractorIds"] as List<*>).contains(case.profileId), case.profileId)
            assertFalse(unrelated.debug.containsKey("extractorIds"), case.profileId)
        }
    }

    private data class TestExtractor(
        override val id: String = "test-profile",
        override val domains: Set<String>,
        override val contentSelectors: List<String> = emptyList(),
        override val postContentRemoveSelectors: List<String> = emptyList(),
    ) : Extractor

    private data class ProfileIsolationCase(
        val url: String,
        val selectorHtml: String,
        val removedText: String,
        val profileId: String,
    )
}
