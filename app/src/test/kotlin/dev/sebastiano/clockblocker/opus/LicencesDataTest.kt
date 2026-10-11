package dev.sebastiano.clockblocker.opus

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.mikepenz.aboutlibraries.Libs
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.string.shouldContain
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The licences the AboutLibraries plugin generates at build time (`res/raw/aboutlibraries.json`, shown by the licences
 * screen). It must carry our own entries from `app/config/aboutlibraries` (what the dependency graph can't show), and
 * every entry needs a licence with its full text.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [37], application = TestApplication::class)
class LicencesDataTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private val libs: Libs by lazy {
        val json = context.resources.openRawResource(R.raw.aboutlibraries).bufferedReader().use { it.readText() }
        Libs.Builder().withJson(json).build()
    }

    @Test
    fun `our own entries are listed`() {
        libs.libraries.map { it.uniqueId } shouldContainAll listOf(
            "dev.sebastiano.clockblocker:clockblock",
            "des.c5inco.torph:torph-compose",
            "dev.c5inco.m3evibes:prime-meridian",
            "font:google-sans-flex",
            "font:fraunces",
            "icons:material-symbols-rounded",
            "data:ourairports",
            "data:mwgg-airports",
            "data:iana-tzdb",
            "data:natural-earth",
        )
    }

    @Test
    fun `dependencies the app is built with are listed`() {
        libs.libraries.map { it.uniqueId } shouldContainAll listOf(
            "androidx.compose.material3:material3",
            "dev.zacsweers.metro:runtime",
            "com.mikepenz:aboutlibraries-core",
        )
    }

    @Test
    fun `every entry has a licence with its text`() {
        libs.libraries.filter { it.licenses.isEmpty() }.map { it.uniqueId }.shouldBeEmpty()
        libs.licenses.filter { it.licenseContent.isNullOrBlank() }.map { it.name }.shouldBeEmpty()
        libs.licenses.single { it.spdxId == "OFL-1.1" || it.hash == "OFL-1.1" }.licenseContent.orEmpty() shouldContain
            "SIL OPEN FONT LICENSE"
    }
}
