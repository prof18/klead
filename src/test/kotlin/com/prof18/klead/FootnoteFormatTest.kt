package com.prof18.klead

import kotlin.test.Test
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
}
