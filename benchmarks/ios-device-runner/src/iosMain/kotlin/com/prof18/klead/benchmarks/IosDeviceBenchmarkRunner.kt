package com.prof18.klead.benchmarks

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.toKString
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import platform.Foundation.NSBundle
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.Foundation.dataWithContentsOfFile
import platform.posix.getenv

class IosDeviceBenchmarkRunner {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun start(completion: (output: String?, error: String?) -> Unit) {
        scope.launch {
            runCatching {
                require(environment(RUN_BENCHMARK_ENV) == "true") {
                    "$RUN_BENCHMARK_ENV must be true"
                }
                val resources = BenchmarkResources()
                val coreFixtureNames = resources.read(CORE_MANIFEST_PATH)
                    .lineSequence()
                    .map(String::trim)
                    .filter { it.isNotEmpty() && !it.startsWith('#') }
                    .toList()
                require(coreFixtureNames.size == coreFixtureNames.toSet().size) {
                    "$CORE_MANIFEST_PATH contains duplicate fixtures"
                }
                val coreFixtureSet = coreFixtureNames.toSet()
                val fixtures = resources.paths
                    .filter { it.startsWith(INPUT_DIRECTORY) && it.endsWith(HTML_SUFFIX) }
                    .filterNot { it.substringAfterLast('/').startsWith("harness--") }
                    .sorted()
                    .map { path ->
                        val html = resources.read(path)
                        val name = path.substringAfterLast('/').removeSuffix(HTML_SUFFIX)
                        BenchmarkFixture(
                            name = name,
                            html = html,
                            inputBytes = html.encodeToByteArray().size.toLong(),
                            isCore = name in coreFixtureSet,
                        )
                    }
                val missingCoreFixtures = coreFixtureSet - fixtures.map(BenchmarkFixture::name).toSet()
                require(coreFixtureSet.isNotEmpty()) { "$CORE_MANIFEST_PATH must not be empty" }
                require(missingCoreFixtures.isEmpty()) {
                    "$CORE_MANIFEST_PATH references missing fixtures: ${missingCoreFixtures.sorted()}"
                }
                RegressionBenchmark(
                    config = RegressionBenchmarkConfig.fromEnvironment(::environment),
                    fixtures = fixtures,
                ).run()
            }.fold(
                onSuccess = { result -> completion(result.output, result.error) },
                onFailure = { failure -> completion(null, failure.stackTraceToString()) },
            )
        }
    }

    fun cancel() {
        scope.cancel()
    }
}

@OptIn(ExperimentalForeignApi::class)
private class BenchmarkResources {
    private val root = "${requireNotNull(NSBundle.mainBundle.resourcePath)}/test-resources"

    val paths: Set<String> = NSFileManager.defaultManager
        .subpathsAtPath(root)
        .orEmpty()
        .map { it.toString() }
        .toSet()

    @OptIn(BetaInteropApi::class)
    fun read(path: String): String {
        val data = requireNotNull(NSData.dataWithContentsOfFile("$root/$path")) {
            "Missing benchmark resource: $path"
        }
        return NSString.create(data, NSUTF8StringEncoding).toString()
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun environment(name: String): String? = getenv(name)?.toKString()

private const val RUN_BENCHMARK_ENV = "KLEAD_RUN_REGRESSION_BENCHMARK"
