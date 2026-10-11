package com.prof18.klead.internal.metadata

import com.fleeksoft.ksoup.nodes.Document
import com.fleeksoft.ksoup.nodes.Element

/** Reads page language from the original document before extraction mutates its structure. */
internal object LanguageMetadataExtractor {
    private val languageTag = Regex(
        "^[A-Za-z]{2,8}(?:-[A-Za-z0-9]{2,8})*" +
            "(?:-[A-WY-Za-wy-z0-9](?:-[A-Za-z0-9]{2,8})+)*" +
            "(?:-[xX](?:-[A-Za-z0-9]{1,8})+)?$",
    )
    private val ignoredArticleAncestors = setOf("nav", "aside", "blockquote", "q", "pre", "code", "kbd", "samp")

    fun extract(document: Document): String? = articleOrMainLanguage(document)
        ?: declaredDocumentLanguage(document)
        ?: headLanguage(document)

    private fun articleOrMainLanguage(document: Document): String? {
        val languageCandidates = document.select("article, main")
            .filter { it.hasAttr("lang") || it.hasAttr("xml:lang") }
            .filterNot(::insideIgnoredContent)
            .mapNotNull(::declaredLanguage)
            .distinct()
        return languageCandidates.singleOrNull()
    }

    private fun declaredDocumentLanguage(document: Document): String? = declaredLanguage(document.selectFirst("html"))
        ?: declaredLanguage(document.selectFirst("body"))
        ?: rootWrapperLanguage(document.body())

    private fun rootWrapperLanguage(body: Element): String? = body.children().singleOrNull()
        ?.takeIf { it.tagName() !in ignoredArticleAncestors }
        ?.takeIf { it.hasAttr("lang") || it.hasAttr("xml:lang") }
        ?.let(::declaredLanguage)

    private fun headLanguage(document: Document): String? {
        val contentLanguage = document.head().select("meta[http-equiv]")
            .firstOrNull { it.attr("http-equiv").equals("content-language", ignoreCase = true) }
            ?.attr("content")
        val declaredContentLanguage = normalize(contentLanguage)
        val locale = document.head().select("meta[property]")
            .firstOrNull { it.attr("property").equals("og:locale", ignoreCase = true) }
            ?.attr("content")
            ?.replace('_', '-')
        return declaredContentLanguage ?: normalize(locale)
    }

    private fun insideIgnoredContent(element: Element): Boolean {
        var current: Element? = element.parent()
        while (current != null) {
            if (current.tagName() in ignoredArticleAncestors) return true
            current = current.parent()
        }
        return false
    }

    private fun declaredLanguage(element: Element?): String? = element?.let {
        normalize(it.attr("lang")) ?: normalize(it.attr("xml:lang"))
    }

    private fun normalize(value: String?): String? {
        val candidate = value?.trim()?.takeIf { languageTag.matches(it) } ?: return null
        val parts = candidate.split('-')
        val primary = parts.first().lowercase()
        if (primary in setOf("und", "mul", "zxx")) return null
        val extensionIndex = parts.indexOfFirst { it.length == 1 }
        val extensionSingletons = parts.filter { part ->
            part.length == 1 && !part.equals("x", ignoreCase = true)
        }
        if (extensionSingletons.size != extensionSingletons.distinctBy { it.lowercase() }.size) return null
        return parts.mapIndexed { index, part ->
            if (extensionIndex >= 0 && index >= extensionIndex) part.lowercase() else canonicalSubtag(index, part)
        }.joinToString("-")
    }

    private fun canonicalSubtag(index: Int, part: String): String = when {
        index == 0 -> part.lowercase()

        part.length == 4 && part.all { it.isLetter() } -> part.lowercase().replaceFirstChar { it.uppercase() }

        (part.length == 2 && part.all { it.isLetter() }) ||
            (part.length == 3 && part.all { it.isDigit() }) -> part.uppercase()

        else -> part.lowercase()
    }
}
