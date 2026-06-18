package dev.defuddle

import dev.defuddle.internal.DefuddleParser
import kotlinx.coroutines.Dispatchers

object Defuddle {
    suspend fun parseHtml(html: String, url: String, options: DefuddleOptions): DefuddleResult =
        DefuddleParser.parseHtml(html = html, url = url, options = options, parserDispatcher = Dispatchers.Default)
}
