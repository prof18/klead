package com.prof18.klead.internal.markdown

import org.jsoup.Jsoup
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class KleadMarkdownWriterTest {
    @Test
    fun `paragraphs headings emphasis strong links and dangerous links render cleanly`() {
        val markdown = render(
            """
            <article>
              <h2>Heading</h2>
              <p>Hello <strong>bold</strong> and <em>em</em> <a href="/path?q=1">link</a> <a href="javascript:bad()">bad</a>.</p>
            </article>
            """.trimIndent(),
        )

        assertEquals(
            """
            ## Heading

            Hello **bold** and *em* [link](https://example.com/path?q=1) bad.
            """.trimIndent() + "\n",
            markdown,
        )
    }

    @Test
    fun `block links around headings render linked heading and plain summary`() {
        val markdown = render(
            """
            <article>
              <a href="/blog/scaling">
                <h3>Scaling Distributed Systems</h3>
                <p>How we redesigned the queue.</p>
              </a>
            </article>
            """.trimIndent(),
        )

        assertEquals(
            """
            ### [Scaling Distributed Systems](https://example.com/blog/scaling)

            How we redesigned the queue.
            """.trimIndent() + "\n",
            markdown,
        )
    }

    @Test
    fun `blank formatting text between inline siblings does not split paragraphs`() {
        val markdown = render(
            """
            <article>
              <div>
                <a href="/blog?tag=infrastructure">Infrastructure</a>
                <span>5 min read</span>
                <span>March 1, 2026</span>
              </div>
            </article>
            """.trimIndent(),
        )

        assertEquals(
            "[Infrastructure](https://example.com/blog?tag=infrastructure) 5 min read March 1, 2026\n",
            markdown,
        )
    }

    @Test
    fun `span data-as paragraph remains a block across formatting whitespace`() {
        val markdown = render(
            """
            <article>
              <span data-as="p">First paragraph.</span>
              <span data-as="p">Second paragraph.</span>
            </article>
            """.trimIndent(),
        )

        assertEquals(
            """
            First paragraph.

            Second paragraph.
            """.trimIndent() + "\n",
            markdown,
        )
    }

    @Test
    fun `persian comma after inline formatting keeps upstream node boundary spacing`() {
        val markdown = render(
            """
            <article>
              <p>از <strong>برنامه‌های سفر</strong>، <strong>فهرست کتاب‌ها</strong> استفاده کنید.</p>
              <p>English <strong>bold</strong>, punctuation stays tight.</p>
            </article>
            """.trimIndent(),
        )

        assertEquals(
            """
            از **برنامه‌های سفر** ، **فهرست کتاب‌ها** استفاده کنید.

            English **bold**, punctuation stays tight.
            """.trimIndent() + "\n",
            markdown,
        )
    }

    @Test
    fun `numbered headings render without ordered list escaping`() {
        val markdown = render("""<article><h2>1. First Item</h2></article>""")

        assertEquals("## 1. First Item\n", markdown)
    }

    @Test
    fun `invisible byte order marks are stripped from text`() {
        val markdown = render("""<article><h2>Methods﻿</h2><p>Body﻿ text.</p></article>""")

        assertEquals("## Methods\n\nBody text.\n", markdown)
    }

    @Test
    fun `link text preserves nested emphasis`() {
        val markdown = render(
            """
            <article>
              <p>Locale<a href="/story"><em> Il Centro</em></a> would link to the <a href="/wideband">the <em>other</em> “wideband”</a>.</p>
            </article>
            """.trimIndent(),
        )

        assertEquals(
            "Locale [*Il Centro*](https://example.com/story) would link to the [the *other* “wideband”](https://example.com/wideband).\n",
            markdown,
        )
    }

    @Test
    fun `placeholder dot tokens render without internal spacing`() {
        val markdown = render(
            """
            <article>
              <p>The author placeholder is ". .". A normal sentence. . keeps its punctuation.</p>
            </article>
            """.trimIndent(),
        )

        assertEquals("The author placeholder is \"..\". A normal sentence. . keeps its punctuation.\n", markdown)
    }

    @Test
    fun `unsafe links unwrap formatted children`() {
        val markdown = render(
            """<article><p>A <a href="javascript:void(0)"><strong>bold js link</strong></a> stays formatted.</p></article>""",
        )

        assertEquals("A **bold js link** stays formatted.\n", markdown)
    }

    @Test
    fun `bare origin links render with root slash`() {
        val markdown = render("""<article><p>See <a href="https://example.com">Example</a>.</p></article>""")

        assertEquals("See [Example](https://example.com/).\n", markdown)
    }

    @Test
    fun `link title attributes render as markdown titles`() {
        val markdown = render(
            """<article><p>Read <a href="/wiki/Markdown" title="Markdown docs">Markdown</a>.</p></article>""",
        )

        assertEquals("Read [Markdown](https://example.com/wiki/Markdown \"Markdown docs\").\n", markdown)
    }

    @Test
    fun `link title matching visible text is omitted`() {
        val markdown = render(
            """<article><p>Read <a href="/wiki/Markdown" title="Markdown"><strong>markdown</strong></a>.</p></article>""",
        )

        assertEquals("Read [**markdown**](https://example.com/wiki/Markdown).\n", markdown)
    }

    @Test
    fun `quoted inline links keep upstream quote spacing`() {
        val markdown = render(
            """
            <article>
              <p>Paper “<a href="/paper">Exploring</a>”, then question “<a href="/question">Relevant?</a>” which follows.</p>
              <p>Metaphor “language is a <a href="/lens">lens</a>”.</p>
            </article>
            """.trimIndent(),
        )

        assertEquals(
            """
            Paper “ [Exploring](https://example.com/paper) ”, then question “ [Relevant?](https://example.com/question)” which follows.

            Metaphor “language is a [lens](https://example.com/lens) ”.
            """.trimIndent() + "\n",
            markdown,
        )
    }

    @Test
    fun `standalone double hyphen blocks are escaped`() {
        val markdown = render("""<article><p>Body.</p><div>--</div><p>Signature.</p></article>""")

        assertEquals("Body.\n\n\\--\n\nSignature.\n", markdown)
    }

    @Test
    fun `mdx paragraph spans render inline content as paragraphs`() {
        val markdown = render(
            """
            <article>
              <span data-as="p">The <code>mode</code> prop controls <strong>layout</strong>.</span>
            </article>
            """.trimIndent(),
        )

        assertEquals("The `mode` prop controls **layout**.\n", markdown)
    }

    @Test
    fun `whitespace only links keep spacing without rendering empty markdown links`() {
        val markdown = render(
            """
            <article>
              <p>AirPods Max 2<a href="/empty">&nbsp;</a><a href="/deal"><strong>$499</strong> (Reg. $549)</a></p>
            </article>
            """.trimIndent(),
        )

        assertEquals("AirPods Max 2 [**$499** (Reg. $549)](https://example.com/deal)\n", markdown)
        assertFalse(markdown.contains("[]("))
        assertTrue(markdown.contains("[**$499**"))
    }

    @Test
    fun `inline code handles embedded backticks`() {
        val markdown = render("""<article><p>Use <code>a `tick` here</code>.</p></article>""")

        assertEquals("Use `` a `tick` here ``.\n", markdown)
    }

    @Test
    fun `inline code preserves nested emphasis markers`() {
        val markdown = render("""<article><p>Use <code>say <em>true</em> when <i>used</i></code>.</p></article>""")

        assertEquals("Use `say *true* when *used*`.\n", markdown)
    }

    @Test
    fun `images choose largest srcset`() {
        val markdown = render(
            """<article><p><img alt="Hero" src="/small.png" srcset="/small.png 320w, /large.png 960w"></p></article>""",
        )

        assertEquals("![Hero](https://example.com/large.png)\n", markdown)
    }

    @Test
    fun `inline images keep source spacing before markdown image marker`() {
        val markdown = render(
            """<article><p>Here is a formula: <img alt="E equals mc squared" src="/formula.png"> in context.</p></article>""",
        )

        assertEquals(
            "Here is a formula: ![E equals mc squared](https://example.com/formula.png) in context.\n",
            markdown,
        )
    }

    @Test
    fun `png lqip data images with alt text render while other data placeholders are skipped`() {
        val markdown = render(
            """
            <article>
              <img alt="Preview" src="data:image/png;base64,AAAA" data-lqip="true">
              <img alt="Placeholder" src="data:image/gif;base64,AAAA">
              <img alt="" src="data:image/png;base64,BBBB" data-lqip="true">
            </article>
            """.trimIndent(),
        )

        assertEquals("![Preview](data:image/png;base64,AAAA)\n", markdown)
    }

    @Test
    fun `paragraph line breaks render as markdown hard breaks with source indentation trimmed`() {
        val markdown = render(
            """
            <article>
              <p>First line<br>
                Second line</p>
              <p>Third line<br><br><strong>Fourth line</strong></p>
            </article>
            """.trimIndent(),
        )

        assertEquals(
            """
            First line%BR%
            Second line

            Third line%BR%
            %BLANK_HARD%
            **Fourth line**
            """.trimIndent()
                .replace("%BR%", "  ")
                .replace("%BLANK_HARD%", "  ") + "\n",
            markdown,
        )
    }

    @Test
    fun `leading line breaks before first content are trimmed`() {
        val markdown = render("""<article><br><br><p>First content.</p></article>""")
        val hardBreakLine = render("""<article>  <br>First content.</article>""")

        assertEquals("First content.\n", markdown)
        assertEquals("First content.\n", hardBreakLine)
    }

    @Test
    fun `list item line breaks render as indented continuations`() {
        val markdown = render(
            """
            <article>
              <ol>
                <li><a href="/story">Story title</a> (example.com)<br>384 points · by dev_user · <a href="/item?id=1">142 comments</a></li>
              </ol>
            </article>
            """.trimIndent(),
        )

        assertEquals(
            "1. [Story title](https://example.com/story) (example.com)  \n\t384 points · by dev\\_user · [142 comments](https://example.com/item?id=1)\n",
            markdown,
        )
    }

    @Test
    fun `list items with sign icons and paragraph bodies stay on one line`() {
        val markdown = render(
            """
            <article>
              <ul>
                <li><span>+</span><p>Great display</p></li>
                <li><span>-</span><div>No local availability</div></li>
                <li>+<p>Fast charging</p></li>
              </ul>
            </article>
            """.trimIndent(),
        )

        assertEquals(
            """
            - +Great display
            - -No local availability
            - +Fast charging
            """.trimIndent() + "\n",
            markdown,
        )
    }

    @Test
    fun `images keep srcset urls containing commas`() {
        val small = "https://substackcdn.com/image/fetch/${'$'}s_!PiGr!,w_424,c_limit,f_auto,q_auto:good,fl_progressive:steep/https%3A%2F%2Fexample.com%2Fsmall.jpeg"
        val large = "https://substackcdn.com/image/fetch/${'$'}s_!PiGr!,w_1456,c_limit,f_auto,q_auto:good,fl_progressive:steep/https%3A%2F%2Fexample.com%2Flarge.jpeg"
        val markdown = render(
            """
            <article>
              <figure>
                <img alt="" src="$small" srcset="$small 424w, $large 1456w">
                <figcaption>Useful caption.</figcaption>
              </figure>
            </article>
            """.trimIndent(),
        )

        assertTrue(markdown.contains("![]($large)"))
        assertTrue(markdown.contains("*Useful caption.*"))
    }

    @Test
    fun `adjacent standalone images render on one line`() {
        val markdown = render(
            """
            <article>
              <img src="/one.jpg" alt="One">
              <img src="/two.jpg" alt="Two">
            </article>
            """.trimIndent(),
        )

        assertEquals(
            "![One](https://example.com/one.jpg) ![Two](https://example.com/two.jpg)\n",
            markdown,
        )
    }

    @Test
    fun `block image wrappers render image before caption text`() {
        val markdown = render(
            """
            <article>
              <div class="wp-caption alignnone">
                <img src="/photo.jpg" alt="" width="980" height="653">
                <p class="wp-caption-text">Useful caption.</p>
              </div>
            </article>
            """.trimIndent(),
        )

        assertEquals(
            """
            ![](https://example.com/photo.jpg)

            Useful caption.
            """.trimIndent() + "\n",
            markdown,
        )
    }

    @Test
    fun `text node caption after root image is preserved`() {
        val markdown = render(
            """
            <article>
              <img alt="Architecture diagram." src="/images/architecture.png?imwidth=3840">Architecture diagram.
              <p>And here is a second diagram:</p>
            </article>
            """.trimIndent(),
        )

        assertEquals(
            """
            ![Architecture diagram.](https://example.com/images/architecture.png?imwidth=3840)

            Architecture diagram.

            And here is a second diagram:
            """.trimIndent() + "\n",
            markdown,
        )
    }

    @Test
    fun `caption and credit spans stay separate block lines`() {
        val markdown = render(
            """
            <article>
              <div>
                <span data-cy="caption">A beagle breeding farm in Wisconsin is officially shutting down.</span>
                <span data-cy="credit">Carolyn Cole-Los Angeles</span>
              </div>
            </article>
            """.trimIndent(),
        )

        assertEquals(
            """
            A beagle breeding farm in Wisconsin is officially shutting down.

            Carolyn Cole-Los Angeles
            """.trimIndent() + "\n",
            markdown,
        )
    }

    @Test
    fun `linked image wrappers preserve the image link`() {
        val markdown = render(
            """
            <article>
              <a href="/full.jpg"><img src="/thumb.jpg" alt=""></a>
            </article>
            """.trimIndent(),
        )

        assertEquals("[![](https://example.com/thumb.jpg)](https://example.com/full.jpg)\n", markdown)
    }

    @Test
    fun `linked image followed by inline prose keeps a readable space`() {
        val markdown = render(
            """
            <article>
              <p><a href="/full.jpg"><img src="/thumb.jpg" alt=""></a>Systems come in many forms.</p>
            </article>
            """.trimIndent(),
        )

        assertEquals(
            "[![](https://example.com/thumb.jpg)](https://example.com/full.jpg) Systems come in many forms.\n",
            markdown,
        )
    }

    @Test
    fun `figures deduplicate alternate image sizes before rendering caption`() {
        val markdown = render(
            """
            <article>
              <figure>
                <img src="/portrait-large.webp" alt="">
                <img src="/portrait-small.jpg" alt="">
                <figcaption>Portrait caption.</figcaption>
              </figure>
            </article>
            """.trimIndent(),
        )

        assertEquals(
            """
            ![](https://example.com/portrait-large.webp)

            *Portrait caption.*
            """.trimIndent() + "\n",
            markdown,
        )
    }

    @Test
    fun `figure captions render links as plain text`() {
        val markdown = render(
            """
            <article>
              <figure>
                <img src="/photo.jpg" alt="First gallery image caption.">
                <figcaption>First gallery image caption. <a href="/source">Source One</a></figcaption>
              </figure>
            </article>
            """.trimIndent(),
        )

        assertEquals(
            """
            ![First gallery image caption.](https://example.com/photo.jpg)

            *First gallery image caption. Source One*
            """.trimIndent() + "\n",
            markdown,
        )
    }

    @Test
    fun `figure captions preserve line breaks`() {
        val markdown = render(
            """
            <article>
              <figure>
                <img src="/photo.jpg" alt="Funicular photo">
                <figcaption>Funicular photo<br>(Photo credit)</figcaption>
              </figure>
            </article>
            """.trimIndent(),
        )

        assertEquals(
            """
            ![Funicular photo](https://example.com/photo.jpg)

            *Funicular photo
            (Photo credit)*
            """.trimIndent() + "\n",
            markdown,
        )
    }

    @Test
    fun `figures keep distinct images with repeated alt text`() {
        val markdown = render(
            """
            <article>
              <figure>
                <img src="/gallery-photo-1.jpg" alt="gallery photo">
                <img src="/gallery-photo-3.jpg" alt="gallery photo">
              </figure>
            </article>
            """.trimIndent(),
        )

        assertEquals(
            """
            ![gallery photo](https://example.com/gallery-photo-1.jpg)
            ![gallery photo](https://example.com/gallery-photo-3.jpg)
            """.trimIndent() + "\n",
            markdown,
        )
    }

    @Test
    fun `figures wrapping article body render prose around nested image figures`() {
        val markdown = render(
            """
            <article>
              <figure class="paragraph-image">
                <div class="content-wrapper">
                  <p>First paragraph.</p>
                  <figure class="inline-image">
                    <img src="/diagram.png" alt="A diagram">
                  </figure>
                  <p>Second paragraph.</p>
                </div>
              </figure>
            </article>
            """.trimIndent(),
        )

        assertEquals(
            """
            First paragraph.

            ![A diagram](https://example.com/diagram.png)

            Second paragraph.
            """.trimIndent() + "\n",
            markdown,
        )
    }

    @Test
    fun `lists preserve nesting and blockquotes prefix lines`() {
        val markdown = render(
            """
            <article>
              <ul><li>One<ul><li>Nested</li></ul></li><li>Two</li></ul>
              <blockquote><p>Quote line one.</p><p>Quote line two.</p></blockquote>
            </article>
            """.trimIndent(),
        )

        assertTrue(markdown.contains("- One\n\t- Nested\n- Two"))
        assertTrue(markdown.contains("> Quote line one.\n>\n> Quote line two."))
    }

    @Test
    fun `deep nested lists keep upstream continuation indentation`() {
        val markdown = render(
            """
            <article>
              <ul>
                <li>Top
                  <ul>
                    <li>Middle
                      <ul>
                        <li>Intro
                          <ul>
                            <li>First link</li>
                            <li>Second link</li>
                            <li>Third link</li>
                          </ul>
                        </li>
                        <li>After nested list</li>
                      </ul>
                    </li>
                  </ul>
                </li>
              </ul>
            </article>
            """.trimIndent(),
        )

        assertEquals(
            """
            - Top
            %T%- Middle
            %T%%T%- Intro
            %T%%T%%T%- First link
            %T%%T%%T%%T%%T%%T%- Second link
            %T%%T%%T%%T%%T%%T%- Third link
            %T%%T%%T%%T%- After nested list
            """.trimIndent()
                .replace("%T%", "\t") + "\n",
            markdown,
        )
    }

    @Test
    fun `standalone inline formatting nodes render with delimiters`() {
        val markdown = render(
            """
            <article>
              <blockquote>
                <b>Important title</b><br><br>
                Body text.
              </blockquote>
            </article>
            """.trimIndent(),
        )

        assertTrue(markdown.contains("> **Important title**"))
        assertTrue(markdown.contains("> **Important title**  \n>   \n> Body text."))
        assertTrue(markdown.contains("> Body text."))
    }

    @Test
    fun `blockquote preserves hard break runs around horizontal rules`() {
        val markdown = render(
            """
            <article>
              <blockquote>
                Body text.<br><br>
                <hr><br><br>
                <sup>1</sup> Footnote text.
              </blockquote>
            </article>
            """.trimIndent(),
        )

        assertEquals(
            """
            > Body text.%BR%
            > %BLANK_HARD%
            >%SPACE%
            > ---
            >%SPACE%
            > %BLANK_HARD%
            > %BLANK_HARD%
            > <sup>1</sup> Footnote text.
            """.trimIndent()
                .replace("%BR%", "  ")
                .replace("%SPACE%", " ")
                .replace("%BLANK_HARD%", "  ") + "\n",
            markdown,
        )
    }

    @Test
    fun `lists skip empty items left by stripped chrome`() {
        val markdown = render(
            """
            <article>
              <ul>
                <li></li>
                <li><span></span></li>
                <li>Actual item</li>
              </ul>
            </article>
            """.trimIndent(),
        )

        assertEquals("- Actual item\n", markdown)
    }

    @Test
    fun `fenced code preserves content and post processing does not trim code`() {
        val markdown = render(
            """<article><pre><code data-lang="kotlin">fun main() {  ${"\n"}  println("hi")  ${"\n"}}</code></pre></article>""",
        )

        assertTrue(markdown.contains("```kotlin\nfun main() {  \n  println(\"hi\")  \n}\n```"))
    }

    @Test
    fun `fenced code uses a longer fence when code contains backticks`() {
        val markdown = render("""<article><pre><code>before${"\n"}```${"\n"}after</code></pre></article>""")

        assertTrue(markdown.startsWith("````\n"))
        assertTrue(markdown.contains("\\`\\`\\`\n"))
        assertTrue(markdown.endsWith("\n````\n"))
    }

    @Test
    fun `fenced code escapes template literal backticks`() {
        val markdown = render(
            """<article><pre><code data-lang="js">console.log(`hello`);</code></pre></article>""",
        )

        assertEquals(
            """
            ```js
            console.log(\`hello\`);
            ```
            """.trimIndent() + "\n",
            markdown,
        )
    }

    @Test
    fun `fenced code expands tabs to spaces`() {
        val markdown = render(
            """<article><pre><code data-lang="js">if (ok) {${"\n"}${"\t"}run();${"\n"}}</code></pre></article>""",
        )

        assertEquals(
            """
            ```js
            if (ok) {
                run();
            }
            ```
            """.trimIndent() + "\n",
            markdown,
        )
    }

    @Test
    fun `empty links are removed after markdown conversion including fenced code`() {
        val markdown = render(
            """
            <article>
              <pre><code data-lang="cpp">auto ok = [](uint8_t x) { return x; }</code></pre>
              <p><a href="/empty"></a></p>
            </article>
            """.trimIndent(),
        )

        assertEquals(
            """
            ```cpp
            auto ok =  { return x; }
            ```
            """.trimIndent() + "\n",
            markdown,
        )
    }

    @Test
    fun `block code elements with highlight language classes render as fenced code`() {
        val markdown = render(
            """
            <article>
              <code class="hl lean block" data-lean-context="examples">
                <span class="keyword token">def</span><span class="inter-text"> </span><span class="const token" id="f-next-next">h1</span><span class="inter-text"> (x : Nat) : Nat :=</span>
              </code>
            </article>
            """.trimIndent(),
        )

        assertEquals(
            """
            ```lean
            def h1 (x : Nat) : Nat :=
            ```
            """.trimIndent() + "\n",
            markdown,
        )
    }

    @Test
    fun `image block after fenced code uses compact upstream spacing`() {
        val markdown = render(
            """
            <article>
              <pre><code data-lang="c">node --prof app.js</code></pre>
              <figure>
                <img src="/profile.png">
                <figcaption>Profile output.</figcaption>
              </figure>
            </article>
            """.trimIndent(),
        )

        assertEquals(
            """
            ```c
            node --prof app.js
            ```
            ![](https://example.com/profile.png)

            *Profile output.*
            """.trimIndent() + "\n",
            markdown,
        )
    }

    @Test
    fun `fenced code after list uses compact upstream spacing`() {
        val markdown = render(
            """
            <article>
              <ol>
                <li>Open the Console and run this code:</li>
              </ol>
              <pre><code data-lang="js">console.log("ok");</code></pre>
            </article>
            """.trimIndent(),
        )

        assertEquals(
            """
            1. Open the Console and run this code:
            ```js
            console.log("ok");
            ```
            """.trimIndent() + "\n",
            markdown,
        )
    }

    @Test
    fun `simple tables render as GFM and complex tables fall back to text`() {
        val simple = render(
            """<article><table><tr><th>A</th><th>B</th></tr><tr><td>1</td><td>2</td></tr></table></article>""",
        )
        assertEquals("| A | B |\n| --- | --- |\n| 1 | 2 |\n", simple)

        val noOpSpans = render(
            """<article><table><tr><th colspan="1">A</th><th rowspan="1">B</th></tr><tr><td colspan="1">1</td><td rowspan="1">2</td></tr></table></article>""",
        )
        assertEquals("| A | B |\n| --- | --- |\n| 1 | 2 |\n", noOpSpans)

        val complex = render("""<article><table><tr><td colspan="2">Wide cell</td></tr></table></article>""")
        assertEquals("Wide cell\n", complex)

        val empty = render("""<article><table><tr><td></td><td></td></tr></table><p>Body.</p></article>""")
        assertEquals("Body.\n", empty)
    }

    @Test
    fun `table cells separate block children`() {
        val markdown = render(
            """
            <article>
              <table>
                <tr><th>Phase</th></tr>
                <tr><td><div>Phase G1: cell growth</div><div>Phase S: DNA replication</div></td></tr>
              </table>
            </article>
            """.trimIndent(),
        )

        assertTrue(markdown.contains("Phase G1: cell growth  Phase S: DNA replication"))
    }

    @Test
    fun `blank headings are skipped`() {
        val markdown = render(
            """
            <article>
              <h2><svg></svg></h2>
              <p>Body text.</p>
            </article>
            """.trimIndent(),
        )

        assertEquals("Body text.\n", markdown)
    }

    @Test
    fun `svg blocks render as compact sanitized html`() {
        val markdown = render(
            """
            <article>
              <figure>
                <svg class="diagram" viewBox="0 0 100 100" xmlns="http://www.w3.org/2000/svg">
                  <circle cx="50" cy="50" r="40" fill="none" stroke="black" />
                  <text x="50" y="55" text-anchor="middle">Node</text>
                </svg>
              </figure>
            </article>
            """.trimIndent(),
        )

        val expectedSvg = """<svg viewBox="0 0 100 100" xmlns="http://www.w3.org/2000/svg">""" +
            """<circle cx="50" cy="50" r="40" fill="none" stroke="black"></circle>""" +
            """<text x="50" y="55" text-anchor="middle">Node</text></svg>"""

        assertEquals(
            expectedSvg + "\n",
            markdown,
        )
    }

    @Test
    fun `svg chart classes get fallback styling before class stripping`() {
        val markdown = render(
            """
            <article>
              <svg class="chart-svg" width="400" height="200" viewBox="0 0 400 200">
                <line class="gridline svelte-abc" x1="0" x2="350" y1="0" y2="0"></line>
                <path class="path-area svelte-def" d="M0,120L100,100"></path>
                <path class="path-line svelte-ghi" d="M0,120L100,100"></path>
              </svg>
            </article>
            """.trimIndent(),
        )

        val expectedSvg = """<svg width="400" height="200" viewBox="0 0 400 200">""" +
            """<line stroke-opacity="0.2" stroke="currentColor" x1="0" x2="350" y1="0" y2="0"></line>""" +
            """<path fill="none" d="M0,120L100,100"></path>""" +
            """<path stroke="currentColor" fill="none" d="M0,120L100,100"></path></svg>"""

        assertEquals(expectedSvg + "\n", markdown)
    }

    @Test
    fun `svg css variables and utility classes render with fallback attributes`() {
        val markdown = render(
            """
            <article>
              <svg viewBox="0 0 200 100" fill="var(--background-color-card)">
                <line class="stroke-zinc-400" x1="20" y1="50" x2="180" y2="50"></line>
                <circle class="fill-orange-500" cx="50" cy="50" r="8"></circle>
                <text class="fill-amber-500 text-[14px] font-semibold" x="100" y="90">-0.50</text>
                <path stroke="light-dark(var(--color-slate-400), var(--color-slate-300))" d="M0 0"></path>
              </svg>
            </article>
            """.trimIndent(),
        )

        val expectedSvg = """<svg viewBox="0 0 200 100" fill="Canvas">""" +
            """<line stroke="#a1a1aa" x1="20" y1="50" x2="180" y2="50"></line>""" +
            """<circle fill="#f97316" cx="50" cy="50" r="8"></circle>""" +
            """<text style="font-size:14px;font-weight:600" fill="#f59e0b" x="100" y="90">-0.50</text> """ +
            """<path stroke="#94a3b8" d="M0 0"></path></svg>"""

        assertEquals(expectedSvg + "\n", markdown)
    }

    @Test
    fun `svg text label spacing is preserved before adjacent groups`() {
        val markdown = render(
            """
            <article>
              <svg width="400" height="200" viewBox="0 0 400 200">
                <g>
                  <g><text x="0" y="27">Jan.</text></g>
                  <g><text x="0" y="27">Feb.</text></g>
                </g>
                <path d="M0,120L100,100"></path>
              </svg>
            </article>
            """.trimIndent(),
        )

        val expectedSvg = """<svg width="400" height="200" viewBox="0 0 400 200">""" +
            """<g><g><text x="0" y="27">Jan.</text> </g>""" +
            """<g><text x="0" y="27">Feb.</text></g></g>""" +
            """<path d="M0,120L100,100"></path></svg>"""

        assertEquals(expectedSvg + "\n", markdown)
    }

    @Test
    fun `sprite only svg icons render blank`() {
        val markdown = render(
            """
            <article>
              <p>Published today <svg><use href="/images/icons/spritemap.svg#sprite-clock-icon"></use></svg></p>
              <p>Article <svg viewBox="0 0 20 20"><path d="M10,0C4.5,0,0,4.5,0,10"></path></svg></p>
              <p>Copy <svg width="1em" height="1em" viewBox="0 0 24 24"><path d="M19,21H8V7H19"></path></svg></p>
            </article>
            """.trimIndent(),
        )

        assertEquals(
            """
            Published today

            Article

            Copy
            """.trimIndent() + "\n",
            markdown,
        )
    }

    @Test
    fun `callouts render as alert blockquotes`() {
        val markdown = render(
            """
            <article>
              <div data-callout="info" class="callout">
                <div class="callout-title"><div class="callout-title-inner">Info</div></div>
                <div class="callout-content"><p>Body text.</p></div>
              </div>
            </article>
            """.trimIndent(),
        )

        assertEquals("> [!info] Info\n> Body text.\n", markdown)
    }

    @Test
    fun `callouts preserve fold markers and render from non div elements`() {
        val markdown = render(
            """
            <article>
              <aside data-callout="faq" data-callout-fold="-" class="callout">
                <div class="callout-title">Is this foldable?</div>
                <div class="callout-content"><p>Yes, it is.</p></div>
              </aside>
            </article>
            """.trimIndent(),
        )

        assertEquals("> [!faq]- Is this foldable?\n> Yes, it is.\n", markdown)
    }

    @Test
    fun `footnotes render references and definitions`() {
        val markdown = render(
            """
            <article>
              <p>Text<sup><a href="#fn1">1</a></sup></p>
              <section data-footnotes="true"><ol><li id="fn1">Footnote content.</li></ol></section>
            </article>
            """.trimIndent(),
        )

        assertEquals("Text[^1]\n\n[^1]: Footnote content.\n", markdown)
    }

    @Test
    fun `footnote section heading is preserved before collected definitions`() {
        val markdown = render(
            """
            <article>
              <p>Text<sup><a href="#fn1">1</a></sup></p>
              <section data-footnotes="true">
                <h2 class="sr-only">Footnotes</h2>
                <ol><li id="fn1">Footnote content.</li></ol>
              </section>
            </article>
            """.trimIndent(),
        )

        assertEquals("Text[^1]\n\n## Footnotes\n\n[^1]: Footnote content.\n", markdown)
    }

    @Test
    fun `footnote definitions drop terminal periods after inline links and code`() {
        val markdown = render(
            """
            <article>
              <p>Links<sup><a href="#fn1">1</a></sup> and code<sup><a href="#fn2">2</a></sup>.</p>
              <section data-footnotes="true">
                <ol>
                  <li id="fn1">See <a href="https://example.com/ref">reference</a>.</li>
                  <li id="fn2">Use <code>x &amp; 0xff</code>.</li>
                  <li id="fn3">Plain sentence.</li>
                </ol>
              </section>
            </article>
            """.trimIndent(),
        )

        assertTrue(markdown.contains("[^1]: See [reference](https://example.com/ref)\n"))
        assertTrue(markdown.contains("[^2]: Use `x & 0xff`\n"))
        assertTrue(markdown.contains("[^3]: Plain sentence."))
    }

    @Test
    fun `inline text removes spaces before three-dot ellipses`() {
        val markdown = render(
            """
            <article>
              <p>The 15 Pro ... was mentioned.</p>
            </article>
            """.trimIndent(),
        )

        assertEquals("The 15 Pro... was mentioned.\n", markdown)
    }

    @Test
    fun `footnote references after words keep a separating space before punctuation`() {
        val markdown = render(
            """
            <article>
              <p>First word<sup><a href="#fn1">1</a></sup>. Second word<sup><a href="#fn2">2</a></sup>, continued.</p>
              <section data-footnotes="true">
                <ol>
                  <li id="fn1">First definition.</li>
                  <li id="fn2">Second definition.</li>
                </ol>
              </section>
            </article>
            """.trimIndent(),
        )

        assertTrue(markdown.contains("First word [^1]. Second word [^2], continued."))
    }

    @Test
    fun `footnotes render ftnt references and block definitions`() {
        val markdown = render(
            """
            <article>
              <p>Text<sup id="ftnt_ref1"><a href="#ftnt1">[1]</a></sup></p>
              <section data-footnotes="true">
                <ol>
                  <li id="ftnt1">
                    <p>First paragraph.</p>
                    <ul><li>Supporting point.</li></ul>
                    <p>Continuation.</p>
                  </li>
                </ol>
              </section>
            </article>
            """.trimIndent(),
        )

        assertEquals(
            """
            Text[^1]

            [^1]: First paragraph.

            - Supporting point.

            Continuation.
            """.trimIndent() + "\n",
            markdown,
        )
    }

    @Test
    fun `footnote references between words keep readable spacing`() {
        val markdown = render(
            """
            <article>
              <p>The main<sup><a href="#fn2">2</a></sup> benefits stay readable.</p>
              <section data-footnotes="true"><ol><li id="fn2">Definition.</li></ol></section>
            </article>
            """.trimIndent(),
        )

        assertEquals("The main [^2] benefits stay readable.\n\n[^2]: Definition.\n", markdown)
    }

    @Test
    fun `footnote references use numeric order for non numeric target ids`() {
        val markdown = render(
            """
              <article>
              <p>Text <span id="fnref:calibration"><a href="#fn:calibration">1</a></span>.</p>
              <section data-footnotes="true"><ol><li id="fn:calibration">Definition.</li></ol></section>
            </article>
            """.trimIndent(),
        )

        assertEquals("Text [^1].\n\n[^1]: Definition.\n", markdown)
    }

    @Test
    fun `footnote random alphanumeric ids use definition order`() {
        val markdown = render(
            """
            <article>
                <p>First <span class="footnote-reference"><sup><a href="#fnabc123">[1]</a></sup></span>
                and second <span class="footnote-reference"><sup><a href="#fna35qx2ldayo">[2]</a></sup></span>.</p>
                <section data-footnotes="true">
                  <ol>
                    <li id="fnabc123">First definition.</li>
                    <li id="fna35qx2ldayo">Second definition.</li>
                  </ol>
                </section>
              </article>
            """.trimIndent(),
        )

        assertEquals(
            """
            First [^1] and second [^2].

            [^1]: First definition.

            [^2]: Second definition.
            """.trimIndent() + "\n",
            markdown,
        )
    }

    @Test
    fun `footnote opaque ids ending with digits still use definition order`() {
        val markdown = render(
            """
            <article>
              <p>Body<span class="footnote-reference"><sup><a href="#fncrt8wagfir9">[26]</a></sup></span>.</p>
              <section data-footnotes="true"><ol>
                ${List(25) { index -> """<li id="fn${index + 1}">Definition ${index + 1}.</li>""" }.joinToString("\n")}
                <li id="fncrt8wagfir9">Opaque definition.</li>
              </ol></section>
            </article>
            """.trimIndent(),
        )

        assertTrue(markdown.contains("Body [^26]."))
        assertTrue(markdown.contains("[^26]: Opaque definition."))
        assertFalse(markdown.contains("[^9]: Opaque definition."))
    }

    @Test
    fun `dotted footnote ids clean to numeric references`() {
        val markdown = render(
            """
            <article>
              <p>Text<sup><a href="#fn.1">1</a></sup></p>
              <section data-footnotes="true"><ol><li id="fn.1">Definition.</li></ol></section>
            </article>
            """.trimIndent(),
        )

        assertEquals("Text[^1]\n\n[^1]: Definition.\n", markdown)
    }

    @Test
    fun `footnote reference wrappers trim spacing after punctuation`() {
        val markdown = render(
            """
            <article>
              <p>Wrapped <span>reference.<span id="ft-1" class="reference"> <sup class="footnote-ref">1</sup> </span></span>Continues.</p>
              <section data-footnotes="true"><ol><li id="fn1">Definition.</li></ol></section>
            </article>
            """.trimIndent(),
        )

        assertEquals("Wrapped reference.[^1] Continues.\n\n[^1]: Definition.\n", markdown)
    }

    @Test
    fun `footnote references after inline code keep a separating space before punctuation`() {
        val markdown = render(
            """
            <article>
              <p>Use <code>x</code><sup><a href="#1">1</a></sup>.</p>
              <section data-footnotes="true"><ol><li id="1">Definition.</li></ol></section>
            </article>
            """.trimIndent(),
        )

        assertEquals("Use `x` [^1].\n\n[^1]: Definition.\n", markdown)
    }

    @Test
    fun `footnote references after inline formatting images and table cells keep upstream spacing`() {
        val markdown = render(
            """
            <article>
              <p>Model <em>prediction</em><sup><a href="#fn1">1</a></sup> continues.</p>
              <p><img src="/plot.png"><sup><a href="#fn2">2</a></sup></p>
              <table><tr><th>Type</th></tr><tr><td>X<sup><a href="#fn3">3</a></sup></td></tr></table>
              <p><strong>Bold claim</strong><sup><a href="#fn4">4</a></sup></p>
              <section data-footnotes="true"><ol>
                <li id="fn1">First.</li>
                <li id="fn2">Second.</li>
                <li id="fn3">Third.</li>
                <li id="fn4">Fourth.</li>
              </ol></section>
            </article>
            """.trimIndent(),
        )

        assertTrue(markdown.contains("Model *prediction* [^1] continues."))
        assertTrue(markdown.contains("![](https://example.com/plot.png) [^2]"))
        assertTrue(markdown.contains("| X [^3] |"))
        assertTrue(markdown.contains("**Bold claim**[^4]"))
    }

    @Test
    fun `footnote references after closing quotes keep sentence spacing`() {
        val markdown = render(
            """
            <article>
              <p>Analysts called it "too weak."<sup><a href="#fn1">1</a></sup> The figures are staggering.</p>
              <section data-footnotes="true"><ol><li id="fn1">Definition.</li></ol></section>
            </article>
            """.trimIndent(),
        )

        assertEquals(
            "Analysts called it \"too weak.\" [^1] The figures are staggering.\n\n[^1]: Definition.\n",
            markdown,
        )
    }

    @Test
    fun `footnote references after closing quotes at paragraph end keep sentence spacing`() {
        val markdown = render(
            """
            <article>
              <p>He asked, "Are you sure?"<sup><a href="#fn1">1</a></sup></p>
              <section data-footnotes="true"><ol><li id="fn1">Definition.</li></ol></section>
            </article>
            """.trimIndent(),
        )

        assertEquals("He asked, \"Are you sure?\" [^1]\n\n[^1]: Definition.\n", markdown)
    }

    @Test
    fun `footnote references after closing quotes keep spacing before inline elements`() {
        val markdown = render(
            """
            <article>
              <p>Analysts called it "too weak."<sup><a href="#fn1">1</a></sup> <i>PC Magazine</i> disagreed.</p>
              <section data-footnotes="true"><ol><li id="fn1">Definition.</li></ol></section>
            </article>
            """.trimIndent(),
        )

        assertEquals(
            "Analysts called it \"too weak.\" [^1] *PC Magazine* disagreed.\n\n[^1]: Definition.\n",
            markdown,
        )
    }

    @Test
    fun `non footnote sup and sub tags are preserved as html`() {
        val markdown = render("""<article><p>Value<sup>1</sup> and H<sub>2</sub>O.</p></article>""")

        assertEquals("Value<sup>1</sup> and H<sub>2</sub>O.\n", markdown)
    }

    @Test
    fun `readable date age subscripts keep a separating space`() {
        val markdown = render("""<article><p>Written in <span>2021<sub>5ya</sub></span>.</p></article>""")

        assertEquals("Written in 2021 <sub>5ya</sub>.\n", markdown)
    }

    @Test
    fun `inline links trim source whitespace before punctuation`() {
        val markdown = render(
            """
            <article>
              <p>Read <span><a href="/story">the story</a> </span> .</p>
              <p>Namely, <span><a href="/person">the architect</a> </span> , whose work matters.</p>
              <p>See <span><a href="/docs">docs</a> </span> : notes.</p>
            </article>
            """.trimIndent(),
        )

        assertEquals(
            """
            Read [the story](https://example.com/story).

            Namely, [the architect](https://example.com/person), whose work matters.

            See [docs](https://example.com/docs): notes.
            """.trimIndent() + "\n",
            markdown,
        )
    }

    @Test
    fun `inline emphasis gets readable spacing when source glues it to words or quotes`() {
        val markdown = render(
            """
            <article>
              <p>The web<em>page</em> evolved.</p>
              <p>Original “<em>Xanadocs</em>” linked text.</p>
              <p>Lorelai (<em>Parenthood</em>'s Lauren Graham) appears.</p>
              <p><strong>Thesis:</strong><em>A model</em> predicts.</p>
            </article>
            """.trimIndent(),
        )

        assertEquals(
            """
            The web *page* evolved.

            Original “ *Xanadocs* ” linked text.

            Lorelai (*Parenthood*'s Lauren Graham) appears.

            **Thesis:** *A model* predicts.
            """.trimIndent() + "\n",
            markdown,
        )
    }

    @Test
    fun `age subscripts preserve explicit source spacer before punctuation`() {
        val markdown = render("""<article><p>Written in <span> 2021<sub>5ya</sub> </span> .</p></article>""")

        assertEquals("Written in 2021 <sub>5ya</sub> .\n", markdown)
    }

    @Test
    fun `numeric variable subscripts before punctuation keep readable spacing`() {
        val markdown = render(
            """
            <article>
              <p><strong><em>X = { (x<sub>1</sub>, y<sub>2</sub>) }</em></strong></p>
            </article>
            """.trimIndent(),
        )

        assertEquals("***X = { (x <sub>1</sub>, y <sub>2</sub>) }***\n", markdown)
    }

    @Test
    fun `non footnote fragment links stay relative`() {
        val markdown = render(
            """<article><p>See <a href="#section-one">section one</a>.</p></article>""",
        )

        assertEquals("See [section one](#section-one).\n", markdown)
    }

    @Test
    fun `math data latex renders as markdown math and mathml falls back to text`() {
        val markdown = render("""<article><p><span data-latex="x^2"></span> <math><mi>y</mi></math></p></article>""")

        assertEquals("${'$'}x^2${'$'} y\n", markdown)
    }

    @Test
    fun `trusted video iframes render as markdown media`() {
        val markdown = render(
            """
            <article>
              <p>See the trailer below</p>
              <iframe
                title="YouTube video"
                src="https://www.youtube-nocookie.com/embed/1hKyYaBzko8"
                data-klead-video-url="https://www.youtube.com/watch?v=1hKyYaBzko8">
              </iframe>
            </article>
            """.trimIndent(),
        )

        assertEquals(
            """
            See the trailer below

            ![](https://www.youtube.com/watch?v=1hKyYaBzko8)
            """.trimIndent() + "\n",
            markdown,
        )
    }

    @Test
    fun `trusted video iframes can preserve a requested leading spacer`() {
        val markdown = render(
            """
            <article>
              <p>See the trailer below:</p>
              <iframe
                title="YouTube video"
                src="https://www.youtube-nocookie.com/embed/1hKyYaBzko8"
                data-klead-leading-spacer="true"
                data-klead-video-url="https://www.youtube.com/watch?v=1hKyYaBzko8">
              </iframe>
            </article>
            """.trimIndent(),
        )

        assertEquals(
            """
            See the trailer below:


            ![](https://www.youtube.com/watch?v=1hKyYaBzko8)
            """.trimIndent() + "\n",
            markdown,
        )
    }

    @Test
    fun `trusted social iframes render as markdown media`() {
        val markdown = render(
            """
            <article>
              <p>A tweet:</p>
              <iframe src="https://platform.twitter.com/embed/Tweet.html?id=1675626836821409792"></iframe>
              <p>An X.com embed:</p>
              <iframe src="https://x.com/kepano/status/1675626836821409792"></iframe>
            </article>
            """.trimIndent(),
        )

        assertEquals(
            """
            A tweet:

            ![](https://x.com/i/status/1675626836821409792)

            An X.com embed:

            ![](https://x.com/kepano/status/1675626836821409792)
            """.trimIndent() + "\n",
            markdown,
        )
    }

    @Test
    fun `trusted raw vimeo iframe renders as sanitized html`() {
        val markdown = render(
            """
            <article>
              <p>A Vimeo video should stay as iframe:</p>
              <iframe src="https://player.vimeo.com/video/45725193?h=a290f71a57" width="100%" height="100%" style="aspect-ratio: 3 / 1.025" frameborder="0" allow="autoplay; fullscreen; picture-in-picture" allowfullscreen=""></iframe>
            </article>
            """.trimIndent(),
        )

        assertEquals(
            """
            A Vimeo video should stay as iframe:

            <iframe src="https://player.vimeo.com/video/45725193?h=a290f71a57" width="100%" height="100%" frameborder="0" allow="autoplay; fullscreen; picture-in-picture" allowfullscreen=""></iframe>
            """.trimIndent() + "\n",
            markdown,
        )
    }

    private fun render(html: String): String = KleadMarkdownWriter.write(
        root = Jsoup.parse(html, "https://example.com/base/").selectFirst("article") ?: error("missing article"),
        baseUrl = "https://example.com/base/",
    )
}
