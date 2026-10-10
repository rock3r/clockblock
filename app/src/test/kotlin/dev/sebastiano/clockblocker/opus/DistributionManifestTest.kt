package dev.sebastiano.clockblocker.opus

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Exact alarms per distribution. The play flavour asks for SCHEDULE_EXACT_ALARM, which the user grants. The oss
 * flavour (builds outside Google Play) gets USE_EXACT_ALARM, granted at install. Play only allows USE_EXACT_ALARM for
 * alarm and calendar apps, so it must never reach the play build from any module's manifest.
 *
 * Unit tests run on the play variant only (see app/build.gradle.kts), so the merged manifest checked here is play's.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [37], application = TestApplication::class)
class DistributionManifestTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private val requested: List<String>
        get() = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
            .requestedPermissions.orEmpty().toList()

    @Test
    fun `the play build never asks for USE_EXACT_ALARM`() {
        requested shouldNotContain Manifest.permission.USE_EXACT_ALARM
        requested shouldContain Manifest.permission.SCHEDULE_EXACT_ALARM
    }

    @Test
    fun `the oss manifest swaps SCHEDULE_EXACT_ALARM for USE_EXACT_ALARM`() {
        val android = "http://schemas.android.com/apk/res/android"
        val tools = "http://schemas.android.com/tools"
        val manifest = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }.newDocumentBuilder()
            .parse(File("src/oss/AndroidManifest.xml")).documentElement
        val permissions = manifest.getElementsByTagName("uses-permission").let { nodes ->
            (0 until nodes.length).map { nodes.item(it) as Element }
        }
        val added = permissions.filter { it.getAttributeNS(tools, "node").isEmpty() }.map { it.getAttributeNS(android, "name") }
        val removed = permissions.filter { it.getAttributeNS(tools, "node") == "remove" }.map { it.getAttributeNS(android, "name") }
        added shouldBe listOf(Manifest.permission.USE_EXACT_ALARM)
        removed shouldBe listOf(Manifest.permission.SCHEDULE_EXACT_ALARM)
    }
}
