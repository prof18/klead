package com.prof18.klead

data class KleadResult(val content: KleadContent, val metadata: KleadMetadata, val debug: Map<String, Any?>) {
    // The debug map can hold every removal record; its keys are enough in a log line.
    override fun toString(): String = "KleadResult(content=$content, metadata=$metadata, debug=${debug.keys})"
}
