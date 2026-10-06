plugins {
    alias(libs.plugins.clockblock.android.feature)
}

dependencies {
    implementation(projects.core.circadian)
    // "Snooze 15 min" on the Now card goes through the same scheduler as the notification action.
    implementation(projects.core.notifications)
    // Storage Access Framework launcher for the .ics export.
    implementation(libs.androidx.activity.compose)
}
