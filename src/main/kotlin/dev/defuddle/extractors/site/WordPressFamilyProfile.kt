package dev.defuddle.extractors.site

object WordPressFamilyProfile : dev.defuddle.extractors.Extractor {
    override val id: String = "wordpress-family"
    override val domains: Set<String> = setOf("berlinomagazine.com", "ilmitte.com", "basketuniverso.it")
    override val postContentRemoveSelectors: List<String> = listOf(
        ".entry-footer",
        ".entry-aside",
        ".big-preview",
        ".avia-copyright",
        ".bm-social-top",
        ".abh_box",
        ".author-bio-box",
        ".post-cat-wrap",
        ".post-cat",
        ".post-cats-list",
        ".post-categories",
        ".entry-categories",
        ".cat-links",
        ".category-button",
        ".wp-block-mailchimp-mailchimp",
        ".mc_embed_signup",
        ".mailchimp-signup",
    )
}
