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
    compileSdk = 35
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
        release {
            isMinifyEnabled = false
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
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2023.10.01"))
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.6.2")
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

    // OAuth: Customer Account PKCE runs in an in-app WebView (see AuthScreen). androidx.browser kept if needed elsewhere.
    implementation("androidx.browser:browser:1.8.0")

    // Play Store: prompt for update when a newer version is available (Play-installed builds only)
    implementation("com.google.android.play:app-update:2.1.0")
    implementation("com.google.android.play:app-update-ktx:2.1.0")
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
