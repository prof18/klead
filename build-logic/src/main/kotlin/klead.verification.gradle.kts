val commonTestResources = layout.projectDirectory.dir("src/commonTest/resources")

tasks.named<Test>("jvmTest") {
    useJUnitPlatform()
    filter.excludeTestsMatching("com.prof18.klead.fixtures.SiteRegressionSnapshotWriterTest")
    filter.excludeTestsMatching("com.prof18.klead.fixtures.RegressionCorpusBenchmarkTest")
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

tasks.named("check") {
    dependsOn("detekt")
    dependsOn("docsCheck")
}
