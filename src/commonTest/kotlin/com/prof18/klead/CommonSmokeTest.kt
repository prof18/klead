package com.prof18.klead

import com.prof18.klead.internal.KleadParser
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CommonSmokeTest {
    @Test
    fun emptyHtmlProducesEmptyMarkdown() = runTest {
        val result = parse("", outputs = setOf(KleadOutput.MARKDOWN))

        assertEquals("", result.content.requireMarkdown())
    }

    @Test
    fun minimalArticleProducesStableMarkdown() = runTest {
        val result = parse(
            """
            <html>
              <head><title>Document title</title></head>
              <body><article>
                <h1>Readable title</h1>
                <p>This is the first paragraph.</p>
                <p>This is the second paragraph.</p>
              </article></body>
            </html>
            """.trimIndent(),
            outputs = setOf(KleadOutput.MARKDOWN),
        )

        assertEquals(
            """
            ## Readable title

            This is the first paragraph.

            This is the second paragraph.
            """.trimIndent() + "\n",
            result.content.requireMarkdown(),
        )
    }

    @Test
    fun htmlOutputKeepsArticleAndRemovesScripts() = runTest {
        val result = parse(
            "<article><p>Visible body.</p><script>bad()</script></article>",
            outputs = setOf(KleadOutput.HTML),
        )

        assertTrue(result.content.requireHtml().contains("<article>"))
        assertTrue(result.content.requireHtml().contains("Visible body."))
        assertFalse(result.content.requireHtml().contains("<script"))
    }

    @Test
    fun sanitizerRemovesDangerousAttributesAndKeepsImageData() = runTest {
        val result = parse(
            """
            <article>
              <p onclick="bad()">Safe text.</p>
              <a href="javascript:alert(1)">bad href</a>
              <img src="data:text/html,bad" alt="bad src">
              <iframe srcdoc="bad"></iframe>
              <img src="data:image/png;base64,AAAA" alt="safe image">
            </article>
            """.trimIndent(),
            outputs = setOf(KleadOutput.HTML),
        )
        val html = result.content.requireHtml()

        assertFalse(html.contains("javascript:"), html)
        assertFalse(html.contains("data:text/html"), html)
        assertFalse(html.contains("onclick"), html)
        assertFalse(html.contains("srcdoc"), html)
        assertTrue(html.contains("data:image/png"), html)
    }

    @Test
    fun malformedHtmlBadUrlAndInvalidJsonLdDoNotCrash() = runTest {
        val result = parse(
            """<article><h1>Broken</h1><p>Still readable<script type="application/ld+json">{bad</script>""",
            url = "not a url",
            debug = true,
        )

        assertTrue(result.content.requireMarkdown().contains("Still readable"))
        assertTrue(result.debug.toString().contains("Invalid JSON-LD"))
    }

    @Test
    fun texinfoFootnoteBecomesReferenceAndDefinition() = runTest {
        val result = parse(
            """
            <article>
              <h1>Texinfo Manual</h1>
              <p>Some explanatory prose long enough to remain in the article body,
                with a marker<a class="footnote" id="DOCF1" href="#FOOT1">(1)</a> referencing a note.</p>
              <div class="footnotes-segment">
                <h3 class="footnotes-heading">Footnotes</h3>
                <h5 class="footnote-body-heading"><a id="FOOT1" href="#DOCF1">(1)</a></h5>
                <p>This is the body of the first footnote.</p>
              </div>
            </article>
            """.trimIndent(),
        )
        val markdown = result.content.requireMarkdown()

        assertTrue(markdown.contains("[^1]"), markdown)
        assertTrue(markdown.contains("This is the body of the first footnote."), markdown)
    }

    @Test
    fun metadataComesFromBasicMetaTags() = runTest {
        val result = parse(
            """
            <html><head>
              <title>Metadata title</title>
              <meta name="description" content="Metadata description">
              <meta name="author" content="Ada Example">
              <meta property="og:site_name" content="Example Journal">
            </head><body><article><p>Article body for metadata extraction.</p></article></body></html>
            """.trimIndent(),
        )

        assertEquals("Metadata title", result.metadata.title)
        assertEquals("Metadata description", result.metadata.description)
        assertEquals("Ada Example", result.metadata.author)
        assertEquals("Example Journal", result.metadata.site)
    }

    @Test
    fun relativeLinksResolveAgainstDocumentUrl() = runTest {
        val result = parse(
            "<article><p>Read the <a href=\"../guide?q=1#part\">complete guide</a> for all details.</p></article>",
            url = "https://example.com/articles/current/",
        )

        assertTrue(
            result.content.requireMarkdown().contains(
                "[complete guide](https://example.com/articles/guide?q=1#part)",
            ),
        )
    }

    @Test
    fun validJsonLdSuppliesArticleMetadata() = runTest {
        val result = parse(
            """
            <html><head><script type="application/ld+json">
              {"@type":"Article","headline":"Schema headline","author":{"name":"Schema Author"}}
            </script></head><body><article><p>Schema-backed article body.</p></article></body></html>
            """.trimIndent(),
        )

        assertEquals("Schema headline", result.metadata.title)
        assertEquals("Schema Author", result.metadata.author)
    }

    @Test
    fun embeddedMdnPageKeepsStableMainContent() = runTest {
        val result = parse(
            COMMON_MEDIUM_FIXTURE,
            url = "https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Array",
        )
        val markdown = result.content.requireMarkdown()

        assertTrue(markdown.contains("## Array"), markdown)
        assertTrue(markdown.contains("JavaScript arrays are zero-indexed"), markdown)
        assertTrue(markdown.contains("`Array.prototype.reduce()`"), markdown)
        assertFalse(markdown.contains("Last modified:"), markdown)
    }

    private suspend fun TestScope.parse(
        html: String,
        url: String = "https://example.com/article",
        outputs: Set<KleadOutput> = setOf(KleadOutput.HTML, KleadOutput.MARKDOWN),
        debug: Boolean = false,
    ): KleadResult = KleadParser.parseHtml(
        html = html,
        url = url,
        options = KleadOptions(outputs = outputs, debug = debug),
        parserDispatcher = StandardTestDispatcher(testScheduler),
    )
}

internal val COMMON_MEDIUM_FIXTURE = """
    <!DOCTYPE html>
    <html>
    <head>
      <title>Array - JavaScript | MDN</title>
      <meta name="description" content="The Array object enables storing a collection of multiple items under a single variable name.">
    </head>
    <body>
      <header><nav><a href="/">MDN</a></nav></header>
      <main>
        <h1>Array</h1>
        <p>The <strong>Array</strong> object enables storing a collection of multiple items under a single variable name, and has members for performing common array operations.</p>
        <h2 id="description"><a href="#description">Description</a></h2>
        <p>In JavaScript, arrays aren't primitives but are instead Array objects with the following core characteristics:</p>
        <ul>
          <li><strong>JavaScript arrays are resizable</strong> and <strong>can contain a mix of different data types</strong>.</li>
          <li><strong>JavaScript arrays are not associative arrays</strong> and cannot use nonnumeric strings as indexes.</li>
          <li><strong>JavaScript arrays are zero-indexed</strong>: the first element is at index 0, the second at index 1, and so on.</li>
        </ul>
        <h3 id="iterative_methods"><a href="#iterative_methods">Iterative methods</a></h3>
        <p>Several methods take functions to be called while processing the array. The length is sampled, and elements added beyond this length from within the callback are not visited.</p>
        <h3 id="generic_array_methods"><a href="#generic_array_methods">Generic Array methods</a></h3>
        <p>Array methods are generic. They access array elements through the length property and indexed elements.</p>
        <h2 id="constructor"><a href="#constructor">Constructor</a></h2>
        <dl><dt><code>Array()</code></dt><dd>Creates a new Array object.</dd></dl>
        <h2 id="static_methods"><a href="#static_methods">Static methods</a></h2>
        <dl>
          <dt><code>Array.from()</code></dt><dd>Creates a new Array instance from an iterable or array-like object.</dd>
          <dt><code>Array.isArray()</code></dt><dd>Returns true if the argument is an array.</dd>
          <dt><code>Array.of()</code></dt><dd>Creates a new Array instance with a variable number of arguments.</dd>
        </dl>
        <h2 id="instance_methods"><a href="#instance_methods">Instance methods</a></h2>
        <dl>
          <dt><code>Array.prototype.at()</code></dt><dd>Returns the array item at the given index.</dd>
          <dt><code>Array.prototype.concat()</code></dt><dd>Returns a new array joined with other arrays or values.</dd>
          <dt><code>Array.prototype.filter()</code></dt><dd>Returns elements for which the filtering function is true.</dd>
          <dt><code>Array.prototype.find()</code></dt><dd>Returns the first element satisfying the provided test.</dd>
          <dt><code>Array.prototype.map()</code></dt><dd>Returns results of invoking a function on every element.</dd>
          <dt><code>Array.prototype.push()</code></dt><dd>Adds elements to the end and returns the new length.</dd>
          <dt><code>Array.prototype.reduce()</code></dt><dd>Executes a reducer callback on each element of the array.</dd>
        </dl>
      </main>
      <footer><p>Last modified: 2025-01-15</p></footer>
    </body>
    </html>
""".trimIndent()
