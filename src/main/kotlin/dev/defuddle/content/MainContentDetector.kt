package dev.defuddle.content

import dev.defuddle.DefuddleOptions
import dev.defuddle.dom.selectFirstSafe
import dev.defuddle.dom.selectSafe
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

data class DetectedContent(
    val element: Element,
    val selectedSelector: String,
    val debug: ContentDetectionDebug,
)

data class ContentDetectionDebug(
    val selectedSelector: String,
    val candidates: List<ContentCandidateDebug>,
)

data class ContentCandidateDebug(
    val selector: String,
    val score: Double,
)

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
        options: DefuddleOptions = DefuddleOptions(),
    ): DetectedContent {
        options.contentSelector?.takeIf { it.isNotBlank() }?.let { selector ->
            document.selectFirstSafe(selector)?.let { element ->
                return detected(element, selector, emptyList())
            }
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
        val selected = refineListingParent(sorted.first(), sorted)
        return detected(
            element = selected.element,
            selector = selected.selector,
            candidates = sorted,
        )
    }

    private fun score(
        element: Element,
        selectorIndex: Int,
    ): Double {
        val priorityBonus = (entryPointSelectors.size - selectorIndex) * 30.0
        return ContentScorer.scoreElement(element).total + priorityBonus
    }

    private fun refineListingParent(
        selected: Candidate,
        candidates: List<Candidate>,
    ): Candidate {
        val parentCandidate = candidates.firstOrNull { candidate ->
            candidate.element !== selected.element &&
                candidate.element.children().count { it.tagName() == "article" } > 1 &&
                candidate.element.children().any { it === selected.element }
        }
        return parentCandidate ?: selected
    }

    private fun detected(
        element: Element,
        selector: String,
        candidates: List<Candidate>,
    ): DetectedContent =
        DetectedContent(
            element = element,
            selectedSelector = selector,
            debug = ContentDetectionDebug(
                selectedSelector = selector,
                candidates = candidates.map {
                    ContentCandidateDebug(
                        selector = it.selector,
                        score = it.score,
                    )
                },
            ),
        )

    private data class Candidate(
        val element: Element,
        val selector: String,
        val score: Double,
    )

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
}
