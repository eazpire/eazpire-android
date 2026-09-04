// Google mirror before mavenCentral(): Maven Central often returns 403 for GitHub Actions IPs.
pluginManagement {
    repositories {
        google()
        gradlePluginPortal()
        maven {
            name = "MavenCentralGoogleMirror"
            url = uri("https://maven-central.storage-download.googleapis.com/maven2/")
        }
        mavenCentral()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.10.0"
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        maven {
            name = "MavenCentralGoogleMirror"
            url = uri("https://maven-central.storage-download.googleapis.com/maven2/")
        }
        mavenCentral()
    }
}

rootProject.name = "eazpire"
include(":app")
include(":android-shared")
// Monorepo: ../android-shared. Mirror sync: ./android-shared (same root as :app).
project(":android-shared").projectDir =
    listOf(file("android-shared"), file("../android-shared")).first { it.isDirectory }
// Macrobenchmark is local-only; CI builds :app only (see build.yml).
if (System.getenv("CI") != "true") {
    include(":benchmark")
}
