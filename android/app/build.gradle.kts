plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.tracolivre.automator"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.tracolivre.automator"
        minSdk = 26
        targetSdk = 35
        versionCode = 27
        versionName = "1.26"
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildTypes {
        getByName("release") {
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    androidResources {
        noCompress += "tflite"
    }
}

tasks.register("prepareReleaseApk", org.gradle.api.tasks.Copy::class) {
    dependsOn("assembleRelease")
    from(layout.buildDirectory.file("outputs/apk/release/app-release.apk"))
    into(layout.buildDirectory.dir("outputs/weelife"))
    rename { "WeeLife Line Art.apk" }
}

dependencies {
    implementation("androidx.activity:activity-ktx:1.10.1")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("com.google.mediapipe:tasks-vision:0.10.35")
}
