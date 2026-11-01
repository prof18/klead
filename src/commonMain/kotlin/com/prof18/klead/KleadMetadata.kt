package com.prof18.klead

data class KleadMetadata(
    val title: String?,
    val description: String?,
    val favicon: String?,
    val image: String?,
    val author: String?,
    val site: String?,
    val language: String? = null,
    /** True when a site extractor chose the content instead of generic content detection. */
    val fromSiteProfile: Boolean = false,
)
