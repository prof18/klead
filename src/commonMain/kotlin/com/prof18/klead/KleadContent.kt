package com.prof18.klead

data class KleadContent(val html: String?, val markdown: String?) {
    init {
        require(html != null || markdown != null) { "At least one content output must be present." }
    }

    fun requireHtml(): String = checkNotNull(html) { "HTML output was not requested." }

    fun requireMarkdown(): String = checkNotNull(markdown) { "Markdown output was not requested." }
}
