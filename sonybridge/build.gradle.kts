plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.mdomokos.lilaccamera.sonybridge"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.mdomokos.lilaccamera.sonybridge"
        minSdk = 30
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    signingConfigs {
        // Same key handling as :app. The bridge only serves callers signed with its own key,
        // so it must be signed with the key of the Lilac build that will use it.
        getByName("debug") {
            storeFile = rootProject.file("app/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
        create("release") {
            val storeFileProp = project.findProperty("RELEASE_STORE_FILE") as? String
            if (storeFileProp != null) {
                storeFile = rootProject.file(storeFileProp)
                storePassword = project.findProperty("RELEASE_STORE_PASSWORD") as? String
                keyAlias = project.findProperty("RELEASE_KEY_ALIAS") as? String
                keyPassword = project.findProperty("RELEASE_KEY_PASSWORD") as? String
            }
        }
    }

    buildTypes {
        debug { signingConfig = signingConfigs.getByName("debug") }
        release {
            // The Sony-named classes are bound by name from the ROM's native library: no shrinking or renaming.
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }
    buildFeatures { aidl = true }
}
