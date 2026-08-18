import java.util.Properties

/**
 * Local-only secrets: routing API keys. Never committed — see `local.properties.example`
 * and `docs/upgrade/deps-map.md`. Absent keys are a fully supported state: RouteRepository
 * falls through its provider chain to a straight-line route with no key at all.
 */
val localProperties = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) load(f.inputStream())
}

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
}

/**
 * Firebase is wired up only when its config file is present.
 *
 * `google-services.json` carries the project's identifiers, so it cannot be committed
 * for someone else's Firebase project to use. Applying the plugin unconditionally would
 * therefore break `git clone && ./gradlew build` for every contributor who has not yet
 * created their own project — the plugin fails the build outright when the file is
 * missing. Guarding it keeps the repo buildable, and [FirebaseAvailability] reads the
 * same flag at runtime so the app falls back to the local Room store instead of
 * crashing on a missing FirebaseApp.
 */
val googleServicesFile = file("google-services.json")
val hasFirebaseConfig = googleServicesFile.exists()
if (hasFirebaseConfig) {
    apply(plugin = "com.google.gms.google-services")
}

/**
 * Release signing is read from keystore.properties, which is git-ignored. Without
 * that file the project still builds and runs — debug builds and `assembleRelease`
 * fall back to unsigned output — so a fresh clone works immediately, and no keystore
 * password ever lands in version control. See docs/RELEASE.md.
 */
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        load(keystorePropertiesFile.inputStream())
    }
}
val hasReleaseKeystore = keystorePropertiesFile.exists()

android {
    namespace = "com.potheride.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.potheride.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"
        // Surfaced to Kotlin so the app can tell at runtime whether a Firebase backend
        // was compiled in, without probing for classes that may not be on the classpath.
        buildConfigField("boolean", "HAS_FIREBASE", hasFirebaseConfig.toString())
        // Read reflectively by MapApiKeys rather than referenced directly, so the map
        // package compiles independently of this file — see docs/upgrade/deps-map.md.
        buildConfigField(
            "String", "ORS_API_KEY",
            "\"${localProperties.getProperty("ors.apiKey", "")}\""
        )
        buildConfigField(
            "String", "GRAPHHOPPER_API_KEY",
            "\"${localProperties.getProperty("graphhopper.apiKey", "")}\""
        )
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        resourceConfigurations += listOf("en", "bn")
    }

    signingConfigs {
        if (hasReleaseKeystore) {
            create("release") {
                storeFile = file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            // Debug builds report `com.potheride.app.debug`, which is registered as its
            // own Android app in the Firebase project. Both package names are present in
            // google-services.json — Firebase rejects requests from any package it does
            // not know, so removing that second registration breaks every debug build's
            // auth calls.
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            // R8 in full mode: required to keep the Play Store download under control
            // and to strip the unused half of the icon pack.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (hasReleaseKeystore) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    testOptions {
        unitTests.isReturnDefaultValues = true
        // Robolectric needs the merged resources and manifest to inflate a real
        // Android context for the Compose test host.
        unitTests.isIncludeAndroidResources = true
    }
}

// Room writes its schema JSON here. Committing these files is what makes it possible
// to write and test real migrations later instead of wiping user data on every
// schema change.
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.incremental", "true")
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.4")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.4")
    implementation("androidx.activity:activity-compose:1.9.1")

    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:2.7.7")

    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // Fused location for live driver tracking. The Maps SDK is deliberately absent:
    // the app ships a coordinate-accurate custom route view so it runs with no API
    // key and no billing account. See docs/MAPS.md to switch to Google Maps.
    implementation("com.google.android.gms:play-services-location:21.3.0")

    // Free, tile-based map — no billing account, no API key. See docs/MAPS.md and
    // docs/upgrade/deps-map.md for why Google Maps is deliberately absent.
    implementation("org.osmdroid:osmdroid-android:6.1.18")

    // Driver face verification (Level 7): on-device detection + liveness only, never a
    // paid recognition service — see core/verification/FaceVerification.kt for why the
    // confidence figure the wireframe shows is deliberately not produced by this build.
    val cameraxVersion = "1.3.4"
    implementation("androidx.camera:camera-core:$cameraxVersion")
    implementation("androidx.camera:camera-camera2:$cameraxVersion")
    implementation("androidx.camera:camera-lifecycle:$cameraxVersion")
    implementation("androidx.camera:camera-view:$cameraxVersion")
    implementation("com.google.mlkit:face-detection:16.1.7")

    // Firebase. The BOM pins one consistent set of versions across every Firebase
    // artifact; declaring versions individually is how the SDKs end up mismatched.
    // These are always on the compile classpath so the code builds without the config
    // file — it is the *plugin* above, and FirebaseAvailability at runtime, that decide
    // whether Firebase is actually used.
    implementation(platform("com.google.firebase:firebase-bom:33.1.2"))
    implementation("com.google.firebase:firebase-auth-ktx")
    implementation("com.google.firebase:firebase-firestore-ktx")
    implementation("com.google.firebase:firebase-storage-ktx")
    implementation("com.google.firebase:firebase-messaging-ktx")
    // Bridges Play Services' Task<T> to `suspend`. Without it every Firestore call
    // needs a hand-rolled suspendCancellableCoroutine wrapper.
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.8.1")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")

    // Robolectric runs the Compose tests on the JVM. Without it every UI assertion
    // needs a booted emulator, which in practice means the UI is never covered at all.
    testImplementation("org.robolectric:robolectric:4.12.2")
    testImplementation("androidx.test.ext:junit:1.2.1")
    testImplementation(platform("androidx.compose:compose-bom:2024.06.00"))
    testImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.room:room-testing:2.6.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.06.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
