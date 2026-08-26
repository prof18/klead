import dev.detekt.gradle.Detekt
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.NativeBuildType

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.vanniktech.maven.publish)
    alias(libs.plugins.detekt)
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
            implementation(libs.ksoup)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.atomicfu)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
        getByName("androidDeviceTest") {
            resources.srcDir(commonTestResources)
            dependencies {
                implementation(libs.androidx.test.runner)
                implementation(libs.androidx.test.ext.junit)
                implementation(libs.kotlinx.coroutines.test)
            }
        }
    }
}

dependencies {
    detektPlugins(libs.detekt.rules.ktlint.wrapper)
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
