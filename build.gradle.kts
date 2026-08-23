import dev.detekt.gradle.Detekt

plugins {
    kotlin("multiplatform") version "2.3.21"
    id("dev.detekt") version "2.0.0-alpha.5"
}

group = "com.prof18"
version = "0.1.0-SNAPSHOT"

kotlin {
    jvmToolchain(21)
    jvm()
    iosArm64()
    iosSimulatorArm64()
    iosX64()

    sourceSets {
        commonMain.dependencies {
            implementation("com.fleeksoft.ksoup:ksoup:0.2.6")
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0")
        }
    }
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
            "docs/upstream-sync.md",
        )
        for (doc in requiredDocs) {
            check(file(doc).isFile) { "$doc is required" }
        }
    }
}

tasks.check {
    dependsOn("detekt")
    dependsOn("detektCommonMainSourceSet")
    dependsOn("detektCommonTestSourceSet")
    dependsOn("detektJvmTestSourceSet")
    dependsOn("docsCheck")
}
