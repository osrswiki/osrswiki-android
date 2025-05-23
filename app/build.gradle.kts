plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.androidx.navigation.safeargs)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.omiyawaki.osrswiki"
    compileSdk = 35 // Keeping your existing compileSdk
    buildToolsVersion = "35.0.1"

    defaultConfig {
        applicationId = "com.omiyawaki.osrswiki"
        minSdk = 24 // Keeping your existing minSdk
        targetSdk = 35 // Keeping your existing targetSdk
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
    buildFeatures {
        viewBinding = true
    }
}

dependencies {
    implementation(libs.google.gson)
    implementation(libs.androidxPagingRuntimeKtx)
    implementation(libs.retrofit.core)
    implementation(libs.retrofit.converterKotlinxSerialization)
    implementation(libs.kotlinx.serializationJson)
    implementation(libs.okhttp.core)
    implementation(libs.okhttp.loggingInterceptor) // Optional: for logging network requests
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)

    // Room components
    implementation(libs.androidx.room.runtime) // Assuming alias exists in libs.versions.toml
    implementation(libs.androidx.room.ktx)     // Assuming alias exists
    ksp(libs.androidx.room.compiler)       // Assuming alias exists

    // Added dependencies
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidxLifecycleViewmodelKtx)
    implementation(libs.androidxLifecycleViewmodelSavedstate)
    implementation(libs.androidxNavigationFragmentKtx)
    implementation(libs.androidxNavigationUiKtx)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.glide.core)
    ksp(libs.glide.ksp)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.ext.junit) // Corrected alias if it was androidx.junit
    androidTestImplementation(libs.androidx.espresso.core)
}
