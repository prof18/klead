package com.prof18.klead.internal.extractors.site

internal object CorriereProfile : com.prof18.klead.extractors.Extractor {
    override val id: String = "corriere"
    override val domains: Set<String> = setOf("corriere.it")
    override val contentSelectors: List<String> = listOf(
        "#content-to-read",
    )
    override val postContentRemoveSelectors: List<String> = listOf(
        ".bck-social-nav",
        ".bck-social-comment-read",
        ".bck-modal-comments",
        ".bck-box-newsletter",
    )
}
