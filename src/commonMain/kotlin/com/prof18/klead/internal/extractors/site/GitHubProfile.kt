package com.prof18.klead.internal.extractors.site

import com.fleeksoft.ksoup.nodes.Element
import com.prof18.klead.extractors.Extractor
import com.prof18.klead.extractors.ExtractorContext
import com.prof18.klead.extractors.ExtractorMetadata
import com.prof18.klead.extractors.ExtractorResult
import com.prof18.klead.internal.dom.appendChildNodesFrom
import com.prof18.klead.internal.dom.isoDatePart
import com.prof18.klead.internal.dom.parseKleadUri
import com.prof18.klead.internal.dom.textTrimmedOrNull
import com.prof18.klead.internal.dom.toAbsoluteSiteUrl

internal object GitHubProfile : Extractor {
    override val id: String = "github"
    override val domains: Set<String> = setOf("github.com")

    override fun extract(context: ExtractorContext): ExtractorResult? =
        extractPullRequest(context) ?: extractIssue(context)

    private fun extractPullRequest(context: ExtractorContext): ExtractorResult? {
        if (!context.isPullRequestPage()) return null
        val bodies = context.document.select(".pull-discussion-timeline .comment-body.markdown-body")
        val body = bodies.firstOrNull() ?: return null

        val article = Element("article")
        article.appendChildNodesFrom(body)

        val comments = bodies.drop(1)
            .mapNotNull { body -> body.githubComment() }
        if (comments.isNotEmpty()) {
            article.appendElement("hr")
            article.appendElement("h2").text("Comments")
            comments.forEach { comment ->
                article.appendComment(comment)
            }
        }

        return ExtractorResult(
            contentHtml = article.outerHtml(),
            metadata = ExtractorMetadata(
                title = context.document.title().ifBlank { null },
                author = context.document.selectFirst(".pull-discussion-timeline .author")
                    ?.text()
                    ?.trim()
                    ?.ifBlank { null },
                site = context.repositorySiteName(),
            ),
        )
    }

    private fun extractIssue(context: ExtractorContext): ExtractorResult? {
        val issueBody = context.document.selectFirst("""[data-testid="issue-body"]""") ?: return null
        val body = issueBody.selectFirst("""[data-testid="issue-body-viewer"] [data-testid="markdown-body"]""")
            ?: issueBody.selectFirst("""[data-testid="markdown-body"]""")
            ?: return null

        val author = issueBody.selectFirst("""[data-testid="issue-body-header-author"]""")
        val authorName = author?.textTrimmedOrNull()
        val article = Element("article")
        if (authorName != null) {
            article.appendElement("p")
                .appendElement("a")
                .attr("href", author?.attr("href").orEmpty().toGitHubUrl())
                .text(authorName)
        }

        issueBody.selectFirst("""[data-testid="comment-author-association"]""")
            ?.text()
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.let { association ->
                article.appendElement("p").text(association)
            }

        val content = body.clone()
        content.cleanGitHubIssueBody()
        article.appendChildNodesFrom(content)

        if (article.text().isBlank()) return null
        return ExtractorResult(
            contentHtml = article.outerHtml(),
            contentSelector = "article",
            metadata = ExtractorMetadata(
                title = context.document.githubIssueTitle(),
                author = authorName,
                site = GITHUB_SITE_NAME,
            ),
        )
    }

    private fun com.fleeksoft.ksoup.nodes.Document.githubIssueTitle(): String? {
        val rawTitle = selectFirst("""meta[property="og:title"]""")
            ?.attr("content")
            ?.ifBlank { null }
            ?: title().ifBlank { null }
        return rawTitle
            ?.replace(GITHUB_REPOSITORY_TITLE_SUFFIX, "")
            ?.trim()
            ?.ifBlank { null }
    }

    private fun ExtractorContext.isPullRequestPage(): Boolean = url.orEmpty().contains("/pull/") ||
        document.selectFirst(".pull-discussion-timeline") != null

    private fun Element.githubComment(): GitHubComment? {
        val container = parents().firstOrNull { it.hasClass("js-comment") }
            ?: parents().firstOrNull { it.hasClass("timeline-comment-group") }
            ?: return null
        val author = container.selectFirst(".author")?.text()?.trim().orEmpty()
        val date = container.selectFirst("relative-time[datetime]")
            ?.attr("datetime")
            ?.isoDatePart()
            .orEmpty()
        if (author.isBlank() || date.isBlank()) return null
        return GitHubComment(author = author, date = date, body = this)
    }

    private fun Element.appendComment(comment: GitHubComment) {
        val quote = appendElement("blockquote")
        quote.appendElement("p").also { header ->
            header.appendElement("strong").text(comment.author)
            header.appendText(" · ${comment.date}")
        }
        quote.appendChildNodesFrom(comment.body)
    }

    private fun ExtractorContext.repositorySiteName(): String {
        val repositoryFromUrl = runCatching {
            parseKleadUri(url.orEmpty())?.path
                .orEmpty()
                .trim('/')
                .split('/')
                .take(2)
                .takeIf { it.size == 2 }
                ?.joinToString("/")
        }.getOrNull()
        if (repositoryFromUrl != null) return "GitHub - $repositoryFromUrl"

        val title = document.title()
        val repository = title.substringAfterLast(" · ", missingDelimiterValue = "").trim()
        return if ('/' in repository) {
            "GitHub - $repository"
        } else {
            "GitHub"
        }
    }

    private fun Element.cleanGitHubIssueBody() {
        select("clipboard-copy, .zeroclipboard-container, [role=toolbar], [aria-label=Reactions]").remove()
        convertEmbeddedBlobPreviews()
        select("pre, code, .highlight, .snippet-clipboard-content").forEach { element ->
            element.removeAttr("class")
            element.removeAttr("data-lang")
            element.removeAttr("data-language")
            element.removeAttr("language")
        }
        select("p").filter { paragraph ->
            paragraph.text().isBlank() && paragraph.children().isEmpty()
        }.forEach { it.remove() }
    }

    private fun Element.convertEmbeddedBlobPreviews() {
        select("td.blob-code-inner").mapNotNull { cell ->
            cell.parents().firstOrNull { parent -> parent.hasClass("Box") }
        }.distinct().forEach { preview ->
            val lines = preview.select("td.blob-code-inner")
                .map { cell -> cell.wholeText().replace('\u00A0', ' ').trimEnd() }
                .stripCommonLeadingSpaces()
            val codeText = lines.joinToString("\n").trim('\n')
            if (codeText.isBlank()) {
                preview.remove()
                return@forEach
            }
            val pre = Element("pre")
            pre.appendElement("code").text(codeText)
            preview.replaceWith(pre)
        }
    }

    private fun List<String>.stripCommonLeadingSpaces(): List<String> {
        val indent = filter { it.isNotBlank() }
            .minOfOrNull { line -> line.takeWhile { it == ' ' }.length }
            ?: return this
        if (indent == 0) return this
        return map { line ->
            if (line.length >= indent) line.drop(indent) else line
        }
    }

    private fun String.toGitHubUrl(): String = if (isBlank()) "https://github.com" else toAbsoluteSiteUrl("github.com")

    private data class GitHubComment(val author: String, val date: String, val body: Element)

    private val GITHUB_REPOSITORY_TITLE_SUFFIX = Regex("""\s+·\s+[^·\s]+/[^·\s]+$""")
    private const val GITHUB_SITE_NAME = "GitHub"
}
