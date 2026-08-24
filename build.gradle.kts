import dev.detekt.gradle.Detekt
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.NativeBuildType
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.gradle.targets.native.tasks.KotlinNativeHostTest
import org.jetbrains.kotlin.gradle.targets.native.tasks.KotlinNativeSimulatorTest
import org.jetbrains.kotlin.gradle.targets.native.tasks.KotlinNativeTest
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Properties

plugins {
    kotlin("multiplatform") version "2.3.21"
    id("com.android.kotlin.multiplatform.library") version "9.2.0"
    id("dev.detekt") version "2.0.0-alpha.5"
    `maven-publish`
}

group = "com.prof18"
version = "0.1.0-SNAPSHOT"

val commonTestResources = layout.projectDirectory.dir("src/commonTest/resources")
val regressionBenchmarkBaselines = Properties().apply {
    file("benchmarks/regression-corpus-baselines.properties").inputStream().use(::load)
}
val regressionBenchmarkSamples = providers.gradleProperty("benchmarkSamples")
    .orElse(providers.environmentVariable("KLEAD_BENCHMARK_SAMPLES"))
    .orElse("3")
    .get()

fun regressionBenchmarkBudget(platform: String): String =
    requireNotNull(regressionBenchmarkBaselines.getProperty("$platform.maxMedianMillis"))

kotlin {
    jvmToolchain(21)
    jvm()
    android {
        namespace = "com.prof18.klead"
        compileSdk = 36
        minSdk = 21
        withDeviceTest {
            instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
            execution = "HOST"
        }
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }
    iosArm64 {
        binaries {
            test("benchmark", listOf(NativeBuildType.RELEASE))
        }
    }
    iosSimulatorArm64 {
        binaries {
            test("benchmark", listOf(NativeBuildType.RELEASE))
        }
    }
    iosX64()
    macosArm64 {
        binaries {
            test("benchmark", listOf(NativeBuildType.RELEASE))
        }
    }
    macosX64()

    sourceSets {
        commonMain.dependencies {
            implementation("com.fleeksoft.ksoup:ksoup:0.2.6")
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
            implementation("org.jetbrains.kotlinx:atomicfu:0.33.0")
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0")
        }
        getByName("androidDeviceTest") {
            resources.srcDir(commonTestResources)
            dependencies {
                implementation("androidx.test:runner:1.7.0")
                implementation("androidx.test.ext:junit:1.3.0")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0")
            }
        }
    }
}

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
    environment("KLEAD_REGRESSION_CORPUS_MAX_MEDIAN_MS", regressionBenchmarkBudget("ios-simulator"))
    environment(
        "SIMCTL_CHILD_KLEAD_REGRESSION_CORPUS_MAX_MEDIAN_MS",
        regressionBenchmarkBudget("ios-simulator"),
    )
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
    environment("KLEAD_REGRESSION_CORPUS_MAX_MEDIAN_MS", regressionBenchmarkBudget("macos"))
    outputs.upToDateWhen { false }
    mustRunAfter("macosArm64ReleaseBenchmarkTest")
}

dependencies {
    detektPlugins("dev.detekt:detekt-rules-ktlint-wrapper:2.0.0-alpha.5")
}

detekt {
    buildUponDefaultConfig = true
    config.setFrom(files("config/detekt/detekt.yml"))
    basePath.set(projectDir)
}

tasks.withType<Detekt>().configureEach {
    jvmTarget.set("21")
}

tasks.named<Test>("jvmTest") {
    useJUnitPlatform()
    filter.excludeTestsMatching("com.prof18.klead.fixtures.SiteRegressionSnapshotWriterTest")
    filter.excludeTestsMatching("com.prof18.klead.fixtures.RegressionCorpusBenchmarkTest")
    environment("TEST_RESOURCES_ROOT", commonTestResources.asFile.absolutePath)
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
    environment("KLEAD_REGRESSION_CORPUS_MAX_MEDIAN_MS", regressionBenchmarkBudget("jvm"))
    outputs.upToDateWhen { false }
}

tasks.register<Test>("writeSiteRegressionSnapshot") {
    group = "verification"
    description = "Writes current Klead Markdown and HTML snapshots for one captured regression input."
    val jvmTest = tasks.named<Test>("jvmTest")
    testClassesDirs = jvmTest.get().testClassesDirs
    classpath = jvmTest.get().classpath
    useJUnitPlatform()
    filter.includeTestsMatching("com.prof18.klead.fixtures.SiteRegressionSnapshotWriterTest")
    environment("TEST_RESOURCES_ROOT", commonTestResources.asFile.absolutePath)
    outputs.upToDateWhen { false }

    val fixtureName = providers.gradleProperty("siteRegressionName")
    doFirst {
        systemProperty(
            "klead.siteRegressionName",
            fixtureName.orNull ?: error("Pass -PsiteRegressionName=<fixture-name>"),
        )
    }
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
            """TIMING_REGRESSION_CORPUS platform=(\S+) target=(\S+) fixtures=(\d+) """ +
                """median=(\d+)ms samples=\[([^]]+)]""",
        )
        val inputRoots = listOf(
            layout.buildDirectory.dir("test-results/jvmRegressionBenchmark").get().asFile,
            layout.buildDirectory.dir("test-results/iosSimulatorArm64ReleaseRegressionBenchmarkTest").get().asFile,
            layout.buildDirectory.dir("test-results/macosArm64ReleaseRegressionBenchmarkTest").get().asFile,
            layout.buildDirectory.dir("outputs/androidTest-results/connected").get().asFile,
            layout.buildDirectory.dir("test-results/iosArm64PhysicalRegressionBenchmark").get().asFile,
        )
        val results = inputRoots.flatMap { root ->
            if (!root.exists()) emptyList() else root.walkTopDown()
                .filter { it.isFile && (it.extension == "xml" || it.extension == "txt") }
                .flatMap { file -> resultPattern.findAll(file.readText()) }
                .map { match ->
                    val (platform, target, fixtures, median, samples) = match.destructured
                    BenchmarkResult(
                        platform = platform,
                        target = target,
                        fixtures = fixtures.toInt(),
                        medianMillis = median.toLong(),
                        samplesMillis = samples.split(',').map(String::trim).map(String::toLong),
                    )
                }
                .toList()
        }.associateBy(BenchmarkResult::platform)

        val expectedPlatforms = setOf("jvm", "android", "ios-simulator", "ios-device", "macos")
        check(results.keys == expectedPlatforms) {
            "Expected results for $expectedPlatforms but found ${results.keys}"
        }

        val createdAt = Instant.now()
        val json = buildString {
            appendLine("{")
            appendLine("  \"schemaVersion\": 1,")
            appendLine("  \"createdAt\": \"$createdAt\",")
            appendLine("  \"results\": [")
            results.values.sortedBy(BenchmarkResult::platform).forEachIndexed { index, result ->
                val comma = if (index == results.size - 1) "" else ","
                appendLine("    {")
                appendLine("      \"platform\": \"${result.platform}\",")
                appendLine("      \"target\": \"${result.target}\",")
                appendLine("      \"fixtures\": ${result.fixtures},")
                appendLine("      \"medianMillis\": ${result.medianMillis},")
                appendLine("      \"samplesMillis\": ${result.samplesMillis}")
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
    val target: String,
    val fixtures: Int,
    val medianMillis: Long,
    val samplesMillis: List<Long>,
)

tasks.register("docsCheck") {
    group = "verification"
    description = "Checks that human documentation and AI-note indexes exist."
    inputs.files(
        fileTree("docs") { include("*.md") },
        fileTree("ai-notes") { include("**/*.md") },
    )
    doLast {
        val requiredFiles = listOf(
            "docs/benchmarking.md",
            "docs/markdown-policy.md",
            "docs/security-policy.md",
            "ai-notes/README.md",
            "ai-notes/plans/README.md",
            "ai-notes/notes/README.md",
        )
        for (requiredFile in requiredFiles) {
            check(file(requiredFile).isFile) { "$requiredFile is required" }
        }
    }
}

tasks.named("detekt") {
    dependsOn("detektAndroidDeviceTestSourceSet")
    dependsOn("detektAndroidMainSourceSet")
    dependsOn("detektCommonMainSourceSet")
    dependsOn("detektCommonTestSourceSet")
    dependsOn("detektJvmTestSourceSet")
    dependsOn("detektMacosTestSourceSet")
}

tasks.check {
    dependsOn("detekt")
    dependsOn("docsCheck")
}
