import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.mpp.NativeBuildType
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.gradle.targets.native.tasks.KotlinNativeHostTest
import org.jetbrains.kotlin.gradle.targets.native.tasks.KotlinNativeSimulatorTest
import org.jetbrains.kotlin.gradle.targets.native.tasks.KotlinNativeTest
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Properties

val commonTestResources = layout.projectDirectory.dir("src/commonTest/resources")
val regressionBenchmarkBaselines = Properties().apply {
    file("benchmarks/regression-corpus-baselines.properties").inputStream().use(::load)
}
val regressionBenchmarkSamples = providers.gradleProperty("benchmarkSamples")
    .orElse(providers.environmentVariable("KLEAD_BENCHMARK_SAMPLES"))
    .orElse("5")
    .get()

fun regressionBenchmarkBudget(platform: String, metric: String): String? =
    regressionBenchmarkBaselines.getProperty("$platform.core.$metric")

fun requiredRegressionBenchmarkBudget(platform: String, metric: String): String =
    requireNotNull(regressionBenchmarkBudget(platform, metric))

fun regressionBenchmarkBudgets(platform: String, simulator: Boolean = false): Map<String, String> {
    val budgets = mapOf(
        "KLEAD_REGRESSION_CORE_MAX_MEDIAN_MS" to requiredRegressionBenchmarkBudget(platform, "maxMedianMillis"),
        "KLEAD_REGRESSION_CORE_MAX_P95_ARTICLE_MS" to regressionBenchmarkBudget(platform, "maxP95ArticleMillis"),
        "KLEAD_REGRESSION_CORE_MAX_WORST_ARTICLE_MS" to regressionBenchmarkBudget(platform, "maxWorstArticleMillis"),
    )
    return buildMap {
        budgets.forEach { (name, value) ->
            if (value != null) {
                put(name, value)
                if (simulator) put("SIMCTL_CHILD_$name", value)
            }
        }
    }
}

val kotlin = extensions.getByType<KotlinMultiplatformExtension>()
val iosSimulatorArm64BenchmarkBinary = kotlin.targets
    .getByName<KotlinNativeTarget>("iosSimulatorArm64")
    .binaries.getTest("benchmark", NativeBuildType.RELEASE)
val macosArm64BenchmarkBinary = kotlin.targets
    .getByName<KotlinNativeTarget>("macosArm64")
    .binaries.getTest("benchmark", NativeBuildType.RELEASE)

tasks.register<KotlinNativeSimulatorTest>("iosSimulatorArm64ReleaseBenchmarkTest") {
    group = "verification"
    description = "Runs optimized benchmark smoke tests on the iOS arm64 Simulator."
    targetName = "iosSimulatorArm64"
    workingDir = projectDir.absolutePath
    binaryResultsDirectory.set(layout.buildDirectory.dir("test-results/$name/binary"))
    reports.junitXml.outputLocation.set(layout.buildDirectory.dir("test-results/$name"))
    reports.html.outputLocation.set(layout.buildDirectory.dir("reports/tests/$name"))
    executable(iosSimulatorArm64BenchmarkBinary.linkTaskProvider.map { iosSimulatorArm64BenchmarkBinary.outputFile })
    val debugTest = tasks.named<KotlinNativeSimulatorTest>("iosSimulatorArm64Test")
    device.set(debugTest.flatMap { it.device })
    standalone.set(true)
}

tasks.register<KotlinNativeSimulatorTest>("iosSimulatorArm64ReleaseRegressionBenchmarkTest") {
    group = "verification"
    description = "Benchmarks the real-world regression fixture corpus on the iOS arm64 Simulator."
    targetName = "iosSimulatorArm64"
    workingDir = projectDir.absolutePath
    binaryResultsDirectory.set(layout.buildDirectory.dir("test-results/$name/binary"))
    reports.junitXml.outputLocation.set(layout.buildDirectory.dir("test-results/$name"))
    reports.html.outputLocation.set(layout.buildDirectory.dir("reports/tests/$name"))
    executable(iosSimulatorArm64BenchmarkBinary.linkTaskProvider.map { iosSimulatorArm64BenchmarkBinary.outputFile })
    val debugTest = tasks.named<KotlinNativeSimulatorTest>("iosSimulatorArm64Test")
    device.set(debugTest.flatMap { it.device })
    standalone.set(true)
    filter.includeTestsMatching("com.prof18.klead.fixtures.RegressionCorpusBenchmarkTest")
    environment("KLEAD_RUN_REGRESSION_BENCHMARK", "true")
    environment("SIMCTL_CHILD_KLEAD_RUN_REGRESSION_BENCHMARK", "true")
    environment("KLEAD_BENCHMARK_PLATFORM", "ios-simulator")
    environment("SIMCTL_CHILD_KLEAD_BENCHMARK_PLATFORM", "ios-simulator")
    environment("KLEAD_BENCHMARK_TARGET", "iosSimulatorArm64Release")
    environment("SIMCTL_CHILD_KLEAD_BENCHMARK_TARGET", "iosSimulatorArm64Release")
    environment("KLEAD_BENCHMARK_SAMPLES", regressionBenchmarkSamples)
    environment("SIMCTL_CHILD_KLEAD_BENCHMARK_SAMPLES", regressionBenchmarkSamples)
    regressionBenchmarkBudgets("ios-simulator", simulator = true).forEach { (name, value) ->
        environment(name, value)
    }
    outputs.upToDateWhen { false }
    mustRunAfter("iosSimulatorArm64ReleaseBenchmarkTest")
}

tasks.register<KotlinNativeHostTest>("macosArm64ReleaseBenchmarkTest") {
    group = "verification"
    description = "Runs optimized benchmark smoke tests on macOS arm64."
    targetName = "macosArm64"
    workingDir = projectDir.absolutePath
    binaryResultsDirectory.set(layout.buildDirectory.dir("test-results/$name/binary"))
    reports.junitXml.outputLocation.set(layout.buildDirectory.dir("test-results/$name"))
    reports.html.outputLocation.set(layout.buildDirectory.dir("reports/tests/$name"))
    executable(macosArm64BenchmarkBinary.linkTaskProvider.map { macosArm64BenchmarkBinary.outputFile })
}

tasks.register<KotlinNativeHostTest>("macosArm64ReleaseRegressionBenchmarkTest") {
    group = "verification"
    description = "Benchmarks the real-world regression fixture corpus on optimized macOS arm64."
    targetName = "macosArm64"
    workingDir = projectDir.absolutePath
    binaryResultsDirectory.set(layout.buildDirectory.dir("test-results/$name/binary"))
    reports.junitXml.outputLocation.set(layout.buildDirectory.dir("test-results/$name"))
    reports.html.outputLocation.set(layout.buildDirectory.dir("reports/tests/$name"))
    executable(macosArm64BenchmarkBinary.linkTaskProvider.map { macosArm64BenchmarkBinary.outputFile })
    filter.includeTestsMatching("com.prof18.klead.fixtures.RegressionCorpusBenchmarkTest")
    environment("KLEAD_RUN_REGRESSION_BENCHMARK", "true")
    environment("KLEAD_BENCHMARK_PLATFORM", "macos")
    environment("KLEAD_BENCHMARK_TARGET", "macosArm64Release")
    environment("KLEAD_BENCHMARK_SAMPLES", regressionBenchmarkSamples)
    regressionBenchmarkBudgets("macos").forEach { (name, value) -> environment(name, value) }
    outputs.upToDateWhen { false }
    mustRunAfter("macosArm64ReleaseBenchmarkTest")
}

tasks.register<Test>("jvmRegressionBenchmark") {
    group = "verification"
    description = "Benchmarks the real-world regression fixture corpus on the JVM."
    val jvmTest = tasks.named<Test>("jvmTest")
    testClassesDirs = jvmTest.get().testClassesDirs
    classpath = jvmTest.get().classpath
    useJUnitPlatform()
    filter.includeTestsMatching("com.prof18.klead.fixtures.RegressionCorpusBenchmarkTest")
    environment("TEST_RESOURCES_ROOT", commonTestResources.asFile.absolutePath)
    environment("KLEAD_RUN_REGRESSION_BENCHMARK", "true")
    environment("KLEAD_BENCHMARK_PLATFORM", "jvm")
    environment("KLEAD_BENCHMARK_TARGET", "jvm21")
    environment("KLEAD_BENCHMARK_SAMPLES", regressionBenchmarkSamples)
    regressionBenchmarkBudgets("jvm").forEach { (name, value) -> environment(name, value) }
    outputs.upToDateWhen { false }
}

tasks.withType<KotlinNativeTest>().configureEach {
    environment("TEST_RESOURCES_ROOT", commonTestResources.asFile.absolutePath)
    environment("SIMCTL_CHILD_TEST_RESOURCES_ROOT", commonTestResources.asFile.absolutePath)
    if (name == "iosSimulatorArm64ReleaseBenchmarkTest" || name == "macosArm64ReleaseBenchmarkTest") {
        filter.includeTestsMatching("com.prof18.klead.CommonPerformanceSmokeTest")
        outputs.upToDateWhen { false }
    }
}

tasks.register("nativeReleaseBenchmark") {
    group = "verification"
    description = "Runs optimized smoke and real-world corpus benchmarks on Apple arm64 targets."
    dependsOn(
        "iosSimulatorArm64ReleaseBenchmarkTest",
        "iosSimulatorArm64ReleaseRegressionBenchmarkTest",
        "macosArm64ReleaseBenchmarkTest",
        "macosArm64ReleaseRegressionBenchmarkTest",
    )
}

tasks.register("collectRegressionBenchmarkResults") {
    group = "verification"
    description = "Collects the latest JVM, Android, iOS, and macOS corpus benchmark results into JSON."
    val reportDirectory = layout.buildDirectory.dir("reports/benchmarks/regression-corpus")
    outputs.dir(reportDirectory)
    outputs.upToDateWhen { false }
    doLast {
        val resultPattern = Regex(
            """TIMING_REGRESSION_(CORPUS|CORE) platform=(\S+) target=(\S+) fixtures=(\d+) """ +
                """median=(\d+)ms samples=\[([^]]+)] inputBytes=(\d+) meanArticle=(\d+)us """ +
                """p50Article=(\d+)us p95Article=(\d+)us maxArticle=(\d+)us slowest=(\S+) """ +
                """throughput=(\d+)Bps""",
        )
        val inputRoots = listOf(
            layout.buildDirectory.dir("test-results/jvmRegressionBenchmark").get().asFile,
            layout.buildDirectory.dir("test-results/iosSimulatorArm64ReleaseRegressionBenchmarkTest").get().asFile,
            layout.buildDirectory.dir("test-results/macosArm64ReleaseRegressionBenchmarkTest").get().asFile,
            layout.buildDirectory.dir("outputs/androidTest-results/connected").get().asFile,
            layout.buildDirectory.dir("test-results/iosArm64PhysicalRegressionBenchmark").get().asFile,
        )
        val metrics = inputRoots.flatMap { root ->
            if (!root.exists()) emptyList() else root.walkTopDown()
                .filter { it.isFile && (it.extension == "xml" || it.extension == "txt") }
                .flatMap { file -> resultPattern.findAll(file.readText()) }
                .map { match ->
                    val values = match.groupValues
                    BenchmarkMetrics(
                        cohort = values[1].lowercase(),
                        platform = values[2],
                        target = values[3],
                        fixtures = values[4].toInt(),
                        medianMillis = values[5].toLong(),
                        samplesMillis = values[6].split(',').map(String::trim).map(String::toLong),
                        inputBytes = values[7].toLong(),
                        meanArticleMicros = values[8].toLong(),
                        p50ArticleMicros = values[9].toLong(),
                        p95ArticleMicros = values[10].toLong(),
                        maxArticleMicros = values[11].toLong(),
                        slowestFixture = values[12],
                        throughputBytesPerSecond = values[13].toLong(),
                    )
                }
                .toList()
        }
        val results = metrics.groupBy(BenchmarkMetrics::platform).mapValues { (platform, platformMetrics) ->
            val byCohort = platformMetrics.associateBy(BenchmarkMetrics::cohort)
            BenchmarkResult(
                platform = platform,
                full = requireNotNull(byCohort["corpus"]) { "Missing full corpus result for $platform" },
                core = requireNotNull(byCohort["core"]) { "Missing core corpus result for $platform" },
            )
        }

        val expectedPlatforms = setOf("jvm", "android", "ios-simulator", "ios-device", "macos")
        check(results.keys == expectedPlatforms) {
            "Expected results for $expectedPlatforms but found ${results.keys}"
        }

        val createdAt = Instant.now()
        val json = buildString {
            appendLine("{")
            appendLine("  \"schemaVersion\": 2,")
            appendLine("  \"createdAt\": \"$createdAt\",")
            appendLine("  \"results\": [")
            results.values.sortedBy(BenchmarkResult::platform).forEachIndexed { index, result ->
                val full = result.full
                val comma = if (index == results.size - 1) "" else ","
                appendLine("    {")
                appendLine("      \"platform\": \"${result.platform}\",")
                appendLine("      \"target\": \"${full.target}\",")
                appendLine("      \"fixtures\": ${full.fixtures},")
                appendLine("      \"medianMillis\": ${full.medianMillis},")
                appendLine("      \"samplesMillis\": ${full.samplesMillis},")
                appendLine("      \"inputBytes\": ${full.inputBytes},")
                appendLine("      \"meanArticleMicros\": ${full.meanArticleMicros},")
                appendLine("      \"p50ArticleMicros\": ${full.p50ArticleMicros},")
                appendLine("      \"p95ArticleMicros\": ${full.p95ArticleMicros},")
                appendLine("      \"maxArticleMicros\": ${full.maxArticleMicros},")
                appendLine("      \"slowestFixture\": \"${full.slowestFixture}\",")
                appendLine("      \"throughputBytesPerSecond\": ${full.throughputBytesPerSecond},")
                appendLine("      \"core\": {")
                appendLine("        \"fixtures\": ${result.core.fixtures},")
                appendLine("        \"medianMillis\": ${result.core.medianMillis},")
                appendLine("        \"samplesMillis\": ${result.core.samplesMillis},")
                appendLine("        \"inputBytes\": ${result.core.inputBytes},")
                appendLine("        \"meanArticleMicros\": ${result.core.meanArticleMicros},")
                appendLine("        \"p50ArticleMicros\": ${result.core.p50ArticleMicros},")
                appendLine("        \"p95ArticleMicros\": ${result.core.p95ArticleMicros},")
                appendLine("        \"maxArticleMicros\": ${result.core.maxArticleMicros},")
                appendLine("        \"slowestFixture\": \"${result.core.slowestFixture}\",")
                appendLine("        \"throughputBytesPerSecond\": ${result.core.throughputBytesPerSecond}")
                appendLine("      }")
                appendLine("    }$comma")
            }
            appendLine("  ]")
            appendLine("}")
        }
        val outputDirectory = reportDirectory.get().asFile.apply { mkdirs() }
        val timestamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
            .withZone(ZoneOffset.UTC)
            .format(createdAt)
        outputDirectory.resolve("run-$timestamp.json").writeText(json)
        outputDirectory.resolve("latest.json").writeText(json)
        println("Regression benchmark report: ${outputDirectory.resolve("latest.json")}")
    }
}

data class BenchmarkResult(
    val platform: String,
    val full: BenchmarkMetrics,
    val core: BenchmarkMetrics,
)

data class BenchmarkMetrics(
    val cohort: String,
    val target: String,
    val fixtures: Int,
    val medianMillis: Long,
    val samplesMillis: List<Long>,
    val inputBytes: Long,
    val meanArticleMicros: Long,
    val p50ArticleMicros: Long,
    val p95ArticleMicros: Long,
    val maxArticleMicros: Long,
    val slowestFixture: String,
    val throughputBytesPerSecond: Long,
    val platform: String,
)
