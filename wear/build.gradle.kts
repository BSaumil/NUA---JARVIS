plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.nua.assistant.wear"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.nua.assistant.wear"
        // Wear OS 3+ devices run API 30+; older Wear OS 2 hardware (API 25-28) can't
        // run this module, which is fine — it's a companion tile, not the main app.
        minSdk = 30
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        release {
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
}

dependencies {
    // Tile status surface (NuaTileService.kt) — the classic (pre-Material3) Tiles API,
    // stable and unchanged since 2021.
    implementation("androidx.wear.tiles:tiles:1.2.0")
    implementation("androidx.core:core-ktx:1.13.1")
    // ListenableFuture + Futures.immediateFuture, used by the async TileService callbacks.
    implementation("com.google.guava:guava:33.0.0-android")

    // Presence Mesh's real transport, watch side -- reads the phone's published
    // presence DataItem (see docs/PRESENCE_MESH_RFC.md and app/build.gradle.kts's own
    // copy of this dependency).
    implementation("com.google.android.gms:play-services-wearable:19.0.0")
}
