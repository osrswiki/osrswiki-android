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
    implementation("com.google.code.gson:gson:2.10.1")
    implementation("androidx.paging:paging-runtime-ktx:3.3.0")
    implementation(libs.retrofit.core)
    implementation(libs.retrofit.converterKotlinxSerialization)
    implementation(libs.kotlinx.serializationJson)
    implementation(libs.okhttp.core)
    implementation(libs.okhttp.loggingInterceptor) // Optional: for logging network requests
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)

    // Added dependencies
    implementation(libs.androidx.constraintlayout)
        implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.1") // Corrected alias if it was lifecycle.viewmodelKtx
        implementation("androidx.lifecycle:lifecycle-viewmodel-savedstate:2.8.1")
    implementation("androidx.navigation:navigation-fragment-ktx:2.7.7") // Corrected alias if it was navigation.fragmentKtx
    implementation("androidx.navigation:navigation-ui-ktx:2.7.7")       // Corrected alias if it was navigation.uiKtx
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.glide.core)
    ksp(libs.glide.ksp)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.ext.junit) // Corrected alias if it was androidx.junit
    androidTestImplementation(libs.androidx.espresso.core)
}
