plugins {
    alias(libs.plugins.clockblock.android.application)
    alias(libs.plugins.clockblock.android.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.metro)
}

android {
    defaultConfig {
        versionCode = 1
        versionName = "1.0.0"
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Signed with the debug key so the release build is installable for local perf checks.
            signingConfig = signingConfigs.getByName("debug")
        }
    }
}

dependencies {
    implementation(projects.core.model)
    implementation(projects.core.circadian)
    implementation(projects.core.data)
    implementation(projects.core.designsystem)
    implementation(projects.core.notifications)
    implementation(projects.feature.onboarding)
    implementation(projects.feature.trips)
    implementation(projects.feature.plan)
    implementation(projects.feature.settings)
    implementation(projects.widget)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.compose.material3.adaptive)
    implementation(libs.androidx.compose.material3.adaptive.layout)
    implementation(libs.androidx.compose.material3.adaptive.navigation3)
    implementation(libs.androidx.compose.material3.adaptive.navigation.suite)
    implementation(libs.google.material)
    implementation(libs.androidx.profileinstaller)
    implementation(libs.metrox.android)
    implementation(libs.metrox.viewmodel.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.collections.immutable)

    testImplementation(projects.core.testing)

    // Not :core:testing: it exposes Robolectric, and with Robolectric on the instrumentation classpath Espresso
    // (used by the Compose rule to idle) drives a shadow looper and every device test crashes.
    androidTestImplementation(libs.androidx.test.core)
    // The e2e suite resets every DataStore to its default document before each test (see AppDataReset).
    androidTestImplementation(libs.androidx.datastore)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.espresso.core)
    // Stubs the system document picker with a prepared backup file (BackupImportTest), so no DocumentsUI driving.
    androidTestImplementation(libs.androidx.test.espresso.intents)
    androidTestImplementation(libs.androidx.test.uiautomator)
}
