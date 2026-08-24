package com.prof18.klead.fixtures

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.toKString
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.Foundation.dataWithContentsOfFile
import platform.posix.getenv

@OptIn(ExperimentalForeignApi::class)
internal actual fun commonTestResourcePaths(): Set<String> = NSFileManager.defaultManager
    .subpathsAtPath(testResourcesRoot)
    .orEmpty()
    .map { it.toString() }
    .toSet()

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
internal actual fun readCommonTestResource(path: String): String {
    val data = requireNotNull(NSData.dataWithContentsOfFile("$testResourcesRoot/$path")) {
        "Missing common test resource: $path"
    }
    return NSString.create(data, NSUTF8StringEncoding).toString()
}

@OptIn(ExperimentalForeignApi::class)
private val testResourcesRoot: String by lazy {
    requireNotNull(getenv(TEST_RESOURCES_ROOT)?.toKString()) {
        "$TEST_RESOURCES_ROOT must point to the common test resources directory"
    }
}

private const val TEST_RESOURCES_ROOT = "TEST_RESOURCES_ROOT"
