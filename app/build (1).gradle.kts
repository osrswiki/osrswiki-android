import org.gradle.api.attributes.Usage
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    id("org.jetbrains.kotlin.plugin.parcelize")
}

android {
    namespace = "com.omiyawaki.osrswiki"
    compileSdk = 36
    buildToolsVersion = "36.0.0"

    defaultConfig {
        applicationId = "com.omiyawaki.osrswiki"
        minSdk = 24
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
    buildFeatures {
        viewBinding = true
        buildConfig = true
    }
    androidResources {
        noCompress += listOf("mbtiles")
    }

    packaging {
        jniLibs {
            useLegacyPackaging = false
        }
    }

    lint {
        abortOnError = false
    }

    sourceSets {
        getByName("main") {
            // Dual-mode asset configuration: auto-detect monorepo vs standalone
            val sharedDirs = listOf(
                "../../../shared/css",
                "../../../shared/js", 
                "../../../shared/assets"
            )
            
            // Check if we're in monorepo environment (shared directories exist)
            val isMonorepo = sharedDirs.any { File(it).exists() }
            
            if (isMonorepo) {
                // Monorepo mode: use relative paths to shared directories
                assets.srcDirs(sharedDirs)
                println("🔧 Android Build: Monorepo mode - using shared directories")
            } else {
                // Standalone mode: assets are copied to local assets directory
                assets.srcDirs("src/main/assets")
                println("🔧 Android Build: Standalone mode - using local assets")
            }
        }
    }
}

dependencies {
    // MapLibre Native SDK
    implementation(libs.maplibre.native)

    implementation(libs.androidxPagingRuntimeKtx)
    implementation(libs.retrofit.core)
    implementation(libs.retrofit.converterKotlinxSerialization)
    implementation(libs.gson)
    implementation(libs.kotlinx.serializationJson)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.okhttp.core)
    implementation(libs.okhttp.loggingInterceptor)
    implementation(libs.moshi.kotlin) // Added Moshi runtime
    ksp(libs.moshi.codegen)           // Added Moshi codegen processor
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.preference.ktx)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.webkit)
    implementation(libs.jsoup)
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

    // Google Pay
    implementation(libs.google.pay.wallet)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
