package dev.defuddle

import dev.defuddle.content.MainContentDetector
import dev.defuddle.dom.cloneDocument
import dev.defuddle.dom.isDangerousUrl
import dev.defuddle.dom.parseFragment
import dev.defuddle.extractors.Extractor
import dev.defuddle.extractors.ExtractorContext
import dev.defuddle.extractors.ExtractorRegistry
import dev.defuddle.extractors.ExtractorResult
import dev.defuddle.extractors.site.ExtractorRemovalPipeline
import dev.defuddle.markdown.DefuddleMarkdownWriter
import dev.defuddle.metadata.MetadataExtractor
import dev.defuddle.metadata.PageMetadataExtractor
import dev.defuddle.removal.RemovalPipeline
import dev.defuddle.removal.RemovalPolicy
import dev.defuddle.removal.RemovalRecord
import dev.defuddle.standardize.HtmlStandardizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.net.URI
import kotlin.math.max
import kotlin.time.measureTimedValue

data class DefuddleOptions(
    val customExtractors: List<Extractor> = emptyList(),
    val markdown: Boolean = true,
    val debug: Boolean = false,
)

data class DefuddleResult(
    val contentMarkdown: String,
    val contentHtml: String,
    val title: String?,
    val description: String?,
    val favicon: String?,
    val image: String?,
    val author: String?,
    val site: String?,
    val wordCount: Int,
    val parseTimeMillis: Long,
    val variables: Map<String, String>,
    val debug: Map<String, Any?>,
)

object Defuddle {
    fun parseHtml(html: String, url: String, options: DefuddleOptions = DefuddleOptions()): DefuddleResult =
        runBlocking {
            parseHtmlAsync(html = html, url = url, options = options)
        }

    suspend fun parseHtmlAsync(
        html: String,
        url: String,
        options: DefuddleOptions = DefuddleOptions(),
    ): DefuddleResult = withContext(Dispatchers.Default) {
        val timed = measureTimedValue {
            val document = Jsoup.parse(html, url)
            document.outputSettings().prettyPrint(false)
            prepareDocument(document)
            val extractorContext = ExtractorContext(
                url = url,
                host = url.hostOrNull(),
                document = document,
            )
            val extractorResult = ExtractorRegistry(options.effectiveExtractors()).extract(context = extractorContext)
            val parseDocument = extractorResult?.contentHtml
                ?.let { Jsoup.parseBodyFragment(it, url).also { parsed -> parsed.outputSettings().prettyPrint(false) } }
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
                    value = result,
                    wordCount = result.wordCount,
                    removalPolicy = removalPolicy,
                )
            }.value
        }

        val parseTimeMillis = max(0, timed.duration.inWholeMilliseconds)
        val debug = timed.value.debug.toMutableMap()
        if (options.debug) {
            debug["profileTimings"] = mapOf("parseHtml" to parseTimeMillis)
        }
        timed.value.copy(
            parseTimeMillis = parseTimeMillis,
            debug = debug,
        )
    }

    private fun parseInternal(
        document: Document,
        url: String,
        options: DefuddleOptions,
        extractorResult: ExtractorResult?,
        removalPolicy: RemovalPolicy,
    ): DefuddleResult {
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
        val markdown = if (options.markdown) DefuddleMarkdownWriter.write(content, url) else ""

        return DefuddleResult(
            contentMarkdown = markdown,
            contentHtml = content.cleanOuterHtml(),
            title = metadata.title,
            description = metadata.description,
            favicon = metadata.favicon,
            image = metadata.image,
            author = metadata.author,
            site = metadata.site,
            wordCount = countBodyWords(content),
            parseTimeMillis = 0,
            variables = extractorResult?.variables.orEmpty(),
            debug = buildDebug(
                options,
                detected.debug,
                schemaOrg.diagnostics,
                removals,
                matchedExtractors.map { it.id },
            ),
        ).withExtractorMetadata(extractorResult)
    }

    private fun DefuddleResult.withExtractorMetadata(extractorResult: ExtractorResult?): DefuddleResult {
        val metadata = extractorResult?.metadata ?: return this
        return copy(
            title = metadata.title ?: title,
            description = metadata.description ?: description,
            author = metadata.author ?: author,
            site = metadata.site ?: site,
        )
    }

    private fun dev.defuddle.metadata.SchemaOrgResult.contentText(): String? = firstString("articleBody")
        ?: firstString("text")

    private fun prepareDocument(document: Document) {
        promoteNoscriptImages(document)
    }

    private fun buildDebug(
        options: DefuddleOptions,
        detectionDebug: dev.defuddle.content.ContentDetectionDebug,
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

    private fun DefuddleOptions.effectiveExtractors(): List<Extractor> =
        customExtractors + dev.defuddle.extractors.DefaultExtractors.all

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
