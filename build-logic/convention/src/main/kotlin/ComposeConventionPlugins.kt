import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies
import org.jetbrains.kotlin.compose.compiler.gradle.ComposeCompilerGradlePluginExtension

/**
 * Enables Compose on an Android module (library or application) using the Compose *alpha* BOM, which is the
 * only line shipping public Material 3 Expressive APIs. Also wires Roborazzi screenshot tests.
 */
class AndroidComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("org.jetbrains.kotlin.plugin.compose")
        pluginManager.apply("io.github.takahirom.roborazzi")

        extensions.findByType(LibraryExtension::class.java)?.apply { buildFeatures { compose = true } }
        extensions.findByType(ApplicationExtension::class.java)?.apply { buildFeatures { compose = true } }

        extensions.configure(io.github.takahirom.roborazzi.RoborazziExtension::class.java) {
            // Goldens are committed next to the tests that produce them.
            outputDir.set(layout.projectDirectory.dir("src/test/screenshots"))
        }

        extensions.configure(ComposeCompilerGradlePluginExtension::class.java) {
            val stabilityFile = rootProject.layout.projectDirectory.file("compose_stability.conf")
            stabilityConfigurationFiles.add(stabilityFile)
        }

        dependencies {
            val bom = libs.lib("androidx-compose-bom-alpha")
            add("implementation", platform(bom))
            add("testImplementation", platform(bom))
            add("androidTestImplementation", platform(bom))
            add("implementation", libs.lib("androidx-compose-ui"))
            add("implementation", libs.lib("androidx-compose-ui-graphics"))
            add("implementation", libs.lib("androidx-compose-foundation"))
            add("implementation", libs.lib("androidx-compose-animation"))
            add("implementation", libs.lib("androidx-compose-material3"))
            add("implementation", libs.lib("androidx-compose-ui-tooling-preview"))
            add("debugImplementation", libs.lib("androidx-compose-ui-tooling"))
            add("debugImplementation", libs.lib("androidx-compose-ui-test-manifest"))
            add("testImplementation", libs.lib("androidx-compose-ui-test-junit4"))
            add("testImplementation", libs.lib("roborazzi"))
            add("testImplementation", libs.lib("roborazzi-compose"))
            add("testImplementation", libs.lib("roborazzi-junit-rule"))
            add("androidTestImplementation", libs.lib("androidx-compose-ui-test-junit4"))
        }
    }
}

/** A feature module: Android library + Compose + the core modules every screen needs. */
class AndroidFeatureConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("opus.android.library")
        pluginManager.apply("opus.android.compose")
        pluginManager.apply("dev.zacsweers.metro")
        dependencies {
            add("implementation", project(":core:model"))
            add("implementation", project(":core:designsystem"))
            add("implementation", project(":core:data"))
            add("implementation", libs.lib("metrox-viewmodel-compose"))
            add("implementation", libs.lib("androidx-lifecycle-runtime-compose"))
            add("implementation", libs.lib("androidx-lifecycle-viewmodel-compose"))
            add("implementation", libs.lib("androidx-navigation3-runtime"))
            add("implementation", libs.lib("kotlinx-collections-immutable"))
            add("implementation", libs.lib("kotlinx-coroutines-android"))
            add("testImplementation", project(":core:testing"))
        }
    }
}
