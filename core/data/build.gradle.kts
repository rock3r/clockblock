plugins {
    alias(libs.plugins.opus.android.library)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.metro)
}

dependencies {
    api(projects.core.model)
    api(projects.core.circadian)
    implementation(libs.androidx.datastore)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    testImplementation(projects.core.testing)
}
