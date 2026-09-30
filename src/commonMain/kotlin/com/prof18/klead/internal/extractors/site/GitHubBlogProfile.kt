package com.prof18.klead.internal.extractors.site

internal object GitHubBlogProfile : com.prof18.klead.extractors.Extractor {
    override val id: String = "github-blog"
    override val domains: Set<String> = setOf("github.blog")
    override val postContentRemoveSelectors: List<String> = listOf(
        // The opening byline, dates, reading time, and share list use only layout classes.
        // Remove their section together so icon-only links cannot leave empty list items.
        "main > header + section:has(a[rel=author]):has(time[datetime])",
    )
}
