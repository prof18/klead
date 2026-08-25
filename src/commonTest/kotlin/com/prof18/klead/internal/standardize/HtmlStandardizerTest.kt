package com.prof18.klead.internal.standardize

import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.nodes.Element
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class HtmlStandardizerTest {
    @Test
    fun `heading duplicate title is removed`() {
        val article = article("""<article><h1>Article Title</h1><p>Body.</p></article>""")

        HtmlStandardizer.apply(article, title = "Article Title")

        assertFalse(article.outerHtml().contains("<h1>Article Title</h1>"))
        assertFalse(article.text().contains("Article Title"))
        assertTrue(article.text().contains("Body."))
    }

    @Test
    fun `heading duplicate title normalizes smart apostrophes`() {
        val article = article("""<article><h1>SpaceX’s biggest-ever IPO</h1><p>Body.</p></article>""")

        HtmlStandardizer.apply(article, title = "SpaceX's biggest-ever IPO")

        assertFalse(article.outerHtml().contains("<h1>SpaceX’s biggest-ever IPO</h1>"))
        assertFalse(article.text().contains("SpaceX’s biggest-ever IPO"))
        assertTrue(article.text().contains("Body."))
    }

    @Test
    fun `heading matching document title prefix with site suffix is removed`() {
        val article = article("""<article><h1>Article Title</h1><p>Body.</p></article>""")

        HtmlStandardizer.apply(article, title = "Article Title - Site Name")

        assertFalse(article.text().contains("Article Title"))
        assertTrue(article.text().contains("Body."))
    }

    @Test
    fun `heading matching document title with site prefix is removed`() {
        val article = article(
            """
            <article>
              <div class="adb-detail__info"><p>19 May 2026</p></div>
              <div class="adb-detail__title">
                <h1>Build for the future with the Android XR Developer Catalyst Program — Apply now!</h1>
              </div>
              <p>Body.</p>
            </article>
            """.trimIndent(),
        )

        HtmlStandardizer.apply(
            article,
            title = "Android Developers Blog: Build for the future with the Android XR Developer Catalyst Program — Apply now!",
        )

        assertFalse(article.text().contains("Build for the future with the Android XR Developer Catalyst Program"))
        assertTrue(article.text().contains("19 May 2026"))
        assertTrue(article.text().contains("Body."))
    }

    @Test
    fun `reference section heading matching document title prefix is preserved`() {
        val article = article("""<article><h1>Array</h1><p>Body.</p></article>""")

        HtmlStandardizer.apply(article, title = "Array - JavaScript")

        assertTrue(article.outerHtml().contains("<h2>Array</h2>"))
        assertTrue(article.text().contains("Body."))
    }

    @Test
    fun `title prefix heading followed by table of contents is preserved`() {
        val article = article(
            """
            <article>
              <h1>Installation Guide</h1>
              <p>Intro.</p>
              <hr>
              <ul>
                <li><a href="#one">One</a></li>
                <li><a href="#two">Two</a></li>
                <li><a href="#three">Three</a></li>
              </ul>
              <hr>
              <h2 id="one">One</h2>
              <p>Body.</p>
            </article>
            """.trimIndent(),
        )

        HtmlStandardizer.apply(article, title = "Installation Guide — Example Blog")

        assertTrue(article.outerHtml().contains("<h2>Installation Guide</h2>"))
        assertTrue(article.text().contains("Body."))
    }

    @Test
    fun `title prefix heading followed by byline is preserved`() {
        val article = article(
            """
            <article>
              <h1>Understanding Widget Architecture</h1>
              <p class="byline">Jane Smith · June 15, 2025</p>
              <p>Body.</p>
            </article>
            """.trimIndent(),
        )

        HtmlStandardizer.apply(article, title = "Understanding Widget Architecture | Example Blog")

        assertTrue(article.outerHtml().contains("<h2>Understanding Widget Architecture</h2>"))
        assertFalse(article.text().contains("Jane Smith"))
        assertTrue(article.text().contains("Body."))
    }

    @Test
    fun `leading by label before date metadata is removed`() {
        val article = article(
            """
            <article>
              <span>By</span>
              <span>June 15, 2026, 12:18 PM ET</span>
              <p>Body paragraph should remain.</p>
            </article>
            """.trimIndent(),
        )

        HtmlStandardizer.apply(article, title = "Article Title")

        assertFalse(article.text().contains("By"))
        assertTrue(article.text().contains("June 15, 2026"))
        assertTrue(article.text().contains("Body paragraph should remain."))
    }

    @Test
    fun `title prefix heading followed by article body wrapper is preserved`() {
        val article = article(
            """
            <article>
              <h1>Understanding Widget Architecture</h1>
              <div class="article-body"><p>Body.</p></div>
            </article>
            """.trimIndent(),
        )

        HtmlStandardizer.apply(article, title = "Understanding Widget Architecture | Example Blog")

        assertTrue(article.outerHtml().contains("<h2>Understanding Widget Architecture</h2>"))
        assertTrue(article.text().contains("Body."))
    }

    @Test
    fun `exact title heading followed by article body wrapper is removed`() {
        val article = article(
            """
            <article>
              <h1>Android Article Title</h1>
              <div class="article-body"><p>Body.</p></div>
            </article>
            """.trimIndent(),
        )

        HtmlStandardizer.apply(article, title = "Android Article Title")

        assertFalse(article.text().contains("Android Article Title"))
        assertTrue(article.text().contains("Body."))
    }

    @Test
    fun `title prefix heading followed by numbered section is preserved`() {
        val article = article(
            """
            <article>
              <h1>Installation Guide</h1>
              <p>Intro.</p>
              <h2 id="one">1. Start Here</h2>
              <p>Body.</p>
            </article>
            """.trimIndent(),
        )

        HtmlStandardizer.apply(article, title = "Installation Guide — Example Blog")

        assertTrue(article.outerHtml().contains("<h2>Installation Guide</h2>"))
        assertTrue(article.text().contains("Body."))
    }

    @Test
    fun `title prefix heading followed by ordinary section is preserved`() {
        val article = article(
            """
            <article>
              <h1>Lessons from Building API Integrations</h1>
              <p>Intro.</p>
              <h2>Understanding Rate Limits</h2>
              <p>Body.</p>
            </article>
            """.trimIndent(),
        )

        HtmlStandardizer.apply(article, title = "Lessons from Building API Integrations - Example Dev Blog")

        assertTrue(article.outerHtml().contains("<h2>Lessons from Building API Integrations</h2>"))
        assertTrue(article.text().contains("Body."))
    }

    @Test
    fun `exact title heading followed by numbered section is removed`() {
        val article = article(
            """
            <article>
              <h1>Weekly Roundup</h1>
              <p>Intro.</p>
              <h2>1. First Item</h2>
              <p>Body.</p>
            </article>
            """.trimIndent(),
        )

        HtmlStandardizer.apply(article, title = "Weekly Roundup")

        assertFalse(article.text().contains("Weekly Roundup"))
        assertTrue(article.text().contains("Body."))
    }

    @Test
    fun `heading duplicate title removes adjacent eyebrow and date chrome`() {
        val article = article(
            """
            <article>
              <div class="category">Blog</div>
              <h1>Article Title</h1>
              <div class="publish-date-block__date">March 19, 2025</div>
              <p>Body.</p>
            </article>
            """.trimIndent(),
        )

        HtmlStandardizer.apply(article, title = "Article Title - Site Name")

        assertFalse(article.text().contains("Blog"))
        assertFalse(article.text().contains("March 19, 2025"))
        assertFalse(article.text().contains("Article Title"))
        assertTrue(article.text().contains("Body."))
    }

    @Test
    fun `heading permalink anchors are removed before duplicate title comparison`() {
        val article = article(
            """
            <article>
              <h1 id="article-title">Article Title<a class="headerlink" href="#article-title" title="Permanent link">¶</a></h1>
              <p>Body.</p>
            </article>
            """.trimIndent(),
        )

        HtmlStandardizer.apply(article, title = "Article Title")

        assertFalse(article.text().contains("Article Title"))
        assertFalse(article.text().contains("¶"))
        assertTrue(article.text().contains("Body."))
    }

    @Test
    fun `subtitle paragraph after duplicate title is preserved`() {
        val article = article(
            """
            <article>
              <h1>Article Title</h1>
              <p class="article-summary">This subtitle explains the story and should stay in the article.</p>
              <figure><img src="/hero.jpg" alt="Hero image"></figure>
              <p>Body paragraph with enough text to represent the actual story.</p>
            </article>
            """.trimIndent(),
        )

        HtmlStandardizer.apply(article, title = "Article Title")

        assertFalse(article.text().contains("Article Title"))
        assertTrue(article.text().contains("This subtitle explains the story"))
        assertTrue(article.text().contains("Body paragraph"))
    }

    @Test
    fun `subtitle heading inside duplicate title wrapper is preserved`() {
        val article = article(
            """
            <article>
              <div class="article-header">
                <h1>Article Title</h1>
                <h2>This subtitle explains the story and should stay in the article.</h2>
              </div>
              <figure><img src="/hero.jpg" alt="Hero image"></figure>
              <p>Body paragraph with enough text to represent the actual story.</p>
            </article>
            """.trimIndent(),
        )

        HtmlStandardizer.apply(article, title = "Article Title")

        assertFalse(article.text().contains("Article Title"))
        assertEquals(
            "This subtitle explains the story and should stay in the article.",
            article.selectFirst("h2")?.text(),
        )
        assertTrue(article.text().contains("Body paragraph"))
    }

    @Test
    fun `heading permalink widgets are removed even when link target is not a hash anchor`() {
        val article = article(
            """
            <article>
              <h2>
                2.6. Variables and Sections
                <span class="permalink-widget inline"><a href="find/?domain=Verso.Genre.Manual.section" title="Permalink">link</a></span>
              </h2>
              <p>Body.</p>
            </article>
            """.trimIndent(),
        )

        HtmlStandardizer.apply(article, title = null)

        assertEquals("2.6. Variables and Sections", article.selectFirst("h2")?.text())
        assertFalse(article.text().contains("link"))
    }

    @Test
    fun `heading inline formatting is unwrapped`() {
        val article = article(
            """
            <article>
              <h1><strong><span>Paper 18:</span><a href="https://example.com/paper"> Variational Lossy Autoencoder</a></strong></h1>
              <p>Body.</p>
            </article>
            """.trimIndent(),
        )

        HtmlStandardizer.apply(article, title = null)

        val heading = article.selectFirst("h2")
        assertNotNull(heading)
        assertEquals("Paper 18: Variational Lossy Autoencoder", heading.text())
        assertTrue(heading.select("strong").isEmpty())
        assertEquals("https://example.com/paper", heading.selectFirst("a")?.attr("href"))
    }

    @Test
    fun `heading links are preserved while permalink anchors are stripped`() {
        val article = article(
            """
            <article>
              <h2>1. <a href="https://example.com/item">First Item</a><a class="headerlink" href="#item">¶</a></h2>
              <p>Body.</p>
            </article>
            """.trimIndent(),
        )

        HtmlStandardizer.apply(article, title = null)

        val heading = article.selectFirst("h2")
        assertNotNull(heading)
        assertEquals("1. First Item", heading.text())
        assertEquals("https://example.com/item", heading.selectFirst("a")?.attr("href"))
        assertFalse(heading.text().contains("¶"))
    }

    @Test
    fun `heading line breaks flatten to spaces`() {
        val article = article(
            """
            <article>
              <h1>Primary title<br>Secondary title</h1>
              <p>Body.</p>
            </article>
            """.trimIndent(),
        )

        HtmlStandardizer.apply(article, title = "Different Title")

        val heading = article.selectFirst("h2")
        assertNotNull(heading)
        assertEquals("Primary title Secondary title", heading.text())
        assertTrue(heading.select("br").isEmpty())
    }

    @Test
    fun `internal spacer markers survive empty wrapper cleanup`() {
        val article = article(
            """
            <article>
              <p>Before.</p>
              <div data-klead-blank-spacer="x-title"></div>
              <p>After.</p>
              <div></div>
            </article>
            """.trimIndent(),
        )

        HtmlStandardizer.apply(article, title = null)

        assertNotNull(article.selectFirst("[data-klead-blank-spacer]"))
        assertTrue(article.select("div:not([data-klead-blank-spacer])").isEmpty())
    }

    @Test
    fun `arxiv cross reference links are unwrapped`() {
        val article = article(
            """
            <article>
              <p>See Figure <a href="#S3.F1" title="Figure 1" class="ltx_ref"><span class="ltx_text ltx_ref_tag">1</span></a>
              and Section <a href="#S3.SS2" class="ltx_ref"><span class="ltx_text ltx_ref_tag">3.2</span></a>.</p>
              <p>Keep regular <a href="#local">internal links</a>.</p>
            </article>
            """.trimIndent(),
        )

        HtmlStandardizer.apply(article, title = null)

        assertEquals("See Figure 1 and Section 3.2.", article.selectFirst("p")?.text())
        assertTrue(article.select("a.ltx_ref").isEmpty())
        assertEquals("#local", article.selectFirst("a")?.attr("href"))
    }

    @Test
    fun `arxiv bibliography citations normalize to footnotes in bibliography order`() {
        val article = article(
            """
            <article>
              <p>Prior work <cite class="ltx_cite">[<a href="https://arxiv.org/html/1706.03762v7#bib.bib35" class="ltx_ref">35</a>, <a href="https://arxiv.org/html/1706.03762v7#bib.bib2" class="ltx_ref">2</a>]</cite>.</p>
              <section class="ltx_bibliography">
                <h2>References</h2>
                <ul class="ltx_biblist">
                  <li id="bib.bib2" class="ltx_bibitem"><span class="ltx_tag_bibitem">[2]</span><span>First reference.</span></li>
                  <li id="bib.bib35" class="ltx_bibitem"><span class="ltx_tag_bibitem">[35]</span><span>Second reference.</span></li>
                </ul>
              </section>
            </article>
            """.trimIndent(),
        )

        HtmlStandardizer.apply(article, title = null)

        val paragraph = article.selectFirst("p")
        assertEquals("Prior work 35 2.", paragraph?.text())
        assertEquals("#fn2", paragraph?.select("sup a")?.get(0)?.attr("href"))
        assertEquals("#fn1", paragraph?.select("sup a")?.get(1)?.attr("href"))
        assertTrue(article.selectFirst("section[data-footnotes]")?.hasClass("footnotes") == true)
        assertEquals("fn1", article.select("section[data-footnotes] li").get(0).id())
        assertEquals("First reference.", article.select("section[data-footnotes] li").get(0).text())
        assertEquals("fn2", article.select("section[data-footnotes] li").get(1).id())
        assertEquals("Second reference.", article.select("section[data-footnotes] li").get(1).text())
    }

    @Test
    fun `arxiv footnote marks remove hidden payload and keep readable spacing`() {
        val article = article(
            """
            <article>
              <p>Rafael Rafailov<span class="ltx_note ltx_role_footnotemark"><sup class="ltx_note_mark">2</sup><span class="ltx_note_outer"><span class="ltx_note_content"><sup class="ltx_note_mark">2</sup><span class="ltx_note_type">footnotemark: </span><span class="ltx_tag ltx_tag_note">2</span></span></span></span></p>
            </article>
            """.trimIndent(),
        )

        HtmlStandardizer.apply(article, title = null)

        val paragraphHtml = article.selectFirst("p")?.html().orEmpty()
        assertTrue(paragraphHtml.contains("Rafael Rafailov <span"))
        assertFalse(paragraphHtml.contains("footnotemark:"))
        assertEquals("2", article.selectFirst(".ltx_role_footnotemark sup")?.text())
    }

    @Test
    fun `non breaking space wrappers preserve word spacing`() {
        val article = article(
            """
            <article>
              <p>We can uphold this<span class="widont">&nbsp;</span>statement.</p>
            </article>
            """.trimIndent(),
        )

        HtmlStandardizer.apply(article, title = null)

        assertEquals("We can uphold this statement.", article.selectFirst("p")?.text())
    }

    @Test
    fun `leading standalone time chrome is removed without losing inline times`() {
        val article = article(
            """
            <article>
              <p><i><time datetime="2025-01-15">15 Jan, 2025</time></i></p>
              <p>The event started at <time datetime="2025-01-15T10:00:00Z">10:00 AM</time> and ended later.</p>
            </article>
            """.trimIndent(),
        )

        HtmlStandardizer.apply(article, title = null)

        assertFalse(article.text().contains("15 Jan, 2025"))
        assertTrue(article.text().contains("10:00 AM"))
    }

    @Test
    fun `standalone date read time text chrome is removed`() {
        val article = article(
            """
            <article>
              <div>
                <span>Oct 18, 2019 · 8 min read</span>
              </div>
              <p>The article body remains after opaque metadata text is removed.</p>
            </article>
            """.trimIndent(),
        )

        HtmlStandardizer.apply(article, title = null)

        assertFalse(article.text().contains("8 min read"))
        assertTrue(article.text().contains("The article body remains"))
    }

    @Test
    fun `standalone full date headings are removed as metadata chrome`() {
        val article = article(
            """
            <article>
              <p>Intro text stays.</p>
              <h3>Thursday, May 27, 2004</h3>
              <blockquote><p>Post body stays.</p></blockquote>
            </article>
            """.trimIndent(),
        )

        HtmlStandardizer.apply(article, title = null)

        assertFalse(article.text().contains("Thursday, May 27, 2004"))
        assertTrue(article.text().contains("Intro text stays."))
        assertTrue(article.text().contains("Post body stays."))
    }

    @Test
    fun `direct edge dividers are removed`() {
        val article = article(
            """
            <article>
              <hr>
              <p>Body.</p>
              <hr>
            </article>
            """.trimIndent(),
        )

        HtmlStandardizer.apply(article, title = null)

        assertFalse(article.outerHtml().contains("<hr"))
        assertTrue(article.text().contains("Body."))
    }

    @Test
    fun `leading compact author metadata block is removed before body`() {
        val article = article(
            """
            <article>
              <div class="content-wrapper">
                <a href="/author/jane-smith" class="author-block">
                  <div class="author-name">Jane Smith</div>
                  <div class="post-date">March 25, 2026</div>
                </a>
                <div class="rich-text-block"><p>Body paragraph stays.</p></div>
              </div>
            </article>
            """.trimIndent(),
        )

        HtmlStandardizer.apply(article, title = null)

        assertFalse(article.text().contains("Jane Smith"))
        assertFalse(article.text().contains("March 25, 2026"))
        assertTrue(article.text().contains("Body paragraph stays."))
    }

    @Test
    fun `leading frontmatter author bio section is removed before body`() {
        val article = article(
            """
            <article>
              <section class="frontMatter">
                <p class="abstract"><i>A short abstract paragraph.</i></p>
                <p class="date">07 April 2026</p>
                <hr>
                <div class="author-list">
                  <address class="name"><a href="/jane" rel="author">Jane Doe</a></address>
                  <div class="bio"><p>Jane writes about storage systems.</p></div>
                </div>
              </section>
              <section>
                <p>The actual article body stays.</p>
              </section>
            </article>
            """.trimIndent(),
        )

        HtmlStandardizer.apply(article, title = null)

        assertFalse(article.text().contains("A short abstract paragraph"))
        assertFalse(article.text().contains("Jane Doe"))
        assertTrue(article.text().contains("The actual article body stays."))
    }

    @Test
    fun `leading timezone widget is removed before live article body`() {
        val article = article(
            """
            <article>
              <div class="timezone-widget">
                <div>Current time in</div>
                <div><p><span>City A</span>2:45 a.m. April 8</p></div>
                <div><p><span>City B</span>2:15 a.m. April 8</p></div>
              </div>
              <div role="region" aria-label="Main content">
                <h1>Breaking News Live Updates</h1>
                <p>The actual live article body stays.</p>
              </div>
            </article>
            """.trimIndent(),
        )

        HtmlStandardizer.apply(article, title = null)

        assertFalse(article.text().contains("Current time in"))
        assertFalse(article.text().contains("City A"))
        assertTrue(article.text().contains("The actual live article body stays."))
    }

    @Test
    fun `icon backed pinned live update label is removed`() {
        val article = article(
            """
            <article>
              <section role="feed">
                <div role="article">
                  <div><svg viewBox="0 0 10 13"><path d="M9.1 7.0655"></path></svg> Pinned</div>
                  <h2>Here's the latest.</h2>
                  <p>The actual live update stays.</p>
                </div>
              </section>
            </article>
            """.trimIndent(),
        )

        HtmlStandardizer.apply(article, title = null)

        assertFalse(article.text().contains("Pinned"))
        assertTrue(article.text().contains("Here's the latest."))
        assertTrue(article.text().contains("The actual live update stays."))
    }

    @Test
    fun `trailing resource headings are removed before footnotes`() {
        val article = article(
            """
            <article>
              <p>Body text.</p>
              <h2>Further Reading</h2>
              <p>Resource list prose should be removed.</p>
              <h2>See also</h2>
              <h2>References</h2>
              <section data-footnotes="true"><ol><li id="fn1">Definition.</li></ol></section>
            </article>
            """.trimIndent(),
        )

        HtmlStandardizer.apply(article, title = null)

        assertFalse(article.text().contains("Further Reading"))
        assertFalse(article.text().contains("Resource list prose"))
        assertFalse(article.text().contains("See also"))
        assertFalse(article.text().contains("References"))
        assertTrue(article.text().contains("Definition."))
    }

    @Test
    fun `orphaned trailing next steps heading is removed`() {
        val article = article(
            """
            <article>
              <p>Body text.</p>
              <h2>Next Steps</h2>
            </article>
            """.trimIndent(),
        )

        HtmlStandardizer.apply(article, title = null)

        assertFalse(article.text().contains("Next Steps"))
        assertTrue(article.text().contains("Body text."))
    }

    @Test
    fun `duplicate title removes paragraph eyebrow`() {
        val article = article(
            """
            <article>
              <div>
                <p class="text-lg">Blog post</p>
                <h1>Article Title</h1>
              </div>
              <p>Body.</p>
            </article>
            """.trimIndent(),
        )

        HtmlStandardizer.apply(article, title = "Article Title")

        assertFalse(article.text().contains("Blog post"))
        assertFalse(article.text().contains("Article Title"))
        assertTrue(article.text().contains("Body."))
    }

    @Test
    fun `compact duplicate title metadata wrapper is removed`() {
        val article = article(
            """
            <article>
              <section class="block-HeaderBlock">
                <div class="header-block">
                  <h1>Article Title</h1>
                  <div class="publish-date-block__date">March 19, 2025</div>
                </div>
              </section>
              <p>Body.</p>
            </article>
            """.trimIndent(),
        )

        HtmlStandardizer.apply(article, title = "Article Title - Site Name")

        assertFalse(article.text().contains("Article Title"))
        assertFalse(article.text().contains("March 19, 2025"))
        assertTrue(article.text().contains("Body."))
    }

    @Test
    fun `remaining h1 headings are demoted to h2`() {
        val article = article("""<article><h1>Section Title</h1><p>Body.</p></article>""")

        HtmlStandardizer.apply(article, title = "Different document title")

        assertFalse(article.outerHtml().contains("<h1>Section Title</h1>"))
        assertTrue(article.outerHtml().contains("<h2>Section Title</h2>"))
    }

    @Test
    fun `leading title image is removed when alt duplicates metadata title`() {
        val article = article(
            """
            <article>
              <a href="/"><img src="/title.gif" alt="Article Title"></a>
              <p>Body text stays.</p>
              <img src="/diagram.png" alt="Useful diagram">
            </article>
            """.trimIndent(),
        )

        HtmlStandardizer.apply(article, title = "Article Title")

        assertFalse(article.outerHtml().contains("title.gif"))
        assertTrue(article.outerHtml().contains("diagram.png"))
        assertTrue(article.text().contains("Body text stays."))
    }

    @Test
    fun `code language retained and line numbers removed`() {
        val article = article(
            """
            <article>
              <pre class="highlight language-kotlin"><span class="lineno">1</span><code><span class="line">val answer = 42</span></code></pre>
            </article>
            """.trimIndent(),
        )

        HtmlStandardizer.apply(article, title = null)

        val code = article.selectFirst("pre > code")
        assertNotNull(code)
        assertEquals("kotlin", code.attr("data-lang"))
        assertTrue(code.hasClass("language-kotlin"))
        assertFalse(article.text().contains("1val"))
        assertTrue(code.text().contains("val answer = 42"))
    }

    @Test
    fun `code language prefers code element over wrapper class`() {
        val article = article(
            """
            <article>
              <div class="highlight">
                <pre class="chroma"><code class="language-go" data-lang="go">package main</code></pre>
              </div>
            </article>
            """.trimIndent(),
        )

        HtmlStandardizer.apply(article, title = null)

        assertEquals("go", article.selectFirst("pre > code")?.attr("data-lang"))
    }

    @Test
    fun `medium code pre class is preferred over layout wrapper classes`() {
        val article = article(
            """
            <article>
              <div class="dd ah ai">
                <pre class="hm hn ho hp hq jx jy cf"><span>node --prof app.js</span></pre>
              </div>
            </article>
            """.trimIndent(),
        )

        HtmlStandardizer.apply(article, title = null)

        assertEquals("c", article.selectFirst("pre > code")?.attr("data-lang"))
    }

    @Test
    fun `highlight tables are converted to code blocks`() {
        val article = article(
            """
            <article>
              <figure class="highlight cpp">
                <table><tr>
                  <td class="gutter"><pre>1</pre></td>
                  <td class="code"><pre><span>int main() {</span><br><span>  return 0;</span><br><span>}</span></pre></td>
                </tr></table>
              </figure>
            </article>
            """.trimIndent(),
        )

        HtmlStandardizer.apply(article, title = null)

        val code = article.selectFirst("pre > code")
        assertEquals("cpp", code?.attr("data-lang"))
        assertEquals("int main() {\n  return 0;\n}", code?.wholeText())
        assertFalse(article.outerHtml().contains("gutter"))
    }

    @Test
    fun `highlight code line spans preserve spaces without double line breaks`() {
        val article = article(
            """
            <article>
              <figure class="highlight cpp">
                <table><tr>
                  <td class="gutter"><pre><span class="line">1</span><br><span class="line">2</span><br><span class="line">3</span><br></pre></td>
                  <td class="code"><pre><span class="line"><span class="type">int</span> <span class="title">main</span>() {</span><br><span class="line">  <span class="keyword">return</span> <span class="number">0</span>;</span><br><span class="line">}</span><br></pre></td>
                </tr></table>
              </figure>
            </article>
            """.trimIndent(),
        )

        HtmlStandardizer.apply(article, title = null)

        assertEquals("int main() {\n  return 0;\n}", article.selectFirst("pre > code")?.wholeText())
    }

    @Test
    fun `verso lean examples merge command and output fragments`() {
        val article = article(
            """
            <article>
              <div class="example">
                <code class="hl lean block" data-lean-context="examples"><span class="has-info information"><span class="hover-container"><span class="hover-info messages"><code class="verso-message information"><span class="highlighted"><span class="const token">Nat</span><span class="inter-text"> : Type</span></span></code></span></span><a href="#"><span class="keyword token">#check</span></a></span><span class="inter-text"> </span><span class="const token">Nat</span></code>
                <pre class="hl lean lean-output information"><span class="verso-message"><span class="highlighted"><span class="const token">Nat</span><span class="inter-text"> : Type</span></span></span></pre>
                <code class="hl lean block" data-lean-context="examples"><span class="has-info information"><span class="hover-container"><span class="hover-info messages"><code class="verso-message information"><span class="highlighted"><span class="const token">Bool</span><span class="inter-text"> : Type</span></span></code></span></span><a href="#"><span class="keyword token">#check</span></a></span><span class="inter-text"> </span><span class="const token">Bool</span></code>
                <pre class="hl lean lean-output information"><span class="verso-message"><span class="highlighted"><span class="const token">Bool</span><span class="inter-text"> : Type</span></span></span></pre>
              </div>
            </article>
            """.trimIndent(),
        )

        HtmlStandardizer.apply(article, title = null)

        val code = article.selectFirst("pre > code")
        assertEquals("lean", code?.attr("data-lang"))
        assertEquals("#check Nat\nNat : Type\n#check Bool\nBool : Type", code?.wholeText())
        assertEquals(1, article.select("pre > code").size)
    }

    @Test
    fun `verso lean examples preserve blank fragments between commands`() {
        val article = article(
            """
            <article>
              <div class="example">
                <code class="hl lean block" data-lean-context="examples"><span class="inter-text">#check true</span></code>
                <pre class="hl lean lean-output information"><span class="verso-message"><span class="highlighted"><span class="inter-text">Bool.true : Bool</span></span></span></pre>
                <code class="hl lean block" data-lean-context="examples"><span class="inter-text">
            </span></code>
                <code class="hl lean block" data-lean-context="examples"><span class="inter-text">/- Evaluate -/

            #eval 5 * 4</span></code>
                <pre class="hl lean lean-output information"><span class="verso-message"><span class="highlighted"><span class="inter-text">20</span></span></span></pre>
              </div>
            </article>
            """.trimIndent(),
        )

        HtmlStandardizer.apply(article, title = null)

        assertEquals(
            """
            #check true
            Bool.true : Bool

            /- Evaluate -/

            #eval 5 * 4
            20
            """.trimIndent(),
            article.selectFirst("pre > code")?.wholeText(),
        )
    }

    @Test
    fun `code mirror blocks keep only editor content`() {
        val article = article(
            """
            <article>
              <pre>
                <div class="sticky"><div>Python</div><button>Run</button></div>
                <div class="cm-content"><span>print</span><span>("hello")</span><br><span>print("bye")</span></div>
              </pre>
            </article>
            """.trimIndent(),
        )

        HtmlStandardizer.apply(article, title = null)

        val code = article.selectFirst("pre > code")
        assertEquals("python", code?.attr("data-lang"))
        assertEquals("print(\"hello\")\nprint(\"bye\")", code?.wholeText())
        assertFalse(article.text().contains("Run"))
    }

    @Test
    fun `code text collapses excess blank lines after ui removal`() {
        val article = article(
            """
            <article>
              <pre><code><span class="line-number">1</span><span>first()</span><br><br><br><span>second()</span></code></pre>
            </article>
            """.trimIndent(),
        )

        HtmlStandardizer.apply(article, title = null)

        val code = article.selectFirst("pre > code")
        assertEquals("first()\n\nsecond()", code?.wholeText())
    }

    @Test
    fun `generic code wrapper classes are not emitted as languages`() {
        val article = article(
            """
            <article>
              <div class="markdown-body">
                <pre class="highlight-wrap"><code class="plaintext">plain text</code></pre>
                <div class="problem-description"><pre><code>Input: s = "babad"</code></pre></div>
                <div class="problem-content"><pre><code>Output: "bab"</code></pre></div>
                <div class="page-container"><pre><code>Explanation: valid answer.</code></pre></div>
                <article class="typeset"><pre><code>Sample output.</code></pre></article>
                <div class="mx-auto max-w-2xl py-24"><pre><code>Another sample output.</code></pre></div>
                <div class="container"><pre><code>Container sample output.</code></pre></div>
              </div>
            </article>
            """.trimIndent(),
        )

        HtmlStandardizer.apply(article, title = null)

        val codeBlocks = article.select("pre > code")
        assertEquals("", codeBlocks[0]?.attr("data-lang"))
        assertFalse(codeBlocks[0]?.hasClass("language-plaintext") == true)
        assertEquals("plain text", codeBlocks[0]?.wholeText())
        assertEquals("", codeBlocks[1]?.attr("data-lang"))
        assertFalse(codeBlocks[1]?.hasClass("language-problem-description") == true)
        assertEquals("", codeBlocks[2]?.attr("data-lang"))
        assertFalse(codeBlocks[2]?.hasClass("language-problem-content") == true)
        assertEquals("", codeBlocks[3]?.attr("data-lang"))
        assertFalse(codeBlocks[3]?.hasClass("language-page-container") == true)
        assertEquals("", codeBlocks[4]?.attr("data-lang"))
        assertFalse(codeBlocks[4]?.hasClass("language-typeset") == true)
        assertEquals("", codeBlocks[5]?.attr("data-lang"))
        assertFalse(codeBlocks[5]?.hasClass("language-mx-auto") == true)
        assertEquals("", codeBlocks[6]?.attr("data-lang"))
        assertFalse(codeBlocks[6]?.hasClass("language-container") == true)
    }

    @Test
    fun `font mono code wrapper class is not emitted as language`() {
        val article = article(
            """
            <article>
              <code class="text-code-snippet font-mono"><pre><div class="line">plain</div></pre></code>
            </article>
            """.trimIndent(),
        )

        HtmlStandardizer.apply(article, title = null)

        val code = article.selectFirst("pre > code")
        assertEquals("", code?.attr("data-lang"))
        assertFalse(code?.hasClass("language-font-mono") == true)
    }

    @Test
    fun `code toolbar sibling is removed`() {
        val article = article(
            """
            <article>
              <div class="code-wrapper">
                <div class="code-toolbar"><span>java</span><button>Copy</button></div>
                <div><pre><code class="language-java">class BuildConfig {}</code></pre></div>
              </div>
            </article>
            """.trimIndent(),
        )

        HtmlStandardizer.apply(article, title = null)

        assertFalse(article.text().contains("Copy"))
        assertFalse(article.text().lines().any { it.trim() == "java" })
        assertEquals("java", article.selectFirst("pre > code")?.attr("data-lang"))
    }

    @Test
    fun `standalone preformatted code is wrapped in pre`() {
        val article = article(
            """
            <article>
              <div><code style="white-space: pre">first${"\n"}second</code></div>
            </article>
            """.trimIndent(),
        )

        HtmlStandardizer.apply(article, title = null)

        assertEquals("first\nsecond", article.selectFirst("pre > code")?.wholeText())
    }

    @Test
    fun `writerside data language code blocks are wrapped in pre`() {
        val article = article(
            """
            <article>
              <div class="code-block" data-lang="http">GET https://api.example.com/items

            > {%
                console.log(response.status)
            %}</div>
            </article>
            """.trimIndent(),
        )

        HtmlStandardizer.apply(article, title = null)

        val code = article.selectFirst("pre > code")
        assertEquals("http", code?.attr("data-lang"))
        assertEquals(
            """
            GET https://api.example.com/items

            > {%
                console.log(response.status)
            %}
            """.trimIndent(),
            code?.wholeText(),
        )
    }

    @Test
    fun `flex row code gutters are removed while line breaks are preserved`() {
        val article = article(
            """
            <article>
              <pre class="flex flex-col">
                <div class="flex flex-row"><span class="text-end">1</span><div class="flex-1">one</div></div>
                <div class="flex flex-row"><span class="text-end">2</span><div class="flex-1">two</div></div>
              </pre>
            </article>
            """.trimIndent(),
        )

        HtmlStandardizer.apply(article, title = null)

        assertEquals("one\ntwo", article.selectFirst("pre > code")?.wholeText())
    }

    @Test
    fun `expressive code line wrappers remove gutter and preserve logical lines`() {
        val article = article(
            """
            <article>
              <pre data-language="kotlin"><code>
                <div class="ec-line">
                  <div class="gutter"><div class="ln" aria-hidden="true">1</div></div>
                  <div class="code"><span>tasks.withType&lt;KotlinCompile&gt;().configureEach {</span></div>
                </div>
                <div class="ec-line">
                  <div class="gutter"><div class="ln" aria-hidden="true">2</div></div>
                  <div class="code"><span class="indent">    </span><span>kotlinOptions {</span></div>
                </div>
                <div class="ec-line">
                  <div class="gutter"><div class="ln" aria-hidden="true">3</div></div>
                  <div class="code"><span class="indent">        </span><span>freeCompilerArgs = args</span></div>
                </div>
                <div class="ec-line">
                  <div class="gutter"><div class="ln" aria-hidden="true">4</div></div>
                  <div class="code"><span class="indent">    </span><span>}</span></div>
                </div>
                <div class="ec-line">
                  <div class="gutter"><div class="ln" aria-hidden="true">5</div></div>
                  <div class="code"><span>}</span></div>
                </div>
              </code></pre>
            </article>
            """.trimIndent(),
        )

        HtmlStandardizer.apply(article, title = null)

        assertEquals(
            """
            tasks.withType<KotlinCompile>().configureEach {
                kotlinOptions {
                    freeCompilerArgs = args
                }
            }
            """.trimIndent(),
            article.selectFirst("pre > code")?.wholeText(),
        )
    }

    @Test
    fun `lazy image promoted and figure caption preserved`() {
        val article = article(
            """
            <article>
              <figure>
                <img src="data:image/svg+xml;base64,placeholder" data-src="/real.png" data-srcset="/real.png 1x, /real@2x.png 2x">
                <figcaption>Useful caption.</figcaption>
              </figure>
            </article>
            """.trimIndent(),
        )

        HtmlStandardizer.apply(article, title = null)

        val image = article.selectFirst("img")
        assertEquals("/real.png", image?.attr("src"))
        assertEquals("/real.png 1x, /real@2x.png 2x", image?.attr("srcset"))
        assertTrue(article.outerHtml().contains("<figcaption>Useful caption.</figcaption>"))
    }

    @Test
    fun `gallery image lists lose list semantics while preserving media wrappers`() {
        val article = article(
            """
            <article>
              <div class="article-gallery">
                <ul class="splide__list">
                  <li class="splide__slide"><picture><img src="/one.jpg" alt="One"></picture></li>
                  <li class="splide__slide"><picture><img src="/two.jpg" alt="Two"></picture></li>
                </ul>
                <ul class="splide__list swiper-wrapper">
                  <li class="splide__slide placeholder"><div class="body-img responsive-img"></div></li>
                  <li class="splide__slide placeholder"><div class="body-img responsive-img"></div></li>
                </ul>
              </div>
            </article>
            """.trimIndent(),
        )

        HtmlStandardizer.apply(article, title = null)

        assertEquals(0, article.select("ul, ol, li").size)
        assertEquals(2, article.select(".splide__list > .splide__slide > picture > img").size)
        assertEquals(0, article.select(".placeholder").size)
    }

    @Test
    fun `ordinary image lists preserve list semantics`() {
        val article = article(
            """
            <article>
              <ul class="examples">
                <li><img src="/one.jpg" alt="One"></li>
                <li><img src="/two.jpg" alt="Two"></li>
              </ul>
            </article>
            """.trimIndent(),
        )

        HtmlStandardizer.apply(article, title = null)

        assertEquals(1, article.select("ul").size)
        assertEquals(2, article.select("ul > li > img").size)
    }

    @Test
    fun `legacy WordPress image caption is normalized to semantic figure markup`() {
        val article = article(
            """
            <article>
              <div id="attachment_42" class="wp-caption alignnone">
                <img src="/photo.jpg" alt="" width="1200" height="675">
                <p class="wp-caption-text" style="--width: 100%">Useful caption.</p>
              </div>
            </article>
            """.trimIndent(),
        )

        HtmlStandardizer.apply(article, title = null)

        val figure = article.selectFirst("figure.wp-caption")
        assertNotNull(figure)
        assertNotNull(figure.selectFirst("img"))
        assertEquals("Useful caption.", figure.selectFirst("figcaption.wp-caption-text")?.text())
        assertTrue(article.select("p.wp-caption-text").isEmpty())
    }

    @Test
    fun `image aspect placeholder padding is removed from responsive wrappers`() {
        val article = article(
            """
            <article>
              <div class="body-img landscape">
                <div class="responsive-img image-expandable img-article-item" style="padding-bottom:59.504132231405%">
                  <figure>
                    <picture>
                      <img src="/myfitnesspal.jpg" width="825" height="491" alt="Images highlighting the app">
                    </picture>
                    <small class="body-img-caption">Credit: MyFitnessPal</small>
                  </figure>
                </div>
              </div>
              <p>Following paragraph stays close.</p>
            </article>
            """.trimIndent(),
        )

        HtmlStandardizer.apply(article, title = null)

        val wrapper = article.selectFirst(".responsive-img")
        assertNotNull(wrapper)
        assertFalse(wrapper.hasAttr("style"), article.outerHtml())
        assertTrue(article.outerHtml().contains("Following paragraph stays close."))
    }

    @Test
    fun `browser managed fill image positioning style is removed`() {
        val article = article(
            """
            <article>
              <img
                src="/next-fill.jpg"
                alt="Next image"
                data-nimg="fill"
                style="position:absolute;height:100%;width:100%;left:0;top:0;color:transparent;background-size:cover"
              >
            </article>
            """.trimIndent(),
        )

        HtmlStandardizer.apply(article, title = null)

        val image = article.selectFirst("img")
        assertNotNull(image)
        assertFalse(image.hasAttr("style"), article.outerHtml())
        assertEquals("/next-fill.jpg", image.attr("src"))
    }

    @Test
    fun `non placeholder image padding style is preserved`() {
        val article = article(
            """
            <article>
              <div class="photo-callout" style="padding-bottom: 1rem; color: red">
                <img src="/inline.png" alt="Inline image">
              </div>
            </article>
            """.trimIndent(),
        )

        HtmlStandardizer.apply(article, title = null)

        assertEquals("padding-bottom: 1rem; color: red", article.selectFirst(".photo-callout")?.attr("style"))
    }

    @Test
    fun `image aspect placeholder padding is removed while other style declarations stay`() {
        val article = article(
            """
            <article>
              <picture class="hero-image" style="padding-top:56.25%; aspect-ratio: 1920 / 1080">
                <source srcset="/hero.webp">
                <img src="/hero.jpg" alt="Hero image">
              </picture>
            </article>
            """.trimIndent(),
        )

        HtmlStandardizer.apply(article, title = null)

        assertEquals("aspect-ratio: 1920 / 1080", article.selectFirst("picture")?.attr("style"))
    }

    @Test
    fun `placeholder image uses picture source srcset`() {
        val article = article(
            """
            <article>
              <picture>
                <source srcSet="/hero.webp 2x, /hero-small.webp 1x">
                <img src="data:image/gif;base64,placeholder" srcset="/hero.jpg 2x, /hero-small.jpg 1x" alt="Hero">
              </picture>
            </article>
            """.trimIndent(),
        )

        HtmlStandardizer.apply(article, title = null)

        val image = article.selectFirst("img")
        assertEquals("/hero.webp 2x, /hero-small.webp 1x", image?.attr("srcset"))
    }

    @Test
    fun `placeholder image uses sibling noscript image source`() {
        val article = article(
            """
            <article>
              <span>
                <img alt="Architecture diagram." src="data:image/gif;base64,placeholder" data-nimg="intrinsic">
                <noscript>
                  <img alt="Architecture diagram." srcset="/images/architecture.png?imwidth=3840 1x" src="/images/architecture.png?imwidth=3840">
                </noscript>
              </span>
            </article>
            """.trimIndent(),
        )

        HtmlStandardizer.apply(article, title = null)

        val image = article.selectFirst("img")
        assertEquals("/images/architecture.png?imwidth=3840", image?.attr("src"))
        assertEquals("/images/architecture.png?imwidth=3840 1x", image?.attr("srcset"))
        assertTrue(article.outerHtml().contains(">Architecture diagram."), article.outerHtml())
        assertFalse(article.outerHtml().contains("<noscript>"))
    }

    @Test
    fun `placeholder noscript image variant is removed when preview image already exists`() {
        val article = article(
            """
            <article>
              <figure>
                <div>
                  <img src="https://cdn.example.com/max/60/hero.png?q=20" width="1200" height="800" role="presentation">
                </div>
                <img width="1200" height="800" role="presentation">
                <noscript>
                  <img src="https://cdn.example.com/max/2400/hero.png" width="1200" height="800" role="presentation">
                </noscript>
                <figcaption>Hero caption.</figcaption>
              </figure>
            </article>
            """.trimIndent(),
        )

        HtmlStandardizer.apply(article, title = null)

        assertEquals(1, article.select("img").size)
        assertEquals("https://cdn.example.com/max/60/hero.png?q=20", article.selectFirst("img")?.attr("src"))
        assertFalse(article.outerHtml().contains("<noscript>"))
    }

    @Test
    fun `unresolved placeholder images are removed while data image loader is promoted`() {
        val article = article(
            """
            <article>
              <img src="data:image/gif;base64,placeholder" alt="Unresolved">
              <img src="data:image/svg+xml,%3Csvg%3E" data-image-loader="/real.png" alt="Resolved">
            </article>
            """.trimIndent(),
        )

        HtmlStandardizer.apply(article, title = null)

        assertFalse(article.outerHtml().contains("Unresolved"))
        assertEquals("/real.png", article.selectFirst("img")?.attr("src"))
    }

    @Test
    fun `callout normalized`() {
        val article = article("""<article><blockquote><p>[!NOTE]</p><p>Remember this.</p></blockquote></article>""")

        HtmlStandardizer.apply(article, title = null)

        val callout = article.selectFirst(".callout")
        assertEquals("note", callout?.attr("data-callout"))
        assertTrue(article.outerHtml().contains("callout-title-inner"))
        assertTrue(article.outerHtml().contains("Remember this."))
    }

    @Test
    fun `bootstrap alerts normalize to callouts`() {
        val article = article(
            """
            <article>
              <div class="alert alert-warning">
                <p>This is a warning without a title.</p>
              </div>
            </article>
            """.trimIndent(),
        )

        HtmlStandardizer.apply(article, title = null)

        val callout = article.selectFirst(".callout")
        assertEquals("warning", callout?.attr("data-callout"))
        assertEquals("Warning", callout?.selectFirst(".callout-title-inner")?.text())
        assertTrue(callout?.selectFirst(".callout-content")?.text()?.contains("warning without a title") == true)
    }

    @Test
    fun `hugo admonitions normalize to callouts`() {
        val article = article(
            """
            <article>
              <div class="details admonition tip open">
                <div class="details-summary admonition-title"><span class="icon"></span>Helpful tip</div>
                <div class="details-content"><div class="admonition-content"><p>Use this shortcut.</p></div></div>
              </div>
            </article>
            """.trimIndent(),
        )

        HtmlStandardizer.apply(article, title = null)

        val callout = article.selectFirst(".callout")
        assertEquals("tip", callout?.attr("data-callout"))
        assertEquals("Helpful tip", callout?.selectFirst(".callout-title-inner")?.text())
        assertTrue(callout?.selectFirst(".callout-content")?.text()?.contains("Use this shortcut.") == true)
    }

    @Test
    fun `simple footnote normalized`() {
        val article = article("""<article><ol class="footnotes"><li id="fn1">Footnote text</li></ol></article>""")

        HtmlStandardizer.apply(article, title = null)

        val footnotes = article.selectFirst("section[data-footnotes]")
        assertNotNull(footnotes)
        assertTrue(footnotes.text().contains("Footnote text"))
    }

    @Test
    fun `substack footnotes normalize to references and definitions`() {
        val article = article(
            """
            <article>
              <p>Body<a data-component-name="FootnoteAnchorToDOM" id="footnote-anchor-1-abc" href="https://example.com/p/post#footnote-1-abc" class="footnote-anchor">1</a> continues.</p>
              <div data-component-name="FootnoteToDOM" class="footnote">
                <a id="footnote-1-abc" href="https://example.com/p/post#footnote-anchor-1-abc" class="footnote-number">1</a>
                <div class="footnote-content"><p>Definition text.</p></div>
              </div>
            </article>
            """.trimIndent(),
        )

        HtmlStandardizer.apply(article, title = null)

        val reference = article.selectFirst("sup > a.footnote-anchor")
        assertNotNull(reference)
        assertEquals("#footnote-1-abc", reference.attr("href"))
        assertEquals("footnote-1-abc", article.selectFirst("section[data-footnotes] li")?.id())
        assertEquals("Definition text.", article.selectFirst("section[data-footnotes] li")?.text())
        assertTrue(article.select("[data-component-name=FootnoteToDOM]").isEmpty())
    }

    @Test
    fun `inline footnote containers normalize to references and definitions`() {
        val article = article(
            """
            <article>
              <p>Body text<span class="footnote-container">
                <label for="1" class="margin-toggle footnote-number"></label>
                <input type="checkbox" id="1" class="margin-toggle">
                <span class="footnote">Definition <a href="https://example.com">source</a>.</span>
              </span> continues.</p>
            </article>
            """.trimIndent(),
        )

        HtmlStandardizer.apply(article, title = null)

        val reference = article.selectFirst("sup > a")
        assertEquals("#fn1", reference?.attr("href"))
        assertEquals("1", reference?.text())
        assertEquals("fn1", article.selectFirst("section[data-footnotes] li")?.id())
        assertEquals("Definition source.", article.selectFirst("section[data-footnotes] li")?.text())
    }

    @Test
    fun `wikidot footnotes normalize javascript references and footer definitions`() {
        val article = article(
            """
            <article>
              <p>Body<sup class="footnoteref"><a id="footnoteref-1" class="footnoteref">1</a></sup> text.</p>
              <div class="footnotes-footer">
                <div class="title">Footnotes</div>
                <div class="footnote-footer" id="footnote-1"><a>1</a>. Definition text.</div>
              </div>
            </article>
            """.trimIndent(),
        )

        HtmlStandardizer.apply(article, title = null)

        assertEquals("#footnote-1", article.selectFirst("sup.footnoteref a")?.attr("href"))
        assertEquals("footnote-1", article.selectFirst("section[data-footnotes] li")?.id())
        assertEquals("Definition text.", article.selectFirst("section[data-footnotes] li")?.text())
        assertTrue(article.select(".footnotes-footer").isEmpty())
    }

    @Test
    fun `dhammatalks note blocks normalize to footnotes after see also`() {
        val article = article(
            """
            <article>
              <p>Body text.<span class="fn"><a href="#mn37note01">1</a></span></p>
              <p>Another sentence.<span class="fn"><a href="#mn37note02">2</a></span></p>
              <div class="note">
                <p class="notetitle">Notes</p>
                <p id="mn37note01">1. First definition.</p>
                <p id="mn37note02">2. Second definition starts.</p>
                <p>Second definition continues <a href="/tail">tail</a>.</p>
              </div>
              <p class="seealso">See also: <a href="/suttas/SN/SN27_1.html">SN 27:1-10</a></p>
            </article>
            """.trimIndent(),
        )

        HtmlStandardizer.apply(article, title = null)

        assertEquals("#mn37note01", article.selectFirst("sup a")?.attr("href"))
        assertEquals("seealso", article.selectFirst("section[data-footnotes]")?.previousElementSibling()?.className())
        val items = article.select("section[data-footnotes] li")
        assertEquals("mn37note01", items.get(0).id())
        assertEquals("First definition.", items.get(0).text())
        assertEquals("mn37note02", items.get(1).id())
        assertEquals("Second definition starts. Second definition continues tail", items.get(1).text())
        assertFalse(items.get(1).html().contains("</a>."))
        assertTrue(article.select("div.note, .notetitle").isEmpty())
    }

    @Test
    fun `aside footnote lists normalize to footnote section with start ids`() {
        val article = article(
            """
            <article>
              <p>Text<sup class="aside-link">2</sup></p>
              <aside><ol start="2"><li>Aside definition.</li></ol></aside>
            </article>
            """.trimIndent(),
        )

        HtmlStandardizer.apply(article, title = null)

        val item = article.selectFirst("section[data-footnotes] li")
        assertEquals("fn2", item?.id())
        assertEquals("Aside definition.", item?.text())
        assertFalse(article.outerHtml().contains("<ol"))
    }

    @Test
    fun `loose bold sup footnotes preserve label and trailing content`() {
        val article = article(
            """
            <article>
              <p>Body text.<sup>1</sup></p>
              <p><b><sup>1</sup> Note 2024-01-15:</b> First definition.</p>
              <p><b><sup>2</sup> Note 2024-01-10:</b> Second definition.</p>
              <p><b>Update 2024-06-01:</b> Keep this in the article.</p>
            </article>
            """.trimIndent(),
        )

        HtmlStandardizer.apply(article, title = null)

        assertTrue(article.text().contains("Body text."))
        assertTrue(article.text().contains("Keep this in the article."))
        assertEquals("fn1", article.selectFirst("section[data-footnotes] li")?.id())
        val footnoteText = article.selectFirst("section[data-footnotes]")?.text().orEmpty()
        assertTrue(footnoteText.contains("Note 2024-01-15: First definition."))
        assertFalse(footnoteText.contains("Update 2024-06-01"))
    }

    @Test
    fun `reference lists with child anchors normalize to footnotes`() {
        val article = article(
            """
            <article>
              <p>Research <a href="#r1">[1]</a>.</p>
              <h4>References and notes</h4>
              <ol><li><a id="r1"></a>Reference text.</li></ol>
            </article>
            """.trimIndent(),
        )

        HtmlStandardizer.apply(article, title = null)

        val item = article.selectFirst("section[data-footnotes] li")
        assertEquals("r1", item?.id())
        assertEquals("Reference text.", item?.text())
        assertTrue(article.outerHtml().contains("<h4>References and notes</h4>"))
    }

    @Test
    fun `named anchor footnote paragraphs normalize to definitions`() {
        val article = article(
            """
            <article>
              <p>Body.<a name="FnAnchor_1" href="#Footnote_1"><span class="fn">1</span></a></p>
              <div class="note">
                <p><a name="Footnote_1" href="#FnAnchor_1"><span class="fn">1</span></a> Footnote text.</p>
              </div>
            </article>
            """.trimIndent(),
        )

        HtmlStandardizer.apply(article, title = null)

        val item = article.selectFirst("section[data-footnotes] li")
        assertEquals("Footnote_1", item?.id())
        assertEquals("Footnote text.", item?.text())
    }

    @Test
    fun `reference div blocks normalize to footnotes`() {
        val article = article(
            """
            <article>
              <p>Research <a href="#ref1">[1]</a>.</p>
              <div class="references">
                <h3>References</h3>
                <div class="reference">
                  <a class="reference-number" id="ref1">1.</a>
                  <div class="reference-content">Reference text.</div>
                </div>
              </div>
            </article>
            """.trimIndent(),
        )

        HtmlStandardizer.apply(article, title = null)

        val item = article.selectFirst("section[data-footnotes] li")
        assertEquals("ref1", item?.id())
        assertEquals("Reference text.", item?.text())
        assertFalse(article.outerHtml().contains("""class="references""""))
    }

    @Test
    fun `sidenote spans are removed after footnote references`() {
        val article = article(
            """
            <article>
              <p>Text<sup class="footnote-reference"><a href="#footnote-1">1</a></sup><span class="sidenote">Duplicate sidenote.</span> continues.</p>
              <div class="footnotes"><div class="footnote-definition"><sup id="footnote-1"><a href="#ref">1</a></sup><div><p>Real definition.</p></div></div></div>
            </article>
            """.trimIndent(),
        )

        HtmlStandardizer.apply(article, title = null)

        assertFalse(article.text().contains("Duplicate sidenote."))
        assertEquals("footnote-1", article.selectFirst("section[data-footnotes] li")?.id())
    }

    @Test
    fun `org mode footrefs and footdefs normalize to footnotes`() {
        val article = article(
            """
            <article>
              <p>Text<label class="footref" for="fn.1">1</label><input id="fn.1"><span class="sidenote">Inline copy.</span></p>
              <div class="footdef"><sup><a id="fn.1" class="footnum" href="#fnr.1">1</a></sup><div class="footpara" role="doc-footnote"><p>Definition text.</p></div></div>
            </article>
            """.trimIndent(),
        )

        HtmlStandardizer.apply(article, title = null)

        assertFalse(article.text().contains("Inline copy."))
        assertEquals("fn.1", article.selectFirst("section[data-footnotes] li")?.id())
        assertEquals("Definition text.", article.selectFirst("section[data-footnotes] li")?.text())
    }

    @Test
    fun `footnotes wrapper lists normalize and preserve wrapper dividers`() {
        val article = article(
            """
            <article>
              <p>Text<span id="fnref:calibration"><a href="#fn:calibration">1</a></span></p>
              <div class="footnotes"><hr><ol><li id="fn:calibration">Definition text.</li></ol></div>
            </article>
            """.trimIndent(),
        )

        HtmlStandardizer.apply(article, title = null)

        assertEquals("fn:calibration", article.selectFirst("section[data-footnotes] li")?.id())
        assertTrue(article.outerHtml().contains("<hr"))
    }

    @Test
    fun `footnote separator class dividers are removed`() {
        val article = article(
            """
            <article>
              <p>Text<span id="fnref:calibration"><a href="#fn:calibration">1</a></span></p>
              <div class="footnotes">
                <hr class="footnotes-separatator">
                <ol><li id="fn:calibration">Definition text.</li></ol>
              </div>
            </article>
            """.trimIndent(),
        )

        HtmlStandardizer.apply(article, title = null)

        assertEquals("fn:calibration", article.selectFirst("section[data-footnotes] li")?.id())
        assertFalse(article.outerHtml().contains("<hr"))
    }

    @Test
    fun `span wrapped word footnotes after divider normalize`() {
        val article = article(
            """
            <article>
              <p>Text <span><sup><a href="#_ftn1">[1]</a></sup></span>.</p>
              <hr>
              <p><span><sup><a href="#_ftnref1">[1]</a></sup>Definition text.</span></p>
            </article>
            """.trimIndent(),
        )

        HtmlStandardizer.apply(article, title = null)

        val item = article.selectFirst("section[data-footnotes] li")
        assertEquals("fn1", item?.id())
        assertEquals("Definition text.", item?.text())
        assertFalse(article.outerHtml().contains("<hr"))
    }

    @Test
    fun `dated edit after divider is not treated as a loose footnote`() {
        val article = article(
            """
            <article>
              <p>Body text.</p>
              <hr>
              <p><strong>2025-09-05 edit:</strong> Updated instructions.</p>
            </article>
            """.trimIndent(),
        )

        HtmlStandardizer.apply(article, title = null)

        assertTrue(article.outerHtml().contains("<hr"))
        assertTrue(article.text().contains("2025-09-05 edit: Updated instructions."))
        assertTrue(article.select("section[data-footnotes]").isEmpty())
    }

    @Test
    fun `simple data table preserved and layout table flattened`() {
        val data = article(
            """<article><table><tr><th>A</th><th>B</th></tr><tr><td>1</td><td>2</td></tr></table></article>""",
        )
        HtmlStandardizer.apply(data, title = null)
        assertNotNull(data.selectFirst("table"))

        val layout = article(
            """<article><table class="layout"><tr><td><p>Layout text.</p></td></tr></table></article>""",
        )
        HtmlStandardizer.apply(layout, title = null)
        assertFalse(layout.outerHtml().contains("<table"))
        assertTrue(layout.text().contains("Layout text."))
    }

    @Test
    fun `nested one cell layout tables are flattened`() {
        val article = article(
            """
            <article>
              <table>
                <tr><td>
                  <table><tr><td><p>Intro quote.</p></td></tr></table>
                  <p>Article paragraph after nested layout.</p>
                </td></tr>
              </table>
            </article>
            """.trimIndent(),
        )

        HtmlStandardizer.apply(article, title = null)

        assertFalse(article.outerHtml().contains("<table"))
        assertTrue(article.text().contains("Intro quote."))
        assertTrue(article.text().contains("Article paragraph after nested layout."))
    }

    @Test
    fun `math data latex and readable fallback are preserved`() {
        val article = article(
            """<article><span class="math" data-latex="x^2"><math><mi>x</mi></math></span></article>""",
        )

        HtmlStandardizer.apply(article, title = null)

        assertTrue(article.outerHtml().contains("""data-latex="x^2""""))
        assertTrue(article.outerHtml().contains("<math>"))
    }

    @Test
    fun `youtube consent placeholder normalizes to safe iframe`() {
        val article = article(
            """
            <article>
              <p>See the trailer below</p>
              <div class="hidden_video" data-video-id="1hKyYaBzko8">
                <img alt="YouTube Thumbnail" src="/youtube_cache_default.png">
                <div class="hidden_video_content">
                  YouTube videos require cookies, you must accept their cookies to view.
                  <a href="/index.php?module=cookie_prefs">View cookie preferences</a>.
                  <a class="accept_video" data-video-id="1hKyYaBzko8" href="#">Accept Cookies &amp; Show</a>
                  <a href="https://www.youtube.com/watch?v=1hKyYaBzko8">Direct Link</a>
                </div>
              </div>
            </article>
            """.trimIndent(),
        )

        HtmlStandardizer.apply(article, title = null)

        val iframe = article.selectFirst("iframe")
        assertNotNull(iframe)
        assertEquals("https://www.youtube-nocookie.com/embed/1hKyYaBzko8", iframe.attr("src"))
        assertEquals("https://www.youtube.com/watch?v=1hKyYaBzko8", iframe.attr("data-klead-video-url"))
        assertFalse(article.text().contains("YouTube videos require cookies"))
        assertFalse(article.text().contains("Accept Cookies"))
    }

    @Test
    fun `empty wrappers removed without losing text`() {
        val article = article("""<article><div><span>Kept text</span></div><span></span></article>""")

        HtmlStandardizer.apply(article, title = null)

        assertTrue(article.text().contains("Kept text"))
        assertFalse(article.outerHtml().contains("<span></span>"))
    }

    private fun article(html: String): Element =
        Ksoup.parse(html, "https://example.com").selectFirst("article") ?: error("missing article")
}
