package com.prof18.klead

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FootnoteFormatTest {
    @Test
    fun `texinfo footnotes-segment becomes footnote reference and definition`() {
        val result = parseHtmlForTest(
            html = """
                <article>
                  <h1>Texinfo Manual</h1>
                  <p>
                    Some explanatory prose long enough to be treated as the article body,
                    with a marker here<a class="footnote" id="DOCF1" href="#FOOT1">(1)</a> referencing a note.
                    Additional sentences keep this paragraph above the content threshold so it stays.
                  </p>
                  <div class="footnotes-segment">
                    <hr>
                    <h3 class="footnotes-heading">Footnotes</h3>
                    <h5 class="footnote-body-heading"><a id="FOOT1" href="#DOCF1">(1)</a></h5>
                    <p>This is the body of the first footnote.</p>
                  </div>
                </article>
            """.trimIndent(),
            url = "https://example.com/texinfo",
        )

        val markdown = result.content.requireMarkdown()
        assertTrue(markdown.contains("[^1]"), "expected a footnote reference, got:\n$markdown")
        assertTrue(
            markdown.contains("This is the body of the first footnote."),
            "expected the footnote definition body, got:\n$markdown",
        )
    }

    @Test
    fun `oreilly noteref footnotes become footnote reference and definition`() {
        val result = parseHtmlForTest(
            html = """
                <article>
                  <h1>O'Reilly Chapter</h1>
                  <p>
                    A sufficiently long paragraph of body text that comfortably clears the content
                    threshold, including an inline note marker<a data-type="noteref" href="ch01.html#ch01fn1"><sup>1</sup></a>
                    that points at a definition further down the chapter content.
                  </p>
                  <p data-type="footnote" id="ch01fn1"><sup><a href="#ch01fn1-marker">1</a></sup> The first chapter footnote body.</p>
                </article>
            """.trimIndent(),
            url = "https://example.com/oreilly",
        )

        val markdown = result.content.requireMarkdown()
        assertTrue(markdown.contains("[^1]"), "expected a footnote reference, got:\n$markdown")
        assertTrue(
            markdown.contains("The first chapter footnote body."),
            "expected the footnote definition body, got:\n$markdown",
        )
    }

    @Test
    fun `labeled section ordered list becomes footnote definitions`() {
        val result = parseHtmlForTest(
            html = """
                <article>
                  <h1>Policy Note</h1>
                  <p>We laid out our position in an earlier essay<sup class="caption">1</sup>, and have held it
                    consistently across every subsequent statement we have published on the subject.</p>
                  <p>The second concern is that testing should apply to every sufficiently capable
                    system<sup class="caption">2</sup>, however it was trained.</p>
                  <p>These positions are complementary rather than competing, and we expect to restate
                    them whenever the question comes up again.</p>
                </article>
                <div class="page-wrapper">
                  <div class="PostDetail-module__footnotes">
                    <h4 class="headline-5">Footnotes</h4>
                    <ol>
                      <li id="footnote-1">See Sections 2 and 3 of that essay for the full discussion.</li>
                      <li id="footnote-2">Testing regimes are described in <em>Appendix B</em> of the same document.</li>
                    </ol>
                  </div>
                </div>
            """.trimIndent(),
            url = "https://example.com/policy-note",
        )

        val markdown = result.content.requireMarkdown()
        assertTrue(
            markdown.contains("[^1]: See Sections 2 and 3 of that essay for the full discussion."),
            "expected the first footnote definition, got:\n$markdown",
        )
        assertTrue(
            markdown.contains("[^2]: Testing regimes are described in *Appendix B* of the same document."),
            "expected the second footnote definition, got:\n$markdown",
        )
        assertFalse(
            markdown.contains("Footnotes"),
            "expected the footnote wrapper heading to be dropped, got:\n$markdown",
        )
        assertFalse(
            markdown.contains("<sup>"),
            "expected inline markers to render as footnote references, got:\n$markdown",
        )
    }

    @Test
    fun `line break separated named anchors become footnote definitions`() {
        val result = parseHtmlForTest(
            html = """
                <article>
                  <h1>Thoughts on Open Models</h1>
                  <p>Everyone remembers the famous open letter, written in 2010, because it started the
                    long, slow decline of a key technology of that era.<a href="#one"><sup>1</sup></a></p>
                  <p>A handful of large labs signed on early, but the notable one is obviously the
                    market leader of that particular moment.<a href="#two"><sup>2</sup></a></p>
                  <hr>
                  <p id="footnote"><a name="one"><sup>1</sup></a> It's genuinely odd that the company no
                    longer hosts these pivotal posts on its own site. <a href="#" onclick="window.history.back(); return false;">↩</a>
                  <br><br>
                  <a name="two"><sup>2</sup></a> Other absences from the list include several large
                    database vendors, though none of them train their own frontier models. <a href="#" onclick="window.history.back(); return false;">↩</a>
                  </p>
                </article>
            """.trimIndent(),
            url = "https://example.com/open-models",
        )

        val markdown = result.content.requireMarkdown()
        assertTrue(
            markdown.contains(
                "[^1]: It's genuinely odd that the company no longer hosts these pivotal posts on its own site.",
            ),
            "expected the first footnote definition, got:\n$markdown",
        )
        assertTrue(
            markdown.contains("[^2]: Other absences from the list include several large database vendors"),
            "expected the second footnote definition, got:\n$markdown",
        )
        assertFalse(markdown.contains("↩"), "expected backref arrows to be dropped, got:\n$markdown")
        assertFalse(markdown.contains("---"), "expected the footnote divider to be dropped, got:\n$markdown")
    }
}
