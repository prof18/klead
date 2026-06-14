package dev.defuddle

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode
import java.net.URI
import kotlin.math.max
import kotlin.time.measureTimedValue

data class DefuddleOptions(
    val markdown: Boolean = true,
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
    val debug: Map<String, String>,
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
            document.select("script, style, noscript").remove()

            val content = selectContent(document)
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

        return timed.value.copy(parseTimeMillis = max(0, timed.duration.inWholeMilliseconds))
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
