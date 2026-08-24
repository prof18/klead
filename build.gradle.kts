import dev.detekt.gradle.Detekt
import org.jetbrains.kotlin.gradle.plugin.mpp.NativeBuildType
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.gradle.targets.native.tasks.KotlinNativeHostTest
import org.jetbrains.kotlin.gradle.targets.native.tasks.KotlinNativeSimulatorTest
import org.jetbrains.kotlin.gradle.targets.native.tasks.KotlinNativeTest

plugins {
    kotlin("multiplatform") version "2.3.21"
    id("dev.detekt") version "2.0.0-alpha.5"
    `maven-publish`
}

group = "com.prof18"
version = "0.1.0-SNAPSHOT"

val commonTestResources = layout.projectDirectory.dir("src/commonTest/resources")

kotlin {
    jvmToolchain(21)
    jvm()
    iosArm64()
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
    filter.includeTestsMatching("com.prof18.klead.fixtures.MacosRegressionCorpusBenchmarkTest")
    environment("KLEAD_RUN_REGRESSION_BENCHMARK", "true")
    environment("KLEAD_NATIVE_TARGET", "macosArm64Release")
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
    environment("TEST_RESOURCES_ROOT", commonTestResources.asFile.absolutePath)
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
        "macosArm64ReleaseBenchmarkTest",
        "macosArm64ReleaseRegressionBenchmarkTest",
    )
}

tasks.register("docsCheck") {
    group = "verification"
    description = "Checks that migration documentation exists."
    inputs.files(fileTree("docs") { include("*.md") })
    doLast {
        val requiredDocs = listOf(
            "docs/README.md",
            "docs/fixture-coverage.md",
            "docs/known-differences.md",
            "docs/markdown-policy.md",
            "docs/release-scope.md",
            "docs/security-policy.md",
            "docs/site-regression-workflow.md",
            "docs/upstream-sync.md",
        )
        for (doc in requiredDocs) {
            check(file(doc).isFile) { "$doc is required" }
        }
    }
}

tasks.named("detekt") {
    dependsOn("detektCommonMainSourceSet")
    dependsOn("detektCommonTestSourceSet")
    dependsOn("detektJvmTestSourceSet")
    dependsOn("detektMacosTestSourceSet")
}

tasks.check {
    dependsOn("detekt")
    dependsOn("docsCheck")
}
