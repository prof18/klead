package com.prof18.klead.internal.removal

internal val EXACT_SELECTORS = listOf(
    "nav",
    "footer",
    "#fps",
    "[id*=footer]",
    "form",
    "button",
    "input",
    "select",
    "textarea",
    "[role=navigation]",
    "#comments",
    "#discussion",
    "#article-comments",
    "#comments-loading",
    "#comments-loaded",
    "#viafoura-comments-container",
    "#viafoura-comment-wrapper",
    ".viafoura-twig-component",
    ".viafoura",
    "[data-component-name*=Comments]",
    "[data-component-name*=comments]",
    "[class*=CommentsWrapper]",
    "#reactions-title",
    "[data-component-name*=ScrollUp]",
    "[data-component-name*=scroll]",
    "#author-bio",
    ".more-like-this",
    ":scope > div:first-child > header",
    ".wp-block-post-featured-image__caption",
    ".author-box",
    ".author-profile",
    ".post-author",
    ".byline-box",
    ".writer-contact-block",
    """[class*=WriterContactBlock]""",
    ".entry-meta",
    ".post-meta",
    ".post-meta-infos",
    ".article-meta",
    ".posted-on",
    ".byline",
    ".newsletter-promotion-large",
    ".newsletter-section",
    ".newsletter-form__wrapper",
    ".subscription-widget-wrap",
    ".subscription-widget",
    ".subscribe-widget",
    """[data-inview-type*=newsletter]""",
    """[data-inview-category*=Newsletter]""",
    """[data-component-name*=SubscribeWidget]""",
    ".ad",
    ".ads",
    ".advertisement",
    ".comments",
    ".comment",
    ".top-comment",
    ".share",
    ".sharing",
    ".related",
    ".related-posts",
    ".toc",
    ".table-of-contents",
)

internal val TABLE_OF_CONTENTS_EXACT_SELECTORS = setOf(".toc", ".table-of-contents")
internal val NESTED_ARTICLE_FOOTER_TAGS = setOf("aside", "div", "p", "section", "ul", "ol", "hr")

internal val PARTIAL_PATTERNS = listOf(
    "advert",
    "breadcrumb",
    "promo",
    "recommend",
    "related",
    "share",
    "sidebar",
    "sponsor",
    "subscribe",
    "newsletter",
)

internal val SUBSCRIBE_PATTERN = Regex(
    """\b(subscribe|newsletter|weekly updates|product announcements)\b""",
    RegexOption.IGNORE_CASE,
)

internal val RECOMMENDATION_HEADING_PATTERN = Regex(
    """\b(recommended|related|related terms|explore more|keep exploring|discover more|more stories|more from|more on|read more|you may also like|popular stories|most viewed|consigliati|altre storie|i più letti|in evidenza|potrebbe interessarti)\b|^best(?:\s+[\p{L}\p{N}][\p{L}\p{N} .'"’&-]{0,80})?\s+(accessories|deals|offers|prices?|discounts?|sales?)$""",
    RegexOption.IGNORE_CASE,
)

internal val RECOMMENDATION_SECTION_HEADING_PATTERN = Regex(
    """^(related\s+content|related\s+articles?|related\s+terms|recommended(?:\s+for\s+you)?|explore\s+more|keep\s+exploring|discover\s+more(?:\s+.+)?|what\s+to\s+read\s+next|read\s+more|for\s+more\s+on\s+this\s+topic|popular\s+stories|most\s+viewed|latest\s+articles?|latest\s+in\s+.+|more\s+stories|more\s+from\s+.+|you\s+may\s+also\s+like|best(?:\s+[\p{L}\p{N}][\p{L}\p{N} .'"’&-]{0,80})?\s+(?:accessories|deals|offers|prices?|discounts?|sales?)|consigliati|altre\s+storie|i\s+più\s+letti|potrebbe\s+interessarti)$""",
    RegexOption.IGNORE_CASE,
)

internal val LOREM_PLACEHOLDER_PATTERN = Regex(
    """\blorem\s+ipsum\s+dolor\s+sit\s+amet\b""",
    RegexOption.IGNORE_CASE,
)

internal val SKELETON_RECIRCULATION_HEADING_PATTERN = Regex(
    """\b(latest\s+in|most\s+popular|most\s+viewed|popular\s+stories|recommended|related|read\s+more)\b""",
    RegexOption.IGNORE_CASE,
)

internal val RELATIVE_TIME_AGO_PATTERN = Regex(
    """\b\d+\s+(?:minute|hour|day|week|month|year)s?\s+ago\b""",
    RegexOption.IGNORE_CASE,
)

internal val AUTHOR_FOLLOW_PATTERN = Regex(
    """^\s*follow\s+[\p{L}\p{N} ._'’-]{1,48}\s*:""",
    RegexOption.IGNORE_CASE,
)

internal val POSTED_BY_BYLINE_PATTERN = Regex(
    """^posted\s+by\s+[\p{L}\p{N} ._'&/@-]{1,80}$""",
    RegexOption.IGNORE_CASE,
)

internal val TRAILING_TAG_LABEL_PATTERN = Regex(
    """^\s*(tags?|tagged|etichette?)\s*:""",
    RegexOption.IGNORE_CASE,
)

internal val COMMENT_COUNT_PATTERN = Regex(
    """^\[?\s*\d+\s+comments?\s*\]?$""",
    RegexOption.IGNORE_CASE,
)

internal val SOCIAL_COUNTER_PATTERN = Regex(
    """^\d+\s+(?:likes?|shares?|reposts?)$""",
    RegexOption.IGNORE_CASE,
)

internal val BREADCRUMB_HREF_PATTERN = Regex("""(?:^|\s)/(?:archive|posts?|blog|news|category|tags?)(?:/|\s|$)""")

internal val BACK_TO_TOP_PATTERN = Regex(
    """^back\s+to\s+top$""",
    RegexOption.IGNORE_CASE,
)

internal val COMMENT_PROMPT_PATTERN = Regex(
    """\b(commenting|join the conversation|display name before commenting)\b""",
    RegexOption.IGNORE_CASE,
)

internal val READY_FOR_MORE_PATTERN = Regex(
    """^ready\s+for\s+more\??$""",
    RegexOption.IGNORE_CASE,
)

internal val MOBILE_APP_PROMO_PATTERN = Regex(
    """\b(download our app|scarica l['’]?app|app per rimanere sempre aggiornato|also on mobile|anche su mobile)\b""",
    RegexOption.IGNORE_CASE,
)

internal val NEWSLETTER_SIGNUP_PATTERN = Regex(
    """\b(subscribe\s+to\s+(?:our|the|a)\s+newsletter|receive\s+newsletter|newsletter\s+signup|subscribe\s+.*\bnewsletter|sign\s+up\s+for\s+.{0,80}\bnewsletters?\b)\b""",
    RegexOption.IGNORE_CASE,
)

internal val NEWSLETTER_LEGAL_PATTERN = Regex(
    """\b(marketing\s+emails|terms\s+of\s+use|privacy\s+policy|unsubscribe\s+(?:anytime|any\s+time))\b""",
    RegexOption.IGNORE_CASE,
)

internal val INLINE_NEWSLETTER_PROMO_PATTERN = Regex(
    """\bwant\s+to\s+learn\s+more\s+about\s+getting\s+the\s+best\s+out\s+of\s+your\s+tech\b""",
    RegexOption.IGNORE_CASE,
)

internal val DONATION_WIDGET_PATTERN = Regex(
    """\b(enjoyed\s+the\s+article|buy\s+me\s+a\s+coffee|support\s+(?:us|our\s+work))\b""",
    RegexOption.IGNORE_CASE,
)

internal val DONATION_WIDGET_HINTS = listOf(
    "ko-fi",
    "kofi",
    "buy me a coffee",
)

internal val BYLINE_METADATA_STRIP_PATTERN = Regex(
    """^\s*by\b.+(?:\bedited\s+by\b|\breviewed\s+by\b|\bupdated\s+by\b|[|].+\b(?:jan|feb|mar|apr|may|jun|jul|aug|sep|sept|oct|nov|dec)\p{L}*\.?\s+\d{1,2},\s+\d{4}\b)""",
    RegexOption.IGNORE_CASE,
)

internal val BYLINE_METADATA_DATE_PATTERN = Regex(
    """\b(?:jan|feb|mar|apr|may|jun|jul|aug|sep|sept|oct|nov|dec)\p{L}*\.?\s+\d{1,2},\s+\d{4}\b|\b\d{4}-\d{2}-\d{2}T""",
    RegexOption.IGNORE_CASE,
)

internal val TRAILING_BYLINE_DATE_PATTERN = Regex(
    """^by\s+[\p{L}\p{N} ._'&/@-]{1,80}\s+\b(?:jan|feb|mar|apr|may|jun|jul|aug|sep|sept|oct|nov|dec)\p{L}*\.?\s+\d{1,2},\s+\d{4}$""",
    RegexOption.IGNORE_CASE,
)

internal val ARTICLE_PACKAGE_PATTERN = Regex(
    """^part\s+of\b.+\bsee\s+all\s+updates\b""",
    RegexOption.IGNORE_CASE,
)

internal val authorRoleAlternation = listOf(
    "contributor",
    "freelance\\s+writer",
    "news\\s+writer",
    "staff\\s+writer",
    "senior\\s+writer",
    "reporter",
    "journalist",
    "editor",
    "reviewer",
    "product\\s+manager",
).joinToString("|")

internal val INLINE_AUTHOR_BIO_PATTERN = Regex(
    """\bis\s+an?\s+[\p{L}\p{N} .,&'’/-]{0,80}\b($authorRoleAlternation)\b""",
    RegexOption.IGNORE_CASE,
)

internal val AUTHOR_ROLE_LABEL_PATTERN = Regex(
    """\b($authorRoleAlternation)\b""",
    RegexOption.IGNORE_CASE,
)

internal val FOLLOW_TOPICS_PATTERN = Regex(
    """\bfollow\s+topics\s+and\s+authors\b|\bpersonalized\s+homepage\s+feed\b|\breceive\s+email\s+updates\b|\b(?:favorite|preferred)\s+source\s+in\s+google\b|\bgoogle\s+discover\b""",
    RegexOption.IGNORE_CASE,
)

internal val FOLLOW_TOPICS_STRONG_CONTEXT_PATTERN = Regex(
    """\bfrom\s+this\s+story\b.+\b(receive\s+email\s+updates|personalized\s+homepage)\b""",
    RegexOption.IGNORE_CASE,
)

internal val STORY_SUGGESTION_PATTERN = Regex(
    """\b(do\s+you\s+have\s+a\s+story\s+suggestion|contact\s+us\s+below)\b""",
    RegexOption.IGNORE_CASE,
)

internal val LOCAL_NEWS_FOLLOW_PATTERN = Regex(
    """^follow\s+[\p{L}\p{N} .,'’&-]{1,80}\s+news\s+on\b""",
    RegexOption.IGNORE_CASE,
)

internal val ABOUT_AUTHOR_FOOTER_PATTERN = Regex(
    """^about\s+the\s+authors?\b""",
    RegexOption.IGNORE_CASE,
)

internal val ARTICLE_FOOTER_DETAILS_PATTERN = Regex(
    """\b(?:share\b.*\bdetails\b.*\b(?:last\s+updated|editor|contact|location)\b|details\b.*\b(?:last\s+updated|editor|contact|location)\b.*\b(?:editor|contact|location)\b)""",
    RegexOption.IGNORE_CASE,
)

internal val ARTICLE_FOOTER_DETAILS_HEADING_PATTERN = Regex(
    """^(share|details|related\s+terms)$""",
    RegexOption.IGNORE_CASE,
)

internal val RELATED_TERMS_PATTERN = Regex(
    """^related\s+terms\b""",
    RegexOption.IGNORE_CASE,
)

internal val PROTECTED_EXACT_SELECTOR_OVERRIDES = setOf(
    ".wp-block-post-featured-image__caption",
    "img.hide-when-no-script",
    """img[aria-label="image unavailable"]""",
    """img[src*="grey-placeholder"]""",
)

internal val NON_SUBSTANTIVE_OPENING_TAGS = setOf(
    "script",
    "style",
    "template",
    "noscript",
)

internal val OPENING_ARTICLE_BODY_HINTS = listOf(
    "article-body",
    "article-content",
    "post-content",
    "story-body",
)

internal val OPENING_ARTICLE_HEADER_HINTS = listOf(
    "article-aux",
    "article-meta",
    "block-header",
    "hero-caption",
    "mega-header",
    "river-score",
    "rumor-score",
    "upper-deck",
)

internal val COMMENT_LINK_HINTS = listOf(
    "/thread",
    "/comment",
    "#comment",
    "forums.",
)

internal val RECOMMENDATION_MODULE_HINTS = listOf(
    "article-card",
    "display-card",
    "is-entire-card-clickable",
    "read-next",
    "recommend",
    "recirc",
    "related",
    "river",
    "what-to-read-next",
)

internal val STRONG_RECIRCULATION_HINTS = listOf(
    "recommend",
    "recirc",
    "related",
    "read-next",
    "what-to-read-next",
)

internal val ROOT_CONTENT_TAGS = setOf("article", "main")
internal val RECIRCULATION_CLUSTER_TAGS = setOf("aside", "div", "ol", "section", "ul")
internal val ARTICLE_CARD_RECIRCULATION_TAGS = setOf("article", "div", "section")
internal val ARTICLE_CARD_RECIRCULATION_HINTS = listOf(
    "article-card",
    "article-wrapper",
    "display-card",
    "is-entire-card-clickable",
)

internal val ARTICLE_CARD_METADATA_HINTS = listOf(
    "article-card-date",
    "article-eyebrow",
    "time-ago",
)

internal val ORPHAN_SEPARATOR_TEXTS = setOf("/", "|")

internal val WHITESPACE_PATTERN = Regex("""\s+""")
internal val HEADING_TAG_PATTERN = Regex("""h[1-6]""")

internal val SKELETON_RECIRCULATION_HINTS = listOf(
    "animate-pulse",
    "bg-helper",
    "skeleton",
    "placeholder",
)

internal val POSTED_BY_BYLINE_HINTS = listOf(
    "author",
    "byline",
    "post-meta",
    "posted",
    "separator",
)

internal const val RECOMMENDATION_MIN_LINKS = 2
internal const val RECOMMENDATION_MIN_ARTICLES = 2
internal const val RECOMMENDATION_MIN_IMAGES = 2
internal const val RECOMMENDATION_HEADING_MAX_LENGTH = 90
internal const val SKELETON_RECIRCULATION_MAX_LENGTH = 7_000
internal const val SKELETON_RECIRCULATION_MIN_PLACEHOLDERS = 2
internal const val SKELETON_RECIRCULATION_PROSE_WORD_GUARD = 8
internal const val BREADCRUMB_MAX_LENGTH = 180
internal const val BREADCRUMB_MIN_LINKS = 2
internal const val BREADCRUMB_MAX_LINKS = 6
internal const val TABLE_OF_CONTENTS_MAX_LENGTH = 2_000
internal const val TABLE_OF_CONTENTS_MIN_LINKS = 4
internal const val RECIRCULATION_CLUSTER_MAX_LENGTH = 2_000
internal const val RECIRCULATION_CLUSTER_MIN_LINKS = 2
internal const val RECIRCULATION_CLUSTER_ROW_MIN_LINKS = 1
internal const val RECIRCULATION_CLUSTER_MIN_ROWS = 2
internal const val RECIRCULATION_CLUSTER_MIN_TAG_LINKS = 2
internal const val RECIRCULATION_CLUSTER_MIN_LINK_DENSITY = 0.55
internal const val TRAILING_TAG_MIN_LINKS = 1
internal const val TRAILING_TAG_MAX_WORDS = 16
internal const val COMMENT_COUNT_MAX_LINKS = 2
internal const val COMMENT_PROMPT_MAX_LENGTH = 260
internal const val MOBILE_APP_PROMO_MAX_LENGTH = 180
internal const val NEWSLETTER_SIGNUP_MAX_LENGTH = 700
internal const val DONATION_WIDGET_MAX_LENGTH = 220
internal const val BYLINE_METADATA_STRIP_MAX_LENGTH = 360
internal const val ARTICLE_PACKAGE_MAX_LENGTH = 320
internal const val ARTICLE_CARD_RECIRCULATION_MAX_LENGTH = 900
internal const val ARTICLE_CARD_PROSE_WORD_GUARD = 12
internal const val ARTICLE_CARD_MIN_HEADLINE_WORDS = 4
internal const val ARTICLE_CARD_IMAGE_ONLY_MAX_WORDS = 8
internal const val INLINE_AUTHOR_BIO_MAX_LENGTH = 700
internal const val FOLLOW_TOPICS_MAX_LENGTH = 360
internal const val STORY_SUGGESTION_MAX_LENGTH = 220
internal const val LOCAL_NEWS_FOLLOW_MAX_LENGTH = 360
internal const val OPENING_ARTICLE_HEADER_MAX_LENGTH = 700
internal const val OPENING_ARTICLE_HEADER_MAX_PARAGRAPHS = 2
internal const val AUTHOR_FOLLOW_MAX_LENGTH = 220
internal const val POSTED_BY_BYLINE_MAX_LENGTH = 120
internal const val RECOMMENDATION_TEXT_PREFIX_LENGTH = 80
internal const val ABOUT_AUTHOR_FOOTER_MAX_LENGTH = 900
internal const val ABOUT_AUTHOR_FOOTER_MAX_WORDS = 80
internal const val ABOUT_AUTHOR_PROSE_WORD_GUARD = 16
internal const val ARTICLE_FOOTER_DETAILS_MAX_LENGTH = 1_600
internal const val ARTICLE_FOOTER_DETAILS_PROSE_WORD_GUARD = 18
internal const val RELATED_TERMS_MAX_LENGTH = 1_200
internal const val RELATED_TERMS_PROSE_WORD_GUARD = 14
internal const val RELATED_TERMS_MIN_LINKS = 2
internal const val PARAGRAPH_FOOTER_SIGNAL_MAX_LENGTH = 700
