// Top-level build file where configuration options common to all sub-projects/modules can be added.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.serialization) apply false  // Ensuring Kotlin Serialization plugin is here
    alias(libs.plugins.androidx.navigation.safeargs) apply false
    alias(libs.plugins.ksp) apply false                  // KSP plugin
}
