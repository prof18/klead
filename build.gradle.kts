plugins {
    kotlin("jvm") version "2.3.21"
}

group = "dev.defuddle"
version = "0.1.0-SNAPSHOT"

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation("org.jsoup:jsoup:1.22.2")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")

    testImplementation(kotlin("test"))
}

tasks.test {
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
    dependsOn("docsCheck")
}
