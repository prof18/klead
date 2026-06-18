package dev.defuddle.site

import dev.defuddle.Defuddle
import dev.defuddle.DefuddleOptions
import dev.defuddle.extractors.Extractor
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

        val result = Defuddle.parseHtml(
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
            options = DefuddleOptions(extractors = listOf(profile), debug = true),
        )

        assertTrue(result.contentMarkdown.contains("This preferred article paragraph"))
        assertFalse(result.contentMarkdown.contains("Unrelated recommendation text"))
        assertEquals(".preferred-story", result.debug["selectedContentSelector"])
        assertEquals(".preferred-story", result.debug["extractorContentSelector"])
    }

    @Test
    fun `profile content selector chooses strongest matching element`() {
        val profile = TestExtractor(
            contentSelectors = listOf("article"),
            domains = setOf("example.com"),
        )

        val result = Defuddle.parseHtml(
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
            options = DefuddleOptions(extractors = listOf(profile), debug = true),
        )

        assertTrue(result.contentMarkdown.contains("The primary article paragraph"))
        assertFalse(result.contentMarkdown.contains("Teaser article paragraph"))
        assertEquals("article", result.debug["selectedContentSelector"])
        assertEquals("article", result.debug["extractorContentSelector"])
    }

    @Test
    fun `weak profile content selector falls back to generic scoring`() {
        val profile = TestExtractor(
            contentSelectors = listOf(".weak-preferred"),
            domains = setOf("example.com"),
        )

        val result = Defuddle.parseHtml(
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
            options = DefuddleOptions(extractors = listOf(profile), debug = true),
        )

        assertTrue(result.contentMarkdown.contains("This generic article paragraph"))
        assertFalse(result.contentMarkdown.contains("Tiny promo"))
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

        val matching = Defuddle.parseHtml(
            html = html,
            url = "https://example.com/story",
            options = DefuddleOptions(extractors = listOf(profile), debug = true),
        )
        val unrelated = Defuddle.parseHtml(
            html = html,
            url = "https://unrelated.test/story",
            options = DefuddleOptions(extractors = listOf(profile), debug = true),
        )

        assertFalse(matching.contentMarkdown.contains("Site-specific chrome"))
        assertTrue(unrelated.contentMarkdown.contains("Site-specific chrome"))
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

        val matching = Defuddle.parseHtml(
            html = html,
            url = "https://www.macrumors.com/example",
            options = DefuddleOptions(debug = true),
        )
        val unrelated = Defuddle.parseHtml(
            html = html,
            url = "https://example.com/not-macrumors",
            options = DefuddleOptions(debug = true),
        )

        assertFalse(matching.contentMarkdown.contains("Related Roundup"))
        assertTrue(unrelated.contentMarkdown.contains("Related Roundup"))
        assertEquals(listOf("macrumors"), matching.debug["extractorIds"])
        assertFalse(unrelated.debug.containsKey("extractorIds"))
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

        val matching = Defuddle.parseHtml(
            html = html,
            url = "https://www.veneziatoday.it/example",
            options = DefuddleOptions(debug = true),
        )
        val unrelated = Defuddle.parseHtml(
            html = html,
            url = "https://example.com/not-citynews",
            options = DefuddleOptions(debug = true),
        )

        assertFalse(matching.contentMarkdown.contains("Riproduzione riservata"))
        assertTrue(unrelated.contentMarkdown.contains("Riproduzione riservata"))
        assertEquals(listOf("citynews"), matching.debug["extractorIds"])
        assertFalse(unrelated.debug.containsKey("extractorIds"))
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

        val matching = Defuddle.parseHtml(
            html = html,
            url = "https://techcrunch.com/example",
            options = DefuddleOptions(debug = true),
        )
        val unrelated = Defuddle.parseHtml(
            html = html,
            url = "https://example.com/not-techcrunch",
            options = DefuddleOptions(debug = true),
        )

        assertFalse(matching.contentMarkdown.contains("TechCrunch event promo"))
        assertTrue(unrelated.contentMarkdown.contains("TechCrunch event promo"))
        assertEquals(listOf("techcrunch"), matching.debug["extractorIds"])
        assertFalse(unrelated.debug.containsKey("extractorIds"))
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

        val matching = Defuddle.parseHtml(
            html = html,
            url = "https://arstechnica.com/example",
            options = DefuddleOptions(debug = true),
        )
        val unrelated = Defuddle.parseHtml(
            html = html,
            url = "https://example.com/not-ars",
            options = DefuddleOptions(debug = true),
        )

        assertFalse(matching.contentMarkdown.contains("Story text Size Links"))
        assertTrue(unrelated.contentMarkdown.contains("Story text Size Links"))
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

        val matching = Defuddle.parseHtml(
            html = html,
            url = "https://android-developers.googleblog.com/example",
            options = DefuddleOptions(contentSelector = "main", debug = true),
        )
        val unrelated = Defuddle.parseHtml(
            html = html,
            url = "https://example.com/not-blogger",
            options = DefuddleOptions(contentSelector = "main", debug = true),
        )

        assertFalse(matching.contentMarkdown.contains("Link copied to clipboard"))
        assertFalse(matching.contentMarkdown.contains("Older post"))
        assertTrue(unrelated.contentMarkdown.contains("Link copied to clipboard"))
        assertTrue(unrelated.contentMarkdown.contains("Older post"))
        assertEquals(listOf("blogger"), matching.debug["extractorIds"])
        assertFalse(unrelated.debug.containsKey("extractorIds"))
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

        val matching = Defuddle.parseHtml(
            html = html,
            url = "https://www.gamingonlinux.com/example",
            options = DefuddleOptions(debug = true),
        )
        val unrelated = Defuddle.parseHtml(
            html = html,
            url = "https://example.com/not-gamingonlinux",
            options = DefuddleOptions(debug = true),
        )

        assertFalse(matching.contentMarkdown.contains("Article taken from"))
        assertFalse(matching.contentMarkdown.contains("4 Likes"))
        assertTrue(unrelated.contentMarkdown.contains("Article taken from"))
        assertTrue(unrelated.contentMarkdown.contains("4 Likes"))
        assertEquals(listOf("gamingonlinux"), matching.debug["extractorIds"])
        assertFalse(unrelated.debug.containsKey("extractorIds"))
    }

    @Test
    fun `substack profile matches hosted and custom domain pages`() {
        val html = """
            <article>
              <p>The actual article body should stay because it contains enough realistic prose for deterministic parsing.</p>
              <p>A second paragraph keeps the article body stable while Substack footer and comment chrome appears below it.</p>
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
            val result = Defuddle.parseHtml(
                html = html,
                url = url,
                options = DefuddleOptions(debug = true),
            )

            assertTrue(result.contentMarkdown.contains("The actual article body should stay"), url)
            assertFalse(result.contentMarkdown.contains("Substack comments chrome"), url)
            assertFalse(result.contentMarkdown.contains("Substack top posts chrome"), url)
            assertTrue((result.debug["extractorIds"] as List<*>).contains("substack"), url)
        }
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

            val matching = Defuddle.parseHtml(
                html = html,
                url = case.url,
                options = DefuddleOptions(debug = true),
            )
            val unrelated = Defuddle.parseHtml(
                html = html,
                url = "https://example.com/not-profile",
                options = DefuddleOptions(debug = true),
            )

            assertFalse(matching.contentMarkdown.contains(case.removedText), case.profileId)
            assertTrue(unrelated.contentMarkdown.contains(case.removedText), case.profileId)
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
