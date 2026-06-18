package dev.defuddle.site

object AndroidAuthorityProfile : SiteExtractor {
    override val id: String = "android-authority"
    override val domains: Set<String> = setOf("androidauthority.com")
    override val postContentRemoveSelectors: List<String> = listOf(
        """div:has(> p:matchesOwn((?i)^Affiliate links on Android Authority may earn us a commission))""",
        """a[href="https://www.androidauthority.com/mobile/"]""",
        """div:matchesOwn((?i)^The Android 17-based update brings critical display, camera, and stability patches\.$)""",
        """div:has(> a[href*="AAGooglePrefSource"])""",
        """div:has(> a[href*="AAGoogleDiscoverSource"])""",
        """div[data-container-type="content"]:has(a[href*="AAGoogleDiscoverSource"])""",
        """div[data-container-type="content"]:has(a[href*="AAGooglePreferredSource"])""",
        """div:has(> div:matchesOwn((?i)^Follow$))""",
        """div[data-container-type="content"]:has(a[href*="android-authority-comment-policy"])""",
    )
}
