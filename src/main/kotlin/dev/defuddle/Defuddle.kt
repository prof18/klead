package dev.defuddle

import dev.defuddle.dom.cloneDocument
import dev.defuddle.dom.isDangerousUrl
import dev.defuddle.dom.parseFragment
import dev.defuddle.extractors.AppliedExtractor
import dev.defuddle.extractors.DefaultExtractors
import dev.defuddle.extractors.DefuddleHttpClient
import dev.defuddle.extractors.Extractor
import dev.defuddle.extractors.ExtractorContext
import dev.defuddle.extractors.ExtractorRegistry
import dev.defuddle.markdown.DefuddleMarkdownWriter
import dev.defuddle.content.MainContentDetector
import dev.defuddle.metadata.MetaTagItem
import dev.defuddle.metadata.MetadataExtractor
import dev.defuddle.metadata.PageMetadataExtractor
import dev.defuddle.removal.RemovalPipeline
import dev.defuddle.removal.RemovalRecord
import dev.defuddle.standardize.HtmlStandardizer
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import kotlin.math.max
import kotlin.time.measureTimedValue

data class DefuddleOptions(
    val contentSelector: String? = null,
    val extractors: List<Extractor> = DefaultExtractors.all,
    val disabledExtractors: Set<String> = emptySet(),
    val httpClient: DefuddleHttpClient? = null,
    val removeExactSelectors: Boolean = true,
    val removePartialSelectors: Boolean = true,
    val removeHiddenElements: Boolean = true,
    val removeLowScoring: Boolean = true,
    val removeSmallImages: Boolean = true,
    val removeImages: Boolean = false,
    val removeContentPatterns: Boolean = true,
    val standardize: Boolean = true,
    val markdown: Boolean = true,
    val separateMarkdown: Boolean = true,
    val debug: Boolean = false,
    val profile: Boolean = false,
)

data class DefuddleResult(
    val contentMarkdown: String,
    val contentHtml: String,
    val title: String?,
    val description: String?,
    val domain: String?,
    val favicon: String?,
    val image: String?,
    val language: String?,
    val published: String?,
    val author: String?,
    val site: String?,
    val wordCount: Int,
    val parseTimeMillis: Long,
    val metaTags: List<MetaTagItem>,
    val schemaOrgData: List<Map<String, Any?>>,
    val extractor: String?,
    val variables: Map<String, String>,
    val debug: Map<String, Any?>,
)

object Defuddle {
    fun parseHtml(
        html: String,
        url: String,
        options: DefuddleOptions = DefuddleOptions(),
    ): DefuddleResult {
        val timed = measureTimedValue {
            val document = Jsoup.parse(html, url)
            document.outputSettings().prettyPrint(false)
            prepareDocument(document)
            val appliedExtractor = ExtractorRegistry(options.extractors).extract(
                document = document,
                url = url,
                context = ExtractorContext(
                    httpClient = options.httpClient,
                    disabledExtractors = options.disabledExtractors,
                ),
            )
            val parseDocument = appliedExtractor?.result?.contentHtml
                ?.let { Jsoup.parseBodyFragment(it, url).also { parsed -> parsed.outputSettings().prettyPrint(false) } }
                ?: document
            val effectiveOptions = appliedExtractor?.result?.contentSelector
                ?.takeIf { options.contentSelector == null }
                ?.let { options.copy(contentSelector = it) }
                ?: options

            RetryController.run(effectiveOptions) { attemptOptions ->
                val result = parseInternal(
                    document = parseDocument.cloneDocument(),
                    url = url,
                    options = attemptOptions,
                    appliedExtractor = appliedExtractor,
                )
                RetryCandidate(
                    value = result,
                    wordCount = result.wordCount,
                    options = attemptOptions,
                )
            }.value
        }

        val parseTimeMillis = max(0, timed.duration.inWholeMilliseconds)
        val debug = timed.value.debug.toMutableMap()
        if (options.profile) {
            debug["profileTimings"] = mapOf("parseHtml" to parseTimeMillis)
        }
        return timed.value.copy(
            parseTimeMillis = parseTimeMillis,
            debug = debug,
        )
    }

    private fun selectContent(document: Document): Element =
        document.selectFirst("article")
            ?: document.selectFirst("main")
            ?: document.body()
            ?: Element("body")

    private fun parseInternal(
        document: Document,
        url: String,
        options: DefuddleOptions,
        appliedExtractor: AppliedExtractor?,
    ): DefuddleResult {
        val metaTags = MetadataExtractor.collectMetaTags(document)
        val schemaOrg = MetadataExtractor.extractSchemaOrg(document, options.debug)
        val detected = MainContentDetector.detect(document, options)
        val content = detected.element
        val removals = mutableListOf<RemovalRecord>()
        stripUnsafe(content)
        val metadata = PageMetadataExtractor.extract(
            document = document,
            sourceUrl = url,
            content = content,
            metaTags = metaTags,
            schemaOrg = schemaOrg,
        )
        RemovalPipeline.apply(content, options, removals, metadata.image)
        if (options.standardize) {
            HtmlStandardizer.apply(content, metadata.title)
        }
        val markdown = if (options.markdown) DefuddleMarkdownWriter.write(content, url) else ""

        return DefuddleResult(
            contentMarkdown = markdown,
            contentHtml = content.cleanOuterHtml(),
            title = metadata.title,
            description = metadata.description,
            domain = metadata.domain,
            favicon = metadata.favicon,
            image = metadata.image,
            language = metadata.language,
            published = metadata.published,
            author = metadata.author,
            site = metadata.site,
            wordCount = countBodyWords(content),
            parseTimeMillis = 0,
            metaTags = metaTags,
            schemaOrgData = schemaOrg.items,
            extractor = appliedExtractor?.name,
            variables = appliedExtractor?.result?.variables.orEmpty(),
            debug = buildDebug(options, detected.debug, schemaOrg.diagnostics, removals),
        ).withExtractorMetadata(appliedExtractor)
    }

    private fun DefuddleResult.withExtractorMetadata(appliedExtractor: AppliedExtractor?): DefuddleResult {
        val metadata = appliedExtractor?.result?.metadata ?: return this
        return copy(
            title = metadata.title ?: title,
            description = metadata.description ?: description,
            published = metadata.published ?: published,
            author = metadata.author ?: author,
            site = metadata.site ?: site,
        )
    }

    private fun prepareDocument(document: Document) {
        promoteNoscriptImages(document)
    }

    private fun buildDebug(
        options: DefuddleOptions,
        detectionDebug: dev.defuddle.content.ContentDetectionDebug,
        schemaDiagnostics: List<String>,
        removals: List<RemovalRecord>,
    ): Map<String, Any?> {
        val debug = mutableMapOf<String, Any?>(
            "unsupportedBrowserBehavior" to "Browser layout, JavaScript execution, and CSS generated content are unsupported.",
        )
        if (options.debug) {
            debug["selectedContentSelector"] = detectionDebug.selectedSelector
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
            }
        }
        return debug
    }

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
        content.select("style, noscript, frame, frameset, iframe, object, embed, applet, base").remove()

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

    private val DANGEROUS_URL_ATTRIBUTES = setOf("href", "src", "action", "formaction", "xlink:href")
}
