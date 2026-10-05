plugins {
    alias(libs.plugins.opus.android.library)
    alias(libs.plugins.metro)
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
}
