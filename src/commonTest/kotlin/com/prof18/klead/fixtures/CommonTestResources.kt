package com.prof18.klead.fixtures

internal object CommonTestResources {
    val paths: Set<String> by lazy(::commonTestResourcePaths)

    fun read(path: String): String = readCommonTestResource(path)
}

internal expect fun commonTestResourcePaths(): Set<String>

internal expect fun readCommonTestResource(path: String): String

internal expect fun commonTestEnvironment(name: String): String?
