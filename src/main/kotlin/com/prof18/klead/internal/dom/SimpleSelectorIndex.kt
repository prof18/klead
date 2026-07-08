package com.prof18.klead.internal.dom

import org.jsoup.nodes.Element

// Matches a fixed list of simple selectors (tag, #id, .class, [attr=v], [attr*=v], with optional
// quotes around the value) using hash lookups in a single tree walk, instead of one jsoup
// traversal per selector. Selector strings that don't parse into one of those shapes fall back
// to selectSafe, so new shapes stay correct, just slower. Matching replicates the jsoup
// semantics pinned by ExactSelectorParityTest: tag = normalName equality; #id = case-sensitive
// untrimmed equality; .class = case-insensitive class token; attribute values = case-insensitive
// and untrimmed.
internal class SimpleSelectorIndex(private val selectors: List<String>) {
    fun collect(root: Element): Map<String, List<Element>> {
        val buckets = mutableMapOf<String, MutableList<Element>>()
        fun add(selector: String, element: Element) {
            buckets.getOrPut(selector) { mutableListOf() }.add(element)
        }

        for (element in root.select("*")) {
            tagSelectors[element.normalName()]?.let { add(it, element) }
            if (element.attributesSize() == 0) continue
            val id = element.id()
            if (id.isNotEmpty()) idSelectors[id]?.let { add(it, element) }
            if (element.hasAttr("class")) {
                for (className in element.classNames()) {
                    classSelectors[className.lowercase()]?.let { add(it, element) }
                }
            }
            for (attribute in element.attributes()) {
                attributeSelectors[attribute.key.lowercase()]?.forEach { spec ->
                    if (spec.matches(attribute.value)) add(spec.selector, element)
                }
            }
        }
        for (selector in fallbackSelectors) {
            root.selectSafe(selector).forEach { add(selector, it) }
        }
        return buckets
    }

    private class AttributeSpec(val selector: String, val value: String, val contains: Boolean) {
        fun matches(attrValue: String): Boolean = if (contains) {
            attrValue.contains(value, ignoreCase = true)
        } else {
            attrValue.equals(value, ignoreCase = true)
        }
    }

    private val tagSelectors = mutableMapOf<String, String>()
    private val idSelectors = mutableMapOf<String, String>()
    private val classSelectors = mutableMapOf<String, String>()
    private val attributeSelectors = mutableMapOf<String, MutableList<AttributeSpec>>()
    private val fallbackSelectors = mutableListOf<String>()

    init {
        for (selector in selectors) {
            val attributeMatch = ATTRIBUTE_SHAPE.matchEntire(selector)
            when {
                TAG_SHAPE.matches(selector) -> tagSelectors[selector] = selector

                ID_SHAPE.matchEntire(selector) != null ->
                    idSelectors[selector.removePrefix("#")] = selector

                CLASS_SHAPE.matchEntire(selector) != null ->
                    classSelectors[selector.removePrefix(".").lowercase()] = selector

                attributeMatch != null -> {
                    val attr = attributeMatch.groupValues[1]
                    val operator = attributeMatch.groupValues[2]
                    val value = attributeMatch.groupValues[3].ifEmpty { attributeMatch.groupValues[4] }
                    attributeSelectors.getOrPut(attr.lowercase()) { mutableListOf() } +=
                        AttributeSpec(selector = selector, value = value, contains = operator == "*")
                }

                else -> fallbackSelectors += selector
            }
        }
    }

    private companion object {
        val TAG_SHAPE = Regex("""^[a-z]+$""")
        val ID_SHAPE = Regex("""^#([A-Za-z0-9_-]+)$""")
        val CLASS_SHAPE = Regex("""^\.([A-Za-z0-9_-]+)$""")
        val ATTRIBUTE_SHAPE = Regex("""^\[([a-zA-Z-]+)(\*?)=(?:"([A-Za-z0-9_-]+)"|([A-Za-z0-9_-]+))]$""")
    }
}
