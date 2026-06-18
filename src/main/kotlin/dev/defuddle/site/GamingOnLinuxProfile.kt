package dev.defuddle.site

object GamingOnLinuxProfile : dev.defuddle.extractors.Extractor {
    override val id: String = "gamingonlinux"
    override val domains: Set<String> = setOf("gamingonlinux.com")
    override val postContentRemoveSelectors: List<String> = listOf(
        ".hidden_message",
        ".article_likes",
        ".social-media-comments",
        ".rules-reminder",
    )
}
