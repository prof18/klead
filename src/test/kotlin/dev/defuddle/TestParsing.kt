package dev.defuddle

import dev.defuddle.extractors.Extractor

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

internal fun parseHtmlForTest(html: String, url: String, options: DefuddleOptions = testOptions()): DefuddleResult =
    Defuddle.parseHtml(html = html, url = url, options = options)

internal suspend fun parseHtmlAsyncForTest(
    html: String,
    url: String,
    options: DefuddleOptions = testOptions(),
): DefuddleResult = Defuddle.parseHtmlAsync(html = html, url = url, options = options)
