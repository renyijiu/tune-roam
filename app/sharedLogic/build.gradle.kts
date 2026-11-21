import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kmp.library)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.sqldelight)
}

/** AppVersion.NAME for AppIdentity, from `tuneroam.versionName` in gradle.properties. */
val generateAppVersion by tasks.registering {
    val versionName = providers.gradleProperty("tuneroam.versionName")
    val outputDir = layout.buildDirectory.dir("generated/appVersion/kotlin")
    inputs.property("versionName", versionName)
    outputs.dir(outputDir)
    doLast {
        val file = outputDir.get().file("app/tuneroam/radio/core/AppVersion.kt").asFile
        file.parentFile.mkdirs()
        file.writeText(
            """
            |package app.tuneroam.radio.core
            |
            |// Generated from tuneroam.versionName in gradle.properties. Do not edit.
            |internal object AppVersion {
            |    const val NAME = "${versionName.get()}"
            |}
            |""".trimMargin(),
        )
    }
}

kotlin {
    android {
        namespace = "app.tuneroam.radio.shared"
        compileSdk = 37
        minSdk = 26
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
        withHostTest {}
    }

    // No iOS app yet. These targets keep commonMain honest: CI compiles them and runs
    // the common tests on a simulator, so a JVM-only call fails the build.
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain {
            kotlin.srcDir(generateAppVersion)
        }
        commonMain.dependencies {
            implementation(libs.coroutines.core)
            implementation(libs.serialization.json)
            implementation(libs.ktor.client.core)
            implementation(libs.sqldelight.runtime)
            implementation(libs.sqldelight.coroutines)
            implementation(libs.koin.core)
            implementation(libs.lifecycle.viewmodel)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.coroutines.test)
            implementation(libs.ktor.client.mock)
        }
        androidMain.dependencies {
            implementation(libs.ktor.client.okhttp)
            implementation(libs.sqldelight.android)
            implementation(libs.koin.android)
            implementation(libs.coroutines.android)
        }
        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
            implementation(libs.sqldelight.native)
        }
        getByName("androidHostTest").dependencies {
            implementation(libs.sqldelight.jvm)
        }
    }
}

sqldelight {
    databases {
        create("TuneRoamDatabase") {
            packageName.set("app.tuneroam.radio.data.local")
        }
    }
}
