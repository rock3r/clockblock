plugins {
    alias(libs.plugins.opus.android.library)
    alias(libs.plugins.opus.android.compose)
    alias(libs.plugins.metro)
}

android {
    lint {
        // Remote Compose widget profiles and host-evaluated time are still @RestrictTo(LIBRARY_GROUP) in
        // alpha20. All such usages live in RestrictedRemoteCompose.kt (file-level suppression); keep the check
        // as a warning everywhere else so new usages get noticed.
        warning += "RestrictedApiAndroidX"
    }
}

dependencies {
    implementation(projects.core.model)
    implementation(projects.core.data)
    // Only for the Done button: it fires the notification module's Done broadcast (NotificationIntents.widgetDone).
    implementation(projects.core.notifications)
    implementation(projects.core.designsystem)
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.metrox.android)
    implementation(libs.androidx.compose.remote.creation.compose)
    implementation(libs.androidx.compose.remote.creation)
    implementation(libs.androidx.compose.remote.core)
    implementation(libs.androidx.compose.remote.tooling.preview)
    testImplementation(projects.core.testing)
    testImplementation(libs.androidx.compose.remote.player.compose)
    testImplementation(libs.androidx.compose.remote.player.core)
}
