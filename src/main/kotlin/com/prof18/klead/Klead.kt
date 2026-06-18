package com.prof18.klead

import com.prof18.klead.internal.KleadParser
import kotlinx.coroutines.Dispatchers

object Klead {
    suspend fun parseHtml(html: String, url: String, options: KleadOptions): KleadResult =
        KleadParser.parseHtml(html = html, url = url, options = options, parserDispatcher = Dispatchers.Default)
}
