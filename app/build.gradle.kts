plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.parcelize)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.omiyawaki.osrswiki"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.omiyawaki.osrswiki"
        minSdk = 24
        targetSdk = 35
        versionCode = 6
        versionName = "1.5"

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
        dataBinding = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "*.apk"
            excludes += "*.jar"
            excludes += "**/native-image.properties"
        }
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
        animationsDisabled = true
    }

    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }

    sourceSets {
        getByName("main") {
            // Only include local assets directory - organized assets will be copied here
            assets.srcDirs("src/main/assets")
        }
    }
}

// Task to organize assets according to asset-mapping.json
tasks.register<Copy>("organizeAssets") {
    description = "Organize shared assets according to asset-mapping.json"
    group = "assets"
    
    val sharedCssDir = File(project.projectDir, "../../../shared/css")
    val sharedJsDir = File(project.projectDir, "../../../shared/js")
    val cacheBasePath = "${System.getProperty("user.home")}/Develop/osrswiki/cache"
    val cacheDirs = listOf(
        "$cacheBasePath/binary-assets/mbtiles",
        "$cacheBasePath/binary-assets/map-images/output",
        "$cacheBasePath/game-data"
    )
    val targetAssetsDir = File(project.projectDir, "src/main/assets")
    
    // Check if we're in monorepo mode
    val isMonorepo = sharedCssDir.exists() && sharedJsDir.exists()
    val hasCache = File(cacheBasePath).exists()
    
    if (isMonorepo) {
        println("🔧 Android Build: Organizing assets according to mapping...")
        
        // Clear and recreate assets directory
        doFirst {
            if (targetAssetsDir.exists()) {
                targetAssetsDir.deleteRecursively()
            }
            targetAssetsDir.mkdirs()
        }
        
        // Organize CSS files: shared/css/*.css -> assets/styles/
        from(sharedCssDir) {
            include("*.css")
            into("styles")
        }
        
        // Organize CSS modules: shared/css/modules/*.css -> assets/styles/modules/
        from(File(sharedCssDir, "modules")) {
            include("*.css")
            into("styles/modules")
        }
        
        // Organize JS files: shared/js/*.js -> assets/js/ (excluding WebView files)
        from(sharedJsDir) {
            include("*.js")
            exclude("collapsible_content.js", "horizontal_scroll_interceptor.js", "responsive_videos.js",
                    "clipboard_bridge.js", "infobox_switcher_bootstrap.js", "switch_infobox.js",
                    "ge_charts_init.js", "highcharts-stock.js")
            exclude("mediawiki/*.js")
            into("js")
        }
        
        // Organize WebView files: specific JS files -> assets/web/
        from(sharedJsDir) {
            include("collapsible_content.js", "horizontal_scroll_interceptor.js", "responsive_videos.js",
                    "clipboard_bridge.js", "infobox_switcher_bootstrap.js", "switch_infobox.js",
                    "ge_charts_init.js", "highcharts-stock.js")
            into("web")
        }
        
        // Organize WebView CSS: JS directory CSS files -> assets/web/
        from(sharedJsDir) {
            include("*.css")
            into("web")
        }
        
        // MediaWiki startup.js -> assets/startup.js (root)
        from(File(sharedJsDir, "mediawiki")) {
            include("startup.js")
            into("")
        }
        
        // Other MediaWiki modules -> assets/mediawiki/
        from(File(sharedJsDir, "mediawiki")) {
            include("*.js")
            exclude("startup.js")
            into("mediawiki")
        }
        
        // Cache assets (if available) - EXCLUDE large binary files
        if (hasCache) {
            cacheDirs.forEach { cacheDir ->
                val dir = File(cacheDir)
                if (dir.exists()) {
                    from(dir) {
                        // Exclude large binary cache files to prevent GitHub size limit issues
                        exclude("**/*.dat2")
                        exclude("**/*.idx*")
                        exclude("**/main_file_cache.*")
                        exclude("**/openrs2_cache/**")
                        into("")
                    }
                }
            }
        }
        
        into(targetAssetsDir)
        
        println("🔧 Android Build: Assets will be organized into proper structure")
    } else {
        println("🔧 Android Build: Standalone mode - using existing local assets")
        // In standalone mode, do nothing - assets should already be in place
        doLast {
            // Just ensure the directory exists
            targetAssetsDir.mkdirs()
        }
    }
}

// Make sure assets are organized before they are processed
tasks.whenTaskAdded {
    if (name.startsWith("merge") && name.contains("Assets")) {
        dependsOn("organizeAssets")
    }
    // Fix lint task dependencies
    if (name == "lintAnalyzeDebug" || name == "generateDebugLintReportModel") {
        dependsOn("organizeAssets")
    }
}

dependencies {
    // MapLibre for map functionality
    implementation(libs.maplibre.native)
    
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.coordinatorlayout)
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.cardview)
    implementation(libs.androidx.viewpager2)
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.livedata.ktx)
    implementation(libs.androidx.preference.ktx)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.webkit)
    implementation(libs.androidx.swiperefreshlayout)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidxPagingRuntimeKtx)
    implementation(libs.material)
    implementation(libs.gson)
    implementation(libs.okhttp)
    implementation(libs.okhttp.loggingInterceptor)
    implementation(libs.picasso)
    implementation(libs.glide.core)
    ksp(libs.glide.ksp)
    implementation(libs.jsoup)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serializationJson)
    implementation(libs.retrofit.core)
    implementation(libs.retrofit.converterKotlinxSerialization)
    implementation(libs.apacheCommonsLang3)
    implementation(libs.play.billing)
    implementation(libs.play.billing.ktx)

    testImplementation(libs.junit)
    testImplementation(libs.androidx.core.testing)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockito.kotlin)
    
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.espresso.contrib)
    androidTestImplementation(libs.androidx.espresso.intents)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(libs.kotlinx.coroutines.test)
}