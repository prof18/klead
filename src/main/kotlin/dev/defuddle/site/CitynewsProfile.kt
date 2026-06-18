package dev.defuddle.site

object CitynewsProfile : SiteExtractor {
    override val id: String = "citynews"
    override val domains: Set<String> = setOf("veneziatoday.it")
    override val postContentRemoveSelectors: List<String> = listOf(
        ".l-entry__footer",
        ".l-entry__sidebar",
        ".l-entry--infos-square > .l-entry__header",
        ".l-entry__byline",
        ".l-entry__byline--small",
        ".article__meta",
        ".c-story--stack",
        ":scope > article.c-story--stack",
        """[data-section-key^="article-footer"]""",
    )
}
