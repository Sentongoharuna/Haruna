plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.sentongoharuna.pulse"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.sentongoharuna.pulse"
        minSdk = 23
        targetSdk = 36
        versionCode = 28500
        versionName = "285.00-fivemods12.1-field-reliability-assembly"
        testInstrumentationRunner = "com.sentongoharuna.pulse.DevelopUgandaFiveModeInstrumentation"
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
    val camerax = "1.6.1"
    implementation("androidx.appcompat:appcompat:1.8.0")
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.activity:activity-ktx:1.11.0")
    implementation("androidx.media3:media3-common:1.11.0")
    implementation("androidx.media3:media3-exoplayer:1.11.0")
    implementation("androidx.media3:media3-ui:1.11.0")
    implementation("androidx.media3:media3-transformer:1.11.0")
    implementation("androidx.media3:media3-effect:1.11.0")
    implementation("androidx.camera:camera-core:$camerax")
    implementation("androidx.camera:camera-camera2:$camerax")
    implementation("androidx.camera:camera-lifecycle:$camerax")
    implementation("androidx.camera:camera-video:$camerax")
    implementation("androidx.camera:camera-view:$camerax")
    implementation("androidx.camera:camera-effects:$camerax")
    implementation("com.google.android.gms:play-services-location:21.4.0")
    implementation("com.google.mlkit:image-labeling:17.0.9")
    implementation("com.google.mlkit:face-detection:16.1.7")
    // RootEncoder owns the LIVE-only RTMPS encoder and muxer. CameraX remains
    // untouched for the other four modes and for their guarded capture path.
    implementation(files("libs/rootencoder-library-2.8.1.aar"))
    implementation(files("libs/rootencoder-encoder-2.8.1.aar"))
    implementation(files("libs/rootencoder-rtmp-2.8.1.aar"))
    implementation(files("libs/rootencoder-common-2.8.1.aar"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    // Keep the instrumented-test graph on modern AndroidX artifacts instead
    // of the obsolete transitive 1.0/1.1 variants requested by old metadata.
    androidTestImplementation("androidx.collection:collection:1.4.2")
    androidTestImplementation("androidx.exifinterface:exifinterface:1.4.2")
    androidTestImplementation("androidx.arch.core:core-runtime:2.2.0")
}
