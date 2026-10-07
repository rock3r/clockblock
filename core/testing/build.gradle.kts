plugins {
    alias(libs.plugins.clockblock.android.library)
    alias(libs.plugins.clockblock.android.compose)
}

dependencies {
    api(projects.core.model)
    api(projects.core.data)
    api(projects.core.designsystem)
    // Consumers without the Compose convention (e.g. :core:data tests) still need versions for the
    // unversioned Compose test artifacts below.
    api(platform(libs.androidx.compose.bom.alpha))
    api(libs.kotlinx.coroutines.test)
    api(libs.turbine)
    api(libs.junit4)
    api(libs.robolectric)
    api(libs.roborazzi)
    api(libs.roborazzi.compose)
    api(libs.roborazzi.junit.rule)
    api(libs.androidx.compose.ui.test.junit4)
    api(libs.androidx.test.core)
}
