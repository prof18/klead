package com.prof18.klead

import com.prof18.klead.internal.KleadParser
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.TimeSource

class CommonPerformanceSmokeTest {
    @Test
    fun printEmbeddedMediumFixtureTimings() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val options = KleadOptions(outputs = setOf(KleadOutput.HTML, KleadOutput.MARKDOWN))
        val parse: suspend () -> KleadResult = {
            KleadParser.parseHtml(
                html = COMMON_MEDIUM_FIXTURE,
                url = "https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Array",
                options = options,
                parserDispatcher = dispatcher,
            )
        }
        parse().assertBenchmarkOutput()
        val samples = sampleTimings(parse)

        println("TIMING_COMMON_MEDIUM ${samples.summary()}")
    }

    @Test
    fun printSyntheticFixtureTimings() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val options = KleadOptions(outputs = setOf(KleadOutput.HTML, KleadOutput.MARKDOWN))
        val smallHtml = "<article><p>Small article text.</p></article>"
        val longHtml = buildString {
            append("<article>")
            repeat(300) { index ->
                append("<p>Long article paragraph $index with enough words for a benchmark smoke test.</p>")
            }
            append("</article>")
        }
        val parseSmall: suspend () -> KleadResult = {
            KleadParser.parseHtml(smallHtml, "https://example.com/small", options, dispatcher)
        }
        val parseLong: suspend () -> KleadResult = {
            KleadParser.parseHtml(longHtml, "https://example.com/long", options, dispatcher)
        }
        parseSmall().assertBenchmarkOutput()
        parseLong().assertBenchmarkOutput()
        val smallSamples = sampleTimings(parseSmall)
        val longSamples = sampleTimings(parseLong)

        println("TIMING_COMMON_SMALL ${smallSamples.summary()}")
        println("TIMING_COMMON_LONG ${longSamples.summary()}")
        assertTrue(smallSamples.last() < SMALL_MAX_MILLIS, "small samples exceeded threshold: $smallSamples")
        assertTrue(longSamples.last() < LONG_MAX_MILLIS, "long samples exceeded threshold: $longSamples")
    }

    private suspend fun sampleTimings(block: suspend () -> KleadResult): List<Long> = buildList {
        repeat(SAMPLE_COUNT) {
            val mark = TimeSource.Monotonic.markNow()
            val result = block()
            val elapsedMillis = mark.elapsedNow().inWholeMilliseconds
            result.assertBenchmarkOutput()
            add(elapsedMillis)
        }
    }.sorted()

    private fun KleadResult.assertBenchmarkOutput() {
        assertTrue(content.html?.isNotBlank() == true, "benchmark HTML output was empty")
        assertTrue(content.markdown?.isNotBlank() == true, "benchmark Markdown output was empty")
    }

    private fun List<Long>.summary(): String =
        "min=${first()}ms median=${get(size / 2)}ms max=${last()}ms samples=$this"

    private companion object {
        const val SAMPLE_COUNT = 11
        const val SMALL_MAX_MILLIS = 1_000
        const val LONG_MAX_MILLIS = 5_000
    }
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
