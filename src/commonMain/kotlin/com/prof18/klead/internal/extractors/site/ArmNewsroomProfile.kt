package com.prof18.klead.internal.extractors.site

import com.prof18.klead.extractors.Extractor
import com.prof18.klead.extractors.ExtractorContext
import com.prof18.klead.extractors.ExtractorMetadata
import com.prof18.klead.extractors.ExtractorResult
import com.prof18.klead.internal.dom.selectFirstSafe
import com.prof18.klead.internal.extractors.document

internal object ArmNewsroomProfile : Extractor {
    override val id: String = "arm-newsroom"
    override val domains: Set<String> = setOf("newsroom.arm.com", "arm.com")
    override val postContentRemoveSelectors: List<String> = listOf(
        ".single_post__breadcrumbs",
        ".single_post__intro_box ads-breadcrumbs",
        ".single_post__title",
        ".single_post__final_box",
        ".CopyContent",
        """.single_post__editorial_contact""",
        ".TwiBlock",
        """p:matches((?i)Any re-use permitted for informational)""",
    )

    override fun matches(context: ExtractorContext): Boolean =
        context.hostMatches(domains) || context.document.selectFirstSafe("#single_post .single_post__content") != null

    override fun extract(context: ExtractorContext): ExtractorResult? {
        val author = context.document.selectFirst(".single_post__author__info a[href*=/author/]")
            ?.text()
            ?.trim()
            ?.ifBlank { null }
            ?: return null
        return ExtractorResult(
            metadata = ExtractorMetadata(
                author = author,
                site = author,
            ),
        )
    }
}
