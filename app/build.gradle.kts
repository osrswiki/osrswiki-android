import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.parcelize)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

fun loadReleaseSigningProperties(file: File): Map<String, String> {
    if (!file.isFile) {
        return emptyMap()
    }
    return file.readLines().mapNotNull { line ->
        val trimmed = line.trim()
        if (trimmed.isEmpty() || trimmed.startsWith("#")) {
            return@mapNotNull null
        }
        val separator = trimmed.indexOf('=')
        if (separator <= 0) {
            return@mapNotNull null
        }
        trimmed.substring(0, separator).trim() to trimmed.substring(separator + 1).trim()
    }.toMap()
}

val releaseSigningFile = System.getenv("OSRSWIKI_ANDROID_SIGNING_PROPERTIES")
    ?.let(::File)
    ?: File(System.getProperty("user.home"), ".config/osrswiki/android-signing.properties")
val releaseSigningProperties = loadReleaseSigningProperties(releaseSigningFile)

fun releaseSigningValue(propertyName: String, envName: String): String? {
    val fromFile = releaseSigningProperties[propertyName]
    if (!fromFile.isNullOrBlank()) {
        return fromFile
    }
    val fromEnv = System.getenv(envName)
    return fromEnv?.takeIf { it.isNotBlank() }
}

val releaseKeystorePath = releaseSigningValue("storeFile", "OSRSWIKI_ANDROID_KEYSTORE")
val releaseKeystorePassword = releaseSigningValue("storePassword", "OSRSWIKI_ANDROID_KEYSTORE_PASSWORD")
val releaseKeyAlias = releaseSigningValue("keyAlias", "OSRSWIKI_ANDROID_KEY_ALIAS")
val releaseKeyPassword = releaseSigningValue("keyPassword", "OSRSWIKI_ANDROID_KEY_PASSWORD")
val hasReleaseSigning = listOf(
    releaseKeystorePath,
    releaseKeystorePassword,
    releaseKeyAlias,
    releaseKeyPassword
).all { !it.isNullOrBlank() }

android {
    namespace = "com.omiyawaki.osrswiki"
    compileSdk = 36
    // The default remains the isolated map-prototype test host. Article WebView regression
    // tests can opt into the complete application manifest with
    // `-PosrswikiHostInstrumentation=true` without weakening the map lane.
    testBuildType = if (providers.gradleProperty("osrswikiHostInstrumentation").orNull == "true") {
        "debug"
    } else {
        "mapPrototype"
    }

    defaultConfig {
        applicationId = "com.omiyawaki.osrswiki"
        minSdk = 24
        targetSdk = 36
        versionCode = 53
        versionName = "2.0.6"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // distribution: play (default, Play Billing tips) vs foss (F-Droid / no Billing on classpath).
    // Same applicationId for both. Instrumentation default variant is playMapPrototype
    // (play isDefault + existing testBuildType = mapPrototype).
    flavorDimensions += "distribution"
    productFlavors {
        create("play") {
            dimension = "distribution"
            isDefault = true
        }
        create("foss") {
            dimension = "distribution"
        }
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(releaseKeystorePath!!)
                storePassword = releaseKeystorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        create("benchmark") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
            isDebuggable = false
        }
        create("mapPrototype") {
            initWith(getByName("debug"))
            applicationIdSuffix = ".mapprototype"
            matchingFallbacks += listOf("debug")
            buildConfigField("String", "MAP_PROTOTYPE_CANDIDATE_ID", "\"candidate-008\"")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
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
        unitTests.apply {
            isReturnDefaultValues = true
            isIncludeAndroidResources = true
        }
        animationsDisabled = true
    }

    lint {
        checkReleaseBuilds = true
        abortOnError = true
    }

    sourceSets {
        getByName("main") {
            // Only include local assets directory - organized assets will be copied here
            assets.srcDirs("src/main/assets")
        }
        getByName("mapPrototype") {
            // Shared instrumentation references debug-only QA hosts. Keep that fallback scoped to
            // this debuggable prototype variant; its manifest remains map-only.
            java.srcDirs("src/debug/java")
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_11)
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

val androidAssetsDir = File(project.projectDir, "src/main/assets")
val requiredReleaseMapAssets = listOf(
    "map-metadata.json",
    "map_floor_0.mbtiles",
    "map_floor_1.mbtiles",
    "map_floor_2.mbtiles",
    "map_floor_3.mbtiles"
)

// Task to organize assets according to asset-mapping.json
tasks.register<Copy>("organizeAssets") {
    description = "Organize shared assets according to asset-mapping.json"
    group = "assets"
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE

    val sharedCssDir = File(project.projectDir, "../../../shared/css")
    val sharedJsDir = File(project.projectDir, "../../../shared/js")

    // Source files remain in the repository, while cache bytes must come from
    // the verified machine-local artifact root. Do not fall back to the legacy
    // synced <repo>/cache directory: Gradle snapshots every input directory.
    val monorepoRoot = File(project.projectDir, "../../..").canonicalFile
        .takeIf { File(it, "shared").isDirectory }
    val artifactResolver = File(project.projectDir, "../../../scripts/shared/local-artifact-root.sh")
    val cacheBasePath = if (artifactResolver.isFile) {
        providers.exec {
            commandLine(artifactResolver.absolutePath, "cache")
        }.standardOutput.asText.orNull?.trim()?.takeIf { it.isNotEmpty() }
    } else {
        null
    }
    val cacheDirs = if (cacheBasePath != null) listOf(
        "$cacheBasePath/binary-assets/mbtiles",
        "$cacheBasePath/binary-assets/map-images/output",
        "$cacheBasePath/game-data"
    ) else emptyList()
    val targetAssetsDir = androidAssetsDir

    // Check if we're in monorepo mode
    val isMonorepo = sharedCssDir.exists() && sharedJsDir.exists()
    val hasCache = cacheBasePath != null && File(cacheBasePath).exists()

    if (monorepoRoot != null) {
        println("🔧 Android Build: Monorepo root found at ${monorepoRoot.absolutePath}")
        println("🔧 Android Build: Machine-local cache path: ${cacheBasePath ?: "unavailable"}")
    } else {
        println("🔧 Android Build: Could not find monorepo root")
    }
    
    if (isMonorepo) {
        println("🔧 Android Build: Organizing assets according to mapping...")
        
        // Ensure assets directory exists (but don't delete existing platform-specific files)
        doFirst {
            targetAssetsDir.mkdirs()
        }
        
        // Root-relative wiki artwork in dumped CSS (`url(/images/...)`) would
        // otherwise resolve against appassets.androidplatform.net and vanish.
        val wikiImageUrlFilter: (String) -> String = { line ->
            line.replace(
                Regex("""url\(\s*(['"]?)(/images/)"""),
                "url($1https://oldschool.runescape.wiki$2"
            )
        }

        // Organize CSS files: shared/css/*.css -> assets/styles/
        from(sharedCssDir) {
            include("*.css")
            into("styles")
            filter(wikiImageUrlFilter)
        }
        
        // Organize CSS modules: shared/css/modules/*.css -> assets/styles/modules/
        from(File(sharedCssDir, "modules")) {
            include("*.css")
            into("styles/modules")
            filter(wikiImageUrlFilter)
        }
        
        // Organize JS files: shared/js/*.js -> assets/js/ (excluding WebView files)
        from(sharedJsDir) {
            include("*.js")
            exclude("collapsible_content.js", "horizontal_scroll_interceptor.js", "responsive_videos.js",
                    "clipboard_bridge.js", "infobox_switcher_bootstrap.js", "switch_infobox.js",
                    "mobile_article_polish.js", "ge_charts_init.js", "chart.umd.min.js",
                    "live_article_asset_warm.js", "first_viewport_assets.js", "image_area_cap.js",
                    "article_audio_player.js")
            exclude("osrs_calculator_runtime.js")
            exclude("osrs_native_calc_indoc.js")
            exclude("mediawiki/*.js")
            into("js")
        }
        
        // Organize WebView files: specific JS files -> assets/web/
        from(sharedJsDir) {
            include("collapsible_content.js", "horizontal_scroll_interceptor.js", "responsive_videos.js",
                    "clipboard_bridge.js", "infobox_switcher_bootstrap.js", "switch_infobox.js",
                    "mobile_article_polish.js", "ge_charts_init.js", "chart.umd.min.js",
                    "tabber_init.js", "table_column_normalize.js", "osrs_native_calc_indoc.js", "osrs_calculator_runtime.js",
                    "live_article_asset_warm.js", "first_viewport_assets.js", "image_area_cap.js",
                    "article_audio_player.js")
            into("web")
        }
        
        // Organize WebView CSS: JS directory CSS files -> assets/web/
        from(sharedJsDir) {
            include("*.css")
            exclude("collapsible_*.css") // Exclude collapsible CSS - use platform-specific versions
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

        val sharedManifestsDir = File(project.projectDir, "../../../shared/manifests")
        if (sharedManifestsDir.exists()) {
            from(sharedManifestsDir) {
                include("osrs-wiki-calculators.json")
                into("manifests")
            }
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
                        if (cacheDir.endsWith("binary-assets/map-images/output")) {
                            exclude("map-metadata.json")
                        }
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

tasks.register("validateReleaseAssets") {
    description = "Fail release builds when required map assets are missing or empty."
    group = "verification"
    dependsOn("organizeAssets")

    inputs.files(requiredReleaseMapAssets.map { File(androidAssetsDir, it) })

    doLast {
        val missingOrEmpty = requiredReleaseMapAssets
            .map { File(androidAssetsDir, it) }
            .filter { !it.isFile || it.length() == 0L }

        if (missingOrEmpty.isNotEmpty()) {
            val details = missingOrEmpty.joinToString(separator = "\n") { " - ${it.relativeTo(project.projectDir)}" }
            throw GradleException(
                "Required Android release map assets are missing or empty:\n$details\n" +
                    "Materialize the pinned release assets with ./scripts/fetch-map-assets.sh before release validation."
            )
        }
    }
}

tasks.register("validateReleaseNetworkPolicy") {
    description = "Fail release builds when app-wide cleartext traffic is enabled."
    group = "verification"

    val manifestFile = File(project.projectDir, "src/main/AndroidManifest.xml")
    inputs.file(manifestFile)

    doLast {
        val manifest = manifestFile.readText()
        if (manifest.contains("""android:usesCleartextTraffic="true"""")) {
            throw GradleException(
                "Android release guardrail forbids app-wide cleartext traffic. " +
                    "Use HTTPS or add a narrow network security config exception for a documented host."
            )
        }
    }
}

tasks.register("validateReleaseGuardrails") {
    description = "Run bounded Android release guardrails: assets, network policy, release lint, and release assembly."
    group = "verification"
    dependsOn(
        "validateReleaseAssets",
        "validateReleaseNetworkPolicy",
        "lintPlayRelease",
        "assemblePlayRelease"
    )
}

// Make sure assets are organized before they are processed
tasks.configureEach {
    val taskName = name
    val isLintTask = taskName.contains("Lint", ignoreCase = true)
    val isReleaseLikeTask = taskName.contains("Release") || taskName.contains("Benchmark")

    if (taskName.startsWith("merge") && taskName.contains("Assets")) {
        dependsOn("organizeAssets")
    }
    if (isLintTask) {
        dependsOn("organizeAssets")
    }
    val isPreReleaseOrBenchmark = taskName.startsWith("pre") && (
        taskName.endsWith("ReleaseBuild") || taskName.endsWith("BenchmarkBuild")
    )
    if (isPreReleaseOrBenchmark || (isReleaseLikeTask && isLintTask)) {
        dependsOn("validateReleaseAssets", "validateReleaseNetworkPolicy")
    }
}

dependencies {
    // MapLibre for map functionality
    implementation(project(":undergroundmaps"))
    
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
    implementation(libs.glide.core)
    ksp(libs.glide.ksp)
    implementation(libs.jsoup)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serializationJson)
    implementation(libs.retrofit.core)
    implementation(libs.retrofit.converterKotlinxSerialization)
    implementation(libs.apacheCommonsLang3)
    "playImplementation"(libs.play.billing)
    "playImplementation"(libs.play.billing.ktx)
    implementation(libs.androidx.profileinstaller)

    testImplementation(libs.junit)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.junit)
    testImplementation(libs.androidx.core.testing)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockito.kotlin)
    testImplementation(libs.robolectric)
    testImplementation(libs.okhttp.mockwebserver)
    // The prototype unit test parses its generated payload directly. The map
    // library intentionally keeps this implementation dependency private.
    // Flavor × mapPrototype configs are created late by AGP; wire afterEvaluate.
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.espresso.contrib)
    androidTestImplementation(libs.androidx.espresso.intents)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.androidx.uiautomator)
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(libs.kotlinx.coroutines.test)
}


// mapPrototype unit tests need MapLibre geojson; AGP creates flavor×buildType
// configurations only after the android block is evaluated.
afterEvaluate {
    listOf("testPlayMapPrototypeImplementation", "testFossMapPrototypeImplementation").forEach { configName ->
        configurations.findByName(configName)?.let { config ->
            dependencies.add(config.name, "org.maplibre.gl:android-sdk-geojson:6.0.1")
        } ?: logger.warn("Skipping missing configuration {}", configName)
    }
}
