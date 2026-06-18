package dev.defuddle.content

import dev.defuddle.dom.selectFirstSafe
import dev.defuddle.dom.selectSafe
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

data class DetectedContent(val element: Element, val selectedSelector: String, val debug: ContentDetectionDebug)

data class ContentDetectionDebug(
    val selectedSelector: String,
    val candidates: List<ContentCandidateDebug>,
    val extractorContentSelector: String? = null,
)

data class ContentCandidateDebug(val selector: String, val score: Double)

object MainContentDetector {
    val entryPointSelectors = listOf(
        "#post",
        ".post-content",
        ".post-body",
        ".article-content",
        "#article-content",
        ".js-article-content",
        ".entry-content",
        ".markdown-body",
        "article",
        """[role="article"]""",
        "main",
        """[role="main"]""",
        ".article-body",
        "#content",
        "body",
    )

    fun detect(
        document: Document,
        extractorContentSelector: String? = null,
        schemaText: String? = null,
        preferredSelectors: List<String> = emptyList(),
    ): DetectedContent {
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

        detectPreferredContent(document, preferredSelectors)?.let { candidate ->
            return detected(
                element = candidate.element,
                selector = candidate.selector,
                candidates = listOf(candidate),
                extractorContentSelector = candidate.selector,
            )
        }

        val candidates = entryPointSelectors.flatMapIndexed { index, selector ->
            document.selectSafe(selector).map { element ->
                Candidate(
                    element = element,
                    selector = selector,
                    score = score(element, index),
                )
            }
        }.distinctByIdentity()

        if (candidates.isEmpty()) {
            val body = document.body() ?: Element("body")
            return detected(body, "body", emptyList())
        }

        val sorted = candidates.sortedByDescending { it.score }
        var selected = refineListingParent(sorted.first(), sorted)
        refineBroadContainerToDirectArticle(selected, sorted)?.let { selected = it }
        if (selected.element.tagName() == "body") {
            refineBodyToFocusedCandidate(selected, sorted)?.let { selected = it }
        }
        if (selected.element.tagName() == "body") {
            detectTableLayout(selected.element)?.let { selected = it }
        }
        if (selected.element.tagName() == "body") {
            refineWithSchemaText(document, schemaText)?.let { selected = it }
        }
        return detected(
            element = selected.element,
            selector = selected.selector,
            candidates = sorted,
        )
    }

    private fun score(element: Element, selectorIndex: Int): Double {
        val priorityBonus = (entryPointSelectors.size - selectorIndex) * 30.0
        return ContentScorer.scoreElement(element).total + priorityBonus
    }

    private fun detectPreferredContent(document: Document, preferredSelectors: List<String>): Candidate? {
        for (selector in preferredSelectors.distinct().filter { it.isNotBlank() }) {
            val candidate = document.selectSafe(selector)
                .map { element -> element to ContentScorer.scoreElement(element) }
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

    private fun refineBroadContainerToDirectArticle(selected: Candidate, candidates: List<Candidate>): Candidate? {
        if (selected.selector !in BROAD_CONTAINER_SELECTORS) return null
        val directArticles = selected.element.children()
            .filter { it.normalName() == "article" || it.attr("role").equals("article", ignoreCase = true) }
        if (directArticles.size != 1) return null

        val article = directArticles.single()
        val selectedScore = ContentScorer.scoreElement(selected.element)
        val articleScore = ContentScorer.scoreElement(article)
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

    private fun refineBodyToFocusedCandidate(selected: Candidate, candidates: List<Candidate>): Candidate? {
        val focusedCandidates = candidates
            .filter { candidate ->
                candidate.element !== selected.element &&
                    candidate.isFocusedContentCandidate() &&
                    candidate.score >= selected.score * BODY_REFINEMENT_MIN_SCORE_RATIO &&
                    ContentScorer.scoreElement(candidate.element).wordCount >= BODY_REFINEMENT_MIN_WORDS
            }
        val semanticMainCandidates = candidates
            .filter { candidate ->
                candidate.element !== selected.element &&
                    candidate.selector in SEMANTIC_MAIN_SELECTORS &&
                    ContentScorer.scoreElement(candidate.element).wordCount >= BODY_REFINEMENT_MIN_WORDS
            }
        return focusedCandidates.firstOrNull { it.selector in ARTICLE_SELECTORS }
            ?: semanticMainCandidates.firstOrNull()
            ?: focusedCandidates.firstOrNull()
    }

    private fun Candidate.isFocusedContentCandidate(): Boolean = selector in FOCUSED_CONTENT_SELECTORS

    private fun detectTableLayout(body: Element): Candidate? {
        val bodyWords = ContentScorer.scoreElement(body).wordCount
        if (bodyWords < 15) return null

        return body.select("table")
            .filter(::looksLikeLayoutTable)
            .flatMap { table -> table.select("td").map { table to it } }
            .map { (_, cell) ->
                val score = ContentScorer.scoreElement(cell)
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

    private fun looksLikeLayoutTable(table: Element): Boolean {
        val width = table.attr("width").filter { it.isDigit() }.toIntOrNull()
        val hints = "${table.id()} ${table.className()}".lowercase()
        return (width != null && width >= 600) ||
            table.attr("align").equals("center", ignoreCase = true) ||
            "content" in hints ||
            "article" in hints
    }

    private fun refineWithSchemaText(document: Document, schemaText: String?): Candidate? {
        val needle = schemaText?.trim()?.takeIf { it.length >= 20 } ?: return null
        return document.body()
            ?.select("article, main, section, div")
            ?.filter { it.text().contains(needle, ignoreCase = false) }
            ?.minByOrNull { ContentScorer.scoreElement(it).wordCount }
            ?.let { element ->
                Candidate(
                    element = element,
                    selector = "schema-text",
                    score = ContentScorer.scoreElement(element).total,
                )
            }
    }

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
        val seen = mutableListOf<Element>()
        val result = mutableListOf<Candidate>()
        for (candidate in this) {
            if (seen.none { it === candidate.element }) {
                seen.add(candidate.element)
                result.add(candidate)
            }
        }
        return result
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
        ".js-article-content",
        ".entry-content",
        ".markdown-body",
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

    private const val BROAD_REFINEMENT_MIN_SCORE_RATIO = 0.45
    private const val BROAD_REFINEMENT_MIN_WORD_RATIO = 0.35
    private const val BROAD_REFINEMENT_MIN_WORDS = 50
    private const val BODY_REFINEMENT_MIN_SCORE_RATIO = 0.55
    private const val BODY_REFINEMENT_MIN_WORDS = 80
    private const val PREFERRED_SELECTOR_MIN_WORDS = 80
    private const val PREFERRED_SELECTOR_SHORT_MIN_WORDS = 35
    private const val PREFERRED_SELECTOR_MIN_PARAGRAPHS = 2
    private const val PREFERRED_SELECTOR_MAX_LINK_DENSITY = 0.35
}
