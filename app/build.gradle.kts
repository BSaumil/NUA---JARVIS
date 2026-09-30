import java.io.FileInputStream
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.dagger.hilt.android")
    id("com.google.devtools.ksp")
}

// Release signing -- loaded from keystore.properties (local, gitignored; see
// keystore.properties.example) if present, else from CI-injected Gradle properties
// (RELEASE_STORE_FILE/RELEASE_STORE_PASSWORD/RELEASE_KEY_ALIAS/RELEASE_KEY_PASSWORD, set
// by .github/workflows/android-build.yml from repository secrets), else absent entirely.
// A release build with none of these still succeeds unsigned, exactly as before this
// signing setup existed -- CI's own "verifies R8/minification" release step never
// required signing and must keep not requiring it.
val keystorePropsFile = rootProject.file("keystore.properties")
val ciPropertyNames = mapOf(
    "storeFile" to "RELEASE_STORE_FILE",
    "storePassword" to "RELEASE_STORE_PASSWORD",
    "keyAlias" to "RELEASE_KEY_ALIAS",
    "keyPassword" to "RELEASE_KEY_PASSWORD",
)
val signingProps = Properties().apply {
    if (keystorePropsFile.exists()) {
        load(FileInputStream(keystorePropsFile))
    } else {
        ciPropertyNames.forEach { (key, ciProp) ->
            (project.findProperty(ciProp) as String?)?.let { setProperty(key, it) }
        }
    }
}
val hasReleaseSigning = listOf("storeFile", "storePassword", "keyAlias", "keyPassword")
    .all { !signingProps.getProperty(it).isNullOrBlank() }

android {
    namespace = "com.nua.assistant"
    compileSdk = 35

    if (hasReleaseSigning) {
        signingConfigs {
            create("release") {
                storeFile = file(signingProps.getProperty("storeFile"))
                storePassword = signingProps.getProperty("storePassword")
                keyAlias = signingProps.getProperty("keyAlias")
                keyPassword = signingProps.getProperty("keyPassword")
            }
        }
    }

    defaultConfig {
        applicationId = "com.nua.assistant"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.3.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Picovoice console access key for wake-word detection (services/NuaForegroundService.kt).
        // Not a per-user secret like the Claude key — set via -PPICOVOICE_ACCESS_KEY=... or
        // gradle.properties. Empty by default; the service no-ops without one.
        val picovoiceAccessKey = (project.findProperty("PICOVOICE_ACCESS_KEY") as String?) ?: ""
        buildConfigField("String", "PICOVOICE_ACCESS_KEY", "\"$picovoiceAccessKey\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        debug {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    // Core / Compose
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // Hilt
    implementation("com.google.dagger:hilt-android:2.52")
    ksp("com.google.dagger:hilt-android-compiler:2.52")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")

    // Room
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // Networking (Claude API, Open-Meteo)
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:okhttp-sse:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.jakewharton.retrofit:retrofit2-kotlinx-serialization-converter:1.0.0")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    // Secure storage for the Claude API key
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // Biometric step-up authentication for the highest-risk actions (security/BiometricGate.kt)
    implementation("androidx.biometric:biometric:1.1.0")
    implementation("androidx.fragment:fragment-ktx:1.8.5")

    // Wake-word detection (services/WakePhrase.kt catalog)
    implementation("ai.picovoice:porcupine-android:3.0.3")

    // Voice owner verification (voice/OwnerVerifier.kt) — separate Picovoice product/entitlement from Porcupine
    implementation("ai.picovoice:eagle-android:3.0.2")

    // Proactive scheduled briefings (briefing/MorningBriefingWorker.kt)
    implementation("androidx.work:work-runtime-ktx:2.10.0")
    implementation("androidx.hilt:hilt-work:1.2.0")
    ksp("androidx.hilt:hilt-compiler:1.2.0")

    // Home-screen widget (widget/NuaWidget.kt)
    implementation("androidx.glance:glance-appwidget:1.1.1")
    implementation("androidx.glance:glance-material3:1.1.1")

    // Geofenced proactive suggestions (geofencing/GeofenceManager.kt)
    implementation("com.google.android.gms:play-services-location:21.3.0")

    // Android Auto entry point (car/NuaCarAppService.kt)
    implementation("androidx.car.app:app:1.4.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    // Verifies executeCancellably (ai/CancellableHttpCall.kt) actually cancels an
    // in-flight request against a real local socket — same publisher/version as the
    // production okhttp dependency, no new transitive risk.
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
}
