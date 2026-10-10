package dev.sebastiano.clockblocker.opus.e2e

import android.app.Activity
import android.app.Instrumentation.ActivityResult
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.test.performClick
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.Intents.intending
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.sebastiano.clockblocker.opus.core.data.backup.Backup
import dev.sebastiano.clockblocker.opus.core.data.backup.BackupCodec
import dev.sebastiano.clockblocker.opus.core.data.demo.DemoData
import dev.sebastiano.clockblocker.opus.feature.settings.SettingsTags
import dev.sebastiano.clockblocker.opus.shell.ShellTestTags
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.Instant
import java.time.LocalDate

/**
 * Importing a backup from Settings. The system document picker is stubbed (Espresso-Intents) to return a backup
 * file the test wrote, so the dialog and the import itself run for real without driving DocumentsUI.
 */
@RunWith(AndroidJUnit4::class)
class BackupImportTest : ClockblockE2eTest() {
    private val today = LocalDate.now()
    private val onDevice = DemoData.sfoToLhr(today.plusDays(10), id = "e2e-on-device")
    private val inBackup = DemoData.lhrToSydneyViaSingapore(today.plusDays(20), id = "e2e-in-backup")

    @Before
    fun stubDocumentPicker() {
        Intents.init()
        val file = File(context.cacheDir, "e2e-backup.json")
        file.writeText(BackupCodec().encode(Backup(exportedAt = Instant.now(), profile = DemoData.profile, trips = listOf(inBackup))))
        intending(hasAction(Intent.ACTION_OPEN_DOCUMENT))
            .respondWith(ActivityResult(Activity.RESULT_OK, Intent().setData(Uri.fromFile(file))))
    }

    @After
    fun releaseDocumentPicker() {
        Intents.release()
    }

    @Test
    fun mergeIsTheDefaultAndKeepsWhatIsOnTheDevice() {
        openImportDialog()

        // Merge is the dialog's confirm action: the filled button at the end, after the destructive Replace.
        val merge = awaitTag(SettingsTags.ImportMerge).fetchSemanticsNode().boundsInRoot
        val replace = awaitTag(SettingsTags.ImportReplace).fetchSemanticsNode().boundsInRoot
        assertTrue("Merge ($merge) should come after Replace ($replace)", merge.left >= replace.right || merge.top >= replace.bottom)

        awaitTag(SettingsTags.ImportMerge).performClick()
        awaitGone(SettingsTags.ImportMerge)
        compose.waitUntil("both trips after a merge", DefaultTimeoutMillis) { tripIds() == setOf(onDevice.id, inBackup.id) }
    }

    @Test
    fun replaceSwapsTheDeviceTripsForTheBackups() {
        openImportDialog()

        awaitTag(SettingsTags.ImportReplace).performClick()
        awaitGone(SettingsTags.ImportReplace)
        compose.waitUntil("only the backup's trip after a replace", DefaultTimeoutMillis) { tripIds() == setOf(inBackup.id) }
    }

    private fun openImportDialog() {
        seedOnboarded()
        seedTrip(onDevice)
        launch()
        awaitTag(ShellTestTags.NavSettings, LongTimeoutMillis).performClick()
        awaitTag(SettingsTags.Import).scrollToIfScrollable().performClick()
        awaitTag(SettingsTags.ImportMerge, LongTimeoutMillis)
    }

    private fun tripIds(): Set<String> = runBlocking { graph.tripRepository.trips.first() }.map { it.id }.toSet()
}
