plugins {
    alias(libs.plugins.clockblock.android.library)
    alias(libs.plugins.clockblock.android.compose)
}

dependencies {
    api(projects.core.model)
    api(libs.androidx.graphics.shapes)
    api(libs.kotlinx.collections.immutable)
    implementation(libs.androidx.core.ktx)
    // Predictive back for the full-screen globe overlay (PredictiveBackHandler).
    implementation(libs.androidx.activity.compose)
    testImplementation(projects.core.testing)
}
