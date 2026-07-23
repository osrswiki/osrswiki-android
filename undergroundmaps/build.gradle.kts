import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
}

val osrsMapLibreSanitizationSource by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
    isTransitive = false
}

val osrsSanitizedMapLibreAar = layout.buildDirectory.file(
    "generated/sanitizedDependencies/maplibre-android-sdk-11.12.1.aar"
)
val osrsSanitizeMapLibreAar by tasks.registering(Exec::class) {
    description = "Replaces upstream MapLibre CI host paths with fixed-width logical paths."
    group = "build setup"
    inputs.files(osrsMapLibreSanitizationSource)
    inputs.property(
        "sourceSha256",
        "7b86efb12b6581d1e73128d55036a4a4c8f4b756c7272b7cde774cbdb906c2f7"
    )
    inputs.property("expectedReplacementCount", 24)
    outputs.file(osrsSanitizedMapLibreAar)

    doFirst {
        commandLine(
            "python3",
            rootProject.projectDir.resolve("../../tools/map/sanitize_osrs_maplibre_aar.py"),
            "--input",
            osrsMapLibreSanitizationSource.singleFile,
            "--output",
            osrsSanitizedMapLibreAar.get().asFile,
            "--expected-source-sha256",
            "7b86efb12b6581d1e73128d55036a4a4c8f4b756c7272b7cde774cbdb906c2f7",
            "--expected-replacements",
            "24"
        )
    }
}

val osrsSanitizedMapLibreFiles = files(osrsSanitizedMapLibreAar).builtBy(osrsSanitizeMapLibreAar)

android {
    namespace = "com.omiyawaki.osrswiki.undergroundmaps"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.omiyawaki.osrswiki.undergroundmaps"
        minSdk = 24
        targetSdk = 35
        versionCode = 6
        versionName = "0.6.0-candidate-006"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
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

    buildFeatures {
        buildConfig = true
    }

    androidResources {
        noCompress += "mbtiles"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    testOptions {
        unitTests.apply {
            isReturnDefaultValues = true
            isIncludeAndroidResources = true
        }
    }

    lint {
        checkReleaseBuilds = true
        abortOnError = true
        // Retained command logs must not expose the host-absolute HTML report URI.
        htmlReport = false
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_11)
    }
}

dependencies {
    add(osrsMapLibreSanitizationSource.name, libs.maplibre.native)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.material)
    implementation(osrsSanitizedMapLibreFiles)
    // Explicit upstream runtime dependencies retained when the pinned AAR is sanitized locally.
    implementation("org.maplibre.gl:android-sdk-turf:6.0.1")
    implementation("androidx.annotation:annotation:1.8.2")
    implementation("androidx.fragment:fragment:1.8.2")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.jakewharton.timber:timber:5.0.1")
    implementation("androidx.interpolator:interpolator:1.0.0")
    implementation("org.maplibre.gl:android-sdk-geojson:6.0.1")
    implementation("org.maplibre.gl:maplibre-android-gestures:0.0.4")
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serializationJson)

    testImplementation(libs.junit)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.robolectric)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.uiautomator)
}

val generatedRealmAssets = layout.buildDirectory.dir("generated/realmAssets/main")
val suppliedRealmAssets = providers.gradleProperty("osrsUndergroundAssetsDir")
    .orElse(providers.environmentVariable("OSRS_UNDERGROUND_ASSETS_DIR"))
val suppliedPublicationEvidence = providers.gradleProperty("osrsUndergroundEvidenceDir")
    .orElse(providers.environmentVariable("OSRS_UNDERGROUND_EVIDENCE_DIR"))
val requireCompletePublicationClosure = providers.gradleProperty(
    "osrsRequireUndergroundPublicationClosure"
).map(String::toBoolean).orElse(false)

val prepareUndergroundRealmAssets by tasks.registering(Sync::class) {
    description = "Stages the generated non-surface realm release into Android assets."
    group = "assets"
    into(generatedRealmAssets)

    if (suppliedRealmAssets.isPresent) {
        from(suppliedRealmAssets.map(::file)) {
            include("underground-realms.json", "**/*.mbtiles")
        }
    } else {
        from(layout.projectDirectory.dir("src/fixtureAssets"))
    }

    doLast {
        val manifest = generatedRealmAssets.get().file("underground-realms.json").asFile
        check(manifest.isFile) {
            "Expected underground-realms.json in " +
                (suppliedRealmAssets.orNull ?: "undergroundmaps/src/fixtureAssets")
        }
    }
}

android.sourceSets.getByName("main").assets.srcDir(generatedRealmAssets)
tasks.named("preBuild").configure { dependsOn(prepareUndergroundRealmAssets) }

val osrsReleaseApk = layout.buildDirectory.file("outputs/apk/release/undergroundmaps-release.apk")
val osrsValidateReleaseApkPathHygiene by tasks.registering(Exec::class) {
    description = "Fails when the APK, release, or retained evidence closure contains a host path."
    group = "verification"
    mustRunAfter("assembleRelease")
    inputs.file(osrsReleaseApk)
    if (suppliedRealmAssets.isPresent) {
        inputs.dir(suppliedRealmAssets.map(::file))
    }
    if (suppliedPublicationEvidence.isPresent) {
        inputs.dir(suppliedPublicationEvidence.map(::file))
    }

    doFirst {
        if (requireCompletePublicationClosure.get()) {
            check(suppliedRealmAssets.isPresent) {
                "Candidate publication requires -PosrsUndergroundAssetsDir."
            }
            check(suppliedPublicationEvidence.isPresent) {
                "Candidate publication requires -PosrsUndergroundEvidenceDir."
            }
        }
        val scannerArguments = mutableListOf(
            "python3",
            rootProject.projectDir.resolve("../../tools/map/osrs_public_path_hygiene.py"),
            "--archive",
            osrsReleaseApk.get().asFile
        )
        if (suppliedRealmAssets.isPresent) {
            scannerArguments.add("--public-tree")
            scannerArguments.add(file(suppliedRealmAssets.get()))
        }
        if (suppliedPublicationEvidence.isPresent) {
            scannerArguments.add("--artifact-root")
            scannerArguments.add(file(suppliedPublicationEvidence.get()))
        }
        commandLine(scannerArguments)
    }
}
tasks.matching { it.name == "assembleRelease" }.configureEach {
    finalizedBy(osrsValidateReleaseApkPathHygiene)
}
