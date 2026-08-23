package com.prof18.klead.internal.extractors.site

internal object LessWrongProfile : com.prof18.klead.extractors.Extractor {
    override val id: String = "lesswrong"
    override val domains: Set<String> = setOf("lesswrong.com")
    override val contentSelectors: List<String> = listOf(
        ".PostsPage-postContent",
        "#postContent",
    )
}
