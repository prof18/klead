package dev.defuddle.metadata

import dev.defuddle.dom.resolveUrl
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.net.URI

data class PageMetadata(
    val title: String?,
    val description: String?,
    val domain: String?,
    val favicon: String?,
    val image: String?,
    val language: String?,
    val published: String?,
    val author: String?,
    val site: String?,
)

object PageMetadataExtractor {
    fun extract(
        document: Document,
        sourceUrl: String,
        content: Element?,
        metaTags: List<MetaTagItem>,
        schemaOrg: SchemaOrgResult,
    ): PageMetadata {
        val canonicalUrl = document.selectFirst("link[rel=canonical]")?.absUrl("href")?.ifBlank { null }
        val metadataBaseUrl = canonicalUrl ?: sourceUrl
        val site = cleanValue(metaTags.firstContent("og:site_name", "application-name"))
            ?: cleanValue(schemaOrg.firstString("publisher.name"))
        val domain = parseDomain(canonicalUrl ?: sourceUrl)
        val h1 = content?.selectFirst("h1")?.text()?.trim()?.ifBlank { null }

        return PageMetadata(
            title = extractTitle(document, metaTags, schemaOrg, site, domain, h1),
            description = cleanValue(metaTags.firstContent("description", "og:description", "twitter:description")),
            domain = domain,
            favicon = extractFavicon(document, metadataBaseUrl),
            image = extractImage(metaTags, schemaOrg, metadataBaseUrl),
            language = extractLanguage(document, metaTags, schemaOrg),
            published = extractPublished(document, content, metaTags, schemaOrg),
            author = extractAuthor(document, content, metaTags, schemaOrg),
            site = site,
        )
    }

    private fun extractTitle(
        document: Document,
        metaTags: List<MetaTagItem>,
        schemaOrg: SchemaOrgResult,
        site: String?,
        domain: String?,
        h1: String?,
    ): String? {
        val candidates = listOf(
            metaTags.firstContent("og:title"),
            metaTags.firstContent("twitter:title"),
            schemaOrg.firstString("headline"),
            metaTags.firstContent("title"),
            metaTags.firstContent("sailthru.title"),
            document.title().ifBlank { null },
            h1,
        )

        return candidates
            .asSequence()
            .mapNotNull { cleanTitle(it, site, domain) }
            .firstOrNull()
            ?: cleanTitle(h1, site, domain)
    }

    private fun extractAuthor(
        document: Document,
        content: Element?,
        metaTags: List<MetaTagItem>,
        schemaOrg: SchemaOrgResult,
    ): String? {
        val citationAuthors = metaTags.contents("citation_author").mapNotNull(::cleanAuthor)
        if (citationAuthors.isNotEmpty()) return citationAuthors.joinToString(", ")

        val candidates = listOf(
            metaTags.firstContent("sailthru.author"),
            metaTags.firstContent("article:author"),
            metaTags.firstContent("author"),
            metaTags.firstContent("byl"),
            metaTags.firstContent("authorList"),
            metaTags.firstContent("dc.creator"),
            schemaOrg.firstString("author.name"),
            content?.selectFirst("a[rel~=author], address[rel~=author]")?.text(),
            content?.selectFirst(".byline, .author, [class*=author]")?.text(),
            h1SiblingByline(content),
            document.selectFirst("a[rel~=author], address[rel~=author]")?.text(),
        )

        return candidates.firstNotNullOfOrNull(::cleanAuthor)
    }

    private fun extractPublished(
        document: Document,
        content: Element?,
        metaTags: List<MetaTagItem>,
        schemaOrg: SchemaOrgResult,
    ): String? {
        val candidates = listOf(
            schemaOrg.firstString("datePublished"),
            schemaOrg.firstString("dateCreated"),
            metaTags.firstContent("article:published_time"),
            metaTags.firstContent("datePublished"),
            metaTags.firstContent("pubdate"),
            metaTags.firstContent("date"),
            document.selectFirst("time[datetime]")?.attr("datetime"),
            h1SiblingDate(content),
        )
        return candidates.firstNotNullOfOrNull(::cleanValue)
    }

    private fun extractImage(
        metaTags: List<MetaTagItem>,
        schemaOrg: SchemaOrgResult,
        baseUrl: String,
    ): String? {
        val raw = schemaOrg.firstString("image.url")
            ?: schemaOrg.firstString("image")
            ?: metaTags.firstContent("og:image", "twitter:image")
        return raw?.let { resolveUrl(baseUrl, it).ifBlank { null } }
    }

    private fun extractFavicon(
        document: Document,
        baseUrl: String,
    ): String? =
        document.selectFirst("link[rel~=(?i)^(shortcut icon|icon)$]")
            ?.attr("href")
            ?.let { resolveUrl(baseUrl, it) }
            ?.ifBlank { null }

    private fun extractLanguage(
        document: Document,
        metaTags: List<MetaTagItem>,
        schemaOrg: SchemaOrgResult,
    ): String? =
        document.selectFirst("html")?.attr("lang")?.trim()?.ifBlank { null }
            ?: metaTags.firstContent("content-language", "og:locale")
            ?: schemaOrg.firstString("inLanguage")

    private fun h1SiblingByline(content: Element?): String? {
        val h1 = content?.selectFirst("h1") ?: return null
        return h1.nextElementSiblings()
            .take(3)
            .map { it.text() }
            .firstOrNull { it.trim().startsWith("by ", ignoreCase = true) }
    }

    private fun h1SiblingDate(content: Element?): String? {
        val h1 = content?.selectFirst("h1") ?: return null
        return h1.nextElementSiblings()
            .take(4)
            .map { it.text().trim() }
            .firstOrNull { DATE_HINT_REGEX.containsMatchIn(it) }
    }

    private fun cleanTitle(
        value: String?,
        site: String?,
        domain: String?,
    ): String? {
        var title = cleanValue(value) ?: return null
        if (isPlaceholder(title)) return null

        for (brand in listOfNotNull(site, domain)) {
            if (title.equals(brand, ignoreCase = true)) return null
            title = title
                .replace(Regex("""\s+[|:\-–—]\s+${Regex.escape(brand)}$""", RegexOption.IGNORE_CASE), "")
                .replace(Regex("""^${Regex.escape(brand)}\s+[|:\-–—]\s+""", RegexOption.IGNORE_CASE), "")
                .trim()
        }

        return title.takeUnless { isPlaceholder(it) }
    }

    private fun cleanAuthor(value: String?): String? {
        val cleaned = cleanValue(value)
            ?.replace(Regex("""^by\s+""", RegexOption.IGNORE_CASE), "")
            ?.replace(Regex("""\s+and\s+""", RegexOption.IGNORE_CASE), ", ")
            ?.trim()
            ?: return null
        if (isPlaceholder(cleaned)) return null
        if (cleaned.equals("admin", ignoreCase = true)) return null
        if (cleaned.startsWith("http://") || cleaned.startsWith("https://")) return null
        if (cleaned.length > 120) return null
        return cleaned
    }

    private fun cleanValue(value: String?): String? =
        value
            ?.replace(Regex("""\s+"""), " ")
            ?.trim()
            ?.takeIf { it.isNotBlank() }

    private fun isPlaceholder(value: String): Boolean =
        value.lowercase() in PLACEHOLDERS

    private fun parseDomain(url: String): String? =
        runCatching { URI(url).host?.removePrefix("www.") }
            .getOrNull()
            ?.ifBlank { null }

    private fun List<MetaTagItem>.firstContent(vararg keys: String): String? {
        for (key in keys) {
            val match = firstOrNull { tag ->
                tag.name.equals(key, ignoreCase = true) ||
                    tag.property.equals(key, ignoreCase = true)
            }
            val content = cleanValue(match?.content)
            if (content != null) return content
        }
        return null
    }

    private fun List<MetaTagItem>.contents(key: String): List<String> =
        filter { tag ->
            tag.name.equals(key, ignoreCase = true) ||
                tag.property.equals(key, ignoreCase = true)
        }.mapNotNull { cleanValue(it.content) }

    private val PLACEHOLDERS = setOf(
        "untitled",
        "undefined",
        "null",
        "home",
        "homepage",
        "n/a",
    )

    private val DATE_HINT_REGEX = Regex(
        """\b(?:\d{4}-\d{1,2}-\d{1,2}|(?:jan|feb|mar|apr|may|jun|jul|aug|sep|sept|oct|nov|dec)[a-z]*\s+\d{1,2},?\s+\d{4})\b""",
        RegexOption.IGNORE_CASE,
    )
}
