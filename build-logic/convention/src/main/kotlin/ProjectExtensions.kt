import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.KotlinProjectExtension

internal val Project.libs: VersionCatalog
    get() = extensions.getByType<VersionCatalogsExtension>().named("libs")

internal fun VersionCatalog.version(alias: String): String =
    findVersion(alias).orElseThrow { IllegalStateException("No version '$alias' in catalog") }.requiredVersion

internal fun VersionCatalog.lib(alias: String) =
    findLibrary(alias).orElseThrow { IllegalStateException("No library '$alias' in catalog") }

/** `:feature:plan` -> `dev.sebastiano.clockblocker.opus.feature.plan` */
internal val Project.derivedNamespace: String
    get() = "dev.sebastiano.clockblocker.opus" + path.replace(':', '.').replace("-", "")

internal fun Project.configureKotlinToolchain() {
    extensions.findByType(KotlinProjectExtension::class.java)?.apply {
        jvmToolchain(libs.version("jvmToolchain").toInt())
    }
}

/**
 * All modules run tests on the JUnit Platform: Jupiter for pure logic (JUnit 6 + Kotest assertions), and the
 * Vintage engine for Robolectric/Compose UI tests, which are still JUnit 4 based.
 */
internal fun Project.configureUnitTests(includeVintage: Boolean) {
    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
        maxParallelForks = (Runtime.getRuntime().availableProcessors() / 2).coerceAtLeast(1)
        systemProperty("user.timezone", "UTC")
        jvmArgs(
            "--add-exports=java.base/jdk.internal.access=ALL-UNNAMED",
            "--add-opens=java.base/jdk.internal.access=ALL-UNNAMED",
            "--add-opens=java.base/java.io=ALL-UNNAMED",
        )
        testLogging {
            events("failed")
            exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        }
    }
    dependencies {
        add("testImplementation", platform(libs.lib("junit-bom")))
        add("testImplementation", libs.lib("junit-jupiter"))
        add("testImplementation", libs.lib("junit-jupiter-params"))
        add("testImplementation", libs.lib("kotest-assertions-core"))
        add("testImplementation", libs.lib("kotest-property"))
        add("testImplementation", libs.lib("kotlinx-coroutines-test"))
        add("testImplementation", libs.lib("turbine"))
        add("testRuntimeOnly", libs.lib("junit-platform-launcher"))
        if (includeVintage) {
            add("testImplementation", libs.lib("junit4"))
            add("testRuntimeOnly", libs.lib("junit-vintage-engine"))
        }
    }
}
