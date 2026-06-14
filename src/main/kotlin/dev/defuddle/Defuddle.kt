package dev.defuddle

import dev.defuddle.dom.isDangerousUrl
import dev.defuddle.dom.parseFragment
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode
import java.net.URI
import kotlin.math.max
import kotlin.time.measureTimedValue

data class DefuddleOptions(
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
    val metaTags: Map<String, String>,
    val schemaOrgData: List<Map<String, String>>,
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

            val content = selectContent(document)
            stripUnsafe(content)
            val title = firstText(content.selectFirst("h1"), document.title())
            val description = document.firstMetaContent("description", "og:description")
            val markdown = if (options.markdown) MarkdownWriter.write(content) else ""

            DefuddleResult(
                contentMarkdown = markdown,
                contentHtml = content.cleanOuterHtml(),
                title = title,
                description = description,
                domain = parseDomain(url),
                favicon = document.selectFirst("link[rel~=(?i)^(shortcut icon|icon)$]")?.absUrl("href")?.ifBlank { null },
                image = document.firstMetaContent("og:image", "twitter:image"),
                language = document.selectFirst("html")?.attr("lang")?.ifBlank { null },
                published = document.firstMetaContent("article:published_time", "date", "pubdate"),
                author = document.firstMetaContent("author", "article:author"),
                site = document.firstMetaContent("og:site_name", "application-name"),
                wordCount = countBodyWords(content),
                parseTimeMillis = 0,
                metaTags = document.collectMetaTags(),
                schemaOrgData = emptyList(),
                debug = mapOf(
                    "unsupportedBrowserBehavior" to "Browser layout, JavaScript execution, and CSS generated content are unsupported.",
                ),
            )
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

    private fun firstText(
        element: Element?,
        fallback: String,
    ): String? = element?.text()?.ifBlank { null } ?: fallback.ifBlank { null }

    private fun prepareDocument(document: Document) {
        promoteNoscriptImages(document)
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

    private fun Document.firstMetaContent(vararg names: String): String? {
        for (name in names) {
            val selector = "meta[name=$name], meta[property=$name]"
            val content = selectFirst(selector)?.attr("content")?.trim()
            if (!content.isNullOrBlank()) return content
        }
        return null
    }

    private fun Document.collectMetaTags(): Map<String, String> =
        select("meta").mapNotNull { meta ->
            val key = meta.attr("name").ifBlank { meta.attr("property") }.ifBlank { return@mapNotNull null }
            val value = meta.attr("content").ifBlank { return@mapNotNull null }
            key to value
        }.toMap()

    private fun Element.cleanOuterHtml(): String =
        if (tagName() == "body" && children().isEmpty() && text().isBlank()) {
            ""
        } else {
            outerHtml().trim()
        }

    private fun parseDomain(url: String): String? =
        runCatching { URI(url).host?.removePrefix("www.") }
            .getOrNull()
            ?.ifBlank { null }

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
