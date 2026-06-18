package dev.defuddle

data class DefuddleResult(val content: DefuddleContent, val metadata: DefuddleMetadata, val debug: Map<String, Any?>)
