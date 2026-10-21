plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

import org.gradle.api.GradleException
import java.util.Properties

// One version for the whole tree. `version.properties` is the only place a
// release number is written; versionCode is derived from it so the two can never
// disagree (an APK whose versionCode did not increase is never offered as an
// update, which is the classic Android release bug).
val appVersion = Properties().apply {
    rootProject.file("version.properties").inputStream().use { load(it) }
}.getProperty("version").orEmpty().ifBlank { "1.0.0" }

/** `major * 10000 + minor * 100 + patch`: 1.0.1 -> 10001. Strictly increasing. */
fun deriveVersionCode(version: String): Int {
    val parts = version.trim().removePrefix("v").split('.')
    val major = parts.getOrNull(0)?.toIntOrNull() ?: 0
    val minor = parts.getOrNull(1)?.toIntOrNull() ?: 0
    val patch = parts.getOrNull(2)?.toIntOrNull() ?: 0
    require(minor < 100 && patch < 100) {
        "Version components must stay below 100 so versionCode stays unambiguous: $version"
    }
    return major * 10000 + minor * 100 + patch
}

// A side-loaded app still has to be signed to install. The key is not in the
// repo: CI restores it from a repository secret, and a local release build
// needs an untracked keystore.properties. See RELEASING.md.
val keystoreProperties = Properties()
val keystoreFile = rootProject.file("keystore.properties")
if (keystoreFile.exists()) {
    keystoreFile.inputStream().use { stream -> keystoreProperties.load(stream) }
}

val storeFilePath = keystoreProperties.getProperty("storeFile")
val hasSigningMaterial = !keystoreProperties.isEmpty() &&
    storeFilePath != null &&
    rootProject.file(storeFilePath).exists()

// An unsigned release APK builds fine and installs on nobody's phone, so the
// build stops here instead. Debug and test tasks are unaffected: they do not
// need the key.
val requestsRelease = gradle.startParameter.taskNames.any { task ->
    task.contains("release", ignoreCase = true) ||
        task.endsWith(":assemble", ignoreCase = true) ||
        task.endsWith(":build", ignoreCase = true) ||
        task.equals("assemble", ignoreCase = true) ||
        task.equals("build", ignoreCase = true)
}
if (requestsRelease && !hasSigningMaterial) {
    throw GradleException(
        "The release build needs keystore.properties and the keystore it points at. " +
            "CI restores both from repository secrets; a local release build needs a " +
            "copy of them. See RELEASING.md."
    )
}

android {
    namespace = "dev.opencode.mobile"
    compileSdk = 36

    signingConfigs {
        create("release") {
            val path = storeFilePath
            if (path != null && hasSigningMaterial) {
                storeFile = rootProject.file(path)
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    defaultConfig {
        applicationId = "dev.opencode.mobile"
        minSdk = 26
        targetSdk = 36
        // Derived, never hand-written: see version.properties and RELEASING.md.
        versionCode = deriveVersionCode(appVersion)
        versionName = appVersion
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = signingConfigs.getByName("release")
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
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.datastore.preferences)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.okhttp)
    implementation(libs.okhttp.sse)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.markdown)

    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.mlkit.barcode)

    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)

    testImplementation(libs.junit)
}
