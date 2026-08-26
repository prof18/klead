import dev.detekt.gradle.Detekt
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.NativeBuildType

plugins {
    kotlin("multiplatform") version "2.3.21"
    id("com.android.kotlin.multiplatform.library") version "9.3.2"
    id("com.vanniktech.maven.publish") version "0.37.0"
    id("dev.detekt") version "2.0.0-alpha.5"
    id("klead.benchmarking") apply false
    id("klead.verification") apply false
}

val commonTestResources = layout.projectDirectory.dir("src/commonTest/resources")

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

apply(plugin = "klead.benchmarking")
apply(plugin = "klead.verification")
