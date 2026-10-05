import org.gradle.api.Plugin
import org.gradle.api.Project

/** Pure Kotlin/JVM module (e.g. `:core:model`, `:core:circadian`). No Android, millisecond tests. */
class JvmLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("org.jetbrains.kotlin.jvm")
        configureKotlinToolchain()
        configureUnitTests(includeVintage = false)
    }
}
