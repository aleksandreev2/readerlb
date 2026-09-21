plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.gms.google-services")
}

val releaseKeystorePath =
    System.getenv("READERLB_KEYSTORE_PATH")
val debugKeystorePath =
    System.getenv("READERLB_DEBUG_KEYSTORE_PATH")
        ?: (
            (System.getenv("HOME")
                ?: System.getProperty("user.home")) +
                "/.android/debug.keystore"
            )

android {
    namespace = "com.readerlb.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.readerlb.app"
        minSdk = 29
        targetSdk = 35
        versionCode = 14
        versionName = "0.9.5"
        testInstrumentationRunner =
            "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    signingConfigs {
        getByName("debug") {
            storeFile = file(debugKeystorePath)
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }

        if (!releaseKeystorePath.isNullOrBlank()) {
            create("release") {
                storeFile = file(releaseKeystorePath)
                storePassword =
                    System.getenv("READERLB_KEYSTORE_PASSWORD")
                keyAlias =
                    System.getenv("READERLB_KEY_ALIAS")
                keyPassword =
                    System.getenv("READERLB_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfigs.findByName("release")
                ?.let { signingConfig = it }
            proguardFiles(
                getDefaultProguardFile(
                    "proguard-android-optimize.txt"
                ),
                "proguard-rules.pro"
            )
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.activity:activity-compose:1.10.0")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation(
        "androidx.compose.ui:ui-test-manifest"
    )

    androidTestImplementation(
        platform(
            "androidx.compose:compose-bom:2024.12.01"
        )
    )
    androidTestImplementation(
        "androidx.compose.ui:ui-test-junit4"
    )
    androidTestImplementation(
        "androidx.test.ext:junit:1.2.1"
    )
    androidTestImplementation(
        "androidx.test:runner:1.6.2"
    )

    implementation("androidx.core:core-ktx:1.15.0")
    implementation(platform("com.google.firebase:firebase-bom:34.19.0"))
    implementation("com.google.firebase:firebase-messaging")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.documentfile:documentfile:1.0.1")
    implementation("org.jsoup:jsoup:1.18.3")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}
