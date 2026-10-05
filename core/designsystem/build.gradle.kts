plugins {
    alias(libs.plugins.opus.android.library)
    alias(libs.plugins.opus.android.compose)
}

dependencies {
    api(projects.core.model)
    api(libs.androidx.graphics.shapes)
    api(libs.kotlinx.collections.immutable)
    implementation(libs.androidx.core.ktx)
}
