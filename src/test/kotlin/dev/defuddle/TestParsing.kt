package dev.defuddle

import dev.defuddle.extractors.Extractor
import dev.defuddle.internal.DefuddleParser
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest

internal val TestOutputs = setOf(DefuddleOutput.HTML, DefuddleOutput.MARKDOWN)

internal fun testOptions(
    outputs: Set<DefuddleOutput> = TestOutputs,
    customExtractors: List<Extractor> = emptyList(),
    debug: Boolean = false,
): DefuddleOptions = DefuddleOptions(
    outputs = outputs,
    customExtractors = customExtractors,
    debug = debug,
)

internal fun parseHtmlForTest(html: String, url: String, options: DefuddleOptions = testOptions()): DefuddleResult {
    var parsed: DefuddleResult? = null
    runTest {
        parsed = parseHtmlWithTestDispatcher(html = html, url = url, options = options)
    }
    return checkNotNull(parsed)
}

internal suspend fun TestScope.parseHtmlWithTestDispatcher(
    html: String,
    url: String,
    options: DefuddleOptions = testOptions(),
): DefuddleResult = DefuddleParser.parseHtml(
    html = html,
    url = url,
    options = options,
    parserDispatcher = StandardTestDispatcher(testScheduler),
)
