plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.jeremy.test"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.jeremy.test"
        minSdk = 34
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
}

dependencies {

    // Core AndroidX
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")

    // Leanback for TV UI (optional but recommended for TV launcher icon)
    implementation("androidx.leanback:leanback:1.0.0")

    // NanoHTTPD - lightweight embedded web server (still actively used in 2026)
    implementation("org.nanohttpd:nanohttpd:2.3.1")

    // Gson for easy JSON parsing
    implementation("com.google.code.gson:gson:2.10.1")

    // Optional: If you want better logging
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")

    // Test dependencies (keep for basic unit tests)
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
}