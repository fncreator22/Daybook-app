// Root build file. Plugins are declared here with `apply false` so the version
// catalog resolves them once, then modules apply them without repeating versions.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
}
