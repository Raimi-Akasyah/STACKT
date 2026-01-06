pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
    plugins {
        id("com.android.application") version "8.9.1" apply false
        id("com.android.library") version "8.3.0" apply false
        id("org.jetbrains.kotlin.android") version "1.9.0" apply false
        id("com.google.gms.google-services") version "4.4.2" apply false
        id("org.jetbrains.kotlin.kapt") version "1.9.0" apply false
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { setUrl("https://jitpack.io") }
    }
}

rootProject.name = "STACKT"
include(":app")
