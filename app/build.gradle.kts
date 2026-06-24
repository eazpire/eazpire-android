import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.gms.google-services")
}

val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties()
if (keystorePropertiesFile.exists()) {
    keystorePropertiesFile.inputStream().use { keystoreProperties.load(it) }
}

android {
    namespace = "com.eazpire.creator"
    compileSdk = 36
    // Build in temp – vermeidet OneDrive-Sperren im Projektordner (nur lokal, nicht in CI)
    if (System.getenv("CI") != "true") {
        buildDir = file("${System.getProperty("java.io.tmpdir")}/eazpire-android-build/${project.name}")
    }

    defaultConfig {
        applicationId = "com.eazpire.creator"
        minSdk = 26
        targetSdk = 35
        // Increment for every Play upload (must be > previous release).
        val appVersionCode = (System.getenv("VERSION_CODE") ?: "3").toIntOrNull() ?: 3
        versionCode = appVersionCode
        // Play Console: same versionName for every build is confusing — include versionCode (or set VERSION_NAME in CI).
        versionName = System.getenv("VERSION_NAME") ?: "1.0.3 ($appVersionCode)"
    }

    signingConfigs {
        if (keystorePropertiesFile.exists()) {
            create("release") {
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile")!!)
                storePassword = keystoreProperties.getProperty("storePassword")
            }
        }
    }
    buildTypes {
        debug {
            buildConfigField("boolean", "EAZ_PERF_TRACE", "true")
        }
        release {
            isMinifyEnabled = false
            buildConfigField("boolean", "EAZ_PERF_TRACE", "false")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (keystorePropertiesFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
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
    // NDK r28+ aligns ELF segments for 16 KB page-size devices (Play Console requirement).
    ndkVersion = "28.0.12433566"
    packaging {
        resources {
            pickFirsts += setOf(
                "environments/neutral/neutral_ibl.ktx",
                "environments/neutral/neutral_skybox.ktx",
                "environments/neutral/sh.txt",
                "materials/image_texture.filamat",
                "materials/opaque_colored.filamat",
                "materials/opaque_textured.filamat",
                "materials/transparent_colored.filamat",
                "materials/transparent_textured.filamat",
                "materials/video_texture.filamat",
                "materials/video_texture_chroma_key.filamat",
                "materials/view_renderable.filamat",
                "materials/view_texture_lit.filamat",
                "materials/view_texture_unlit.filamat",
            )
        }
    }
}

dependencies {
    // Must match or exceed arsceneview's Compose needs; exclude its transitive BOM below
    // so compile + runtime resolve the same material3 (avoids rememberModalBottomSheetState NoSuchMethodError).
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.tracing:tracing-ktx:1.2.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.6.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.6.2")
    implementation("androidx.lifecycle:lifecycle-process:2.6.2")
    implementation("androidx.activity:activity-compose:1.8.1")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.animation:animation")

    // Networking
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // Auth: EncryptedSharedPreferences
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // DataStore for locale overrides
    implementation("androidx.datastore:datastore-preferences:1.0.0")

    // Coil for images (logo from URL, flags)
    implementation("io.coil-kt:coil:2.5.0")
    implementation("io.coil-kt:coil-compose:2.5.0")

    // Jsoup for HTML parsing (policy content extraction)
    implementation("org.jsoup:jsoup:1.17.2")

    // Push & background work
    implementation(platform("com.google.firebase:firebase-bom:33.5.1"))
    implementation("com.google.firebase:firebase-messaging-ktx")
    implementation("androidx.work:work-runtime-ktx:2.9.1")
    implementation("androidx.activity:activity-ktx:1.8.2")

    // OAuth: Customer Account PKCE via Chrome Custom Tabs (AuthScreen)
    implementation("androidx.browser:browser:1.8.0")

    // Play Store: prompt for update when a newer version is available (Play-installed builds only)
    implementation("com.google.android.play:app-update:2.1.0")
    implementation("com.google.android.play:app-update-ktx:2.1.0")

    // Wear OS: sync JWT to companion watch app
    implementation("com.google.android.gms:play-services-wearable:18.2.0")

    // Wear QR pairing scanner (CameraX 1.4.2+ ships 16 KB-aligned libimage_processing_util_jni.so)
    val cameraxVersion = "1.4.2"
    implementation("androidx.camera:camera-camera2:$cameraxVersion")
    implementation("androidx.camera:camera-lifecycle:$cameraxVersion")
    implementation("androidx.camera:camera-view:$cameraxVersion")
    implementation("com.google.mlkit:barcode-scanning:17.3.0")

    // Poster AR (SceneView + ARCore — same stack as wear-android)
    val sceneViewVersion = "3.6.2"
    implementation("io.github.sceneview:arsceneview:$sceneViewVersion") {
        exclude(group = "androidx.compose", module = "compose-bom")
    }
    implementation("com.google.ar:core:1.46.0")

    // Creator theme background video (remote MP4, object-fit cover via RESIZE_MODE_ZOOM)
    val media3Version = "1.5.1"
    implementation("androidx.media3:media3-exoplayer:$media3Version")
    implementation("androidx.media3:media3-ui:$media3Version")

    testImplementation("junit:junit:4.13.2")
    /** Same JSON stack as JVM unit tests ([ShopSidebarMenuParser] uses JSONObject) */
    testImplementation("org.json:json:20240303")
}

// Play Console rejects debug-signed bundles; require a real upload keystore for bundleRelease.
tasks.register("checkReleaseSigning") {
    group = "verification"
    doLast {
        val f = rootProject.file("keystore.properties")
        require(f.exists()) {
            """
            Play requires a signed release bundle. Create android/keystore.properties (see keystore.properties.example).
            Generate a keystore, e.g.:
              cd android
              keytool -genkey -v -keystore upload-keystore.jks -keyalg RSA -keysize 2048 -validity 10000 -alias upload
            """.trimIndent()
        }
        val p = Properties()
        f.inputStream().use { p.load(it) }
        val store = rootProject.file(p.getProperty("storeFile")!!)
        require(store.isFile) { "Keystore file not found: ${store.absolutePath}" }
    }
}
afterEvaluate {
    tasks.named("bundleRelease").configure {
        dependsOn(tasks.named("checkReleaseSigning"))
    }
}
