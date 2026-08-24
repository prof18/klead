package com.prof18.klead.fixtures

import java.io.File

internal actual fun commonTestResourcePaths(): Set<String> = testResourcesRoot
    .walkTopDown()
    .filter(File::isFile)
    .map { it.relativeTo(testResourcesRoot).invariantSeparatorsPath }
    .toSet()

internal actual fun readCommonTestResource(path: String): String = testResourcesRoot.resolve(path).readText()

private val testResourcesRoot: File by lazy {
    val path = requireNotNull(System.getenv(TEST_RESOURCES_ROOT)) {
        "$TEST_RESOURCES_ROOT must point to the common test resources directory"
    }
    File(path).also { require(it.isDirectory) { "Missing common test resources directory: $it" } }
}

private const val TEST_RESOURCES_ROOT = "TEST_RESOURCES_ROOT"
