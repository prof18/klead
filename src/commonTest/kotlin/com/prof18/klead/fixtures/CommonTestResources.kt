package com.prof18.klead.fixtures

internal object CommonTestResources {
    val paths: Set<String> by lazy(::commonTestResourcePaths)

    fun read(path: String): String = readCommonTestResource(path)
}

internal expect fun commonTestResourcePaths(): Set<String>

internal expect fun readCommonTestResource(path: String): String

internal expect fun commonTestEnvironment(name: String): String?

// Growth tests divide their sizes by this. Debug native binaries parse many times slower than the JVM, so they
// check linear growth on smaller inputs; the JVM keeps the sizes at which the old quadratic paths fail.
internal expect val growthTestSizeDivisor: Int
