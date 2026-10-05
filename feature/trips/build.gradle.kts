plugins {
    alias(libs.plugins.opus.android.feature)
}

dependencies {
    implementation(projects.core.data)
    // Adaptation progress (`adaptationProgressAt`) and plan day spans for the trip cards.
    implementation(projects.core.circadian)
    // BackHandler: the editor intercepts back (and predictive back) while it has unsaved changes.
    implementation(libs.androidx.activity.compose)
}
