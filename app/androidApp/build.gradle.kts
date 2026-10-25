import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

/**
 * Release signing comes from `keystore.properties` at the repository root (git-ignored)
 * or, in CI, from environment variables. Without either, `assembleRelease` still builds
 * and shrinks, but leaves the APK unsigned.
 */
val releaseSigning: Map<String, String>? = run {
    val file = rootProject.file("keystore.properties")
    val values = if (file.exists()) {
        Properties().apply { file.inputStream().use(::load) }
            .let { props -> props.stringPropertyNames().associateWith { props.getProperty(it) } }
    } else {
        mapOf(
            "storeFile" to System.getenv("TUNEROAM_KEYSTORE"),
            "storePassword" to System.getenv("TUNEROAM_KEYSTORE_PASSWORD"),
            "keyAlias" to System.getenv("TUNEROAM_KEY_ALIAS"),
            "keyPassword" to System.getenv("TUNEROAM_KEY_PASSWORD"),
        ).filterValues { !it.isNullOrBlank() }.mapValues { it.value!! }
    }
    values.takeIf { it.keys.containsAll(listOf("storeFile", "storePassword", "keyAlias", "keyPassword")) }
}

android {
    namespace = "app.tuneroam.radio"
    compileSdk = 37

    defaultConfig {
        applicationId = "app.tuneroam.radio"
        minSdk = 26
        targetSdk = 35
        versionCode = 4
        versionName = "0.4.0"
    }

    signingConfigs {
        releaseSigning?.let { values ->
            create("release") {
                storeFile = rootProject.file(values.getValue("storeFile"))
                storePassword = values.getValue("storePassword")
                keyAlias = values.getValue("keyAlias")
                keyPassword = values.getValue("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.findByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "META-INF/INDEX.LIST"
            excludes += "META-INF/DEPENDENCIES"
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":app:sharedLogic"))

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.icons)
    implementation(libs.activity.compose)
    implementation(libs.navigation.compose)
    implementation(libs.lifecycle.runtime)
    implementation(libs.lifecycle.viewmodel)
    implementation(libs.core.ktx)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.koin.android)
    implementation(libs.koin.compose)
    implementation(libs.coil)
    implementation(libs.coil.svg)
    implementation(libs.work.runtime)
    implementation(libs.coroutines.android)
    implementation(libs.serialization.json)

    implementation(libs.media3.exoplayer)
    implementation(libs.media3.exoplayer.hls)
    implementation(libs.media3.session)

    testImplementation(kotlin("test-junit"))
    testImplementation(libs.coroutines.test)
}
