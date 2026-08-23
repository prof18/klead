package com.prof18.klead.internal

import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.nodes.Document
import com.fleeksoft.ksoup.nodes.Element
import com.prof18.klead.KleadContent
import com.prof18.klead.KleadMetadata
import com.prof18.klead.KleadOptions
import com.prof18.klead.KleadOutput
import com.prof18.klead.KleadResult
import com.prof18.klead.RemovalRecord
import com.prof18.klead.extractors.Extractor
import com.prof18.klead.extractors.ExtractorContext
import com.prof18.klead.extractors.ExtractorResult
import com.prof18.klead.internal.content.ContentDetectionDebug
import com.prof18.klead.internal.content.DetectedContent
import com.prof18.klead.internal.content.MainContentDetector
import com.prof18.klead.internal.dom.cloneDocument
import com.prof18.klead.internal.dom.parseKleadUri
import com.prof18.klead.internal.extractors.DefaultExtractors
import com.prof18.klead.internal.extractors.ExtractorRegistry
import com.prof18.klead.internal.extractors.site.ExtractorRemovalPipeline
import com.prof18.klead.internal.markdown.KleadMarkdownWriter
import com.prof18.klead.internal.metadata.MetadataExtractor
import com.prof18.klead.internal.metadata.PageMetadata
import com.prof18.klead.internal.metadata.PageMetadataExtractor
import com.prof18.klead.internal.metadata.SchemaOrgResult
import com.prof18.klead.internal.removal.DiscardedRemovals
import com.prof18.klead.internal.removal.RemovalPipeline
import com.prof18.klead.internal.removal.RemovalPolicy
import com.prof18.klead.internal.standardize.HtmlStandardizer
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.time.measureTimedValue

internal object KleadParser {
    private data class ParsedResult(val result: KleadResult, val wordCount: Int)

    private class ParseTimings(private val enabled: Boolean) {
        private val entries = linkedMapOf<String, Long>()

        fun <T> measure(name: String, block: () -> T): T {
            if (!enabled) return block()

            val timed = measureTimedValue(block)
            entries[name] = max(0, timed.duration.inWholeMilliseconds)
            return timed.value
        }

        fun toDebugMap(): Map<String, Long> = entries.toMap()
    }

    internal suspend fun parseHtml(
        html: String,
        url: String,
        options: KleadOptions,
        parserDispatcher: CoroutineDispatcher,
    ): KleadResult = withContext(parserDispatcher) {
        val parseContext = coroutineContext
        val checkCancelled: () -> Unit = { parseContext.ensureActive() }
        val timings = ParseTimings(options.debug)
        val retryAttempts = mutableListOf<Map<String, Any?>>()
        val timed = measureTimedValue {
            val document = timings.measure("documentParse") {
                Ksoup.parse(html, url).also {
                    it.outputSettings().prettyPrint(false)
                }
            }
            timings.measure("prepareDocument") {
                DocumentPreparation.prepare(document)
            }
            val extractorContext = ExtractorContext(
                url = url,
                host = url.hostOrNull(),
                document = document,
            )
            val extractorRegistry = timings.measure("extractorRegistry") {
                ExtractorRegistry(options.effectiveExtractors())
            }
            val matchedExtractors = timings.measure("extractorResolve") {
                extractorRegistry.resolve(context = extractorContext)
            }
            val extractorResult = timings.measure("extractorExtract") {
                extractorRegistry.extract(
                    context = extractorContext,
                    extractors = matchedExtractors,
                )
            }
            val parseDocument = extractorResult?.contentHtml?.let { contentHtml ->
                timings.measure("extractorContentParse") {
                    Ksoup.parseBodyFragment(contentHtml, url).also { parsed ->
                        parsed.outputSettings().prettyPrint(false)
                    }
                }
            } ?: document

            val retryCandidate = timings.measure("retry") {
                RetryController.run { removalPolicy ->
                    checkCancelled()
                    val attemptName = "attempt${retryAttempts.size + 1}"
                    val result = timings.measure("$attemptName.total") {
                        parseInternal(
                            document = timings.measure("$attemptName.cloneDocument") {
                                parseDocument.cloneDocument()
                            },
                            url = url,
                            options = options,
                            extractorResult = extractorResult,
                            matchedExtractors = matchedExtractors,
                            removalPolicy = removalPolicy,
                            timings = timings,
                            timingPrefix = attemptName,
                            checkCancelled = checkCancelled,
                        )
                    }
                    if (options.debug) {
                        retryAttempts += mapOf(
                            "attempt" to attemptName,
                            "wordCount" to result.wordCount,
                            "removalPolicy" to removalPolicy.toDebugMap(),
                        )
                    }
                    RetryCandidate(
                        value = result.result,
                        wordCount = result.wordCount,
                        removalPolicy = removalPolicy,
                    )
                }
            }
            retryCandidate.value
        }

        val parseTimeMillis = max(0, timed.duration.inWholeMilliseconds)
        val debug = timed.value.debug.toMutableMap()
        if (options.debug) {
            debug["parseTimeMillis"] = parseTimeMillis
            debug["timingsMillis"] = timings.toDebugMap()
            debug["retryAttempts"] = retryAttempts
        }
        timed.value.copy(
            debug = debug,
        )
    }

    private fun parseInternal(
        document: Document,
        url: String,
        options: KleadOptions,
        extractorResult: ExtractorResult?,
        matchedExtractors: List<Extractor>,
        removalPolicy: RemovalPolicy,
        timings: ParseTimings,
        timingPrefix: String,
        checkCancelled: () -> Unit = {},
    ): ParsedResult {
        val extractorContext = ExtractorContext(
            url = url,
            host = url.hostOrNull(),
            document = document,
        )
        val removals: MutableList<RemovalRecord> = if (options.debug) mutableListOf() else DiscardedRemovals
        timings.measure("$timingPrefix.preContentRemovals") {
            ExtractorRemovalPipeline.applyPreContentRemovals(document, matchedExtractors, removals)
        }

        val metaTags = timings.measure("$timingPrefix.collectMetaTags") {
            MetadataExtractor.collectMetaTags(document)
        }
        val schemaOrg = timings.measure("$timingPrefix.schemaOrg") {
            MetadataExtractor.extractSchemaOrg(document, options.debug)
        }
        val detected = timings.measure("$timingPrefix.mainContentDetection") {
            MainContentDetector.detect(
                document = document,
                extractorContentSelector = extractorResult?.contentSelector,
                schemaText = schemaOrg.contentText(),
                preferredSelectors = matchedExtractors.flatMap { it.contentSelectors },
            )
        }
        val content = detected.element
        timings.measure("$timingPrefix.mergeFootnotes") {
            ExternalFootnoteMerger.merge(document, content)
        }
        timings.measure("$timingPrefix.stripUnsafe") {
            ContentSanitizer.stripUnsafe(content)
        }
        val metadata = timings.measure("$timingPrefix.metadata") {
            PageMetadataExtractor.extract(
                document = document,
                sourceUrl = url,
                content = content,
                metaTags = metaTags,
                schemaOrg = schemaOrg,
            )
        }
        timings.measure("$timingPrefix.removalPipeline") {
            RemovalPipeline.apply(
                content = content,
                debug = removals,
                metadataImage = metadata.image,
                policy = removalPolicy,
                measure = { step, block ->
                    timings.measure("$timingPrefix.removalPipeline.$step", block)
                },
                checkCancelled = checkCancelled,
            )
        }
        timings.measure("$timingPrefix.postContentRemovals") {
            ExtractorRemovalPipeline.applyPostContentRemovals(content, matchedExtractors, removals)
        }
        val contentExtractorContext = extractorContext.copy(document = content.ownerDocument() ?: document)
        timings.measure("$timingPrefix.extractorPostProcess") {
            matchedExtractors.forEach { it.postProcess(content, contentExtractorContext, removals) }
        }
        timings.measure("$timingPrefix.htmlStandardizer") {
            HtmlStandardizer.apply(content, extractorResult?.metadata?.title ?: metadata.title)
        }
        return buildParsedResult(
            content = content,
            url = url,
            options = options,
            metadata = metadata,
            detected = detected,
            schemaOrg = schemaOrg,
            removals = removals,
            matchedExtractors = matchedExtractors,
            extractorResult = extractorResult,
            timings = timings,
            timingPrefix = timingPrefix,
        )
    }

    private fun buildParsedResult(
        content: Element,
        url: String,
        options: KleadOptions,
        metadata: PageMetadata,
        detected: DetectedContent,
        schemaOrg: SchemaOrgResult,
        removals: List<RemovalRecord>,
        matchedExtractors: List<Extractor>,
        extractorResult: ExtractorResult?,
        timings: ParseTimings,
        timingPrefix: String,
    ): ParsedResult {
        val html = if (KleadOutput.HTML in options.outputs) {
            timings.measure("$timingPrefix.htmlOutput") {
                content.cleanOuterHtml()
            }
        } else {
            null
        }
        val markdown = if (KleadOutput.MARKDOWN in options.outputs) {
            timings.measure("$timingPrefix.markdownOutput") {
                KleadMarkdownWriter.write(content, url)
            }
        } else {
            null
        }
        val wordCount = timings.measure("$timingPrefix.wordCount") {
            countBodyWords(content)
        }

        return ParsedResult(
            result = KleadResult(
                content = KleadContent(
                    html = html,
                    markdown = markdown,
                ),
                metadata = KleadMetadata(
                    title = metadata.title,
                    description = metadata.description,
                    favicon = metadata.favicon,
                    image = metadata.image,
                    author = metadata.author,
                    site = metadata.site,
                ),
                debug = buildDebug(
                    options,
                    detected.debug,
                    schemaOrg.diagnostics,
                    removals,
                    matchedExtractors.map { it.id },
                ),
            ).withExtractorMetadata(extractorResult),
            wordCount = wordCount,
        )
    }

    private fun KleadResult.withExtractorMetadata(extractorResult: ExtractorResult?): KleadResult {
        val extractorMetadata = extractorResult?.metadata ?: return this
        return copy(
            metadata = metadata.copy(
                title = extractorMetadata.title ?: metadata.title,
                description = extractorMetadata.description ?: metadata.description,
                author = extractorMetadata.author ?: metadata.author,
                site = extractorMetadata.site ?: metadata.site,
            ),
        )
    }

    private fun SchemaOrgResult.contentText(): String? = firstString("articleBody")
        ?: firstString("text")

    private fun buildDebug(
        options: KleadOptions,
        detectionDebug: ContentDetectionDebug,
        schemaDiagnostics: List<String>,
        removals: List<RemovalRecord>,
        extractorIds: List<String>,
    ): Map<String, Any?> {
        val debug = mutableMapOf<String, Any?>(
            "unsupportedBrowserBehavior" to
                "Browser layout, JavaScript execution, and CSS generated content are unsupported.",
        )
        if (options.debug) {
            debug["selectedContentSelector"] = detectionDebug.selectedSelector
            if (extractorIds.isNotEmpty()) {
                debug["extractorIds"] = extractorIds
            }
            detectionDebug.extractorContentSelector?.let {
                debug["extractorContentSelector"] = it
            }
            debug["contentCandidates"] = detectionDebug.candidates.map {
                mapOf(
                    "selector" to it.selector,
                    "score" to it.score,
                )
            }
            if (schemaDiagnostics.isNotEmpty()) {
                debug["schemaDiagnostics"] = schemaDiagnostics
            }
            if (removals.isNotEmpty()) {
                debug["removals"] = removals
                val extractorRemovals = removals.filter { it.step.startsWith("removeExtractor") }
                if (extractorRemovals.isNotEmpty()) {
                    debug["extractorRemovals"] = extractorRemovals
                }
            }
        }
        return debug
    }

    private fun String.hostOrNull(): String? = parseKleadUri(this)?.host?.lowercase()

    private fun KleadOptions.effectiveExtractors(): List<Extractor> = customExtractors + DefaultExtractors.all

    private fun RemovalPolicy.toDebugMap(): Map<String, Boolean> = mapOf(
        "removeExactSelectors" to removeExactSelectors,
        "removePartialSelectors" to removePartialSelectors,
        "removeHiddenElements" to removeHiddenElements,
        "removeLowScoring" to removeLowScoring,
        "removeContentPatterns" to removeContentPatterns,
    )

    private fun Element.cleanOuterHtml(): String =
        if (tagName() == "body" && children().isEmpty() && text().isBlank()) {
            ""
        } else {
            outerHtml().trim()
        }

    private fun countBodyWords(content: Element): Int {
        val clone = content.clone()
        clone.select("h1, h2, h3, h4, h5, h6").remove()
        return WORD_REGEX.findAll(clone.text()).count()
    }

    private val WORD_REGEX = Regex("""[\p{L}\p{N}]+(?:['-][\p{L}\p{N}]+)*""")
}
