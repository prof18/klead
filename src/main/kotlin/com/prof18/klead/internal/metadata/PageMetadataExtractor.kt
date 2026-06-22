package com.prof18.klead.internal.metadata

import com.prof18.klead.internal.dom.resolveUrl
import com.prof18.klead.internal.dom.textTrimmedOrNull
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.net.URI

internal data class PageMetadata(
    val title: String?,
    val description: String?,
    val favicon: String?,
    val image: String?,
    val author: String?,
    val site: String?,
)

internal object PageMetadataExtractor {
    private data class TitleResult(val title: String?, val detectedSiteName: String?)

    fun extract(
        document: Document,
        sourceUrl: String,
        content: Element?,
        metaTags: List<MetaTagItem>,
        schemaOrg: SchemaOrgResult,
    ): PageMetadata {
        val canonicalUrl = document.selectFirst("link[rel=canonical]")?.absUrl("href")?.ifBlank { null }
        val metadataBaseUrl = canonicalUrl ?: sourceUrl
        val canonicalDomain = canonicalUrl?.let(::parseDomain)
        val h1 = document.selectFirst("h1")?.textTrimmedOrNull()
        val siteName = extractSiteName(metaTags, schemaOrg)
        val author = extractAuthor(document, content, metaTags, schemaOrg)
        val titleResult = extractTitle(document, metaTags, schemaOrg, siteName, author, h1)
        val authorAsSite = author
            ?.takeUnless { "," in it }
            ?.takeIf { it.isNotBlank() }
        val site = siteName
            ?: titleResult.detectedSiteName
            ?: authorAsSite
            ?: canonicalDomain

        return PageMetadata(
            title = titleResult.title,
            description = listOf(
                metaTags.firstContent("description"),
                metaTags.firstContent("og:description"),
                metaTags.firstContent("twitter:description"),
            ).firstNotNullOfOrNull(::cleanNonPlaceholder),
            favicon = extractFavicon(document, metadataBaseUrl),
            image = extractImage(metaTags, schemaOrg, metadataBaseUrl),
            author = author,
            site = site,
        )
    }

    private fun extractSiteName(metaTags: List<MetaTagItem>, schemaOrg: SchemaOrgResult): String? {
        val candidate = listOf(
            schemaOrg.firstString("publisher.name"),
            metaTags.firstContent("og:site_name"),
            schemaOrg.firstString("WebSite.name"),
            schemaOrg.firstString("sourceOrganization.name"),
            metaTags.firstContent("copyright"),
            schemaOrg.firstString("copyrightHolder.name"),
            schemaOrg.firstString("isPartOf.name"),
            metaTags.firstContent("application-name"),
        ).firstNotNullOfOrNull(::cleanNonPlaceholder)

        return candidate?.takeIf { it.wordCount() <= 6 }
    }

    private fun extractTitle(
        document: Document,
        metaTags: List<MetaTagItem>,
        schemaOrg: SchemaOrgResult,
        site: String?,
        author: String?,
        h1: String?,
    ): TitleResult {
        val candidates = listOf(
            metaTags.firstContent("og:title"),
            metaTags.firstContent("twitter:title"),
            schemaOrg.firstString("headline"),
            metaTags.firstContent("title"),
            metaTags.firstContent("sailthru.title"),
            document.title().ifBlank { null },
            h1,
        )

        val cleanedCandidates = candidates
            .mapNotNull(::cleanNonPlaceholder)

        if (cleanedCandidates.isEmpty()) return TitleResult(title = null, detectedSiteName = null)

        val bestTitle = cleanedCandidates
            .firstOrNull { !it.isSiteIdentifier(site, author) }
            ?: cleanedCandidates.first()

        return cleanTitle(bestTitle, site)
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
            content?.selectFirst(".byline, .author")?.text(),
            h1SiblingAuthorLink(content),
            h1SiblingByline(content),
            document.selectFirst("a[rel~=author], address[rel~=author]")?.text(),
            h1SiblingAuthorLink(document),
        )

        return candidates.firstNotNullOfOrNull(::cleanAuthor)
    }

    private fun extractImage(metaTags: List<MetaTagItem>, schemaOrg: SchemaOrgResult, baseUrl: String): String? {
        val raw = schemaOrg.primaryArticleImage()
            ?: metaTags.firstContent("og:image", "twitter:image")
            ?: schemaOrg.firstString("image.url")
            ?: schemaOrg.firstString("image")
        return raw?.let { resolveUrl(baseUrl, it).ifBlank { null } }
    }

    private fun SchemaOrgResult.primaryArticleImage(): String? {
        val articleTypes = setOf("article", "newsarticle", "blogposting", "reportageNewsArticle".lowercase())
        val pageTypes = setOf("webpage")
        val primary = items.firstNotNullOfOrNull { item ->
            if (item.hasType(
                    articleTypes,
                )
            ) {
                imageValue(item["image"], items) ?: imageValue(item["primaryImageOfPage"], items)
            } else {
                null
            }
        }
        if (primary != null) return primary

        return items.firstNotNullOfOrNull { item ->
            if (item.hasType(pageTypes)) {
                imageValue(item["primaryImageOfPage"], items)
                    ?: imageValue(item["image"], items)
                    ?: (item["thumbnailUrl"] as? String)?.takeIf { it.isNotBlank() }
            } else {
                null
            }
        }
    }

    private fun Map<String, Any?>.hasType(types: Set<String>): Boolean {
        val raw = this["@type"]
        return when (raw) {
            is String -> raw.lowercase() in types
            is List<*> -> raw.filterIsInstance<String>().any { it.lowercase() in types }
            else -> false
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun imageValue(value: Any?, items: List<Map<String, Any?>>): String? = when (value) {
        is String -> value.takeIf { it.isNotBlank() }

        is Map<*, *> -> {
            val map = value as Map<String, Any?>
            (map["url"] as? String)?.takeIf { it.isNotBlank() }
                ?: (map["contentUrl"] as? String)?.takeIf { it.isNotBlank() }
                ?: (map["@id"] as? String)
                    ?.let { id -> items.firstOrNull { it["@id"] == id } }
                    ?.let { imageValue(it, items) }
        }

        is List<*> -> value.firstNotNullOfOrNull { imageValue(it, items) }

        else -> null
    }

    private fun extractFavicon(document: Document, baseUrl: String): String? =
        document.selectFirst("link[rel~=(?i)^(shortcut icon|icon)$]")
            ?.attr("href")
            ?.let { resolveUrl(baseUrl, it) }
            ?.ifBlank { null }

    private fun h1SiblingAuthorLink(content: Element?): String? {
        val h1 = content?.selectFirst("h1") ?: return null
        return h1.nextElementSiblings()
            .take(4)
            .filter { it.isMetadataSiblingCandidate() }
            .firstNotNullOfOrNull { sibling ->
                if (!DATE_HINT_REGEX.containsMatchIn(sibling.text())) return@firstNotNullOfOrNull null

                sibling.select("a[href]")
                    .firstOrNull { link -> link.selectFirst("img[alt]") != null }
                    ?.text()
                    ?.trim()
                    ?.ifBlank { null }
            }
    }

    private fun h1SiblingByline(content: Element?): String? {
        val h1 = content?.selectFirst("h1") ?: return null
        val texts = h1AdjacentTexts(h1, limit = 4)
        texts.firstOrNull { it.trim().startsWith("by ", ignoreCase = true) }?.let { return it }

        val dateIndex = texts.indexOfFirst { DATE_HINT_REGEX.containsMatchIn(it) }
        if (dateIndex == -1) return null

        return texts.drop(dateIndex + 1)
            .firstOrNull { text ->
                text.isNotBlank() &&
                    !DATE_HINT_REGEX.containsMatchIn(text) &&
                    !READING_TIME_REGEX.containsMatchIn(text) &&
                    text.length <= 140 &&
                    text.split(Regex("""\s+""")).size >= 2
            }
    }

    private fun h1AdjacentTexts(h1: Element, limit: Int): List<String> = h1.nextElementSiblings()
        .take(limit)
        .filter { it.isMetadataSiblingCandidate() }
        .flatMap { sibling ->
            val childTexts = sibling.children()
                .filter { it.isMetadataSiblingCandidate() }
                .map { it.text().trim() }
                .filter { it.isNotBlank() }
            childTexts.ifEmpty { listOf(sibling.text().trim()) }
        }
        .filter { it.isNotBlank() }

    private fun Element.isMetadataSiblingCandidate(): Boolean =
        normalName() in METADATA_SIBLING_TAGS && text().length <= METADATA_SIBLING_MAX_TEXT_LENGTH

    private fun cleanTitle(value: String, site: String?): TitleResult {
        val title = cleanValue(value)
            ?.takeUnless(::isPlaceholder)
            ?: return TitleResult(title = null, detectedSiteName = null)

        cleanExactSiteTitle(title, site)?.let { return it }

        val separatorTitle = trySeparatorSplit(
            title = title,
            pattern = Regex("""\s+[|/·]\s+"""),
            suffixOnly = false,
        ) { titleWords, siteWords ->
            siteWords <= 3 && titleWords >= 2 && titleWords >= siteWords * 2
        } ?: if (site == null) {
            trySeparatorSplit(
                title = title,
                pattern = Regex("""\s+[-–—]\s+"""),
                suffixOnly = true,
            ) { titleWords, siteWords ->
                siteWords <= 2 && titleWords >= 2 && titleWords > siteWords
            }
        } else {
            null
        }

        if (separatorTitle != null) return separatorTitle

        return TitleResult(title = title.takeUnless { isPlaceholder(it) }, detectedSiteName = null)
    }

    private fun cleanExactSiteTitle(title: String, site: String?): TitleResult? {
        if (site != null && !title.equals(site, ignoreCase = true) && site.wordCount() <= 6) {
            val siteEscaped = Regex.escape(site)
            val exactPatterns = listOf(
                Regex("""\s*[|\-–—/·:]\s+$siteEscaped$""", RegexOption.IGNORE_CASE),
                Regex("""^$siteEscaped\s*[|\-–—/·:]\s+""", RegexOption.IGNORE_CASE),
            )
            for (pattern in exactPatterns) {
                if (pattern.containsMatchIn(title)) {
                    return TitleResult(
                        title = title.replace(pattern, "").trim().takeUnless(::isPlaceholder),
                        detectedSiteName = site,
                    )
                }
            }
        }

        return null
    }

    private fun trySeparatorSplit(
        title: String,
        pattern: Regex,
        suffixOnly: Boolean,
        guard: (titleWords: Int, siteWords: Int) -> Boolean,
    ): TitleResult? {
        val matches = pattern.findAll(title).toList()
        if (matches.isEmpty()) return null

        val lastMatch = matches.last()
        val suffixTitle = title.substring(0, lastMatch.range.first).trim()
        val suffixSite = title.substring(lastMatch.range.last + 1).trim()
        if (
            suffixTitle.isNotBlank() &&
            suffixSite.isNotBlank() &&
            guard(
                suffixTitle.wordCount(),
                suffixSite.wordCount(),
            )
        ) {
            return TitleResult(title = suffixTitle, detectedSiteName = suffixSite)
        }

        if (!suffixOnly) {
            val firstMatch = matches.first()
            val prefixSite = title.substring(0, firstMatch.range.first).trim()
            val prefixTitle = title.substring(firstMatch.range.last + 1).trim()
            if (
                prefixTitle.isNotBlank() &&
                prefixSite.isNotBlank() &&
                guard(
                    prefixTitle.wordCount(),
                    prefixSite.wordCount(),
                )
            ) {
                return TitleResult(title = prefixTitle, detectedSiteName = prefixSite)
            }
        }

        return null
    }

    private fun String.isSiteIdentifier(site: String?, author: String?): Boolean {
        val candidate = trim()
        for (brand in listOfNotNull(site, author)) {
            if (candidate.equals(brand, ignoreCase = true)) return true
        }

        return false
    }

    private fun cleanAuthor(value: String?): String? {
        val cleaned = cleanValue(value)
            ?.replace(Regex("""^by\s+""", RegexOption.IGNORE_CASE), "")
            ?.replace(Regex("""\(?\s*https?://\S+\s*\)?""", RegexOption.IGNORE_CASE), "")
            ?.replace(Regex("""\s*[-–—|]\s*$"""), "")
            ?.let { author ->
                if ("," in author) {
                    author
                } else {
                    author.replace(Regex("""\s+and\s+""", RegexOption.IGNORE_CASE), ", ")
                }
            }
            ?.trim()
            ?: return null
        if (isPlaceholder(cleaned)) return null
        if (cleaned.equals("admin", ignoreCase = true)) return null
        if (!cleaned.any { it.isLetterOrDigit() }) return null
        if (cleaned.startsWith("http://") || cleaned.startsWith("https://")) return null
        if (cleaned.length > 120) return null
        return cleaned
    }

    private fun cleanValue(value: String?): String? = value
        ?.replace(Regex("""\s+"""), " ")
        ?.trim()
        ?.takeIf { it.isNotBlank() }

    private fun cleanNonPlaceholder(value: String?): String? {
        val cleaned = cleanValue(value) ?: return null
        return cleaned.takeUnless(::isPlaceholder)
    }

    private fun String.wordCount(): Int = split(WHITESPACE_PATTERN).count { it.isNotBlank() }

    private fun isPlaceholder(value: String): Boolean = value.lowercase() in PLACEHOLDERS ||
        value == ".." ||
        "{{" in value ||
        "}}" in value ||
        value.startsWith("\${")

    private fun parseDomain(url: String): String? = runCatching { URI(url).host?.removePrefix("www.") }
        .getOrNull()
        ?.ifBlank { null }

    private fun List<MetaTagItem>.firstContent(vararg keys: String): String? {
        for (key in keys) {
            val match = firstOrNull { tag ->
                tag.matchesKey(key)
            }
            val content = cleanValue(match?.content)
            if (content != null) return content
        }
        return null
    }

    private fun List<MetaTagItem>.contents(key: String): List<String> = filter { tag ->
        tag.matchesKey(key)
    }.mapNotNull { cleanValue(it.content) }

    private fun MetaTagItem.matchesKey(key: String): Boolean = name.equals(key, ignoreCase = true) ||
        property.equals(key, ignoreCase = true)

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
    private val READING_TIME_REGEX = Regex("""\b\d+\s+min(?:ute)?s?\s+read\b""", RegexOption.IGNORE_CASE)
    private const val METADATA_SIBLING_MAX_TEXT_LENGTH = 300
    private val METADATA_SIBLING_TAGS = setOf("p", "time", "span", "div", "address")
    private val WHITESPACE_PATTERN = Regex("""\s+""")
}
