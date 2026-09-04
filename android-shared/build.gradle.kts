plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.eazpire.shared"
    compileSdk = 36
    if (System.getenv("CI") != "true") {
        // Avoid Windows MAX_PATH failures under deep .transforms paths.
        buildDir = file("${System.getProperty("java.io.tmpdir")}/eazpire-android-shared-build")
    }

    defaultConfig {
        minSdk = 26
        // Digests may be empty until Play App Signing certs are registered (see TrustedPackages).
        buildConfigField("boolean", "REQUIRE_CERT_DIGESTS", "false")
    }
    buildFeatures {
        buildConfig = true
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
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}
