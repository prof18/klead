package com.prof18.klead

import com.prof18.klead.extractors.Extractor
import com.prof18.klead.internal.KleadParser
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest

internal val TestOutputs = setOf(KleadOutput.HTML, KleadOutput.MARKDOWN)

internal fun testOptions(
    outputs: Set<KleadOutput> = TestOutputs,
    customExtractors: List<Extractor> = emptyList(),
    debug: Boolean = false,
): KleadOptions = KleadOptions(
    outputs = outputs,
    customExtractors = customExtractors,
    debug = debug,
)

internal fun parseHtmlForTest(html: String, url: String, options: KleadOptions = testOptions()): KleadResult {
    var parsed: KleadResult? = null
    runTest {
        parsed = parseHtmlWithTestDispatcher(html = html, url = url, options = options)
    }
    return checkNotNull(parsed)
}

internal suspend fun TestScope.parseHtmlWithTestDispatcher(
    html: String,
    url: String,
    options: KleadOptions = testOptions(),
): KleadResult = KleadParser.parseHtml(
    html = html,
    url = url,
    options = options,
    parserDispatcher = StandardTestDispatcher(testScheduler),
)
