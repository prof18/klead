package com.prof18.klead.internal.extractors

import com.fleeksoft.ksoup.nodes.Document
import com.fleeksoft.ksoup.nodes.Element
import com.fleeksoft.ksoup.select.Evaluator
import com.prof18.klead.RemovalRecord
import com.prof18.klead.extractors.Extractor
import com.prof18.klead.extractors.ExtractorContext
import com.prof18.klead.extractors.ExtractorResult
import com.prof18.klead.internal.dom.parseKleadUri

internal class DomExtractorContext(val publicContext: ExtractorContext, val document: Document) {
    val url: String? get() = publicContext.url
    val host: String? get() = publicContext.host

    // Built on the first probe; see DocumentSignals.
    val signals: DocumentSignals by lazy(LazyThreadSafetyMode.NONE) { DocumentSignals(document) }

    fun hostMatches(domains: Set<String>): Boolean = publicContext.hostMatches(domains)
}

// What the document holds, gathered in one traversal: the element names, ids, class names and attribute
// names, plus its <meta> elements. Extractor matches() probes check these first, as a necessary condition
// of their selector, so a page that matches no extractor pays one traversal instead of one per probe.
// Lookups follow jsoup's selector semantics: tags and attribute names lowercase, ids exact, classes
// case-insensitive.
internal class DocumentSignals(document: Document) {
    private val tags = HashSet<String>()
    private val ids = HashSet<String>()
    private val classes = HashSet<String>()
    private val attributes = HashSet<String>()
    val metas: List<Element>

    init {
        val metaElements = mutableListOf<Element>()
        for (element in document.getAllElements()) {
            val tag = element.normalName()
            tags.add(tag)
            if (tag == "meta") metaElements.add(element)
            if (element.attributesSize() == 0) continue
            for (attribute in element.attributes()) {
                val key = attribute.key.lowercase()
                attributes.add(key)
                when (key) {
                    "id" -> ids.add(attribute.value)
                    "class" -> attribute.value.split(CLASS_SEPARATOR).forEach { name ->
                        if (name.isNotEmpty()) classes.add(name.lowercase())
                    }
                }
            }
        }
        metas = metaElements
    }

    fun hasTag(name: String): Boolean = name in tags

    fun hasId(id: String): Boolean = id in ids

    fun hasClass(name: String): Boolean = name.lowercase() in classes

    fun hasAttribute(name: String): Boolean = name.lowercase() in attributes

    // The first <meta> matching the evaluator, in document order like selectFirst.
    fun firstMeta(evaluator: Evaluator): Element? = metas.firstOrNull { it.`is`(evaluator) }

    private companion object {
        val CLASS_SEPARATOR = Regex("""\s+""")
    }
}

internal fun createExtractorContext(url: String?, host: String?, document: Document): DomExtractorContext {
    val publicContext = ExtractorContext(url = url, host = host).apply {
        candidateHostsProvider = {
            buildList {
                document.select(
                    """link[rel=canonical][href], meta[property=og:url][content], meta[name=twitter:url][content]""",
                )
                    .mapNotNull { element ->
                        val candidateUrl = element.attr("href").ifBlank { element.attr("content") }
                        parseKleadUri(candidateUrl)?.host?.lowercase()
                    }
                    .forEach { candidate ->
                        if (candidate !in this) add(candidate)
                    }
            }
        }
    }
    return DomExtractorContext(publicContext = publicContext, document = document)
}

internal interface DomExtractor : Extractor {
    fun matches(context: DomExtractorContext): Boolean = matches(context.publicContext)

    fun extract(context: DomExtractorContext): ExtractorResult? = extract(context.publicContext)

    fun preProcess(content: Element, context: DomExtractorContext) = Unit

    fun postProcess(content: Element, context: DomExtractorContext, debug: MutableList<RemovalRecord>) = Unit
}
