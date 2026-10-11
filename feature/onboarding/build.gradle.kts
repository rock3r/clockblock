plugins {
    alias(libs.plugins.clockblock.android.feature)
}

dependencies {
    implementation(projects.core.data)
    // MCTQ mid-sleep helpers for the "Not sure?" chronotype estimate.
    implementation(projects.core.circadian)
    // NotificationPermissions for the Reminders step.
    implementation(projects.core.notifications)
    // rememberLauncherForActivityResult (POST_NOTIFICATIONS) and predictive back between steps.
    implementation(libs.androidx.activity.compose)
    // Morphing text for the sleep dial's bedtime and wake times (vendored, MIT).
    implementation(projects.thirdParty.torph)
}
