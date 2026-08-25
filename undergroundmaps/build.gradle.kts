import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.security.MessageDigest

plugins {
    alias(libs.plugins.android.library)
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
val osrsMapLibreSanitizer = projectDir.resolve(
    "sanitize_osrs_maplibre_aar.py"
)
val osrsSanitizeMapLibreAar by tasks.registering(Exec::class) {
    description =
        "Sanitizes MapLibre paths and enables its fixed-width unconstrained camera mode."
    group = "build setup"
    inputs.files(osrsMapLibreSanitizationSource)
    inputs.file(osrsMapLibreSanitizer)
    inputs.property(
        "sourceSha256",
        "7b86efb12b6581d1e73128d55036a4a4c8f4b756c7272b7cde774cbdb906c2f7"
    )
    inputs.property("expectedReplacementCount", 24)
    inputs.property("expectedConstrainPatchCount", 4)
    outputs.file(osrsSanitizedMapLibreAar)

    doFirst {
        commandLine(
            "python3",
            osrsMapLibreSanitizer,
            "--input",
            osrsMapLibreSanitizationSource.singleFile,
            "--output",
            osrsSanitizedMapLibreAar.get().asFile,
            "--expected-source-sha256",
            "7b86efb12b6581d1e73128d55036a4a4c8f4b756c7272b7cde774cbdb906c2f7",
            "--expected-replacements",
            "24",
            "--expected-constrain-patches",
            "4"
        )
    }
}

val osrsSanitizedMapLibreFiles = files(osrsSanitizedMapLibreAar).builtBy(osrsSanitizeMapLibreAar)

android {
    namespace = "com.omiyawaki.osrswiki.undergroundmaps"
    compileSdk = 36

    defaultConfig {
        minSdk = 24
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
    implementation(libs.androidx.preference.ktx)
    implementation(libs.material)
    api(osrsSanitizedMapLibreFiles)
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
// Explicit opt-in only. Do NOT auto-pick the home underground-realms cache —
// that silently made the v2.0.1 GitHub FOSS APK diverge from a clean public
// assemble / F-Droid build (fixture stub vs 2.3MB candidate 010 + assets/assets).
// Debug/full-map builds must pass -PosrsUndergroundAssetsDir=... or
// OSRS_UNDERGROUND_ASSETS_DIR=...
val suppliedRealmAssets = providers.gradleProperty("osrsUndergroundAssetsDir")
    .orElse(providers.environmentVariable("OSRS_UNDERGROUND_ASSETS_DIR"))
val suppliedPublicationEvidence = providers.gradleProperty("osrsUndergroundEvidenceDir")
    .orElse(providers.environmentVariable("OSRS_UNDERGROUND_EVIDENCE_DIR"))
val expectedRealmManifestSha256 = providers.gradleProperty(
    "osrsExpectedUndergroundManifestSha256"
).orElse(providers.environmentVariable("OSRS_EXPECTED_UNDERGROUND_MANIFEST_SHA256"))
val requireCompletePublicationClosure = providers.gradleProperty(
    "osrsRequireUndergroundPublicationClosure"
).map(String::toBoolean).orElse(false)

val prepareUndergroundRealmAssets by tasks.registering(Sync::class) {
    description = "Stages the generated non-surface realm release into Android assets."
    group = "assets"
    into(generatedRealmAssets)
    // Immutable candidate inputs may intentionally be read-only. The generated
    // destination must remain writable so Gradle can populate nested realm paths.
    dirPermissions {
        unix("0755")
    }
    filePermissions {
        unix("0644")
    }
    inputs.property(
        "expectedRealmManifestSha256",
        expectedRealmManifestSha256.orElse("fixture-or-unpinned")
    )
    inputs.property("requireCompletePublicationClosure", requireCompletePublicationClosure)

    val resolvedRealmAssets = suppliedRealmAssets.orNull.orEmpty()
    if (resolvedRealmAssets.isNotBlank()) {
        // Relative values are the Android Gradle root (public repo root /
        // platforms/android), not this module directory. That matches
        // F-Droid prebuild osrsUndergroundAssetsDir=underground-realms-release.
        from(rootProject.file(resolvedRealmAssets)) {
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
        if (requireCompletePublicationClosure.get()) {
            check(suppliedRealmAssets.isPresent) {
                "Candidate publication requires -PosrsUndergroundAssetsDir."
            }
            val expectedSha256 = expectedRealmManifestSha256.orNull
            check(expectedSha256?.matches(Regex("^[0-9a-f]{64}$")) == true) {
                "Candidate publication requires a lowercase SHA-256 through " +
                    "-PosrsExpectedUndergroundManifestSha256."
            }
            val actualSha256 = MessageDigest.getInstance("SHA-256")
                .digest(manifest.readBytes())
                .joinToString("") { byte -> "%02x".format(byte) }
            check(actualSha256 == expectedSha256) {
                "Canonical realm manifest SHA-256 mismatch: expected " +
                    "$expectedSha256 but staged $actualSha256."
            }
        }
    }
}

android.sourceSets.getByName("main").assets.srcDir(generatedRealmAssets)
tasks.named("preBuild").configure { dependsOn(prepareUndergroundRealmAssets) }
