package com.prof18.klead.benchmarks

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.toKString
import kotlinx.coroutines.CancellationException
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
            // Not runCatching: a cancelled run must stay cancelled instead of reporting a failure.
            try {
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
                            expectsHtml = expectsOutput(resources.readOrNull("$EXPECTED_HTML_DIRECTORY$name.html")),
                            expectsMarkdown = expectsOutput(resources.readOrNull("$EXPECTED_MARKDOWN_DIRECTORY$name.md")),
                        )
                    }
                val missingCoreFixtures = coreFixtureSet - fixtures.map(BenchmarkFixture::name).toSet()
                require(coreFixtureSet.isNotEmpty()) { "$CORE_MANIFEST_PATH must not be empty" }
                require(missingCoreFixtures.isEmpty()) {
                    "$CORE_MANIFEST_PATH references missing fixtures: ${missingCoreFixtures.sorted()}"
                }
                val result = RegressionBenchmark(
                    config = RegressionBenchmarkConfig.fromEnvironment(::environment),
                    fixtures = fixtures,
                ).run()
                completion(result.output, result.error)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Throwable) {
                completion(null, failure.stackTraceToString())
            }
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

    fun readOrNull(path: String): String? = if (path in paths) read(path) else null

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
