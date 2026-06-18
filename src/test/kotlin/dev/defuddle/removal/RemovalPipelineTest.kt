package dev.defuddle.removal

import dev.defuddle.Defuddle
import dev.defuddle.DefuddleOptions
import dev.defuddle.extractors.Extractor
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
        RemovalPipeline.apply(article, debug)

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
            url = "https://www.ilmitte.com/category-chips",
        )

        assertTrue(result.contentMarkdown.contains("The actual article body should stay"))
        assertFalse(result.contentMarkdown.contains("Apertura"))
        assertFalse(result.contentMarkdown.contains("Politica"))
        assertFalse(result.contentMarkdown.contains("Politica Tedesca"))
    }

    @Test
    fun `content patterns remove opening article header chrome before hinted body`() {
        val result = Defuddle.parseHtml(
            html = """
                <main>
                  <article id="post">
                    <header>
                      <div class="block-header hide-mobile"><h2>News</h2></div>
                      <h1>Example title duplicated from metadata</h1>
                      <div class="article-aux">
                        <time datetime="2026-06-15T13:38:00+00:00">Mon Jun 15 2026, 09:38 AM EDT · 2 minute read</time>
                      </div>
                      <div class="hero">
                        <p class="image-caption hero-caption">Hero caption duplicated from the cover image.</p>
                      </div>
                      <div class="river-score-wrap">
                        <div class="rumor-head"><span>Rumor Score</span></div>
                        <div class="rumor-foot">Possible</div>
                      </div>
                    </header>
                    <div class="article-body">
                      <p>The actual article body should stay because it is normal prose with useful information. It includes enough words, punctuation, and context for the parser to keep the default cleaned result instead of invoking short-page retries.</p>
                      <p>A second paragraph keeps the selected content stable and confirms that body paragraphs survive after opening article chrome is removed.</p>
                    </div>
                  </article>
                </main>
            """.trimIndent(),
            url = "https://example.com/opening-header-chrome",
        )

        assertTrue(result.contentMarkdown.contains("The actual article body should stay"))
        assertTrue(result.contentMarkdown.contains("A second paragraph keeps the selected content stable"))
        assertFalse(result.contentMarkdown.contains("News"))
        assertFalse(result.contentMarkdown.contains("Example title duplicated from metadata"))
        assertFalse(result.contentMarkdown.contains("2 minute read"))
        assertFalse(result.contentMarkdown.contains("Hero caption duplicated from the cover image"))
        assertFalse(result.contentMarkdown.contains("Rumor Score"))
        assertFalse(result.contentMarkdown.contains("Possible"))
    }

    @Test
    fun `content patterns remove mega article header chrome before article content`() {
        val result = Defuddle.parseHtml(
            html = """
                <main>
                  <article>
                    <header class="mega-header">
                      <div class="tags">
                        <a href="/tag/charts/" rel="tag">charts</a>
                        <a href="/tag/data-visualization/" rel="tag">data visualization</a>
                      </div>
                      <h1 class="article-title">Example title duplicated from metadata</h1>
                      <div class="author-row">
                        <img src="https://example.com/avatar.png" width="80" height="80">
                        <a class="author-name" href="/author/example/">Example Author</a>
                        <span>on</span>
                        <time datetime="2026-06-04T13:14:49Z">Jun 4, 2026</time>
                      </div>
                    </header>
                    <div class="article-content">
                      <p>The actual article body should stay because it is normal prose with useful information. It includes enough words, punctuation, and context for the parser to keep the default cleaned result.</p>
                      <p>A second paragraph keeps the selected content stable and confirms that CSS-Tricks style article headers can be removed without losing the post body.</p>
                    </div>
                  </article>
                </main>
            """.trimIndent(),
            url = "https://example.com/mega-header",
        )

        val lines = result.contentMarkdown.lines().map { it.trim() }

        assertTrue(result.contentMarkdown.contains("The actual article body should stay"))
        assertTrue(result.contentMarkdown.contains("A second paragraph keeps the selected content stable"))
        assertFalse(lines.any { it == "charts" || it == "data visualization" || it == "on" || it == "Jun 4, 2026" })
        assertFalse(result.contentMarkdown.contains("Example title duplicated from metadata"))
        assertFalse(result.contentMarkdown.contains("Example Author"))
        assertFalse(result.contentHtml.contains("mega-header"))
        assertFalse(result.contentHtml.contains("author-row"))
    }

    @Test
    fun `content patterns remove publisher header controls and author mini bio around post content`() {
        val result = Defuddle.parseHtml(
            html = """
                <main>
                  <article class="h-entry">
                    <header>
                      <div class="upper-deck"><span>THE FALLOUT BEGINS</span></div>
                      <h1>Example title duplicated from metadata</h1>
                      <p>Deck text duplicated from the article description.</p>
                      <div class="byline">
                        <a href="/author/example">Example Author</a>
                        <time datetime="2026-06-12T19:26:47+00:00">Jun 12, 2026 3:26 pm</time>
                        <a href="#comments">47</a>
                      </div>
                    </header>
                    <div class="text-settings-dropdown-story">
                      <div class="text-settings">
                        <span>Story text</span>
                        <label>Size</label>
                        <label>Links</label>
                      </div>
                    </div>
                    <div class="layout-wrapper">
                      <div class="post-content post-content-double">
                        <p>The actual post content should stay because it is normal prose with useful information. It includes enough words, punctuation, and context for the parser to keep the default cleaned result instead of invoking short-page retries.</p>
                        <p>A second paragraph keeps the selected content stable and confirms that body paragraphs survive after publisher header and reader control chrome is removed.</p>
                      </div>
                    </div>
                    <div class="author-mini-bio">
                      <img src="/author.jpg" alt="Photo of Example Author">
                      <p>Example Author is a senior editor and this author biography should not be part of the article.</p>
                    </div>
                  </article>
                </main>
            """.trimIndent(),
            url = "https://arstechnica.com/publisher-header-controls",
        )

        assertTrue(result.contentMarkdown.contains("The actual post content should stay"))
        assertTrue(result.contentMarkdown.contains("A second paragraph keeps the selected content stable"))
        assertFalse(result.contentMarkdown.contains("THE FALLOUT BEGINS"))
        assertFalse(result.contentMarkdown.contains("Example title duplicated from metadata"))
        assertFalse(result.contentMarkdown.contains("Deck text duplicated from the article description"))
        assertFalse(result.contentMarkdown.contains("Jun 12, 2026"))
        assertFalse(result.contentMarkdown.contains("Story text"))
        assertFalse(result.contentMarkdown.contains("Size"))
        assertFalse(result.contentMarkdown.contains("Links"))
        assertFalse(result.contentMarkdown.contains("Example Author is a senior editor"))
        assertFalse(result.contentMarkdown.contains("Photo of Example Author"))
    }

    @Test
    fun `exact selectors remove css module byline and linkback roundup chrome`() {
        val result = Defuddle.parseHtml(
            html = """
                <main>
                  <article>
                    <div class="byline--3Eec5bcq">
                      <time datetime="2026-06-15T05:56:50-07:00">Monday June 15, 2026 5:56 am PDT</time>
                      by <a href="/author/example" rel="author">Example Author</a>
                    </div>
                    <div class="content--2u3grYDr js-content">
                      <div class="ugc--2nTu61bm">
                        <p>The actual article body should stay because it is normal prose with useful information. It includes enough words, punctuation, and context for the parser to keep the default cleaned result instead of invoking short-page retries.</p>
                        <p>A second paragraph keeps the selected content stable and confirms that body paragraphs survive after CSS-module byline and roundup footer chrome is removed.</p>
                        <div class="linkback">Related Roundup: <a href="/roundup/example">Example Product</a></div>
                      </div>
                    </div>
                  </article>
                </main>
            """.trimIndent(),
            url = "https://www.macrumors.com/css-module-byline",
        )

        assertTrue(result.contentMarkdown.contains("The actual article body should stay"))
        assertTrue(result.contentMarkdown.contains("A second paragraph keeps the selected content stable"))
        assertFalse(result.contentMarkdown.contains("Monday June 15"))
        assertFalse(result.contentMarkdown.contains("Example Author"))
        assertFalse(result.contentMarkdown.contains("Related Roundup"))
        assertFalse(result.contentMarkdown.contains("Example Product"))
    }

    @Test
    fun `exact selectors remove TechCrunch article chrome around entry content`() {
        val result = Defuddle.parseHtml(
            html = """
                <main>
                  <article>
                    <div class="article__meta">
                      <span>In Brief</span>
                      <p>Posted:</p>
                      <time datetime="2026-06-15T14:45:03+00:00">7:45 AM PDT · June 15, 2026</time>
                    </div>
                    <figure class="wp-block-post-featured-image">
                      <img src="https://example.com/cover.jpg" alt="Cover image">
                      <figcaption class="wp-block-post-featured-image__caption"><strong>Image Credits:</strong>Example Agency</figcaption>
                    </figure>
                    <div class="wp-block-techcrunch-post-authors-list">
                      <img class="post-authors-list__author-thumb" src="/author.jpg" alt="Example Author">
                      <a class="post-authors-list__author" href="/author/example">Example Author</a>
                    </div>
                    <h1 class="wp-block-post-title">Example title duplicated from metadata</h1>
                    <div class="entry-content">
                      <p>The actual article body should stay because it is normal prose with useful information. It includes enough words, punctuation, and context for the parser to keep the default cleaned result instead of invoking short-page retries.</p>
                      <p>A second paragraph keeps the selected content stable and confirms that body paragraphs survive after TechCrunch article chrome is removed.</p>
                      <div class="wp-block-techcrunch-event-cta"><p class="rightrail-promo__description">Get an inside look at what it takes to scale and succeed from unrelated event sponsors.</p></div>
                      <div class="latest-in-pattern"><h2>Latest in Space</h2></div>
                      <div class="wp-block-query"><a href="/related">Related latest article</a></div>
                    </div>
                  </article>
                </main>
            """.trimIndent(),
            url = "https://techcrunch.com/techcrunch-chrome",
        )

        assertTrue(result.contentMarkdown.contains("The actual article body should stay"))
        assertTrue(result.contentMarkdown.contains("A second paragraph keeps the selected content stable"))
        assertFalse(result.contentMarkdown.contains("In Brief"))
        assertFalse(result.contentMarkdown.contains("Posted:"))
        assertFalse(result.contentMarkdown.contains("7:45 AM PDT"))
        assertFalse(result.contentMarkdown.contains("Image Credits"))
        assertFalse(result.contentMarkdown.contains("Example Author"))
        assertFalse(result.contentMarkdown.contains("Get an inside look"))
        assertFalse(result.contentMarkdown.contains("Latest in Space"))
        assertFalse(result.contentMarkdown.contains("Related latest article"))
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
            url = "https://www.ilmitte.com/mailchimp-newsletter",
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
            url = "https://www.androidcentral.com/embedded-widgets",
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
            url = "https://www.ilpost.it/audio-player",
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
            url = "https://www.entrepreneur.com/social-source-chrome",
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
            url = "https://9to5google.com/top-comment",
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
            url = "https://9to5linux.com/wordpress-cover",
            options = mainSelectorOptions(),
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
            url = "https://www.veneziatoday.it/footer-recirculation",
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
    fun `exact selectors remove article right rail commerce widgets`() {
        val result = Defuddle.parseHtml(
            html = """
                <article>
                  <div class="article-body">
                    <p>The article body should stay because it is normal prose with useful information. It includes enough words, punctuation, and context for the parser to keep the default cleaned result rather than invoking short-page retries.</p>
                    <p>A second paragraph keeps this story body clearly article-like when a commerce sidebar appears as a sibling inside the selected article root.</p>
                  </div>
                  <aside class="single-sidebar right-rail" aria-label="Article sidebar">
                    <div class="widget widget-sidebar widget_block">
                      <div class="widget-decor wp-block-gamespot-blocks-where-to-buy">
                        <h2>Where to Buy</h2>
                        <h3>Loading...</h3>
                        <p>GameSpot may get a commission from retail offers.</p>
                      </div>
                    </div>
                  </aside>
                </article>
            """.trimIndent(),
            url = "https://www.gamespot.com/articles/right-rail-commerce",
        )

        assertTrue(result.contentMarkdown.contains("The article body should stay"))
        assertTrue(result.contentMarkdown.contains("A second paragraph keeps this story body"))
        assertFalse(result.contentMarkdown.contains("Where to Buy"))
        assertFalse(result.contentMarkdown.contains("Loading..."))
        assertFalse(result.contentMarkdown.contains("GameSpot may get a commission"))
        assertFalse(result.contentHtml.contains("right-rail"))
    }

    @Test
    fun `exact selectors remove inline Valnet related article cards while preserving surrounding prose`() {
        val result = Defuddle.parseHtml(
            html = """
                <article>
                  <p>The article introduction should stay because it is normal prose with useful information. It includes enough words, punctuation, and context for the parser to keep the default cleaned result instead of invoking short-page retries.</p>
                  <div class="display-card article article-card small no-badge active-content" data-nosnippet>
                    <a href="/related-story/"><img src="/related.png" alt="Related story image"></a>
                    <span data-field="label" class="article-card-label"><label>Related</label></span>
                    <div class="w-display-card-content regular article-block">
                      <h5 class="display-card-title"><a href="/related-story/">Related story should go</a></h5>
                      <p class="display-card-excerpt">A recirculated article excerpt should go.</p>
                      <div class="w-display-card-extra"><label class="total-info-label">Posts</label><span>1</span></div>
                      <div class="w-display-card-meta"><span>By </span><a class="article-author" href="/author/example/">Example Author</a></div>
                    </div>
                  </div>
                  <h2>The next real article section should stay</h2>
                  <p>The article body after the inline card should also stay. It contains natural prose, useful punctuation, and enough words to prove the related module can be removed without truncating the story.</p>
                </article>
            """.trimIndent(),
            url = "https://screenrant.com/inline-related-card",
        )

        assertTrue(result.contentMarkdown.contains("The article introduction should stay"))
        assertTrue(result.contentMarkdown.contains("The next real article section should stay"))
        assertTrue(result.contentMarkdown.contains("The article body after the inline card should also stay"))
        assertFalse(result.contentMarkdown.contains("Related story should go"))
        assertFalse(result.contentMarkdown.contains("A recirculated article excerpt should go"))
        assertFalse(result.contentMarkdown.contains("Example Author"))
        assertFalse(result.contentHtml.contains("article-card-label"))
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
            url = "https://www.androidpolice.com/author-chrome",
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
            url = "https://www.pianetabasket.com/author-profile",
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
            url = "https://m.pianetabasket.com/mobile-meta",
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
            url = "https://www.veneziatoday.it/event-info-square",
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
            url = "https://9to5google.com/footer-chrome",
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
            url = "https://www.basketuniverso.it/category-author-chrome",
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
    fun `exact selectors remove future newsletter author bio and popular box slices`() {
        val result = Defuddle.parseHtml(
            html = """
                <article>
                  <p>The actual story starts here with enough natural language, punctuation, and context to keep the default cleaned parse result selected. It should survive when Future-style newsletter, author bio, and latest-article slices are removed from the reader output.</p>
                  <p>The article conclusion should also stay after recirculation chrome is removed. It contains normal prose, useful punctuation, and enough words to keep the article body stable in the cleaned result.</p>
                  <div id="slice-container-newsletterForm-example" class="slice-container slice-container-newsletterForm">
                    <div class="newsletter-form__wrapper newsletter-form__wrapper--inbodyContent">
                      <p class="newsletter-form__strapline">Get the latest news from Example, your trusted companion.</p>
                    </div>
                  </div>
                  <div id="slice-container-authorBio-example" class="slice-container slice-author-bio authorBio-example slice-container-authorBio">
                    <div class="author author__default-layout">
                      <img src="/author.jpg" alt="Example Author">
                      <div class="author__name"><a href="/author/example">Example Author</a></div>
                      <div class="author__role">News Writer &amp; Reviewer</div>
                      <div class="author__biography"><p>Example Author has written about consumer tech for years.</p></div>
                    </div>
                  </div>
                  <div id="slice-container-popularBox" class="slice-container popular-box-slice popularBox slice-container-popularBox">
                    <section class="popular-box">
                      <div class="popular-box__label__tab" role="heading">LATEST ARTICLES</div>
                      <ol>
                        <li><a href="/latest-one" data-mrf-recirculation="popular-list">First latest article</a></li>
                        <li><a href="/latest-two" data-mrf-recirculation="popular-list">Second latest article</a></li>
                      </ol>
                    </section>
                  </div>
                </article>
            """.trimIndent(),
            url = "https://www.androidcentral.com/future-slices",
        )

        assertTrue(result.contentMarkdown.contains("The actual story starts here"))
        assertTrue(result.contentMarkdown.contains("The article conclusion should also stay"))
        assertFalse(result.contentMarkdown.contains("Get the latest news from Example"))
        assertFalse(result.contentMarkdown.contains("Example Author"))
        assertFalse(result.contentMarkdown.contains("News Writer & Reviewer"))
        assertFalse(result.contentMarkdown.contains("LATEST ARTICLES"))
        assertFalse(result.contentMarkdown.contains("First latest article"))
    }

    @Test
    fun `content cleanup removes Vox-style article lede package author and follow modules`() {
        val result = Defuddle.parseHtml(
            html = """
                <article>
                  <div class="duet--article--lede">
                    <p>Header summary text should not be part of the reader article.</p>
                    <p>by Example Writer</p>
                    <time>Jun 15, 2026, 3:52 PM UTC</time>
                    <img src="/cover.jpg" alt="Cover image">
                    <cite>Image: Example Photo Desk / Example Site</cite>
                  </div>
                  <div class="duet--layout--entry-body">
                    <div class="story-package-card">
                      <a href="/series/age-checks">
                        <div>Part Of</div>
                        <div>Let me see some ID: age verification is spreading across the internet</div>
                        <div>see all updates</div>
                      </a>
                    </div>
                    <div class="writer-summary">
                      <span id="follow-author-standard_article_details-example">Example Writer</span>
                      <span>is a news writer covering all things consumer tech.</span>
                    </div>
                    <p>The actual story starts here with enough natural language, punctuation, and context to keep the default cleaned parse result selected. It should survive when The Verge style header, package, author, and follow modules are removed.</p>
                    <p>The article conclusion should also stay after footer follow modules are removed. It contains normal prose, useful punctuation, and enough words to keep the article body stable in the cleaned result.</p>
                    <div class="topic-follow-module">
                      <strong>Follow topics and authors</strong>
                      from this story to see more like this in your personalized homepage feed and to receive email updates.
                      <ul><li>Example Writer</li></ul>
                    </div>
                  </div>
                </article>
            """.trimIndent(),
            url = "https://www.theverge.com/vox-style-article",
        )

        assertTrue(result.contentMarkdown.contains("The actual story starts here"))
        assertTrue(result.contentMarkdown.contains("The article conclusion should also stay"))
        assertFalse(result.contentMarkdown.contains("Header summary text"))
        assertFalse(result.contentMarkdown.contains("Part Of"))
        assertFalse(result.contentMarkdown.contains("Let me see some ID"))
        assertFalse(result.contentMarkdown.contains("Example Writer"))
        assertFalse(result.contentMarkdown.contains("Follow topics and authors"))
        assertFalse(result.contentMarkdown.contains("personalized homepage"))
    }

    @Test
    fun `exact selectors remove Business Insider post chrome while preserving story`() {
        val result = Defuddle.parseHtml(
            html = """
                <main role="main">
                  <section class="post-byline subtle" data-component-type="post-byline">
                    <div class="byline-wrapper as-byline">
                      <span class="sep">By</span>
                      <a class="byline-link byline-author-name" href="/author/example-writer">Example Writer</a>
                      <span class="follow-button rich-tooltip">
                        <span>You're currently following this author! Want to unfollow? Unsubscribe via the link in your email.</span>
                      </span>
                    </div>
                  </section>
                  <section id="post-body" class="post-body grid-area-post-body" data-component-type="post-body">
                    <div class="post-details">
                      <div class="timestamp label-md" data-component-type="timestamp">
                        <time class="timestamp js-date-format" data-timestamp="2026-06-15T17:19:08.464Z">2026-06-15T17:19:08.464Z</time>
                      </div>
                    </div>
                    <section class="post-body-content post-story-body-content" data-component-type="post-body-content">
                      <p>The actual story starts here with enough natural language, punctuation, and context to keep the default cleaned parse result selected. It should survive when publisher byline, timestamp, related video, and back-home chrome are removed.</p>
                      <section class="post-video-recirc" data-component-type="post-video-recirc">
                        <header class="post-video-recirc-header">Related video</header>
                        <article class="post-video-recirc-article">
                          <div class="post-video-recirc-title">What are the real-life consequences of AI?</div>
                        </article>
                      </section>
                      <p>The article conclusion should also stay after the inline video recirculation unit is removed. It contains normal prose, useful punctuation, and enough words to keep the article body stable in the cleaned result.</p>
                    </section>
                  </section>
                  <section class="back-to-home-container">
                    <a class="back-to-home-link" href="/"><span class="back-to-home-text">HOME</span></a>
                  </section>
                </main>
            """.trimIndent(),
            url = "https://www.businessinsider.com/post-chrome",
        )

        assertTrue(result.contentMarkdown.contains("The actual story starts here"))
        assertTrue(result.contentMarkdown.contains("The article conclusion should also stay"))
        assertFalse(result.contentMarkdown.contains("By"))
        assertFalse(result.contentMarkdown.contains("Example Writer"))
        assertFalse(result.contentMarkdown.contains("currently following this author"))
        assertFalse(result.contentMarkdown.contains("2026-06-15T17:19:08.464Z"))
        assertFalse(result.contentMarkdown.contains("Related video"))
        assertFalse(result.contentMarkdown.contains("real-life consequences of AI"))
        assertFalse(result.contentMarkdown.contains("HOME"))
    }

    @Test
    fun `content cleanup removes Entrepreneur header controls while preserving deck and story`() {
        val result = Defuddle.parseHtml(
            html = """
                <article>
                  <header>
                    <div class="tw:text-lg">
                      <p>In some cases, the bonuses amount to more than a teacher's salary for the entire year.</p>
                    </div>
                    <div class="tw:text-sm tw:uppercase tw:mb-6 tw:font-sans tw:border-y tw:border-slate-200">
                      By
                      <span><a href="/author/example-writer">Example Writer</a></span>
                      <span role="separator">|</span>
                      <span>edited by <a href="/author/example-editor">Example Editor</a></span>
                      <span role="separator">|</span>
                      <time datetime="2026-06-15T17:58:02+00:00">Jun 15, 2026</time>
                      <a href="https://www.google.com/preferences/source?q=example.com">Add Example</a>
                      <a href="#ep-comments">Comment</a>
                    </div>
                  </header>
                  <div class="classifai-listen-to-post-wrapper">
                    <div class="classifai-post-audio-heading">Listen to this post</div>
                  </div>
                  <audio id="classifai-post-audio-player" src="/post.mp3"></audio>
                  <h2>Key Takeaways</h2>
                  <ul><li>The useful takeaway should stay in the article output.</li></ul>
                  <p>The actual story starts here with enough natural language, punctuation, and context to keep the default cleaned parse result selected. It should survive when publisher byline, editor, preferred-source, comment, and audio controls are removed.</p>
                  <p>The article conclusion should also stay after the article header controls are removed. It contains normal prose, useful punctuation, and enough words to keep the article body stable in the cleaned result.</p>
                </article>
            """.trimIndent(),
            url = "https://www.entrepreneur.com/entrepreneur-header-controls",
        )

        assertTrue(result.contentMarkdown.contains("In some cases, the bonuses amount"))
        assertTrue(result.contentMarkdown.contains("Key Takeaways"))
        assertTrue(result.contentMarkdown.contains("The actual story starts here"))
        assertFalse(result.contentMarkdown.contains("By"))
        assertFalse(result.contentMarkdown.contains("Example Writer"))
        assertFalse(result.contentMarkdown.contains("edited by"))
        assertFalse(result.contentMarkdown.contains("Example Editor"))
        assertFalse(result.contentMarkdown.contains("Jun 15, 2026"))
        assertFalse(result.contentMarkdown.contains("Add Example"))
        assertFalse(result.contentMarkdown.contains("Comment"))
        assertFalse(result.contentMarkdown.contains("Listen to this post"))
    }

    @Test
    fun `content cleanup removes related content card runs after story prose`() {
        val result = Defuddle.parseHtml(
            html = """
                <main>
                  <div class="page-container tw:border-b">
                    <span class="tw:mx-3 tw:flex">/</span>
                  </div>
                  <article>
                    <header>
                      <p>The article deck should stay because it introduces the actual story with useful context and natural punctuation.</p>
                    </header>
                    <div class="story-body">
                      <p>The actual story starts here with enough natural language, punctuation, and context to keep the default cleaned parse result selected. It should survive when a publisher related-content card run follows the article body.</p>
                      <p>The article conclusion should also stay after related cards are removed. It contains normal prose, useful punctuation, and enough words to keep the article body stable in the cleaned result.</p>
                    </div>
                  </article>
                  <div class="tw:mb-2 tw:border-t tw:border-slate-200">
                    <h2>Related Content</h2>
                  </div>
                  <section class="tw:grid tw:sm:grid-cols-2 tw:gap-x-10">
                    <article class="tw:mb-12 is-entire-card-clickable">
                      <img src="/agentic-ai.jpg" alt="">
                      <div>Business Ideas</div>
                      <h3><a href="/related-one">5 Things Companies Get Wrong About Agentic AI</a></h3>
                      <div>By <a href="/author/dean-guida">Dean Guida</a></div>
                    </article>
                    <article class="tw:mb-12 is-entire-card-clickable">
                      <img src="/meta.jpg" alt="">
                      <div>Business News</div>
                      <h3><a href="/related-two">Mark Zuckerberg Admits Meta Made Mistakes</a></h3>
                      <div>By <a href="/author/jonathan-small">Jonathan Small</a></div>
                    </article>
                  </section>
                </main>
            """.trimIndent(),
            url = "https://example.com/related-content-cards",
            options = mainSelectorOptions(),
        )

        assertTrue(result.contentMarkdown.contains("The article deck should stay"))
        assertTrue(result.contentMarkdown.contains("The actual story starts here"))
        assertTrue(result.contentMarkdown.contains("The article conclusion should also stay"))
        assertFalse(result.contentMarkdown.lines().map { it.trim() }.any { it == "/" })
        assertFalse(result.contentMarkdown.contains("Related Content"))
        assertFalse(result.contentMarkdown.contains("5 Things Companies Get Wrong About Agentic AI"))
        assertFalse(result.contentMarkdown.contains("Dean Guida"))
        assertFalse(result.contentMarkdown.contains("Mark Zuckerberg Admits Meta Made Mistakes"))
        assertFalse(result.contentHtml.contains("is-entire-card-clickable"))
    }

    @Test
    fun `content cleanup removes trending author bio and skeleton recirculation modules`() {
        val result = Defuddle.parseHtml(
            html = """
                <main>
                  <div class="container-ft">
                    <div data-cy="trending-top-bar">
                      <h1>Trending</h1>
                      <h1>1</h1>
                      <h1>2</h1>
                      <h1>3</h1>
                    </div>
                  </div>
                  <div data-cy="article-wrapper">
                    <span>
                      <a data-cy="article-section-eyebrow" href="/section/north-america/">North America</a>
                      <a data-cy="article-tag-eyebrow" href="/tag/animals/">Animals</a>
                    </span>
                    <h1 data-cy="article-title">Example title duplicated from metadata</h1>
                    <article data-cy="article-content" class="article-content">
                      <p>The actual article body should stay because it is normal prose with useful information. It includes enough words, punctuation, and context for the parser to keep the default cleaned result.</p>
                      <p>The article conclusion should also stay after publisher top bars, author cards, and skeleton recirculation sections are removed from the broad main selection.</p>
                    </article>
                    <div id="author-bio" data-cy="authors-bio-cards">
                      <span>About the Author</span>
                      <div data-cy="author-bio">By <a href="/author/ap">The Associated Press</a></div>
                      <a data-cy="author-see-full-bio" href="/author/ap">See full bio<span>Right Arrow Button Icon</span></a>
                    </div>
                  </div>
                  <div class="container-ft flex flex-col min-h-[1100px]">
                    <div class="flex flex-col layout-gap-md">
                      <span>Latest in North America</span>
                      <div class="animate-pulse bg-helper">Finance</div>
                      <div class="animate-pulse bg-helper">Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua. Ut enim ad minim veniam</div>
                      <span class="animate-pulse bg-helper">By Fortune Editors</span>
                      <span class="animate-pulse bg-helper">October 20, 2025</span>
                    </div>
                    <div class="flex flex-col layout-gap-md">
                      <span>Most Popular</span>
                      <div class="animate-pulse bg-helper">Finance</div>
                      <div class="animate-pulse bg-helper">Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua. Ut enim ad minim veniam</div>
                      <span class="animate-pulse bg-helper">By Fortune Editors</span>
                      <span class="animate-pulse bg-helper">October 20, 2025</span>
                    </div>
                  </div>
                </main>
            """.trimIndent(),
            url = "https://fortune.com/publisher-chrome",
            options = mainSelectorOptions(),
        )

        val lines = result.contentMarkdown.lines().map { it.trim() }

        assertTrue(result.contentMarkdown.contains("The actual article body should stay"))
        assertTrue(result.contentMarkdown.contains("The article conclusion should also stay"))
        assertFalse(result.contentMarkdown.contains("Trending"))
        assertFalse(lines.any { it == "# 1" || it == "# 2" || it == "# 3" })
        assertFalse(lines.any { it == "North America" || it == "Animals" })
        assertFalse(result.contentMarkdown.contains("About the Author"))
        assertFalse(result.contentMarkdown.contains("The Associated Press"))
        assertFalse(result.contentMarkdown.contains("Right Arrow Button Icon"))
        assertFalse(result.contentMarkdown.contains("Latest in North America"))
        assertFalse(result.contentMarkdown.contains("Most Popular"))
        assertFalse(result.contentMarkdown.contains("Lorem ipsum dolor sit amet"))
        assertFalse(result.contentMarkdown.contains("Fortune Editors"))
    }

    @Test
    fun `content cleanup removes copied tooltip blogger byline and pager chrome`() {
        val result = Defuddle.parseHtml(
            html = """
                <main>
                  <div class="adb-detail">
                    <div class="adb-detail__info">
                      <p>19 May 2026</p>
                    </div>
                    <hr>
                    <div class="copy-tooltip">
                      <span class="copy-tooltiptext">Link copied to clipboard</span>
                    </div>
                    <div class="adb-detail__content">
                      <div>
                        <div class="separator" style="clear: both; text-align: left;">Posted by Android XR Team</div>
                        <div class="separator" style="clear: both; text-align: center;">
                          <a href="/hero.png"><img src="/hero.png" width="1200" height="600"></a>
                        </div>
                        <p>The actual article body should stay because it contains normal explanatory prose about a developer program and gives the reader useful context.</p>
                        <p>The article conclusion should also stay before the publisher previous and next post navigation controls are removed.</p>
                      </div>
                    </div>
                    <hr>
                    <div class="blog-pager pagination" id="blog-pager">
                      <a class="blog-pager-newer-link page-button" href="/newer" title="Newer Post"><span>Newer post</span></a>
                      <a class="blog-pager-older-link page-button" href="/older" title="Older Post"><span>Older post</span></a>
                    </div>
                  </div>
                </main>
            """.trimIndent(),
            url = "https://android-developers.googleblog.com/blogger-post",
            options = mainSelectorOptions(),
        )

        assertTrue(result.contentMarkdown.contains("The actual article body should stay"))
        assertTrue(result.contentMarkdown.contains("The article conclusion should also stay"))
        assertTrue(result.contentMarkdown.contains("hero.png"))
        assertFalse(result.contentMarkdown.contains("Link copied to clipboard"))
        assertFalse(result.contentMarkdown.contains("Posted by Android XR Team"))
        assertFalse(result.contentMarkdown.contains("Newer post"))
        assertFalse(result.contentMarkdown.contains("Older post"))
        assertFalse(result.contentMarkdown.trim().endsWith("---"))
        assertFalse(result.contentHtml.contains("copy-tooltip"))
        assertFalse(result.contentHtml.contains("blog-pager"))
    }

    @Test
    fun `content cleanup removes JetBrains product masthead author chrome and discovery links`() {
        val result = Defuddle.parseHtml(
            html = """
                <main>
                  <div class="top-page">
                    <a href="/kotlin/"><img src="/kotlin.svg" alt="Kotlin logo"><h2>Kotlin</h2></a>
                    <p>A concise multiplatform language developed by JetBrains</p>
                  </div>
                  <section class="article-section">
                    <div class="content js-toc-content">
                      <a class="tag" href="/kotlin/category/news/">News</a>
                      <h1>Example title duplicated from metadata</h1>
                      <div class="author-post">
                        <img src="/author.jpg" alt="Example Author">
                        <a href="/author/example">Example Author</a>
                        <time datetime="2026-05-21">May 21, 2026</time>
                      </div>
                      <p>The actual article body should stay because it contains normal explanatory prose about a language support policy and gives the reader useful context.</p>
                      <p>The article conclusion should also stay before publisher navigation and discovery blocks are removed from the cleaned result.</p>
                      <div class="content__pagination">
                        <a href="/previous">Prev post</a>
                        <a href="/next">Next post</a>
                      </div>
                    </div>
                    <a class="toc-opener" href="#toc">Open table of contents</a>
                  </section>
                  <div class="section light-gray-bg">
                    <div class="container">
                      <div class="section__head"><h2>Discover more</h2></div>
                      <div class="row">
                        <a class="card img-visible" href="/related-one"><img src="/one.jpg" alt="">Related KotlinConf article</a>
                        <a class="card img-visible" href="/related-two"><img src="/two.jpg" alt="">Another related article</a>
                      </div>
                    </div>
                  </div>
                </main>
            """.trimIndent(),
            url = "https://blog.jetbrains.com/kotlin/jetbrains-post",
            options = mainSelectorOptions(),
        )

        assertTrue(result.contentMarkdown.contains("The actual article body should stay"))
        assertTrue(result.contentMarkdown.contains("The article conclusion should also stay"))
        assertFalse(result.contentMarkdown.contains("Kotlin logo"))
        assertFalse(result.contentMarkdown.contains("A concise multiplatform language"))
        assertFalse(result.contentMarkdown.contains("News"))
        assertFalse(result.contentMarkdown.contains("Example title duplicated from metadata"))
        assertFalse(result.contentMarkdown.contains("Example Author"))
        assertFalse(result.contentMarkdown.contains("Prev post"))
        assertFalse(result.contentMarkdown.contains("Next post"))
        assertFalse(result.contentMarkdown.contains("Open table of contents"))
        assertFalse(result.contentMarkdown.contains("Discover more"))
        assertFalse(result.contentMarkdown.contains("Related KotlinConf article"))
        assertFalse(result.contentHtml.contains("top-page"))
        assertFalse(result.contentHtml.contains("author-post"))
        assertFalse(result.contentHtml.contains("content__pagination"))
    }

    @Test
    fun `content cleanup removes BBC headline byline placeholders and social footer`() {
        val result = Defuddle.parseHtml(
            html = """
                <article>
                  <div data-component="headline-block">
                    <h1>Example title duplicated from metadata</h1>
                  </div>
                  <div data-component="byline-block">
                    <time datetime="2026-06-15T05:21:13.657Z">15 hours ago</time>
                    <span>Example Reporter</span>
                    <span>and</span>
                    <span>Second Reporter, Essex</span>
                  </div>
                  <div data-component="image-block">
                    <figure>
                      <img class="hide-when-no-script" aria-label="image unavailable" src="https://static.files.example/grey-placeholder.png">
                      <img src="https://example.com/hero.jpg" alt="Real hero image">
                      <figcaption>Real hero caption should stay.</figcaption>
                    </figure>
                  </div>
                  <div data-component="text-block">
                    <p>The actual article body should stay because it contains normal explanatory prose from a reported news story and gives the reader useful context.</p>
                  </div>
                  <div data-component="text-block">
                    <p>The article conclusion should also stay before publisher contact prompts and social follow prompts are removed from the cleaned result.</p>
                  </div>
                  <div data-component="text-block">
                    <p><b>Do you have a story suggestion for Essex? Contact us below.</b></p>
                  </div>
                  <div data-component="text-block">
                    <p><i>Follow Essex news on </i><a href="/sounds">BBC Sounds</a>, <a href="/facebook">Facebook</a>, <a href="/instagram">Instagram</a> and <a href="/x">X</a>.</p>
                  </div>
                </article>
            """.trimIndent(),
            url = "https://www.bbc.com/news/articles/example",
        )

        assertTrue(result.contentMarkdown.contains("The actual article body should stay"))
        assertTrue(result.contentMarkdown.contains("The article conclusion should also stay"))
        assertTrue(result.contentMarkdown.contains("![Real hero image](https://example.com/hero.jpg)"))
        assertTrue(result.contentMarkdown.contains("Real hero caption should stay."))
        assertFalse(result.contentMarkdown.contains("Example title duplicated from metadata"))
        assertFalse(result.contentMarkdown.contains("15 hours ago"))
        assertFalse(result.contentMarkdown.contains("Example Reporter"))
        assertFalse(result.contentMarkdown.contains("Second Reporter"))
        assertFalse(result.contentMarkdown.contains("grey-placeholder"))
        assertFalse(result.contentMarkdown.contains("image unavailable"))
        assertFalse(result.contentMarkdown.contains("Do you have a story suggestion"))
        assertFalse(result.contentMarkdown.contains("Follow Essex news on"))
        assertFalse(result.contentMarkdown.contains("BBC Sounds"))
        assertFalse(result.contentHtml.contains("""data-component="headline-block""""))
        assertFalse(result.contentHtml.contains("""data-component="byline-block""""))
        assertFalse(result.contentHtml.contains("hide-when-no-script"))
    }

    @Test
    fun `content cleanup removes BuzzFeed post header byline bio and comments wrapper`() {
        val result = Defuddle.parseHtml(
            html = """
                <article>
                  <header class="postHead_postHead__xEyos postHead">
                    <a href="/badge/world-cup"><img src="/badge.png" alt="World Cup 2026 badge"></a>
                    <h1>Example title duplicated from metadata</h1>
                    <p>Example dek duplicated from metadata.</p>
                    <time datetime="2026-06-15T19:06:44.000Z">Posted 27 minutes ago</time>
                  </header>
                  <div class="post_article__U0mpv post_buzzfeed__G3TlM">
                    <div class="post_content__w3pdf">
                      <div class="headline-byline_adaptiveBylineContainer__fvxcl">
                        <img src="/author.jpg" alt="Example Author">
                        <span>by</span>
                        <span>Example Author</span>
                        <span>BuzzFeed Staff</span>
                        <p>I'm the culture editor here at BuzzFeed, where this author bio should not appear in the article body.</p>
                      </div>
                      <div data-module="subbuzz-text">
                        <h2>The actual article body should stay because it starts the useful story content.</h2>
                      </div>
                      <div data-module="subbuzz-text">
                        <p>The article conclusion should also stay before comments chrome is removed from the cleaned result.</p>
                      </div>
                      <div class="CommentsWrapper_commentsWrapper__Qe9Al">
                        <h2 id="reactions-title">Comments</h2>
                      </div>
                    </div>
                  </div>
                </article>
            """.trimIndent(),
            url = "https://www.buzzfeed.com/example/post",
        )

        assertTrue(result.contentMarkdown.contains("The actual article body should stay"))
        assertTrue(result.contentMarkdown.contains("The article conclusion should also stay"))
        assertFalse(result.contentMarkdown.contains("World Cup 2026 badge"))
        assertFalse(result.contentMarkdown.contains("Example title duplicated from metadata"))
        assertFalse(result.contentMarkdown.contains("Example dek duplicated from metadata"))
        assertFalse(result.contentMarkdown.contains("Posted 27 minutes ago"))
        assertFalse(result.contentMarkdown.contains("Example Author"))
        assertFalse(result.contentMarkdown.contains("BuzzFeed Staff"))
        assertFalse(result.contentMarkdown.contains("culture editor here at BuzzFeed"))
        assertFalse(result.contentMarkdown.contains("Comments"))
        assertFalse(result.contentHtml.contains("postHead"))
        assertFalse(result.contentHtml.contains("headline-byline"))
        assertFalse(result.contentHtml.contains("reactions-title"))
    }

    @Test
    fun `exact selectors remove article header chrome and in article recirculation`() {
        val result = Defuddle.parseHtml(
            html = """
                <article>
                  <header class="a-article-grid__header">
                    <div class="article-kicker">Loose Lips</div>
                    <h1>Example title duplicated from metadata</h1>
                    <div class="article-excerpt">Example dek duplicated from metadata.</div>
                  </header>
                  <div class="a-article-grid__author">
                    <time datetime="2026-06-15T14:00:00Z">June 15, 2026</time>
                  </div>
                  <div class="a-article-grid__featured-media">
                    <img src="https://example.com/hero.jpg" alt="Hero image">
                  </div>
                  <div class="a-content">
                    <p>The actual article intro should stay because it contains useful reporting and normal prose for the reader output.</p>
                    <section class="brands-most-popular recirculation-modules trending-in-article">
                      <h2>Trending Stories</h2>
                      <article><h3>Jelly Roll Files for Divorce From Bunnie Xo</h3></article>
                    </section>
                    <p>The article conclusion should also stay after the in-article recirculation module is removed from the cleaned result.</p>
                  </div>
                </article>
            """.trimIndent(),
            url = "https://www.rollingstone.com/rollingstone-article",
        )

        assertTrue(result.contentMarkdown.contains("The actual article intro should stay"))
        assertTrue(result.contentMarkdown.contains("The article conclusion should also stay"))
        assertTrue(result.contentHtml.contains("""class="a-article-grid__featured-media""""))
        assertFalse(result.contentMarkdown.contains("Loose Lips"))
        assertFalse(result.contentMarkdown.contains("Example title duplicated from metadata"))
        assertFalse(result.contentMarkdown.contains("Example dek duplicated from metadata"))
        assertFalse(result.contentMarkdown.contains("June 15, 2026"))
        assertFalse(result.contentMarkdown.contains("Trending Stories"))
        assertFalse(result.contentMarkdown.contains("Jelly Roll Files for Divorce From Bunnie Xo"))
        assertFalse(result.contentHtml.contains("a-article-grid__header"))
        assertFalse(result.contentHtml.contains("a-article-grid__author"))
        assertFalse(result.contentHtml.contains("trending-in-article"))
    }

    @Test
    fun `exact selectors remove Gutenberg article footer sidebar newsletter and video chrome`() {
        val result = Defuddle.parseHtml(
            html = """
                <article>
                  <div class="entry-content">
                    <p>The actual article body should stay because it contains useful reporting and enough prose for the selected content.</p>
                    <div class="wp-block-savage-platform-primis-video">
                      <h2 class="wp-block-savage-platform-primis-video__heading">Videos by PopCulture.com</h2>
                    </div>
                    <p>The article conclusion should also stay before the publisher template modules are removed from the cleaned result.</p>
                  </div>
                  <div class="wp-block-group entry-footer">
                    <hr class="entry-footer__sep">
                    <section class="wp-block-group more-like-this"><h2>Next Article</h2></section>
                  </div>
                  <aside class="wp-block-group entry-aside">
                    <section class="wp-block-group more-like-this"><h2>More Celebrity</h2></section>
                    <div class="wp-block-savage-platform-beehiiv-form">
                      <h2>Your inbox just got relevant</h2>
                      <p>Sign up to get the latest pop culture scoop and celebrity news.</p>
                    </div>
                  </aside>
                  <section class="wp-block-group alignwide">
                    <div class="wp-block-group section-heading"><h2>Most Viewed</h2></div>
                  </section>
                </article>
            """.trimIndent(),
            url = "https://popculture.com/celebrity-news/example",
        )

        assertTrue(result.contentMarkdown.contains("The actual article body should stay"))
        assertTrue(result.contentMarkdown.contains("The article conclusion should also stay"))
        assertFalse(result.contentMarkdown.contains("Videos by PopCulture.com"))
        assertFalse(result.contentMarkdown.contains("Next Article"))
        assertFalse(result.contentMarkdown.contains("More Celebrity"))
        assertFalse(result.contentMarkdown.contains("Your inbox just got relevant"))
        assertFalse(result.contentMarkdown.contains("Most Viewed"))
        assertFalse(result.contentHtml.contains("wp-block-savage-platform-primis-video"))
        assertFalse(result.contentHtml.contains("entry-footer"))
        assertFalse(result.contentHtml.contains("entry-aside"))
        assertFalse(result.contentHtml.contains("wp-block-savage-platform-beehiiv-form"))
    }

    @Test
    fun `exact selectors remove Valnet display card rating widgets`() {
        val result = Defuddle.parseHtml(
            html = """
                <article>
                  <p>The actual article body should stay because it contains useful reporting and enough prose for the selected content.</p>
                  <div class="display-card type-screen medium" data-show-streamrentbuy-links="true" data-include-community-rating="true">
                    <div class="display-card-rate user-rating" data-is-stars-user-rating-widget="true">
                      <h3 class="rating-title">Your Rating</h3>
                      <label>10 stars</label>
                      <label>1 star</label>
                      <div class="rating-text">Rate Now</div>
                      <div class="rating-text rate-number">0<em>/10</em></div>
                      <a class="review-link" href="/db/example/#thread">Leave a Review</a>
                      <p class="comment-msg">Your comment has not been saved</p>
                    </div>
                    <h5 class="display-card-title"><a href="/db/tv-show/example/">Example Show</a></h5>
                    <div class="dc-tags-genre"><a>Comedy</a><a>Drama</a></div>
                    <div class="w-display-card-info"><dl><dt>Release Date</dt><dd>2000</dd></dl></div>
                    <div class="display-card-footer">Powered by ScreenRant</div>
                  </div>
                  <p>The article conclusion should also stay after the display-card rating widget is removed.</p>
                </article>
            """.trimIndent(),
            url = "https://screenrant.com/valnet-display-card",
        )

        assertTrue(result.contentMarkdown.contains("The actual article body should stay"))
        assertTrue(result.contentMarkdown.contains("The article conclusion should also stay"))
        assertFalse(result.contentMarkdown.contains("Your Rating"))
        assertFalse(result.contentMarkdown.contains("10 stars"))
        assertFalse(result.contentMarkdown.contains("Rate Now"))
        assertFalse(result.contentMarkdown.contains("Leave a Review"))
        assertFalse(result.contentMarkdown.contains("Your comment has not been saved"))
        assertFalse(result.contentMarkdown.contains("Example Show"))
        assertFalse(result.contentMarkdown.contains("Powered by ScreenRant"))
        assertFalse(result.contentHtml.contains("""class="display-card"""))
        assertFalse(result.contentHtml.contains("data-include-community-rating"))
    }

    @Test
    fun `exact selectors remove comment jump links and loading placeholders`() {
        val result = Defuddle.parseHtml(
            html = """
                <article>
                  <p>The actual article body should stay because it contains useful reporting and enough prose for the selected content.</p>
                  <p>The article conclusion should also stay before comment loading chrome is removed from the cleaned result.</p>
                  <div class="o-comments-link">
                    <a class="c-link a-content-ignore" href="#article-comments">Jump to Comments</a>
                  </div>
                  <div id="article-comments">
                    <noscript>JavaScript is required to load the comments.</noscript>
                    <div id="comments-loading" style="display:none">Loading comments...</div>
                    <div id="comments-loaded"></div>
                  </div>
                </article>
            """.trimIndent(),
            url = "https://variety.com/comment-widget",
        )

        assertTrue(result.contentMarkdown.contains("The actual article body should stay"))
        assertTrue(result.contentMarkdown.contains("The article conclusion should also stay"))
        assertFalse(result.contentMarkdown.contains("Jump to Comments"))
        assertFalse(result.contentMarkdown.contains("Loading comments"))
        assertFalse(result.contentMarkdown.contains("JavaScript is required to load the comments"))
        assertFalse(result.contentHtml.contains("o-comments-link"))
        assertFalse(result.contentHtml.contains("comments-loading"))
        assertFalse(result.contentHtml.contains("article-comments"))
    }

    @Test
    fun `exact selectors remove Android Authority source and comment chrome`() {
        val result = Defuddle.parseHtml(
            html = """
                <main>
                  <div><p>Affiliate links on Android Authority may earn us a commission. <a href="/external-links/">Learn more.</a></p></div>
                  <div><a href="https://www.androidauthority.com/mobile/">Mobile</a></div>
                  <div>The Android 17-based update brings critical display, camera, and stability patches.</div>
                  <div><span>By</span><p>34 minutes ago</p><a href="https://andauth.co/AAGooglePrefSource">Add AndroidAuthority on Google</a></div>
                  <figure><img src="https://example.com/hero.jpg" alt="Hero"></figure>
                  <div><a href="https://andauth.co/AAGoogleDiscoverSource">Follow us on Google Discover</a><a href="https://andauth.co/AAGooglePreferredSource">Add us as preferred source</a></div>
                  <div data-container-type="content">
                    <p><strong>Don’t want to miss the best from <em>Android Authority</em>?</strong></p>
                    <ul>
                      <li>Set us as a <a href="https://andauth.co/AAGoogleDiscoverSource">favorite source in Google Discover</a>.</li>
                      <li>You can also set us as a <a href="https://andauth.co/AAGooglePreferredSource">preferred source in Google Search</a>.</li>
                    </ul>
                  </div>
                  <div data-container-type="content"><p>The actual article body should stay because it contains useful reporting and enough prose for the selected content.</p></div>
                  <div><div>Follow</div></div>
                  <div data-container-type="content"><p>Thank you for being part of our community. Read our <a href="https://www.androidauthority.com/android-authority-comment-policy/">Comment Policy</a> before posting.</p></div>
                </main>
            """.trimIndent(),
            url = "https://www.androidauthority.com/example",
        )

        assertTrue(result.contentMarkdown.contains("The actual article body should stay"))
        assertTrue(result.contentMarkdown.contains("![Hero](https://example.com/hero.jpg)"))
        assertFalse(result.contentMarkdown.contains("Affiliate links on Android Authority"))
        assertFalse(result.contentMarkdown.contains("Mobile"))
        assertFalse(result.contentMarkdown.contains("The Android 17-based update brings critical display"))
        assertFalse(result.contentMarkdown.contains("Add AndroidAuthority on Google"))
        assertFalse(result.contentMarkdown.contains("Follow us on Google Discover"))
        assertFalse(result.contentMarkdown.contains("Don’t want to miss the best"))
        assertFalse(result.contentMarkdown.contains("preferred source in Google Search"))
        assertFalse(result.contentMarkdown.contains("Thank you for being part of our community"))
        assertFalse(result.contentMarkdown.contains("Comment Policy"))
    }

    @Test
    fun `exact selectors remove PhoneArena article chrome while preserving story`() {
        val result = Defuddle.parseHtml(
            html = """
                <article>
                  <p><strong>The phone is still a force to be reckoned with.</strong></p>
                  <div class="content-header-widgets">
                    <div class="content-comments-counter"><span class="comments-count">0</span></div>
                    <div class="author">
                      <img src="https://example.com/preslav.webp" alt="Preslav Mladenov">
                      <span class="author-label">By</span>
                      <a class="author-name" rel="author" href="/team/preslav.m">Preslav Mladenov</a>
                    </div>
                    <div class="content-date"><time>Published: Jun 16, 2026, 2:26 AM</time></div>
                  </div>
                  <div class="content-disclaimer">We may earn a commission if you make a purchase from the links on this page.</div>
                  <figure><img src="https://example.com/hero.jpg" alt="Hero"></figure>
                  <div class="content-body">
                    <p>The actual article body should stay because it contains useful reporting and enough prose for the selected content.</p>
                    <p>The article conclusion should also stay before PhoneArena footer chrome is removed from the cleaned result.</p>
                  </div>
                  <div class="content-after-content-row"><a class="gnews-wrapper-down" href="/google-news">Follow us on Google News</a></div>
                  <div class="content-author-byline">
                    <span>View Full Bio</span>
                    <p>Preslav Mladenov is a News and Affiliate Content Writer at PhoneArena.</p>
                    <div class="author-byline-latest-title">Read the latest from Preslav Mladenov</div>
                  </div>
                  <div class="discussions-latest">
                    <div class="discussions-latest-title">Latest Discussions</div>
                    <a href="/discussions/example">Galaxy A16 5G Takeover</a>
                    <a href="/community-zone">Discover more from the community</a>
                  </div>
                  <div class="phone-links">
                    <div class="phone-links-title">Explore Related Devices</div>
                    <a href="/reviews/motorola-razr-ultra-2025-review_id7206">Motorola Razr Ultra (2025) Review</a>
                  </div>
                </article>
            """.trimIndent(),
            url = "https://www.phonearena.com/news/example_id1",
        )

        assertTrue(result.contentMarkdown.contains("The actual article body should stay"))
        assertTrue(result.contentMarkdown.contains("The article conclusion should also stay"))
        assertTrue(result.contentMarkdown.contains("![Hero](https://example.com/hero.jpg)"))
        assertTrue(result.contentMarkdown.contains("The phone is still a force"))
        assertFalse(result.contentMarkdown.contains("Preslav Mladenov"))
        assertFalse(result.contentMarkdown.contains("Published: Jun 16"))
        assertFalse(result.contentMarkdown.contains("We may earn a commission"))
        assertFalse(result.contentMarkdown.contains("Follow us on Google News"))
        assertFalse(result.contentMarkdown.contains("View Full Bio"))
        assertFalse(result.contentMarkdown.contains("Latest Discussions"))
        assertFalse(result.contentMarkdown.contains("Galaxy A16 5G Takeover"))
        assertFalse(result.contentMarkdown.contains("Discover more from the community"))
        assertFalse(result.contentMarkdown.contains("Explore Related Devices"))
        assertFalse(result.contentHtml.contains("content-header-widgets"))
        assertFalse(result.contentHtml.contains("content-author-byline"))
        assertFalse(result.contentHtml.contains("discussions-latest"))
        assertFalse(result.contentHtml.contains("phone-links"))
    }

    @Test
    fun `exact selectors remove SI source recirculation author and breadcrumb chrome`() {
        val result = Defuddle.parseHtml(
            html = """
                <article>
                  <header>
                    <div class="mb-4 flex items-center justify-between text-grey font-group-tiny">
                      <div class="flex items-center">
                        <a href="/nfl/draft/onsi/authors/just-melo" data-testtype="author-link">Justin Melo</a>
                        <span>|</span>
                      </div>
                    </div>
                    <figure><img src="https://example.com/hero.jpg" alt="Hero" width="720" height="405"><figcaption>Hero caption stays.</figcaption></figure>
                  </header>
                  <div class="article-content">
                    <p>The actual article body should stay because it contains useful reporting and enough prose for the selected content.</p>
                    <p>Sources familiar with the Seahawks confirm Stephens is well-liked within the building. This conclusion should stay before SI footer chrome is removed.</p>
                    <div data-testid="google-news-widget"><span>Add us as a preferred source on <span>Google</span></span></div>
                  </div>
                  <div data-mm-recirc class="voltax-recirculation-widget">
                    <div class="rw-widget-loading"><p>Loading recommendations... Please wait while we load personalized content recommendations</p></div>
                  </div>
                  <div class="flex flex-col gap-5 my-[30px]">
                    <hr>
                    <div>
                      <span>Published <time datetime="2026-06-16T10:00:04Z">1 hour ago</time></span>
                      <span>| Modified <time datetime="2026-06-16T10:00:04Z">1 hour ago</time></span>
                    </div>
                    <div>
                      <a href="/nfl/draft/onsi/authors/just-melo" data-testtype="author-link">JUSTIN MELO</a>
                      <p data-testtype="author-bio">Justin Melo is the publisher of NFL Draft on SI, a Sports Illustrated channel.</p>
                      <a href="https://x.com/JustinM_NFL" data-testtype="x-link">Follow JustinM_NFL</a>
                    </div>
                  </div>
                  <div class="my-8">
                    <a href="https://www.si.com/nfl/draft/onsi">Home</a>
                    <span>/</span>
                    <a href="https://www.si.com/nfl/draft/onsi/late-round-expert">Late-Round Expert</a>
                  </div>
                </article>
            """.trimIndent(),
            url = "https://www.si.com/nfl/draft/onsi/late-round-expert/example",
        )

        assertTrue(result.contentMarkdown.contains("The actual article body should stay"))
        assertTrue(result.contentMarkdown.contains("Sources familiar with the Seahawks confirm Stephens"))
        assertFalse(result.contentMarkdown.contains("Justin Melo"))
        assertFalse(result.contentMarkdown.contains("Add us as a preferred source"))
        assertFalse(result.contentMarkdown.contains("Loading recommendations"))
        assertFalse(result.contentMarkdown.contains("Published"))
        assertFalse(result.contentMarkdown.contains("Modified"))
        assertFalse(result.contentMarkdown.contains("Follow JustinM"))
        assertFalse(result.contentMarkdown.contains("Late-Round Expert"))
        assertFalse(result.contentHtml.contains("google-news-widget"))
        assertFalse(result.contentHtml.contains("data-mm-recirc"))
        assertFalse(result.contentHtml.contains("author-bio"))
    }

    @Test
    fun `exact selectors remove Motorsport story footer widgets while preserving article prose`() {
        val result = Defuddle.parseHtml(
            html = """
                <main>
                  <article class="ms-page">
                    <div class="ms-article-content">
                      <p>The actual article body should stay because it contains useful reporting and enough prose for the selected content.</p>
                      <p>So it's part of the learning process as a year one.</p>
                      <h2>Photos from Barcelona-Catalunya GP - Sunday</h2>
                    </div>
                    <div class="ms-article-end"><h4>Share Or Save This Story</h4></div>
                    <div class="msnt-article-prev-next"><span>Previous article</span><a>Lewis Hamilton's first Ferrari win celebrations spill into the streets in Italy</a></div>
                    <div class="ms-comments-wrapper"><h3 id="top-comments-title">Top Comments</h3></div>
                  </article>
                  <div class="ms-inarticle-widgets">
                    <div class="ms-items-widget ms-items-widget--more-from-author" data-widget="more-from">
                      <span class="ms-item-more-from__label">More from</span>
                      <address>Oleg Karpov</address>
                      <a>McLaren labels upgraded Ferrari best F1 chassis after Lewis Hamilton's Barcelona win</a>
                    </div>
                    <div class="ms-items-widget ms-items-widget--latest-news" data-widget="latest-news">
                      <span class="msnt-heading__title">Latest news</span>
                    </div>
                    <div class="ms-items-widget ms-items-widget--prime-content-fullwidth" data-widget="prime-content-fullwidth">
                      <span>Discover prime content</span>
                    </div>
                    <div class="adblock-content-blocked">
                      <h3>Subscribe and access Motorsport.com with your ad-blocker.</h3>
                    </div>
                  </div>
                </main>
            """.trimIndent(),
            url = "https://www.motorsport.com/f1/news/example/10830609/",
        )

        assertTrue(result.contentMarkdown.contains("The actual article body should stay"))
        assertTrue(result.contentMarkdown.contains("So it's part of the learning process"))
        assertFalse(result.contentMarkdown.contains("Photos from Barcelona"))
        assertFalse(result.contentMarkdown.contains("Share Or Save This Story"))
        assertFalse(result.contentMarkdown.contains("Previous article"))
        assertFalse(result.contentMarkdown.contains("Top Comments"))
        assertFalse(result.contentMarkdown.contains("More from"))
        assertFalse(result.contentMarkdown.contains("Latest news"))
        assertFalse(result.contentMarkdown.contains("Discover prime content"))
        assertFalse(result.contentMarkdown.contains("Subscribe and access Motorsport.com"))
        assertFalse(result.contentHtml.contains("ms-article-end"))
        assertFalse(result.contentHtml.contains("ms-inarticle-widgets"))
        assertFalse(result.contentHtml.contains("adblock-content-blocked"))
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

    private fun mainSelectorOptions(): DefuddleOptions =
        DefuddleOptions(customExtractors = listOf(MainSelectorExtractor))

    private object MainSelectorExtractor : Extractor {
        override val id: String = "test-main-selector"
        override val contentSelectors: List<String> = listOf("main")
    }
}
