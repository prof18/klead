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
import com.prof18.klead.internal.media.TrustedEmbeds
import com.prof18.klead.internal.metadata.MetadataExtractor
import com.prof18.klead.internal.metadata.PageMetadataExtractor
import com.prof18.klead.internal.metadata.SchemaOrgResult
import com.prof18.klead.internal.removal.RemovalPipeline
import com.prof18.klead.internal.removal.RemovalPolicy
import com.prof18.klead.internal.standardize.HtmlStandardizer
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.nodes.Comment
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
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
            val extractorRegistry = ExtractorRegistry(options.effectiveExtractors())
            val matchedExtractors = extractorRegistry.resolve(context = extractorContext)
            val extractorResult = extractorRegistry.extract(
                context = extractorContext,
                extractors = matchedExtractors,
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
                    matchedExtractors = matchedExtractors,
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
        matchedExtractors: List<Extractor>,
        removalPolicy: RemovalPolicy,
    ): ParsedResult {
        val extractorContext = ExtractorContext(
            url = url,
            host = url.hostOrNull(),
            document = document,
        )
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
        mergeExternalFootnoteBlocks(document, content)
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
        HtmlStandardizer.apply(content, extractorResult?.metadata?.title ?: metadata.title)
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
        hydrateReactStreamedSegments(document)
    }

    private fun hydrateReactStreamedSegments(document: Document) {
        val segmentMoves = document.select("script")
            .flatMap { script ->
                REACT_STREAM_SEGMENT_CALL.findAll(script.data()).map { match ->
                    match.groupValues[1] to match.groupValues[2]
                }
            }

        for ((templateId, segmentId) in segmentMoves) {
            val template = document.getElementById(templateId) ?: continue
            val segment = document.getElementById(segmentId) ?: continue
            removeReactFallbackAfter(template)
            segment.childNodes().toList().forEach { node ->
                template.before(node.clone())
            }
            template.remove()
            segment.remove()
        }
    }

    private fun removeReactFallbackAfter(template: Element) {
        var node = template.nextSibling()
        var nestedBoundaryDepth = 0
        while (node != null) {
            val next = node.nextSibling()
            if (node.isReactBoundaryEnd() && nestedBoundaryDepth == 0) return
            if (node.isReactBoundaryStart()) {
                nestedBoundaryDepth++
            } else if (node.isReactBoundaryEnd()) {
                nestedBoundaryDepth--
            }
            node.remove()
            node = next
        }
    }

    private fun Node.isReactBoundaryStart(): Boolean = this is Comment && data.trim() == "$?"

    private fun Node.isReactBoundaryEnd(): Boolean = this is Comment && data.trim() == "/$"

    private fun mergeExternalFootnoteBlocks(document: Document, content: Element) {
        val referencedNumbers = content.select("sup")
            .mapNotNull { it.text().normalizedFootnoteNumber() }
            .toSet()
        if (referencedNumbers.isEmpty()) return

        document.select("section, aside, div")
            .filterNot { it.isInside(content) }
            .filter { it.isExternalFootnoteBlock(referencedNumbers) }
            .forEach { content.appendChild(it.clone()) }
    }

    private fun Element.isExternalFootnoteBlock(referencedNumbers: Set<String>): Boolean {
        val hints = "${id()} ${className()} ${attributes().asList().joinToString(" ") { it.value }}".lowercase()
        if (!EXTERNAL_FOOTNOTE_HINT_PATTERN.containsMatchIn(hints)) return false

        val definitionNumbers = select("p, li")
            .mapNotNull { it.leadingFootnoteDefinitionNumber() }
            .toSet()
        return definitionNumbers.any { it in referencedNumbers }
    }

    private fun Element.leadingFootnoteDefinitionNumber(): String? {
        val marker = children().firstOrNull() ?: return null
        return when (marker.normalName()) {
            "sup", "span", "a" -> marker.text().normalizedFootnoteNumber()
            else -> null
        }
    }

    private fun Element.isInside(root: Element): Boolean = this === root || parents().any { it === root }

    private fun String.normalizedFootnoteNumber(): String? = trim()
        .trim('[', ']')
        .takeIf { it.matches(FOOTNOTE_NUMBER_PATTERN) }

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
                val altCaption = noscript.nextJsAltCaptionForPromotedImage(image)
                noscript.before(image.clone())
                if (altCaption != null) {
                    noscript.before(Element("span").text(altCaption))
                }
            }
        }
    }

    private fun Element.nextJsAltCaptionForPromotedImage(image: Element): String? {
        if (!image.hasAttr("data-nimg")) return null
        if (parents().any { it.normalName() == "figure" && it.selectFirst("figcaption") != null }) return null

        val placeholder = previousElementSibling()
            ?.takeIf { it.normalName() == "img" && it.hasAttr("data-nimg") }
            ?: return null

        return placeholder.attr("alt").trim().ifBlank { image.attr("alt").trim() }.ifBlank { null }
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
        return TrustedEmbeds.isTrustedIframeSrc(src)
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
    private val REACT_STREAM_SEGMENT_CALL = Regex(
        """\${'$'}RC\(\s*["']([^"']+)["']\s*,\s*["']([^"']+)["']\s*\)""",
    )
    private val FOOTNOTE_NUMBER_PATTERN = Regex("""\d{1,4}""")
    private val EXTERNAL_FOOTNOTE_HINT_PATTERN = Regex("""(?i)(footnotes?|endnotes?)""")
    private val DANGEROUS_URL_ATTRIBUTES = setOf("href", "src", "action", "formaction", "xlink:href")
}
