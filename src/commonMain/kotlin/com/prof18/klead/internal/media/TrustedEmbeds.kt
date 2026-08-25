package com.prof18.klead.internal.media

import com.prof18.klead.internal.dom.KleadUri
import com.prof18.klead.internal.dom.parseKleadUri

internal object TrustedEmbeds {
    fun markdownMediaFromUrl(url: String): TrustedMarkdownMedia? {
        val parsed = parseHttpsUrl(url) ?: return null
        val host = parsed.host
        val path = parsed.uri.rawPath.orEmpty()

        return when {
            host in YOUTUBE_EMBED_HOSTS && path.startsWith("/embed/") -> {
                youtubeVideoFromId(path.removePrefix("/embed/").substringBefore('/'))
            }

            host in YOUTUBE_EMBED_HOSTS && path == "/watch" -> {
                youtubeVideoFromId(queryParameter(parsed.uri.rawQuery.orEmpty(), "v").orEmpty())
            }

            host == "youtu.be" -> {
                youtubeVideoFromId(path.trim('/').substringBefore('/'))
            }

            host == "platform.twitter.com" && path.equals("/embed/Tweet.html", ignoreCase = true) -> {
                twitterStatusFromId(queryParameter(parsed.uri.rawQuery.orEmpty(), "id").orEmpty())
            }

            host in X_STATUS_HOSTS -> xStatusFromPath(path)

            else -> null
        }
    }

    fun youtubeVideoFromId(rawId: String): TrustedMarkdownMedia? {
        val id = rawId.trim()
        if (!YOUTUBE_ID.matches(id)) return null
        return TrustedMarkdownMedia(
            watchUrl = "https://www.youtube.com/watch?v=$id",
            normalizedIframeSrc = "https://www.youtube-nocookie.com/embed/$id",
            defaultTitle = "YouTube video",
            markdownLinkLabel = null,
        )
    }

    fun isTrustedIframeSrc(url: String): Boolean = markdownMediaFromUrl(url) != null || isTrustedRawIframeSrc(url)

    fun isTrustedRawIframeSrc(url: String): Boolean {
        val parsed = parseHttpsUrl(url) ?: return false
        if (parsed.host != "player.vimeo.com") return false
        val segments = parsed.uri.rawPath.orEmpty().trim('/').split('/')
        return segments.size >= 2 &&
            segments[0] == "video" &&
            VIMEO_ID.matches(segments[1])
    }

    private fun twitterStatusFromId(rawId: String): TrustedMarkdownMedia? {
        val id = rawId.trim()
        if (!TWEET_ID.matches(id)) return null
        return TrustedMarkdownMedia(
            watchUrl = "https://x.com/i/status/$id",
            normalizedIframeSrc = twitterEmbedUrl(id),
            defaultTitle = "X post",
            markdownLinkLabel = "X post",
        )
    }

    private fun xStatusFromPath(path: String): TrustedMarkdownMedia? {
        val segments = path.trim('/').split('/').filter { it.isNotBlank() }
        val statusIndex = segments.indexOf("status")
        if (statusIndex == -1 || statusIndex == segments.lastIndex) return null
        val id = segments[statusIndex + 1]
        if (!TWEET_ID.matches(id)) return null

        val prefix = segments.take(statusIndex).joinToString("/")
        val statusPath = if (prefix.isBlank()) {
            "i/status/$id"
        } else {
            "$prefix/status/$id"
        }
        return TrustedMarkdownMedia(
            watchUrl = "https://x.com/$statusPath",
            normalizedIframeSrc = twitterEmbedUrl(id),
            defaultTitle = "X post",
            markdownLinkLabel = "X post",
        )
    }

    private fun twitterEmbedUrl(id: String): String = "https://platform.twitter.com/embed/Tweet.html?id=$id"

    private fun queryParameter(query: String, name: String): String? = query.split('&')
        .firstNotNullOfOrNull { part ->
            val pieces = part.split('=', limit = 2)
            pieces.takeIf { it.size == 2 && it[0] == name }?.get(1)
        }

    private fun parseHttpsUrl(url: String): ParsedUrl? {
        val uri = parseKleadUri(url.trim()) ?: return null
        if (!uri.scheme.equals("https", ignoreCase = true)) return null
        val host = uri.host?.lowercase()?.removePrefix("www.") ?: return null
        return ParsedUrl(uri, host)
    }

    private val YOUTUBE_ID = Regex("""[A-Za-z0-9_-]{6,32}""")
    private val TWEET_ID = Regex("""\d{5,32}""")
    private val VIMEO_ID = Regex("""\d{5,32}""")
    private val YOUTUBE_EMBED_HOSTS = setOf("youtube.com", "youtube-nocookie.com")
    private val X_STATUS_HOSTS = setOf("x.com", "twitter.com")

    private data class ParsedUrl(val uri: KleadUri, val host: String)
}

internal data class TrustedMarkdownMedia(
    val watchUrl: String,
    val normalizedIframeSrc: String?,
    val defaultTitle: String,
    val markdownLinkLabel: String?,
)
