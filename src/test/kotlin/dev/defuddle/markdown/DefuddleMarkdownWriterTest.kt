package dev.defuddle.markdown

import org.jsoup.Jsoup
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DefuddleMarkdownWriterTest {
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
    fun `link text with nested emphasis keeps markdown delimiters tight`() {
        val markdown = render(
            """
            <article>
              <p>Locale<a href="/story"><em> Il Centro</em></a> sarebbe indagato.</p>
            </article>
            """.trimIndent(),
        )

        assertEquals("Locale [*Il Centro*](https://example.com/story) sarebbe indagato.\n", markdown)
    }

    @Test
    fun `inline code handles embedded backticks`() {
        val markdown = render("""<article><p>Use <code>a `tick` here</code>.</p></article>""")

        assertEquals("Use `` a `tick` here ``.\n", markdown)
    }

    @Test
    fun `images choose largest srcset`() {
        val markdown = render("""<article><p><img alt="Hero" src="/small.png" srcset="/small.png 320w, /large.png 960w"></p></article>""")

        assertEquals("![Hero](https://example.com/large.png)\n", markdown)
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
    fun `lists preserve nesting and blockquotes prefix lines`() {
        val markdown = render(
            """
            <article>
              <ul><li>One<ul><li>Nested</li></ul></li><li>Two</li></ul>
              <blockquote><p>Quote line one.</p><p>Quote line two.</p></blockquote>
            </article>
            """.trimIndent(),
        )

        assertTrue(markdown.contains("- One\n  - Nested\n- Two"))
        assertTrue(markdown.contains("> Quote line one.\n>\n> Quote line two."))
    }

    @Test
    fun `fenced code preserves content and post processing does not trim code`() {
        val markdown = render("""<article><pre><code data-lang="kotlin">fun main() {  ${"\n"}  println("hi")  ${"\n"}}</code></pre></article>""")

        assertTrue(markdown.contains("```kotlin\nfun main() {  \n  println(\"hi\")  \n}\n```"))
    }

    @Test
    fun `fenced code uses a longer fence when code contains backticks`() {
        val markdown = render("""<article><pre><code>before${"\n"}```${"\n"}after</code></pre></article>""")

        assertTrue(markdown.startsWith("````\n"))
        assertTrue(markdown.contains("```\n"))
        assertTrue(markdown.endsWith("\n````\n"))
    }

    @Test
    fun `simple tables render as GFM and complex tables fall back to text`() {
        val simple = render("""<article><table><tr><th>A</th><th>B</th></tr><tr><td>1</td><td>2</td></tr></table></article>""")
        assertEquals("| A | B |\n| --- | --- |\n| 1 | 2 |\n", simple)

        val complex = render("""<article><table><tr><td colspan="2">Wide cell</td></tr></table></article>""")
        assertEquals("Wide cell\n", complex)
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
    fun `math data latex renders as markdown math and mathml falls back to text`() {
        val markdown = render("""<article><p><span data-latex="x^2"></span> <math><mi>y</mi></math></p></article>""")

        assertEquals("${'$'}x^2${'$'} y\n", markdown)
    }

    private fun render(html: String): String =
        DefuddleMarkdownWriter.write(
            root = Jsoup.parse(html, "https://example.com/base/").selectFirst("article") ?: error("missing article"),
            baseUrl = "https://example.com/base/",
        )
}
