plugins {
    alias(libs.plugins.opus.android.feature)
}

dependencies {
    implementation(projects.core.notifications)
    // Shared profile editors (sleep dial, chronotype, tools, effort) live in onboarding's `profile` package.
    implementation(projects.feature.onboarding)
    // SAF launchers (backup export/import) and runtime permission requests.
    implementation(libs.androidx.activity.compose)
}
