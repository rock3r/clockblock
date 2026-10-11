// Torph for Jetpack Compose (https://github.com/c5inco/torph-compose), vendored: it has no Maven release, and CI
// must not depend on mavenLocal or an included build. See README.md for the upstream commit and how to update.
plugins {
    alias(libs.plugins.clockblock.android.library)
    alias(libs.plugins.clockblock.android.compose)
}

android {
    // Upstream's own namespace (it has no resources, so nothing else depends on it).
    namespace = "des.c5inco.torph.compose"
}

dependencies {
    implementation(libs.kotlinx.coroutines.android)
}
