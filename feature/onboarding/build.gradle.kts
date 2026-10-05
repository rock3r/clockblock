plugins {
    alias(libs.plugins.opus.android.feature)
}

dependencies {
    implementation(projects.core.data)
    // MCTQ mid-sleep helpers for the "Not sure?" chronotype estimate.
    implementation(projects.core.circadian)
    // NotificationPermissions for the Reminders step.
    implementation(projects.core.notifications)
    // rememberLauncherForActivityResult (POST_NOTIFICATIONS) and predictive back between steps.
    implementation(libs.androidx.activity.compose)
}
