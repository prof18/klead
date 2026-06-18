package com.prof18.klead.internal

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
import com.prof18.klead.internal.content.MainContentDetector
import com.prof18.klead.internal.dom.cloneDocument
import com.prof18.klead.internal.dom.isDangerousUrl
import com.prof18.klead.internal.dom.parseFragment
import com.prof18.klead.internal.extractors.DefaultExtractors
import com.prof18.klead.internal.extractors.ExtractorRegistry
import com.prof18.klead.internal.extractors.site.ExtractorRemovalPipeline
import com.prof18.klead.internal.markdown.KleadMarkdownWriter
import com.prof18.klead.internal.metadata.MetadataExtractor
import com.prof18.klead.internal.metadata.PageMetadataExtractor
import com.prof18.klead.internal.metadata.SchemaOrgResult
import com.prof18.klead.internal.removal.RemovalPipeline
import com.prof18.klead.internal.removal.RemovalPolicy
import com.prof18.klead.internal.standardize.HtmlStandardizer
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.net.URI
import kotlin.math.max
import kotlin.time.measureTimedValue

internal object KleadParser {
    private data class ParsedResult(val result: KleadResult, val wordCount: Int)

    internal suspend fun parseHtml(
        html: String,
        url: String,
        options: KleadOptions,
        parserDispatcher: CoroutineDispatcher,
    ): KleadResult = withContext(parserDispatcher) {
        val timed = measureTimedValue {
            val document = Jsoup.parse(html, url)
            document.outputSettings().prettyPrint(false)
            prepareDocument(document)
            val extractorContext = ExtractorContext(
                url = url,
                host = url.hostOrNull(),
                document = document,
            )
            val extractorResult = ExtractorRegistry(options.effectiveExtractors()).extract(
                context = extractorContext,
            )
            val parseDocument = extractorResult?.contentHtml
                ?.let { contentHtml ->
                    Jsoup.parseBodyFragment(contentHtml, url).also { parsed ->
                        parsed.outputSettings().prettyPrint(false)
                    }
                }
                ?: document

            RetryController.run { removalPolicy ->
                val result = parseInternal(
                    document = parseDocument.cloneDocument(),
                    url = url,
                    options = options,
                    extractorResult = extractorResult,
                    removalPolicy = removalPolicy,
                )
                RetryCandidate(
                    value = result.result,
                    wordCount = result.wordCount,
                    removalPolicy = removalPolicy,
                )
            }.value
        }

        val parseTimeMillis = max(0, timed.duration.inWholeMilliseconds)
        val debug = timed.value.debug.toMutableMap()
        if (options.debug) {
            debug["parseTimeMillis"] = parseTimeMillis
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
        removalPolicy: RemovalPolicy,
    ): ParsedResult {
        val extractorContext = ExtractorContext(
            url = url,
            host = url.hostOrNull(),
            document = document,
        )
        val matchedExtractors = ExtractorRegistry(options.effectiveExtractors()).resolve(context = extractorContext)
        val removals = mutableListOf<RemovalRecord>()
        ExtractorRemovalPipeline.applyPreContentRemovals(document, matchedExtractors, removals)

        val metaTags = MetadataExtractor.collectMetaTags(document)
        val schemaOrg = MetadataExtractor.extractSchemaOrg(document, options.debug)
        val detected = MainContentDetector.detect(
            document = document,
            extractorContentSelector = extractorResult?.contentSelector,
            schemaText = schemaOrg.contentText(),
            preferredSelectors = matchedExtractors.flatMap { it.contentSelectors },
        )
        val content = detected.element
        stripUnsafe(content)
        val metadata = PageMetadataExtractor.extract(
            document = document,
            sourceUrl = url,
            content = content,
            metaTags = metaTags,
            schemaOrg = schemaOrg,
        )
        RemovalPipeline.apply(
            content = content,
            debug = removals,
            metadataImage = metadata.image,
            policy = removalPolicy,
        )
        ExtractorRemovalPipeline.applyPostContentRemovals(content, matchedExtractors, removals)
        val contentExtractorContext = extractorContext.copy(document = content.ownerDocument() ?: document)
        matchedExtractors.forEach { it.postProcess(content, contentExtractorContext, removals) }
        HtmlStandardizer.apply(content, metadata.title)
        val requestedHtml = KleadOutput.HTML in options.outputs
        val requestedMarkdown = KleadOutput.MARKDOWN in options.outputs
        val html = if (requestedHtml) content.cleanOuterHtml() else null
        val markdown = if (requestedMarkdown) KleadMarkdownWriter.write(content, url) else null
        val wordCount = countBodyWords(content)

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

    private fun prepareDocument(document: Document) {
        promoteNoscriptImages(document)
    }

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

    private fun String.hostOrNull(): String? = runCatching { URI(this).host?.lowercase() }.getOrNull()

    private fun KleadOptions.effectiveExtractors(): List<Extractor> = customExtractors + DefaultExtractors.all

    private fun promoteNoscriptImages(document: Document) {
        for (noscript in document.select("noscript").toList()) {
            val fragmentNodes = parseFragment(noscript.html(), document.baseUri())
            val fragmentRoot = Element("fragment")
            fragmentNodes.forEach { fragmentRoot.appendChild(it) }
            val promotedImages = fragmentRoot.select("img[src]").filterNot { image ->
                isDangerousUrl(image.attr("src"))
            }
            for (image in promotedImages) {
                noscript.before(image.clone())
            }
        }
    }

    private fun stripUnsafe(content: Element) {
        content.select("script").filterNot { script ->
            script.attr("type").contains("math/tex", ignoreCase = true)
        }.forEach { it.remove() }
        content.select("style, noscript, frame, frameset, object, embed, applet, base").remove()
        content.select("iframe").filterNot(::isTrustedVideoIframe).forEach { it.remove() }

        for (element in content.select("*")) {
            for (attribute in element.attributes().asList()) {
                val key = attribute.key.lowercase()
                val value = attribute.value
                val shouldRemove = key.startsWith("on") ||
                    key == "srcdoc" ||
                    (key in DANGEROUS_URL_ATTRIBUTES && isDangerousUrl(value))
                if (shouldRemove) {
                    element.removeAttr(attribute.key)
                }
            }
        }
    }

    private fun isTrustedVideoIframe(element: Element): Boolean {
        val src = element.absUrl("src").ifBlank { element.attr("src").trim() }
        if (src.isBlank() || isDangerousUrl(src)) return false
        val uri = runCatching { URI(src) }.getOrNull() ?: return false
        if (!uri.scheme.equals("https", ignoreCase = true)) return false

        val host = uri.host?.lowercase()?.removePrefix("www.") ?: return false
        val path = uri.rawPath.orEmpty()
        return (host == "youtube.com" || host == "youtube-nocookie.com") &&
            path.startsWith("/embed/") &&
            YOUTUBE_VIDEO_ID.matches(path.removePrefix("/embed/").substringBefore('/'))
    }

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
    private val YOUTUBE_VIDEO_ID = Regex("""[A-Za-z0-9_-]{6,32}""")

    private val DANGEROUS_URL_ATTRIBUTES = setOf("href", "src", "action", "formaction", "xlink:href")
}
