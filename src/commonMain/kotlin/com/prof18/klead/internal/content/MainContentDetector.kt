package com.prof18.klead.internal.content

import com.fleeksoft.ksoup.nodes.Document
import com.fleeksoft.ksoup.nodes.Element
import com.prof18.klead.internal.dom.SimpleSelectorIndex
import com.prof18.klead.internal.dom.normalizeSpace
import com.prof18.klead.internal.dom.selectFirstSafe
import com.prof18.klead.internal.dom.selectSafe

internal data class DetectedContent(
    val element: Element,
    val selectedSelector: String,
    val debug: ContentDetectionDebug,
)

internal data class ContentDetectionDebug(
    val selectedSelector: String,
    val candidates: List<ContentCandidateDebug>,
    val extractorContentSelector: String? = null,
)

internal data class ContentCandidateDebug(val selector: String, val score: Double)

internal object MainContentDetector {
    val entryPointSelectors = listOf(
        "#post",
        ".post-content",
        ".post-body",
        ".article-content",
        "#article-content",
        ".article-text",
        ".js-article-content",
        ".entry-content",
        ".markdown-body",
        ".markdown-preview-section",
        ".markdown-preview-view",
        ".markdown-rendered",
        ".updates-scroll-content",
        "article",
        """[role="article"]""",
        "main",
        """[role="main"]""",
        ".article-body",
        "#content",
        "body",
    )

    private val ENTRY_POINT_INDEX = SimpleSelectorIndex(entryPointSelectors)

    fun detect(
        document: Document,
        extractorContentSelector: String? = null,
        schemaText: String? = null,
        preferredSelectors: List<String> = emptyList(),
    ): DetectedContent {
        val scoreCache = mutableMapOf<Element, ContentScore>()
        val scoreOf: (Element) -> ContentScore = { element ->
            scoreCache.getOrPut(element) { ContentScorer.scoreElement(element) }
        }

        extractorContentSelector?.takeIf { it.isNotBlank() }?.let { selector ->
            document.selectFirstSafe(selector)?.let { element ->
                return detected(
                    element = element,
                    selector = selector,
                    candidates = emptyList(),
                    extractorContentSelector = selector,
                )
            }
        }

        detectPreferredContent(document, preferredSelectors, scoreOf)?.let { candidate ->
            return detected(
                element = candidate.element,
                selector = candidate.selector,
                candidates = listOf(candidate),
                extractorContentSelector = candidate.selector,
            )
        }

        val entryPointBuckets = ENTRY_POINT_INDEX.collect(document)
        val candidates = entryPointSelectors.flatMapIndexed { index, selector ->
            entryPointBuckets[selector].orEmpty().map { element ->
                Candidate(
                    element = element,
                    selector = selector,
                    score = score(element, index, scoreOf),
                )
            }
        }.distinctByIdentity()

        if (candidates.isEmpty()) {
            val body = document.body()
            return detected(body, "body", emptyList())
        }

        val sorted = candidates.sortedByDescending { it.score }
        var selected = refineListingParent(sorted.first(), sorted)
        refineBroadContainerToDirectArticle(selected, sorted, scoreOf)?.let { selected = it }
        refineBroadContainerToFocusedDescendant(selected, sorted, scoreOf)?.let { selected = it }
        if (selected.element.tagName() == "body") {
            refineBodyToFocusedCandidate(selected, sorted, scoreOf)?.let { selected = it }
        }
        if (selected.element.tagName() == "body") {
            detectTableLayout(selected.element, scoreOf)?.let { selected = it }
        }
        if (selected.element.tagName() == "body") {
            refineWithSchemaText(document, schemaText, scoreOf)?.let { selected = it }
        }
        return detected(
            element = selected.element,
            selector = selected.selector,
            candidates = sorted,
        )
    }

    private fun score(element: Element, selectorIndex: Int, scoreOf: (Element) -> ContentScore): Double {
        val priorityBonus = (entryPointSelectors.size - selectorIndex) * 30.0
        return scoreOf(element).total + priorityBonus
    }

    private fun detectPreferredContent(
        document: Document,
        preferredSelectors: List<String>,
        scoreOf: (Element) -> ContentScore,
    ): Candidate? {
        for (selector in preferredSelectors.distinct().filter { it.isNotBlank() }) {
            val candidate = document.selectSafe(selector)
                .map { element -> element to scoreOf(element) }
                .filter { (_, score) -> score.passesPreferredSelectorGuard() }
                .maxByOrNull { (_, score) -> score.total }
                ?: continue
            return Candidate(
                element = candidate.first,
                selector = selector,
                score = candidate.second.total,
            )
        }
        return null
    }

    private fun ContentScore.passesPreferredSelectorGuard(): Boolean {
        if (wordCount >= PREFERRED_SELECTOR_MIN_WORDS) return true
        return wordCount >= PREFERRED_SELECTOR_SHORT_MIN_WORDS &&
            paragraphCount >= PREFERRED_SELECTOR_MIN_PARAGRAPHS &&
            linkDensity <= PREFERRED_SELECTOR_MAX_LINK_DENSITY
    }

    private fun refineListingParent(selected: Candidate, candidates: List<Candidate>): Candidate {
        val parentCandidate = candidates.firstOrNull { candidate ->
            candidate.element !== selected.element &&
                candidate.element.children().count { it.tagName() == "article" } > 1 &&
                candidate.element.children().any { it === selected.element }
        }
        return parentCandidate ?: selected
    }

    private fun refineBroadContainerToDirectArticle(
        selected: Candidate,
        candidates: List<Candidate>,
        scoreOf: (Element) -> ContentScore,
    ): Candidate? {
        if (selected.selector !in BROAD_CONTAINER_SELECTORS) return null
        val directArticles = selected.element.children()
            .filter { it.normalName() == "article" || it.attr("role").equals("article", ignoreCase = true) }
        if (directArticles.size != 1) return null

        val article = directArticles.single()
        val selectedScore = scoreOf(selected.element)
        val articleScore = scoreOf(article)
        if (articleScore.wordCount < BROAD_REFINEMENT_MIN_WORDS) return null
        if (
            articleScore.total < selectedScore.total * BROAD_REFINEMENT_MIN_SCORE_RATIO &&
            articleScore.wordCount < selectedScore.wordCount * BROAD_REFINEMENT_MIN_WORD_RATIO
        ) {
            return null
        }

        return candidates.firstOrNull { it.element === article }
            ?: Candidate(
                element = article,
                selector = if (article.normalName() == "article") "article" else """[role="article"]""",
                score = articleScore.total,
            )
    }

    private fun refineBroadContainerToFocusedDescendant(
        selected: Candidate,
        candidates: List<Candidate>,
        scoreOf: (Element) -> ContentScore,
    ): Candidate? {
        if (selected.selector !in BROAD_CONTAINER_SELECTORS) return null
        val focusedDescendants = candidates
            .filter { candidate ->
                candidate.element !== selected.element &&
                    candidate.selector in FOCUSED_DESCENDANT_SELECTORS &&
                    candidate.element.isDescendantOf(selected.element) &&
                    scoreOf(candidate.element).wordCount >= BROAD_REFINEMENT_MIN_WORDS
            }
        if (focusedDescendants.size != 1) return null

        return focusedDescendants.single()
    }

    private fun refineBodyToFocusedCandidate(
        selected: Candidate,
        candidates: List<Candidate>,
        scoreOf: (Element) -> ContentScore,
    ): Candidate? {
        val focusedCandidates = candidates
            .filter { candidate ->
                candidate.element !== selected.element &&
                    candidate.isFocusedContentCandidate() &&
                    candidate.score >= selected.score * BODY_REFINEMENT_MIN_SCORE_RATIO &&
                    scoreOf(candidate.element).wordCount >= BODY_REFINEMENT_MIN_WORDS
            }
        val semanticMainCandidates = candidates
            .filter { candidate ->
                candidate.element !== selected.element &&
                    candidate.selector in SEMANTIC_MAIN_SELECTORS &&
                    scoreOf(candidate.element).wordCount >= BODY_REFINEMENT_MIN_WORDS
            }
        val trustedPostBodies = candidates
            .filter { candidate ->
                candidate.element !== selected.element &&
                    candidate.selector in TRUSTED_BODY_DESCENDANT_SELECTORS &&
                    scoreOf(candidate.element).wordCount >= BROAD_REFINEMENT_MIN_WORDS
            }
        return focusedCandidates.firstOrNull { it.selector in ARTICLE_SELECTORS }
            ?: semanticMainCandidates.firstOrNull()
            ?: trustedPostBodies.singleOrNull()
            ?: focusedCandidates.firstOrNull()
    }

    private fun Candidate.isFocusedContentCandidate(): Boolean = selector in FOCUSED_CONTENT_SELECTORS

    private fun Element.isDescendantOf(ancestor: Element): Boolean = parents().any { it === ancestor }

    private fun detectTableLayout(body: Element, scoreOf: (Element) -> ContentScore): Candidate? {
        val bodyWords = scoreOf(body).wordCount
        if (bodyWords < 15) return null

        return body.select("table")
            .filter { table -> looksLikeLayoutTable(table, scoreOf) }
            .flatMap { table -> table.select("td").map { table to it } }
            .map { (_, cell) ->
                val score = scoreOf(cell)
                cell to score
            }
            .filter { (_, score) ->
                score.wordCount >= 15 && score.wordCount >= bodyWords * 0.35
            }
            .maxByOrNull { (_, score) -> score.total }
            ?.let { (cell, score) ->
                Candidate(
                    element = cell,
                    selector = "table-layout td",
                    score = score.total,
                )
            }
    }

    private fun looksLikeLayoutTable(table: Element, scoreOf: (Element) -> ContentScore): Boolean {
        val width = table.attr("width").filter { it.isDigit() }.toIntOrNull()
        val hints = "${table.id()} ${table.className()}".lowercase()
        return (width != null && width >= 600) ||
            table.attr("align").equals("center", ignoreCase = true) ||
            "content" in hints ||
            "article" in hints ||
            table.hasMultiColumnLayoutRow(scoreOf)
    }

    private fun Element.hasMultiColumnLayoutRow(scoreOf: (Element) -> ContentScore): Boolean =
        directTableRows().any { row ->
            val cells = row.directTableCells()
            cells.size >= 2 &&
                cells.any { scoreOf(it).wordCount >= LAYOUT_CONTENT_CELL_MIN_WORDS } &&
                cells.any { it.isPeripheralLayoutCell(scoreOf) }
        }

    private fun Element.isPeripheralLayoutCell(scoreOf: (Element) -> ContentScore): Boolean {
        val score = scoreOf(this)
        return score.wordCount <= LAYOUT_PERIPHERAL_CELL_MAX_WORDS ||
            score.linkDensity >= LAYOUT_PERIPHERAL_CELL_MIN_LINK_DENSITY ||
            select("img, map, area").isNotEmpty()
    }

    private fun Element.directTableRows(): List<Element> = children().flatMap { child ->
        when (child.normalName()) {
            "tr" -> listOf(child)
            "tbody", "thead", "tfoot" -> child.children().filter { it.normalName() == "tr" }
            else -> emptyList()
        }
    }

    private fun Element.directTableCells(): List<Element> =
        children().filter { it.normalName() == "td" || it.normalName() == "th" }

    private fun refineWithSchemaText(
        document: Document,
        schemaText: String?,
        scoreOf: (Element) -> ContentScore,
    ): Candidate? {
        val needle = schemaText?.normalizeSchemaText()?.takeIf { it.length >= SCHEMA_TEXT_MIN_LENGTH } ?: return null
        return document.body()
            .select("article, main, section, div")
            .filter { it.matchesSchemaText(needle) }
            .minByOrNull { scoreOf(it).wordCount }
            ?.let { element ->
                Candidate(
                    element = element,
                    selector = "schema-text",
                    score = scoreOf(element).total,
                )
            }
    }

    private fun Element.matchesSchemaText(needle: String): Boolean {
        val haystack = text().normalizeSchemaText()
        if (haystack.contains(needle, ignoreCase = false)) return true
        val anchors = needle.schemaTextAnchors()
        return anchors.size > 1 && anchors.all { haystack.contains(it, ignoreCase = false) }
    }

    private fun String.schemaTextAnchors(): List<String> = when {
        length <= SCHEMA_TEXT_ANCHOR_LENGTH * 2 -> listOf(this)
        else -> listOf(take(SCHEMA_TEXT_ANCHOR_LENGTH), takeLast(SCHEMA_TEXT_ANCHOR_LENGTH))
    }

    private fun String.normalizeSchemaText(): String = normalizeSpace()

    private fun detected(
        element: Element,
        selector: String,
        candidates: List<Candidate>,
        extractorContentSelector: String? = null,
    ): DetectedContent = DetectedContent(
        element = element,
        selectedSelector = selector,
        debug = ContentDetectionDebug(
            selectedSelector = selector,
            extractorContentSelector = extractorContentSelector,
            candidates = candidates.map {
                ContentCandidateDebug(
                    selector = it.selector,
                    score = it.score,
                )
            },
        ),
    )

    private data class Candidate(val element: Element, val selector: String, val score: Double)

    private fun List<Candidate>.distinctByIdentity(): List<Candidate> {
        val seen = mutableSetOf<Element>()
        return filter { seen.add(it.element) }
    }

    private val ARTICLE_SELECTORS = setOf(
        "article",
        """[role="article"]""",
    )

    private val FOCUSED_CONTENT_SELECTORS = ARTICLE_SELECTORS + setOf(
        "#post",
        ".post-content",
        ".post-body",
        ".article-content",
        "#article-content",
        ".article-text",
        ".js-article-content",
        ".entry-content",
        ".markdown-body",
        ".updates-scroll-content",
        "main",
        """[role="main"]""",
        ".article-body",
        "#content",
    )

    private val SEMANTIC_MAIN_SELECTORS = setOf(
        "main",
        """[role="main"]""",
    )

    private val BROAD_CONTAINER_SELECTORS = setOf(
        "body",
        "main",
        """[role="main"]""",
        "#content",
    )

    private val FOCUSED_DESCENDANT_SELECTORS = setOf(
        ".article-text",
        ".js-article-content",
    )

    private val TRUSTED_BODY_DESCENDANT_SELECTORS = setOf(
        ".post-body",
    )

    private const val BROAD_REFINEMENT_MIN_SCORE_RATIO = 0.45
    private const val BROAD_REFINEMENT_MIN_WORD_RATIO = 0.35
    private const val BROAD_REFINEMENT_MIN_WORDS = 50
    private const val BODY_REFINEMENT_MIN_SCORE_RATIO = 0.55
    private const val BODY_REFINEMENT_MIN_WORDS = 80
    private const val PREFERRED_SELECTOR_MIN_WORDS = 80
    private const val PREFERRED_SELECTOR_SHORT_MIN_WORDS = 35
    private const val PREFERRED_SELECTOR_MIN_PARAGRAPHS = 2
    private const val PREFERRED_SELECTOR_MAX_LINK_DENSITY = 0.35
    private const val LAYOUT_CONTENT_CELL_MIN_WORDS = 30
    private const val LAYOUT_PERIPHERAL_CELL_MAX_WORDS = 8
    private const val LAYOUT_PERIPHERAL_CELL_MIN_LINK_DENSITY = 0.45
    private const val SCHEMA_TEXT_MIN_LENGTH = 20
    private const val SCHEMA_TEXT_ANCHOR_LENGTH = 120
}
