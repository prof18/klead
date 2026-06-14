package dev.defuddle

import dev.defuddle.dom.cloneDocument
import dev.defuddle.dom.isDangerousUrl
import dev.defuddle.dom.parseFragment
import dev.defuddle.content.MainContentDetector
import dev.defuddle.metadata.MetaTagItem
import dev.defuddle.metadata.MetadataExtractor
import dev.defuddle.metadata.PageMetadataExtractor
import dev.defuddle.removal.RemovalPipeline
import dev.defuddle.removal.RemovalRecord
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode
import kotlin.math.max
import kotlin.time.measureTimedValue

data class DefuddleOptions(
    val contentSelector: String? = null,
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

            RetryController.run(options) { attemptOptions ->
                val result = parseInternal(
                    document = document.cloneDocument(),
                    url = url,
                    options = attemptOptions,
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
    ): DefuddleResult {
        val metaTags = MetadataExtractor.collectMetaTags(document)
        val schemaOrg = MetadataExtractor.extractSchemaOrg(document, options.debug)
        val detected = MainContentDetector.detect(document, options)
        val content = detected.element
        val removals = mutableListOf<RemovalRecord>()
        stripUnsafe(content)
        RemovalPipeline.apply(content, options, removals)
        val metadata = PageMetadataExtractor.extract(
            document = document,
            sourceUrl = url,
            content = content,
            metaTags = metaTags,
            schemaOrg = schemaOrg,
        )
        val markdown = if (options.markdown) MarkdownWriter.write(content) else ""

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
            debug = buildDebug(options, detected.debug, schemaOrg.diagnostics, removals),
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

private object MarkdownWriter {
    fun write(root: Element): String =
        root.childNodes()
            .flatMap(::writeNode)
            .joinToString("\n\n")
            .trim()

    private fun writeNode(node: Node): List<String> =
        when (node) {
            is TextNode -> node.text().normalizeWhitespace().takeIf { it.isNotBlank() }?.let(::listOf).orEmpty()
            is Element -> writeElement(node)
            else -> emptyList()
        }

    private fun writeElement(element: Element): List<String> =
        when (element.normalName()) {
            "h1" -> listOf("# ${element.text().normalizeWhitespace()}").filterNotBlank()
            "h2" -> listOf("## ${element.text().normalizeWhitespace()}").filterNotBlank()
            "h3" -> listOf("### ${element.text().normalizeWhitespace()}").filterNotBlank()
            "h4" -> listOf("#### ${element.text().normalizeWhitespace()}").filterNotBlank()
            "h5" -> listOf("##### ${element.text().normalizeWhitespace()}").filterNotBlank()
            "h6" -> listOf("###### ${element.text().normalizeWhitespace()}").filterNotBlank()
            "p" -> listOf(element.text().normalizeWhitespace()).filterNotBlank()
            "br" -> listOf("\n")
            else -> element.childNodes().flatMap(::writeNode)
        }

    private fun List<String>.filterNotBlank(): List<String> = filter { it.isNotBlank() }

    private fun String.normalizeWhitespace(): String =
        replace(Regex("""\s+"""), " ").trim()
}
