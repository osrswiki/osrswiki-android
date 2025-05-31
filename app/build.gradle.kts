import org.gradle.api.attributes.Usage
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    // alias(libs.plugins.kotlinParcelize)
    id("org.jetbrains.kotlin.plugin.parcelize")
}

android {
    namespace = "com.omiyawaki.osrswiki"
    compileSdk = 35 // Keeping existing compileSdk
    buildToolsVersion = "35.0.1"

    defaultConfig {
        applicationId = "com.omiyawaki.osrswiki"
        minSdk = 24 // Keeping existing minSdk
        targetSdk = 35 // Keeping existing targetSdk
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
        buildConfig = true // Ensure BuildConfig generation is explicitly enabled
    }
}

dependencies {
    implementation(libs.androidxPagingRuntimeKtx)
    implementation(libs.retrofit.core)
    implementation(libs.retrofit.converterKotlinxSerialization)
    implementation(libs.kotlinx.serializationJson)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.okhttp.core)
    implementation(libs.okhttp.loggingInterceptor) // Optional: for logging network requests
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.preference.ktx) // Added for SharedPreferences utilities
    implementation(libs.androidx.work.runtime.ktx) // This alias should match what you defined in libs.versions.toml

    implementation(libs.apacheCommonsLang3)

    // Room components
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    {
        attributes {
            attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage::class.java, Usage.JAVA_RUNTIME))
        }
    }

    // Added dependencies
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidxLifecycleViewmodelKtx)
    implementation(libs.androidxLifecycleViewmodelSavedstate)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidxNavigationFragmentKtx)
    implementation(libs.androidxNavigationUiKtx)
    implementation(libs.glide.core)
    ksp(libs.glide.ksp)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.ext.junit) // Corrected alias if it was androidx.junit
    androidTestImplementation(libs.androidx.espresso.core)
}
