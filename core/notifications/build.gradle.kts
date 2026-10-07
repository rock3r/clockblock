plugins {
    alias(libs.plugins.clockblock.android.library)
    alias(libs.plugins.metro)
    // Goldens of the Now notification's custom views (the plugin is on the classpath via build-logic).
    id("io.github.takahirom.roborazzi")
}

roborazzi {
    outputDir.set(layout.projectDirectory.dir("src/test/screenshots"))
}

dependencies {
    api(projects.core.model)
    // PlanSurface / repositories appear in this module's public API (NowNotificationSurface, scheduler).
    api(projects.core.data)
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)
    // Only for @BroadcastReceiverKey: receivers are constructor-injected by MetroAppComponentFactory, which the app
    // already ships. compileOnly keeps metrox's `appComponentFactory` manifest entry out of every module that depends
    // on this one (it would break their Robolectric tests, whose Application isn't a MetroApplication).
    compileOnly(libs.metrox.android)

    // NotificationPaletteTest compares the copied chip colours with the design system's AdviceColors.
    testImplementation(projects.core.designsystem)
    testImplementation(platform(libs.androidx.compose.bom.alpha))
    testImplementation(libs.androidx.compose.ui.graphics)
    testImplementation(libs.roborazzi)
}
