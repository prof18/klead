package dev.defuddle.removal

import dev.defuddle.Defuddle
import dev.defuddle.DefuddleOptions
import org.jsoup.Jsoup
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class RemovalPipelineTest {
    @Test
    fun `hidden inline styles and attributes are removed`() {
        val result = Defuddle.parseHtml(
            html = """
                <html><body><article>
                  <p>Visible article text with enough ordinary prose to avoid the short-page retry that intentionally disables hidden-element removal for sparse pages. This paragraph keeps the default removal attempt as the selected parse result while still leaving hidden clutter nearby for the removal pipeline to clean up. Additional visible sentences provide stable article length, realistic punctuation, and enough words for the retry controller to trust the cleaned default result.</p>
                  <p hidden>Hidden attribute text.</p>
                  <p aria-hidden="true">Aria hidden text.</p>
                  <p style="display:none">Display hidden text.</p>
                  <p style="visibility:hidden">Visibility hidden text.</p>
                  <p style="opacity:0">Opacity hidden text.</p>
                  <p class="hidden md:block">Class hidden text.</p>
                  <p class="sm:invisible">Variant invisible text.</p>
                </article></body></html>
            """.trimIndent(),
            url = "https://example.com/hidden",
        )

        assertTrue(result.contentMarkdown.contains("Visible article text"))
        assertFalse(result.contentMarkdown.contains("Hidden attribute text."))
        assertFalse(result.contentMarkdown.contains("Aria hidden text."))
        assertFalse(result.contentMarkdown.contains("Display hidden text."))
        assertFalse(result.contentMarkdown.contains("Visibility hidden text."))
        assertFalse(result.contentMarkdown.contains("Opacity hidden text."))
        assertFalse(result.contentMarkdown.contains("Class hidden text."))
        assertFalse(result.contentMarkdown.contains("Variant invisible text."))
    }

    @Test
    fun `math hidden wrappers are preserved`() {
        val document = Jsoup.parse(
            """
            <article>
              <span aria-hidden="true" class="katex">
                <math><mi>x</mi></math>
              </span>
              <p>Visible prose.</p>
            </article>
            """.trimIndent(),
        )
        val article = document.selectFirst("article") ?: error("missing article")

        val debug = mutableListOf<RemovalRecord>()
        RemovalPipeline.apply(article, DefuddleOptions(), debug)

        assertTrue(article.outerHtml().contains("<math>"))
        assertTrue(article.text().contains("Visible prose."))
    }

    @Test
    fun `debug records identify hidden removals`() {
        val result = Defuddle.parseHtml(
            html = """
                <article>
                  <p>Visible debug text with enough ordinary prose to avoid the short-page retry that disables hidden-element removal. This keeps the removal record from the default parse attempt in the final result and makes the debug assertion deterministic. Additional visible sentences provide stable article length, realistic punctuation, and enough words for the retry controller to trust the cleaned default result.</p>
                  <aside hidden>Hidden debug text.</aside>
                </article>
            """.trimIndent(),
            url = "https://example.com/debug-hidden",
            options = DefuddleOptions(debug = true),
        )

        val removals = result.debug["removals"] as? List<*>
        assertNotNull(removals)
        assertTrue(removals.any { it.toString().contains("removeHiddenElements") })
        assertTrue(removals.any { it.toString().contains("Hidden debug text") })
    }

    @Test
    fun `exact selectors remove obvious nav footer and ad blocks but preserve notes`() {
        val result = Defuddle.parseHtml(
            html = """
                <article>
                  <nav>Navigation should go</nav>
                  <p>Main prose stays with enough words to avoid short-page retry and make removal deterministic for this fixture.</p>
                  <aside class="ad">Advertisement should go</aside>
                  <section class="footnotes"><p>Footnote should stay.</p></section>
                  <footer>Footer should go</footer>
                </article>
            """.trimIndent(),
            url = "https://example.com/exact",
        )

        assertFalse(result.contentMarkdown.contains("Navigation should go"))
        assertFalse(result.contentMarkdown.contains("Advertisement should go"))
        assertFalse(result.contentMarkdown.contains("Footer should go"))
        assertTrue(result.contentMarkdown.contains("Footnote should stay."))
    }

    @Test
    fun `exact selectors remove enfold hero caption and entry metadata`() {
        val result = Defuddle.parseHtml(
            html = """
                <main role="main">
                  <article>
                    <div class="big-preview single-big">
                      <a href="/cover.jpg" title="CC0_https://images.example/photo.jpeg">
                        <small class="avia-copyright">CC0_https://images.example/photo.jpeg</small>
                      </a>
                    </div>
                    <div class="entry-content-wrapper">
                      <header class="entry-content-header">
                        <span class="post-meta-infos">
                          <time class="date-container">12 Giugno 2026</time>
                          <span class="text-sep">/</span>
                          <span class="blog-categories">in <a href="/category/cronaca">Cronaca</a></span>
                          <span class="text-sep">/</span>
                          <span class="blog-author">da <a rel="author" href="/author">katherina ricchi</a></span>
                        </span>
                      </header>
                      <div class="entry-content">
                        <h2>Important article subtitle</h2>
                        <p>The actual article body should stay because it is normal prose with useful information. It includes enough words, punctuation, and context for the parser to keep the default cleaned result. This makes Enfold metadata cleanup deterministic while preserving legitimate article content.</p>
                      </div>
                    </div>
                  </article>
                </main>
            """.trimIndent(),
            url = "https://example.com/enfold",
        )

        assertTrue(result.contentMarkdown.contains("Important article subtitle"))
        assertTrue(result.contentMarkdown.contains("The actual article body should stay"))
        assertFalse(result.contentMarkdown.contains("CC0_https"))
        assertFalse(result.contentMarkdown.contains("12 Giugno 2026"))
        assertFalse(result.contentMarkdown.contains("Cronaca"))
        assertFalse(result.contentMarkdown.contains("katherina ricchi"))
        assertFalse(result.contentMarkdown.contains("\n/\n"))
    }

    @Test
    fun `exact selectors remove WordPress category chip wrappers`() {
        val result = Defuddle.parseHtml(
            html = """
                <main role="main">
                  <article class="post-content">
                    <header class="entry-header-outer">
                      <div class="entry-header">
                        <span class="post-cat-wrap">
                          <a class="post-cat tie-cat-1" href="/category/apertura/">Apertura</a>
                          <a class="post-cat tie-cat-2" href="/category/politica/">Politica</a>
                          <a class="post-cat tie-cat-3" href="/category/politica-tedesca/">Politica Tedesca</a>
                        </span>
                      </div>
                    </header>
                    <div class="entry-content">
                      <p>The actual article body should stay because it is normal prose with useful information. It includes enough words, punctuation, and context for the parser to keep the default cleaned result while category chips are removed from the opening article chrome.</p>
                    </div>
                  </article>
                </main>
            """.trimIndent(),
            url = "https://example.com/category-chips",
        )

        assertTrue(result.contentMarkdown.contains("The actual article body should stay"))
        assertFalse(result.contentMarkdown.contains("Apertura"))
        assertFalse(result.contentMarkdown.contains("Politica"))
        assertFalse(result.contentMarkdown.contains("Politica Tedesca"))
    }

    @Test
    fun `partial selectors do not remove code blocks`() {
        val result = Defuddle.parseHtml(
            html = """
                <article>
                  <p>Main prose stays with enough words to avoid short-page retry and make removal deterministic for this fixture.</p>
                  <pre class="related-code"><code>val related = "content"</code></pre>
                  <section class="related-posts">Related posts should go</section>
                </article>
            """.trimIndent(),
            url = "https://example.com/partial",
        )

        assertTrue(result.contentMarkdown.contains("""val related = "content""""))
        assertFalse(result.contentMarkdown.contains("Related posts should go"))
    }

    @Test
    fun `partial selectors preserve prose wrappers with clutter-looking utility classes`() {
        val result = Defuddle.parseHtml(
            html = """
                <article>
                  <div class="[&_.visual-newsletter-fallback-image]:hidden">
                    <p>The actual story body should stay because it is normal prose with useful information. It includes enough words, punctuation, and context for the parser to keep the default cleaned result rather than invoking short-page retries.</p>
                    <p>A second paragraph keeps this prose wrapper clearly article-like even though one of its utility classes contains a clutter keyword from nested CSS selector syntax.</p>
                  </div>
                  <section class="related-posts">
                    <a href="/one">Related one</a>
                    <a href="/two">Related two</a>
                    <a href="/three">Related three</a>
                  </section>
                </article>
            """.trimIndent(),
            url = "https://example.com/prose-utility-classes",
        )

        assertTrue(result.contentMarkdown.contains("The actual story body should stay"))
        assertTrue(result.contentMarkdown.contains("A second paragraph keeps this prose wrapper"))
        assertFalse(result.contentMarkdown.contains("Related one"))
    }

    @Test
    fun `breadcrumb wrappers are removed as navigation clutter`() {
        val result = Defuddle.parseHtml(
            html = """
                <main>
                  <div class="article-breadcrumbs">
                    <a href="/world">World</a>
                    <span>Monday 15 June 2026</span>
                  </div>
                  <article>
                    <p>Main prose stays with enough words to avoid short-page retry and make breadcrumb removal deterministic for this fixture. The text includes useful article context, natural punctuation, and additional words so the cleaned result remains trustworthy.</p>
                  </article>
                </main>
            """.trimIndent(),
            url = "https://example.com/breadcrumbs",
        )

        assertTrue(result.contentMarkdown.contains("Main prose stays"))
        assertFalse(result.contentMarkdown.contains("World"))
        assertFalse(result.contentMarkdown.contains("Monday 15 June 2026"))
    }

    @Test
    fun `low scoring removes link heavy related sections and preserves prose`() {
        val result = Defuddle.parseHtml(
            html = """
                <article>
                  <section>
                    <p>This prose section should stay because it has meaningful text, punctuation, and a low link density. It explains the article topic in complete sentences and provides enough substance for the parser to trust the cleaned result.</p>
                    <p>Another prose paragraph gives the block enough substance to avoid being treated as clutter. It adds stable article length, natural punctuation, and more words so short-page retry paths do not disable the low-scoring removal being tested.</p>
                  </section>
                  <section>
                    <a href="/one">Related one</a>
                    <a href="/two">Related two</a>
                    <a href="/three">Related three</a>
                    <a href="/four">Related four</a>
                  </section>
                </article>
            """.trimIndent(),
            url = "https://example.com/low-score",
        )

        assertTrue(result.contentMarkdown.contains("This prose section should stay"))
        assertFalse(result.contentMarkdown.contains("Related one"))
    }

    @Test
    fun `content patterns remove trailing subscribe blocks but preserve final prose`() {
        val result = Defuddle.parseHtml(
            html = """
                <article>
                  <p>The final article paragraph should stay because it is normal prose with useful information. It includes enough words, punctuation, and context for the parser to keep the default cleaned result rather than invoking short-page retries. This makes the trailing subscription pattern removal deterministic while preserving the legitimate article ending. One more sentence keeps the cleaned article comfortably above the retry threshold.</p>
                  <p>Subscribe to our newsletter for weekly updates and product announcements.</p>
                </article>
            """.trimIndent(),
            url = "https://example.com/subscribe",
        )

        assertTrue(result.contentMarkdown.contains("The final article paragraph should stay"))
        assertFalse(result.contentMarkdown.contains("Subscribe to our newsletter"))
    }

    @Test
    fun `content patterns remove nested newsletter signup widgets while preserving prose`() {
        val result = Defuddle.parseHtml(
            html = """
                <article>
                  <p>The article introduction should stay because it is normal prose with useful information. It includes enough words, punctuation, and context for the parser to keep the default cleaned result rather than invoking short-page retries. This makes inline newsletter cleanup deterministic while preserving legitimate article text.</p>
                  <div class="w-promotion-offer promo-article-content-3/4-depth w-promotion-widget" data-nosnippet>
                    <div class="promotion-offer-box">
                      <div class="newsletter-promotion-large">
                        <div class="newsletter-section" data-inview-category="Newsletter Article Content Widget" data-inview-type="loaded_newsletter">
                          <h3>Subscribe to our newsletter for smarter home-screen tips</h3>
                          <div class="form-notes bottom-note">By subscribing, you agree to receive newsletter and marketing emails, and accept our <a href="/terms">Terms of Use</a> and <a href="/privacy">Privacy Policy</a>. You can unsubscribe anytime.</div>
                        </div>
                      </div>
                    </div>
                  </div>
                  <p>The article conclusion should also stay after the newsletter signup is removed. It contains normal prose, useful punctuation, and enough words to keep the article body stable in the cleaned result.</p>
                </article>
            """.trimIndent(),
            url = "https://example.com/newsletter-widget",
            options = DefuddleOptions(
                removeExactSelectors = false,
                removePartialSelectors = false,
            ),
        )

        assertTrue(result.contentMarkdown.contains("The article introduction should stay"))
        assertTrue(result.contentMarkdown.contains("The article conclusion should also stay"))
        assertFalse(result.contentMarkdown.contains("Subscribe to our newsletter"))
        assertFalse(result.contentMarkdown.contains("marketing emails"))
        assertFalse(result.contentMarkdown.contains("Terms of Use"))
        assertFalse(result.contentMarkdown.contains("unsubscribe anytime"))
    }

    @Test
    fun `exact selectors remove WordPress Mailchimp newsletter blocks`() {
        val result = Defuddle.parseHtml(
            html = """
                <article>
                  <p>The article introduction should stay because it is normal prose with useful information. It includes enough words, punctuation, and context for the parser to keep the default cleaned result rather than invoking short-page retries. This makes inline Mailchimp cleanup deterministic while preserving legitimate article text.</p>
                  <div class="wp-block-mailchimp-mailchimp">
                    <div class="mc_container">
                      <h2 class="mc_custom_border_hdr">La newsletter del Mitte!</h2>
                      <div class="mc_subheader">
                        <h3>Notizie, novità, eventi dalla Germania</h3>
                      </div>
                    </div>
                  </div>
                  <p>The article conclusion should also stay after the newsletter signup is removed. It contains normal prose, useful punctuation, and enough words to keep the article body stable in the cleaned result.</p>
                </article>
            """.trimIndent(),
            url = "https://example.com/mailchimp-newsletter",
        )

        assertTrue(result.contentMarkdown.contains("The article introduction should stay"))
        assertTrue(result.contentMarkdown.contains("The article conclusion should also stay"))
        assertFalse(result.contentMarkdown.contains("La newsletter del Mitte"))
        assertFalse(result.contentMarkdown.contains("Notizie, novità"))
    }

    @Test
    fun `content patterns remove trailing recommendation blocks`() {
        val result = Defuddle.parseHtml(
            html = """
                <article>
                  <p>The final article paragraph should stay because it is normal prose with useful information. It includes enough words, punctuation, and context for the parser to keep the default cleaned result rather than invoking short-page retries. This makes the trailing recommendation pattern removal deterministic while preserving the legitimate article ending. Additional sentences keep this fixture comfortably above the retry threshold, so the normal content-pattern cleanup remains the selected result and the recommendation block is evaluated like a real article footer.</p>
                  <section>
                    <h2>Recommended</h2>
                    <a href="/one">First unrelated story</a>
                    <a href="/two">Second unrelated story</a>
                  </section>
                </article>
            """.trimIndent(),
            url = "https://example.com/recommendations",
        )

        assertTrue(result.contentMarkdown.contains("The final article paragraph should stay"))
        assertFalse(result.contentMarkdown.contains("Recommended"))
        assertFalse(result.contentMarkdown.contains("First unrelated story"))
    }

    @Test
    fun `content patterns remove orphaned trailing commerce headings after product lists`() {
        val result = Defuddle.parseHtml(
            html = """
                <article>
                  <p>The article conclusion should remain because it is normal prose with useful information. It includes enough words, punctuation, and context for the parser to keep the default cleaned result rather than invoking short-page retries. This makes the trailing commerce heading cleanup deterministic while preserving the legitimate article ending. Additional sentences keep this fixture comfortably above the retry threshold, so low scoring product-list cleanup runs before trailing heading cleanup.</p>
                  <h3>Best iPhone accessories</h3>
                  <ul>
                    <li><a href="/one">AirPods Pro discount</a></li>
                    <li><a href="/two">MagSafe car mount</a></li>
                    <li><a href="/three">AirTag battery case</a></li>
                  </ul>
                </article>
            """.trimIndent(),
            url = "https://example.com/product-list-heading",
        )

        assertTrue(result.contentMarkdown.contains("The article conclusion should remain"))
        assertFalse(result.contentMarkdown.contains("Best iPhone accessories"))
        assertFalse(result.contentMarkdown.contains("AirPods Pro discount"))
    }

    @Test
    fun `content patterns remove trailing tag lists`() {
        val result = Defuddle.parseHtml(
            html = """
                <article>
                  <p>The final article paragraph should stay because it is normal prose with useful information. It includes enough words, punctuation, and context for the parser to keep the default cleaned result rather than invoking short-page retries. This makes the trailing tag-list pattern removal deterministic while preserving the legitimate article ending. Additional sentences keep this fixture comfortably above the retry threshold, so the normal content-pattern cleanup remains the selected result and the tag block is evaluated like a real article footer.</p>
                  <div class="post-tags">
                    <span>Tags:</span>
                    <a href="/tag/kotlin/">Kotlin</a>
                    <strong>-</strong>
                    <a href="/tag/jvm/">JVM</a>
                  </div>
                </article>
            """.trimIndent(),
            url = "https://example.com/tags",
        )

        assertTrue(result.contentMarkdown.contains("The final article paragraph should stay"))
        assertFalse(result.contentMarkdown.contains("Tags:"))
        assertFalse(result.contentMarkdown.contains("/tag/kotlin/"))
        assertFalse(result.contentMarkdown.contains("/tag/jvm/"))
    }

    @Test
    fun `content patterns remove nested article footer tags and comment links`() {
        val result = Defuddle.parseHtml(
            html = """
                <article>
                  <div class="article-body">
                    <p>The final article paragraph should stay because it is normal prose with useful information. It includes enough words, punctuation, and context for the parser to keep the default cleaned result rather than invoking short-page retries. This makes nested article-footer cleanup deterministic while preserving the legitimate article ending. Additional sentences keep this fixture comfortably above the retry threshold, so the normal content-pattern cleanup remains the selected result and only footer clutter is removed.</p>
                    <div class="linkback">Tag: <a href="/guide/united-kingdom/">United Kingdom</a></div>
                  </div>
                  <div class="article-footer">
                    <div>[ <a href="https://forums.example.com/threads/story.1/">8 comments</a> ]</div>
                  </div>
                </article>
            """.trimIndent(),
            url = "https://example.com/footer-clutter",
        )

        assertTrue(result.contentMarkdown.contains("The final article paragraph should stay"))
        assertFalse(result.contentMarkdown.contains("Tag:"))
        assertFalse(result.contentMarkdown.contains("United Kingdom"))
        assertFalse(result.contentMarkdown.contains("8 comments"))
    }

    @Test
    fun `content patterns remove trailing comment prompt back to top and read more modules`() {
        val result = Defuddle.parseHtml(
            html = """
                <article>
                  <p>The final article paragraph should stay because it is normal prose with useful information. It includes enough words, punctuation, and context for the parser to keep the default cleaned result rather than invoking short-page retries. This makes trailing comment and recommendation cleanup deterministic while preserving the legitimate article ending. Additional sentences keep this fixture comfortably above the retry threshold, so the normal content-pattern cleanup remains the selected result and only footer clutter is removed.</p>
                  <div class="comments-widget">
                    <p>You must confirm your public display name before commenting</p>
                    <p>Please logout and then login again, you will then be prompted to enter your display name.</p>
                  </div>
                  <div class="scroll-control"><a href="#">Back To Top</a></div>
                  <section>
                    <aside data-mrf-recirculation="article-river-stacked">
                      <div>Read more</div>
                      <a href="/one"><img src="/one.jpg" alt="One">First suggested story</a>
                      <a href="/two"><img src="/two.jpg" alt="Two">Second suggested story</a>
                    </aside>
                  </section>
                </article>
            """.trimIndent(),
            url = "https://example.com/footer-modules",
        )

        assertTrue(result.contentMarkdown.contains("The final article paragraph should stay"))
        assertFalse(result.contentMarkdown.contains("public display name"))
        assertFalse(result.contentMarkdown.contains("Please logout"))
        assertFalse(result.contentMarkdown.contains("Back To Top"))
        assertFalse(result.contentMarkdown.contains("Read more"))
        assertFalse(result.contentMarkdown.contains("First suggested story"))
    }

    @Test
    fun `exact selectors remove embedded video affiliate and gallery chrome while preserving article content`() {
        val result = Defuddle.parseHtml(
            html = """
                <article>
                  <p>The article introduction should stay because it is normal prose with useful information. It includes enough words, punctuation, and context for the parser to keep the default cleaned result rather than invoking short-page retries. This makes embedded widget cleanup deterministic while preserving legitimate article text. Additional sentences keep this fixture comfortably above the retry threshold, so the normal removal pipeline remains the selected result.</p>
                  <div data-jwp-carousel>
                    <span>Latest Videos From</span>
                    <img src="/video-logo.svg" alt="Video logo">
                  </div>
                  <aside class="hawk-root" data-widget-type="review">
                    <div>Today's best Example Phone deals</div>
                    <div>We check over 250 million products every day for the best prices</div>
                  </aside>
                  <div class="table__instruction">Swipe to scroll horizontally</div>
                  <div class="inline-gallery__count">Image 1 of 9</div>
                  <div class="inline-gallery__arrows">Previous Next</div>
                  <p>The article conclusion should also stay after non-article widget chrome is removed. It contains normal prose, useful punctuation, and enough words to keep the article body stable in the cleaned result.</p>
                </article>
            """.trimIndent(),
            url = "https://example.com/embedded-widgets",
        )

        assertTrue(result.contentMarkdown.contains("The article introduction should stay"))
        assertTrue(result.contentMarkdown.contains("The article conclusion should also stay"))
        assertFalse(result.contentMarkdown.contains("Latest Videos From"))
        assertFalse(result.contentMarkdown.contains("Today's best Example Phone deals"))
        assertFalse(result.contentMarkdown.contains("250 million products"))
        assertFalse(result.contentMarkdown.contains("Swipe to scroll horizontally"))
        assertFalse(result.contentMarkdown.contains("Image 1 of 9"))
        assertFalse(result.contentMarkdown.contains("Previous Next"))
        assertFalse(result.contentMarkdown.contains("video-logo.svg"))
    }

    @Test
    fun `exact selectors remove embedded audio player chrome while preserving article content`() {
        val result = Defuddle.parseHtml(
            html = """
                <article>
                  <p>The article introduction should stay because it is normal prose with useful information. It includes enough words, punctuation, and context for the parser to keep the default cleaned result rather than invoking short-page retries. This makes embedded audio player cleanup deterministic while preserving legitimate article text.</p>
                  <div id="audioPlayerArticle" data-mp3="/audio.mp3">Caricamento player</div>
                  <div class="audio-player" data-audio-src="/audio.ogg">Loading player</div>
                  <p>The article conclusion should also stay after non-article audio player chrome is removed. It contains normal prose, useful punctuation, and enough words to keep the article body stable in the cleaned result.</p>
                </article>
            """.trimIndent(),
            url = "https://example.com/audio-player",
        )

        assertTrue(result.contentMarkdown.contains("The article introduction should stay"))
        assertTrue(result.contentMarkdown.contains("The article conclusion should also stay"))
        assertFalse(result.contentMarkdown.contains("Caricamento player"))
        assertFalse(result.contentMarkdown.contains("Loading player"))
        assertFalse(result.contentHtml.contains("audio.mp3"))
        assertFalse(result.contentHtml.contains("audio.ogg"))
    }

    @Test
    fun `exact selectors remove social source and read-next chrome while preserving story body`() {
        val result = Defuddle.parseHtml(
            html = """
                <main>
                  <article>
                    <span data-cy="time-rubric">17 hours ago - <a href="/technology">Technology</a></span>
                    <ul data-cy="byline-list"><li><a data-cy="byline-author" href="/authors/mcuri">Maria Curi</a></li></ul>
                    <ul data-cy="social-share-top"><li><button>Share</button></li><li><button>Copy</button></li></ul>
                    <div data-vars-event-name="preferred_source_view">
                      <div data-cy="preferred-source-top"><a href="https://google.com/preferences/source?q=example.com">Add Example on Google</a></div>
                      <span role="tooltip">Add Example as your preferred source to see more of our stories on Google.</span>
                    </div>
                    <p>The article introduction should stay because it is normal prose with useful information. It includes enough words, punctuation, and context for the parser to keep the default cleaned result rather than invoking short-page retries. This makes social and source cleanup deterministic while preserving legitimate article text.</p>
                    <p>The article conclusion should also stay after source-preference and recirculation chrome is removed. It contains normal prose, useful punctuation, and enough words to keep the article body stable in the cleaned result.</p>
                    <ul data-cy="social-share-bottom"><li><button>Share</button></li><li><button>Copy</button></li></ul>
                    <div data-vars-event-name="preferred_source_view" data-vars-location="bottom">
                      <div data-cy="preferred-source-bottom"><a href="https://google.com/preferences/source?q=example.com">Add Example on Google</a></div>
                    </div>
                    <section data-cy="what-to-read-next">
                      <h2>What to read next</h2>
                      <a href="/one"><img src="data:image/webp;base64,AAAA" alt="">Suggested story</a>
                    </section>
                  </article>
                </main>
            """.trimIndent(),
            url = "https://example.com/social-source-chrome",
        )

        assertTrue(result.contentMarkdown.contains("The article introduction should stay"))
        assertTrue(result.contentMarkdown.contains("The article conclusion should also stay"))
        assertFalse(result.contentMarkdown.contains("17 hours ago"))
        assertFalse(result.contentMarkdown.contains("Technology"))
        assertFalse(result.contentMarkdown.contains("Maria Curi"))
        assertFalse(result.contentMarkdown.contains("Add Example on Google"))
        assertFalse(result.contentMarkdown.contains("preferred source"))
        assertFalse(result.contentMarkdown.contains("What to read next"))
        assertFalse(result.contentMarkdown.contains("data:image/webp;base64"))
    }

    @Test
    fun `exact selectors remove embedded top comment module while preserving adjacent prose`() {
        val result = Defuddle.parseHtml(
            html = """
                <article class="post-content">
                  <p>The article introduction should stay because it is normal prose with useful information. It includes enough words, punctuation, and context for the parser to keep the default cleaned result rather than invoking short-page retries.</p>
                  <div class="top-comment">
                    <h2 class="h4 top-comment__heading">Top comment by <a href="#comments">Good_ole_pinocchio</a></h2>
                    <span class="top_comment__meta">Liked by 11 people</span>
                    <div class="top-comment__body">
                      <p>"The only negative? I often find myself lifting my left wrist to check the time"</p>
                      <p>This is a reader comment and should not be part of the article body.</p>
                    </div>
                    <a href="#comments">View all comments</a>
                  </div>
                  <p><a href="https://amzn.to/example">At $99</a>, it’s hard to go wrong.</p>
                </article>
            """.trimIndent(),
            url = "https://example.com/top-comment",
        )

        assertTrue(result.contentMarkdown.contains("The article introduction should stay"))
        assertTrue(result.contentMarkdown.contains("[At $99](https://amzn.to/example), it’s hard to go wrong."))
        assertFalse(result.contentMarkdown.contains("Top comment by"))
        assertFalse(result.contentMarkdown.contains("Liked by 11 people"))
        assertFalse(result.contentMarkdown.contains("Good_ole_pinocchio"))
        assertFalse(result.contentMarkdown.contains("View all comments"))
    }

    @Test
    fun `exact selectors remove WordPress post thumbnail and social share strip while preserving article body`() {
        val result = Defuddle.parseHtml(
            html = """
                <html><head>
                  <meta property="og:image" content="https://example.com/cover.webp">
                </head><body>
                  <main>
                    <article class="hentry has-post-thumbnail">
                      <div class="bm-social-top">Share this article:<a class="bm-share" href="/share">Share</a></div>
                      <div class="post-thumbnail">
                        <a class="image-link" href="https://example.com/cover.webp">
                          <img src="https://example.com/cover.webp" alt="Cover">
                        </a>
                      </div>
                      <div class="entry-content">
                        <p>The article introduction should stay because it is normal prose with useful information. It includes enough words, punctuation, and context for the parser to keep the default cleaned result rather than invoking short-page retries.</p>
                        <p>The article conclusion should also stay after the social share strip and duplicate cover thumbnail are removed. It contains useful article text and stable punctuation.</p>
                      </div>
                    </article>
                  </main>
                </body></html>
            """.trimIndent(),
            url = "https://example.com/wordpress-cover",
            options = DefuddleOptions(contentSelector = "main"),
        )

        assertTrue(result.contentMarkdown.contains("The article introduction should stay"))
        assertTrue(result.contentMarkdown.contains("The article conclusion should also stay"))
        assertFalse(result.contentMarkdown.contains("Share this article"))
        assertFalse(result.contentMarkdown.contains("![Cover]"))
        assertFalse(result.contentHtml.contains("bm-social-top"))
        assertFalse(result.contentHtml.contains("""class="post-thumbnail""""))
    }

    @Test
    fun `content patterns remove ko-fi donation widgets while preserving article body`() {
        val result = Defuddle.parseHtml(
            html = """
                <article>
                  <p>The article introduction should stay because it is normal prose with useful information. It includes enough words, punctuation, and context for the parser to keep the default cleaned result rather than invoking short-page retries.</p>
                  <div style="margin-top: 20px; display: inline-block; text-align: center;">
                    <p>Enjoyed the article?</p>
                    <a href="https://ko-fi.com/example"><img src="/kofi3.webp" alt="Buy Me a Coffee at ko-fi.com"></a>
                  </div>
                  <p>The article conclusion should also stay after the donation widget is removed. It contains useful article text and stable punctuation.</p>
                </article>
            """.trimIndent(),
            url = "https://example.com/donation-widget",
        )

        assertTrue(result.contentMarkdown.contains("The article introduction should stay"))
        assertTrue(result.contentMarkdown.contains("The article conclusion should also stay"))
        assertFalse(result.contentMarkdown.contains("Enjoyed the article"))
        assertFalse(result.contentMarkdown.contains("Buy Me a Coffee"))
        assertFalse(result.contentMarkdown.contains("ko-fi.com"))
    }

    @Test
    fun `exact selectors remove entry footer sidebar and footer recirculation modules`() {
        val result = Defuddle.parseHtml(
            html = """
                <main>
                  <section class="entry">
                    <p>The article body should stay because it is normal prose with useful information. It includes enough words, punctuation, and context for the parser to keep the default cleaned result rather than invoking short-page retries. This makes footer and recirculation cleanup deterministic while preserving legitimate article text.</p>
                    <p>A second paragraph keeps this story body clearly article-like even when sibling story-card, sidebar, and native footer modules are present in the selected main container.</p>
                    <p class="u-text-center u-body-02"><em>ExampleToday is also on Mobile! Download our app to stay updated.</em></p>
                    <section class="l-entry__footer">
                      <p>© Riproduzione riservata</p>
                      <a class="btn-gpsource-bt-article" href="//www.google.com/preferences/source?q=example.com">Add as source</a>
                    </section>
                  </section>
                  <article class="c-story c-story--stack">
                    <span>attualita</span>
                    <h2>Related story should go</h2>
                  </article>
                  <aside class="l-entry__sidebar">
                    <h2>I più letti</h2>
                    <article><h2>Popular story should go</h2></article>
                  </aside>
                  <section data-section-key="article-footer-natives">
                    <h2>In Evidenza</h2>
                    <article><h2>Highlighted story should go</h2></article>
                  </section>
                  <section data-section-key="article-footer-outbrain">
                    <h3>Potrebbe interessarti</h3>
                  </section>
                </main>
            """.trimIndent(),
            url = "https://example.com/footer-recirculation",
        )

        assertTrue(result.contentMarkdown.contains("The article body should stay"))
        assertTrue(result.contentMarkdown.contains("A second paragraph keeps this story body"))
        assertFalse(result.contentMarkdown.contains("Download our app"))
        assertFalse(result.contentMarkdown.contains("Riproduzione riservata"))
        assertFalse(result.contentMarkdown.contains("Add as source"))
        assertFalse(result.contentMarkdown.contains("Related story should go"))
        assertFalse(result.contentMarkdown.contains("I più letti"))
        assertFalse(result.contentMarkdown.contains("Popular story should go"))
        assertFalse(result.contentMarkdown.contains("In Evidenza"))
        assertFalse(result.contentMarkdown.contains("Highlighted story should go"))
        assertFalse(result.contentMarkdown.contains("Potrebbe interessarti"))
    }

    @Test
    fun `exact selectors remove author header and article options chrome while preserving body`() {
        val result = Defuddle.parseHtml(
            html = """
                <article>
                  <header>
                    <img src="/cover.jpg" alt="Article cover">
                  </header>
                  <div class="w-article-header-comp">
                    <div class="w-author" data-nosnippet>
                      <span>By</span>
                      <a href="/author">Rahul Naskar</a>
                    </div>
                    <div class="meta_txt article-date">Published Jun 15, 2026, 6:00 AM EDT</div>
                    <div class="with-excerpt" data-nosnippet>
                      I have eight years of experience covering Android, with a focus on apps, features, and platform updates.
                    </div>
                  </div>
                  <section class="article-body">
                    <p>The actual article body should stay because it is normal prose with useful information. It includes enough words, punctuation, and context for the parser to keep the default cleaned result rather than invoking short-page retries. This makes author header and article option cleanup deterministic while preserving legitimate article text.</p>
                    <p>A second paragraph keeps the selected content stable and confirms that body paragraphs survive after surrounding chrome is removed.</p>
                  </section>
                  <aside class="article-options" data-nosnippet>
                    <div class="article-tags">
                      <a href="/utilities/">Utilities</a>
                      <a href="/tag/custom-launcher/">Custom Launcher</a>
                    </div>
                    <div class="follow-container">Follow</div>
                    <div class="follow-container">Followed</div>
                  </aside>
                </article>
            """.trimIndent(),
            url = "https://example.com/author-chrome",
        )

        val lines = result.contentMarkdown.lines().map { it.trim() }

        assertTrue(result.contentMarkdown.contains("The actual article body should stay"))
        assertTrue(result.contentMarkdown.contains("A second paragraph keeps the selected content stable"))
        assertFalse(lines.any { it == "By" || it == "Follow" || it == "Followed" })
        assertFalse(result.contentMarkdown.contains("Rahul Naskar"))
        assertFalse(result.contentMarkdown.contains("Published Jun 15"))
        assertFalse(result.contentMarkdown.contains("I have eight years of experience covering Android"))
        assertFalse(result.contentMarkdown.contains("Utilities"))
        assertFalse(result.contentMarkdown.contains("Custom Launcher"))
    }

    @Test
    fun `exact selectors remove author profile boxes while preserving story`() {
        val result = Defuddle.parseHtml(
            html = """
                <article>
                  <p>The actual story starts here with enough natural language, punctuation, and context to keep the default cleaned parse result selected. It should survive when the author profile box below the short article is removed from the reader output.</p>
                  <div class="thumbuser row">
                    <img src="/author.jpg" alt="Iacopo De Santis">
                    <div class="upper">autore</div>
                    <div class="serif"><a href="/redazione/iacopo">Iacopo De Santis</a></div>
                    <div>Editore di Pianeta Basket, 26 anni. Sempre connesso con il mondo della palla a spicchi.</div>
                    <a href="https://twitter.example/iacopo">IacopoDeSantis</a>
                  </div>
                  <p>The article conclusion should also stay after the author profile chrome is removed. It contains normal prose, useful punctuation, and enough words to keep the article body stable in the cleaned result.</p>
                </article>
            """.trimIndent(),
            url = "https://example.com/author-profile",
        )

        assertTrue(result.contentMarkdown.contains("The actual story starts here"))
        assertTrue(result.contentMarkdown.contains("The article conclusion should also stay"))
        assertFalse(result.contentMarkdown.contains("autore"))
        assertFalse(result.contentMarkdown.contains("Iacopo De Santis"))
        assertFalse(result.contentMarkdown.contains("Editore di Pianeta Basket"))
        assertFalse(result.contentMarkdown.contains("IacopoDeSantis"))
    }

    @Test
    fun `exact selectors remove mobile article metadata while preserving story`() {
        val result = Defuddle.parseHtml(
            html = """
                <div role="main">
                  <div class="testo">
                    <div class="data small">
                      15.06.2026 09:05 di
                      <span class="contatta upper"><a href="/redazione/example">Example Author</a></span>
                      <span class="ecc_count_read"><span id="button_letture"><a><span class="box_reading">vedi letture</span></a></span></span>
                    </div>
                    <div class="testo_align">
                      <img src="/story.jpg" alt="Story image">
                      <p>The actual story starts here with enough natural language, punctuation, and context to keep the default cleaned parse result selected. It should survive when mobile article metadata is removed from the reader output.</p>
                      <p>The article conclusion should also stay after the mobile byline and read-count chrome is removed. It contains normal prose, useful punctuation, and enough words to keep the article body stable in the cleaned result.</p>
                    </div>
                  </div>
                </div>
            """.trimIndent(),
            url = "https://example.com/mobile-meta",
        )

        assertTrue(result.contentMarkdown.contains("The actual story starts here"))
        assertTrue(result.contentMarkdown.contains("The article conclusion should also stay"))
        assertTrue(result.contentMarkdown.contains("![Story image]"))
        assertFalse(result.contentMarkdown.contains("15.06.2026 09:05"))
        assertFalse(result.contentMarkdown.contains("Example Author"))
        assertFalse(result.contentMarkdown.contains("vedi letture"))
    }

    @Test
    fun `exact selectors remove Citynews event header and byline chrome while preserving body`() {
        val result = Defuddle.parseHtml(
            html = """
                <main>
                  <article class="l-entry l-entry--infos-square">
                    <header class="l-entry__header">
                      <span class="u-label-02">/</span>
                      <div class="l-grid l-grid--square">
                        <div class="l-grid__item"><span>Dove</span><a href="/eventi/location/piazza-marconi/">Piazza Marconi</a><p><a href="#map">Piazza Guglielmo Marconi</a></p><span>Vigonovo</span></div>
                        <div class="l-grid__item"><span>Quando</span><span>Dal <span>18/06/2026</span></span><span>al <span>21/06/2026</span></span><span>ore 21.00</span></div>
                        <div class="l-grid__item"><span>Prezzo</span><span>Gratis</span></div>
                        <div class="l-grid__item"><span>Altre informazioni</span></div>
                      </div>
                    </header>
                    <section class="l-entry__byline--small">
                      <div class="author">
                        <img alt="Avatar" src="/avatar.png">
                        <span class="author-name">Redazione</span>
                      </div>
                      <time>15 giugno 2026 9:57</time>
                    </section>
                    <section class="c-entry l-entry__body">
                      <p>The actual event article starts here with enough natural language, punctuation, and context to keep the default cleaned parse result selected. It should survive when the event metadata grid and byline chrome are removed from the reader output.</p>
                      <p>The event article conclusion should also stay after the event chrome is removed. It contains normal prose, useful punctuation, and enough words to keep the article body stable in the cleaned result.</p>
                      <p><strong>Dove:</strong> Piazza Marconi, Vigonovo<br><strong>Quando:</strong> dal 18 al 21 giugno 2026<br><strong>Ingresso:</strong> gratuito</p>
                    </section>
                  </article>
                </main>
            """.trimIndent(),
            url = "https://example.com/event-info-square",
        )

        val lines = result.contentMarkdown.lines().map { it.trim() }

        assertTrue(result.contentMarkdown.contains("The actual event article starts here"))
        assertTrue(result.contentMarkdown.contains("The event article conclusion should also stay"))
        assertTrue(result.contentMarkdown.contains("**Dove:** Piazza Marconi, Vigonovo"))
        assertFalse(lines.any { it == "/" || it == "Dove" || it == "Quando" || it == "Prezzo" || it == "Altre informazioni" })
        assertFalse(lines.any { it == "Piazza Marconi" || it == "Piazza Guglielmo Marconi" || it == "Redazione" })
        assertFalse(result.contentMarkdown.contains("15 giugno 2026 9:57"))
    }

    @Test
    fun `content cleanup removes post footer source follow and promo modules while preserving story`() {
        val result = Defuddle.parseHtml(
            html = """
                <article class="post-content">
                  <p>The actual story starts here with enough natural language, punctuation, and context to keep the default cleaned parse result selected. It should survive when publisher footer chrome is removed from the reader output.</p>
                  <p>The story conclusion should also stay after source badges, author-follow prompts, affiliate disclaimers, and visitor promos are removed. It contains normal prose, useful punctuation, and enough words to keep the article body stable in the cleaned result.</p>
                  <h2>More on Google Pixel:</h2>
                  <ul>
                    <li><a href="/related-one">Related one</a></li>
                    <li><a href="/related-two">Related two</a></li>
                    <li><a href="/related-three">Related three</a></li>
                  </ul>
                  <p><em><strong>Follow Ben:</strong> <a href="/twitter">Twitter/X</a>, <a href="/threads">Threads</a>, and <a href="/instagram">Instagram</a></em></p>
                  <div class="google-preferred-source-badge">
                    <a href="https://google.com/preferences/source?q=https://example.com">
                      <img src="/preferred-source-dark.png" alt="Add Example as a preferred source on Google">
                    </a>
                  </div>
                  <div class="ad-disclaimer-container">
                    <p class="disclaimer-affiliate"><em>FTC: We use income earning auto affiliate links.</em> <a href="/about">More.</a></p>
                  </div>
                  <div id="after_disclaimer_placement">
                    <div class="visitor-promo" data-nosnippet="true">
                      You’re reading Example News, day after day. Be sure to check out our homepage and follow Example on Twitter, Facebook, and LinkedIn to stay in the loop. Don’t know where to start? Check out our exclusive stories, reviews, how-tos, and subscribe to our YouTube channel.
                    </div>
                  </div>
                </article>
            """.trimIndent(),
            url = "https://example.com/footer-chrome",
        )

        assertTrue(result.contentMarkdown.contains("The actual story starts here"))
        assertTrue(result.contentMarkdown.contains("The story conclusion should also stay"))
        assertFalse(result.contentMarkdown.contains("More on Google Pixel"))
        assertFalse(result.contentMarkdown.contains("Related one"))
        assertFalse(result.contentMarkdown.contains("Follow Ben"))
        assertFalse(result.contentMarkdown.contains("preferred source on Google"))
        assertFalse(result.contentMarkdown.contains("FTC: We use income earning"))
        assertFalse(result.contentMarkdown.contains("You’re reading Example News"))
        assertFalse(result.contentMarkdown.contains("subscribe to our YouTube channel"))
    }

    @Test
    fun `exact selectors remove category chips and author latest posts boxes while preserving story`() {
        val result = Defuddle.parseHtml(
            html = """
                <article>
                  <div class="post-cats-list">
                    <span class="category-button"><a href="/category/basketball/">Basketball</a></span>
                    <span class="category-button"><a href="/category/news/">News</a></span>
                  </div>
                  <p>The actual story starts here with enough natural language, punctuation, and context to keep the default cleaned parse result selected. It should survive when category chips and an author latest-posts box are removed from the reader output.</p>
                  <p>The article conclusion should also stay after author recirculation chrome is removed. It contains normal prose, useful punctuation, and enough words to keep the article body stable in the cleaned result.</p>
                  <div class="abh_box abh_box_down abh_box_business">
                    <ul class="abh_tabs">
                      <li class="abh_about"><a href="#abh_about">About</a></li>
                      <li class="abh_posts"><a href="#abh_posts">Latest Posts</a></li>
                    </ul>
                    <section class="vcard author abh_about_tab abh_tab">
                      <img src="/author.jpg" alt="Roberto Caporilli">
                      <a href="/author/roberto-caporilli/">Roberto Caporilli</a>
                    </section>
                    <section class="abh_posts_tab abh_tab">
                      <div>Latest posts by Roberto Caporilli <a href="/author/roberto-caporilli/">see all</a></div>
                      <ul>
                        <li><a href="/old-story/">Old recirculated story</a> - 14 Giugno 2026</li>
                      </ul>
                    </section>
                  </div>
                </article>
            """.trimIndent(),
            url = "https://example.com/category-author-chrome",
        )

        val lines = result.contentMarkdown.lines().map { it.trim() }

        assertTrue(result.contentMarkdown.contains("The actual story starts here"))
        assertTrue(result.contentMarkdown.contains("The article conclusion should also stay"))
        assertFalse(lines.any { it == "Basketball" || it == "News" || it == "About" || it == "Latest Posts" })
        assertFalse(result.contentMarkdown.contains("Roberto Caporilli"))
        assertFalse(result.contentMarkdown.contains("Latest posts by"))
        assertFalse(result.contentMarkdown.contains("Old recirculated story"))
    }

    @Test
    fun `duplicate images are removed after first occurrence`() {
        val result = Defuddle.parseHtml(
            html = """
                <article>
                  <p>Article prose has enough words to keep the default cleaned parse result. It describes the image context clearly and avoids short retry paths with stable text.</p>
                  <img src="/image.png" alt="First">
                  <img src="/image.png" alt="Duplicate">
                </article>
            """.trimIndent(),
            url = "https://example.com/images",
        )

        assertTrue(result.contentHtml.contains("""alt="First""""))
        assertFalse(result.contentHtml.contains("""alt="Duplicate""""))
    }

    @Test
    fun `cover image duplicating metadata image is removed`() {
        val result = Defuddle.parseHtml(
            html = """
                <html><head>
                  <meta property="og:image" content="https://example.com/cover.png">
                </head><body>
                  <article>
                    <div class="post-thumbnail"><img src="/cover.png" alt="Cover"></div>
                    <p>Article prose has enough words to keep the default cleaned parse result. It describes the article content clearly and avoids short retry paths with stable text.</p>
                  </article>
                </body></html>
            """.trimIndent(),
            url = "https://example.com/article",
        )

        assertEquals("https://example.com/cover.png", result.image)
        assertFalse(result.contentHtml.contains("""alt="Cover""""))
        assertFalse(result.contentHtml.contains("""class="post-thumbnail""""))
    }
}
