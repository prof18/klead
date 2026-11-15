package com.prof18.klead.internal.extractors.site

import com.fleeksoft.ksoup.nodes.Element
import com.fleeksoft.ksoup.nodes.Node
import com.fleeksoft.ksoup.nodes.TextNode
import com.fleeksoft.ksoup.select.QueryParser
import com.prof18.klead.extractors.ExtractorMetadata
import com.prof18.klead.extractors.ExtractorResult
import com.prof18.klead.internal.dom.selectFirstSafe
import com.prof18.klead.internal.extractors.DomExtractor
import com.prof18.klead.internal.extractors.DomExtractorContext

/**
 * Git's own web interface renders a commit message as `<br>`-separated lines of `&nbsp;` runs and a diff
 * as one `div.diff` per line, so generic extraction turns both into prose and drops the indentation.
 * Commit and commitdiff pages are rebuilt as the subject, the message and one diff code block per file.
 */
internal object GitwebProfile : DomExtractor {
    // Probed against the document's <meta> elements only (see DocumentSignals).
    private val GITWEB_GENERATOR = QueryParser.parse("meta[name=generator][content^=gitweb/]")

    override val id: String = "gitweb"

    override fun matches(context: DomExtractorContext): Boolean = context.signals.firstMeta(GITWEB_GENERATOR) != null

    override fun extract(context: DomExtractorContext): ExtractorResult? {
        val document = context.document
        val pageBody = document.selectFirstSafe("div.page_body") ?: return null
        val title = document.selectFirstSafe("div.header a.title")?.text()?.trim()?.ifBlank { null }

        val article = Element("article")
        pageBody.commitMessageLines(title).takeIf { it.isNotEmpty() }?.let { lines ->
            article.appendElement("pre").appendElement("code").text(lines.joinToString("\n"))
        }

        val patches = document.select("div.patchset > div.patch")
        if (patches.isEmpty()) {
            article.appendChangedFiles(document.select("table.diff_tree a.list"))
        } else {
            patches.forEach { patch -> article.appendPatch(patch) }
        }
        if (article.text().isBlank()) return null

        return ExtractorResult(
            contentHtml = article.outerHtml(),
            metadata = ExtractorMetadata(
                title = title,
                author = document.select("table.object_header tr")
                    .firstOrNull { row -> row.selectFirst("td")?.text()?.trim() == "author" }
                    ?.selectFirst("a.list")
                    ?.text()
                    ?.trim()
                    ?.ifBlank { null },
            ),
        )
    }

    // A commitdiff wraps the message without its subject in `div.log`, and leaves it out when there is only
    // a subject; a commit page writes the whole message straight into the page body, before the file list.
    private fun Element.commitMessageLines(title: String?): List<String> {
        val log = children().firstOrNull { it.normalName() == "div" && it.hasClass("log") }
        if (log != null) return log.childNodes().preformattedLines().map(String::trimEnd).trimBlankLines()

        val lines = childNodes()
            .takeWhile { node -> !(node is Element && node.normalName() in BLOCK_TAGS) }
            .preformattedLines()
            .map(String::trimEnd)
            .trimBlankLines()
        return if (title != null && lines.firstOrNull()?.trim() == title) lines.drop(1).trimBlankLines() else lines
    }

    private fun Element.appendPatch(patch: Element) {
        val lines = patch.children()
            .flatMap { child -> child.diffLineElements() }
            .flatMap { it.childNodes().preformattedLines() }
        if (lines.isEmpty()) return
        appendElement("pre").appendElement("code")
            .addClass("language-diff")
            .text(lines.joinToString("\n"))
    }

    // Side-by-side diffs group lines into chunk blocks with an old and a new column. Context is repeated in
    // both columns, so it is read once; changed lines keep the removed column before the added one.
    private fun Element.diffLineElements(): List<Element> = when {
        hasClass("diff") -> listOf(this)

        hasClass("chunk_block") -> {
            val columns = children().filter { it.hasClass("old") || it.hasClass("new") }
            val read = if (hasClass("ctx")) columns.take(1) else columns
            read.flatMap { column -> column.children().filter { it.hasClass("diff") } }
        }

        else -> emptyList()
    }

    private fun Element.appendChangedFiles(files: List<Element>) {
        val paths = files.map { it.text().trim() }.filter { it.isNotEmpty() }
        if (paths.isEmpty()) return
        val list = appendElement("ul")
        paths.forEach { path -> list.appendElement("li").appendElement("code").text(path) }
    }

    // Gitweb's own line breaks are `<br>` elements; the newlines between tags are only source formatting.
    private fun List<Node>.preformattedLines(): List<String> {
        val lines = mutableListOf<String>()
        val current = StringBuilder()
        fun visit(node: Node) {
            when {
                node is TextNode -> current.append(node.getWholeText().replace("\n", "").replace('\u00A0', ' '))

                node is Element && node.normalName() == "br" -> {
                    lines += current.toString()
                    current.clear()
                }

                node is Element && node.hasClass("info") -> Unit

                else -> node.childNodes().forEach(::visit)
            }
        }
        forEach(::visit)
        if (current.isNotEmpty() || lines.isEmpty()) lines += current.toString()
        return lines
    }

    private fun List<String>.trimBlankLines(): List<String> = dropWhile { it.isBlank() }.dropLastWhile { it.isBlank() }

    private val BLOCK_TAGS = setOf("div", "table", "pre", "ul", "ol", "p", "h1", "h2", "h3", "hr")
}
