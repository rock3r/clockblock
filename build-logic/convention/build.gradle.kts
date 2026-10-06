import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    `kotlin-dsl`
}

group = "dev.sebastiano.clockblocker.opus.buildlogic"

java {
    toolchain { languageVersion = JavaLanguageVersion.of(21) }
}

kotlin {
    compilerOptions { jvmTarget = JvmTarget.JVM_21 }
}

dependencies {
    compileOnly(libs.android.gradlePlugin)
    compileOnly(libs.kotlin.gradlePlugin)
    compileOnly(libs.compose.gradlePlugin)
    compileOnly(libs.metro.gradlePlugin)
    compileOnly(libs.roborazzi.gradlePlugin)
}

gradlePlugin {
    plugins {
        register("androidApplication") {
            id = "clockblock.android.application"
            implementationClass = "AndroidApplicationConventionPlugin"
        }
        register("androidLibrary") {
            id = "clockblock.android.library"
            implementationClass = "AndroidLibraryConventionPlugin"
        }
        register("androidCompose") {
            id = "clockblock.android.compose"
            implementationClass = "AndroidComposeConventionPlugin"
        }
        register("androidFeature") {
            id = "clockblock.android.feature"
            implementationClass = "AndroidFeatureConventionPlugin"
        }
        register("jvmLibrary") {
            id = "clockblock.jvm.library"
            implementationClass = "JvmLibraryConventionPlugin"
        }
    }
}
