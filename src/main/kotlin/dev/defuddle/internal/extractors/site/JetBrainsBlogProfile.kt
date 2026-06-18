package dev.defuddle.internal.extractors.site

internal object JetBrainsBlogProfile : dev.defuddle.extractors.Extractor {
    override val id: String = "jetbrains-blog"
    override val domains: Set<String> = setOf("blog.jetbrains.com")
    override val postContentRemoveSelectors: List<String> = listOf(
        ".top-page",
        ".article-section .content > a.tag",
        ".article-section .content > h1:first-of-type",
        ".author-post",
        ".content__pagination",
        ".toc-opener",
        ".article-section + .section.light-gray-bg",
    )
}
