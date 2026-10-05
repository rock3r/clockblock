package dev.sebastiano.clockblocker.opus.feature.settings

import app.cash.turbine.test
import dev.sebastiano.clockblocker.opus.core.data.backup.BackupCodec
import dev.sebastiano.clockblocker.opus.core.data.backup.BackupManager
import dev.sebastiano.clockblocker.opus.core.data.backup.ImportMode
import dev.sebastiano.clockblocker.opus.core.data.demo.DemoData
import dev.sebastiano.clockblocker.opus.core.model.AppSettings
import dev.sebastiano.clockblocker.opus.core.model.Chronotype
import dev.sebastiano.clockblocker.opus.core.model.ThemeMode
import dev.sebastiano.clockblocker.opus.core.testing.FakeAdviceLogRepository
import dev.sebastiano.clockblocker.opus.core.testing.FakePlaceSearch
import dev.sebastiano.clockblocker.opus.core.testing.FakeProfileRepository
import dev.sebastiano.clockblocker.opus.core.testing.FakeSettingsRepository
import dev.sebastiano.clockblocker.opus.core.testing.FakeTripRepository
import dev.sebastiano.clockblocker.opus.core.testing.MainDispatcherRule
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContain as shouldContainElement
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import java.io.IOException
import java.time.Clock
import java.time.ZoneOffset

class SettingsViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val settings = FakeSettingsRepository()
    private val profiles = FakeProfileRepository.onboarded()
    private val trips = FakeTripRepository.withDemoTrips()
    private val logs = FakeAdviceLogRepository()
    private val permissions = FakeNotificationPermissions()
    private val clock = Clock.fixed(DemoData.Now, ZoneOffset.UTC)
    private val codec = BackupCodec()
    private val backup = BackupManager(profiles, settings, trips, logs, codec, clock)

    private fun viewModel() = SettingsViewModel(settings, profiles, permissions, FakePlaceSearch(), backup, codec, clock)

    @Test
    fun `state mirrors settings, profile and permissions`() = runTest {
        settings.set(AppSettings(themeMode = ThemeMode.Dark, reminderLeadMinutes = 30))
        permissions.current = FakeNotificationPermissions.AllGranted
        viewModel().state.test {
            val state = expectMostRecentItem()
            state.settings.themeMode shouldBe ThemeMode.Dark
            state.settings.reminderLeadMinutes shouldBe 30
            state.profile shouldBe DemoData.profile
            state.permissions shouldBe FakeNotificationPermissions.AllGranted
        }
    }

    @Test
    fun `appearance and reminder setters write through to the repository`() = runTest {
        val vm = viewModel()
        vm.setThemeMode(ThemeMode.Light)
        vm.setDynamicColor(false)
        vm.setReduceMotion(true)
        vm.setNightSafeAuto(false)
        vm.setRemindersEnabled(false)
        vm.setReminderLead(5)
        settings.current shouldBe AppSettings(
            themeMode = ThemeMode.Light,
            dynamicColor = false,
            reduceMotion = true,
            nightSafeAuto = false,
            remindersEnabled = false,
            reminderLeadMinutes = 5,
        )
    }

    @Test
    fun `opus mode can only be toggled once unlocked`() = runTest {
        val vm = viewModel()
        vm.setOpusModeEnabled(true)
        settings.current.opusModeEnabled.shouldBeFalse()
        settings.set(AppSettings(opusModeUnlocked = true))
        vm.setOpusModeEnabled(true)
        settings.current.opusModeEnabled.shouldBeTrue()
    }

    @Test
    fun `profile edits are saved immediately`() = runTest {
        val vm = viewModel()
        vm.updateProfile { it.copy(chronotype = Chronotype.DefiniteEvening) }
        profiles.current!!.chronotype shouldBe Chronotype.DefiniteEvening
    }

    @Test
    fun `home zone search finds places and picking one saves its zone`() = runTest {
        val vm = viewModel()
        vm.state.test {
            vm.searchHome("tokyo")
            val results = expectMostRecentItem().homeResults
            results.map { it.code } shouldContainElement "HND"
            vm.setHomePlace(results.first { it.code == "HND" })
            profiles.current!!.homeZoneId shouldBe "Asia/Tokyo"
            val cleared = expectMostRecentItem()
            cleared.homeQuery shouldBe ""
            cleared.homeResults.shouldBeEmpty()
        }
    }

    @Test
    fun `melatonin needs the safety note acknowledged first`() = runTest {
        val vm = viewModel()
        vm.state.test {
            vm.setMelatonin(true)
            profiles.current!!.useMelatonin.shouldBeFalse()
            vm.acknowledgeMelatoninNote(true)
            vm.setMelatonin(true)
            profiles.current!!.useMelatonin.shouldBeTrue()
            expectMostRecentItem().melatoninAcknowledged.shouldBeTrue()
        }
    }

    @Test
    fun `export writes the backup json and reports success`() = runTest {
        val vm = viewModel()
        vm.exportFileName() shouldBe "opus-clockblock-backup-2026-06-12.json"
        vm.events.test {
            var written: String? = null
            vm.export { written = it }
            awaitItem() shouldBe SettingsEvent.Exported
            written.shouldNotBeNull() shouldContain "\"format\": \"opus-clockblock\""
        }
    }

    @Test
    fun `a failed write reports an export failure`() = runTest {
        val vm = viewModel()
        vm.events.test {
            vm.export { throw IOException("disk full") }
            awaitItem() shouldBe SettingsEvent.ExportFailed
        }
    }

    @Test
    fun `import asks for confirmation, then replaces`() = runTest {
        val text = backup.export()
        trips.setTrips(emptyList())
        val vm = viewModel()
        vm.state.test {
            vm.prepareImport { text }
            val pending = expectMostRecentItem().pendingImport.shouldNotBeNull()
            pending.tripCount shouldBe DemoData.trips().size
            trips.current.shouldBeEmpty()

            vm.events.test {
                vm.confirmImport(ImportMode.Replace)
                awaitItem().shouldBeInstanceOf<SettingsEvent.Imported>().result.tripsImported shouldBe DemoData.trips().size
            }
            expectMostRecentItem().pendingImport.shouldBeNull()
            trips.current.size shouldBe DemoData.trips().size
        }
    }

    @Test
    fun `cancelling an import changes nothing`() = runTest {
        val text = backup.export()
        trips.setTrips(emptyList())
        val vm = viewModel()
        vm.state.test {
            vm.prepareImport { text }
            expectMostRecentItem().pendingImport.shouldNotBeNull()
            vm.cancelImport()
            expectMostRecentItem().pendingImport.shouldBeNull()
        }
        trips.current.shouldBeEmpty()
    }

    @Test
    fun `a file that is not a backup is refused with a reason`() = runTest {
        val vm = viewModel()
        vm.events.test {
            vm.prepareImport { "{\"hello\": 1}" }
            awaitItem() shouldBe SettingsEvent.ImportFailed(ImportFailure.NotABackup)
            vm.prepareImport { "not json at all" }
            awaitItem() shouldBe SettingsEvent.ImportFailed(ImportFailure.NotABackup)
            vm.prepareImport { "{\"format\": \"opus-clockblock\", \"version\": 99, \"exportedAt\": \"2026-01-01T00:00:00Z\"}" }
            awaitItem() shouldBe SettingsEvent.ImportFailed(ImportFailure.NewerVersion)
            vm.prepareImport { throw IOException("gone") }
            awaitItem() shouldBe SettingsEvent.ImportFailed(ImportFailure.Unreadable)
        }
    }

    @Test
    fun `test reminder reports whether it could be sent`() = runTest {
        val vm = viewModel()
        vm.events.test {
            vm.sendTestReminder()
            awaitItem() shouldBe SettingsEvent.TestReminderBlocked
            permissions.current = FakeNotificationPermissions.AllGranted
            vm.sendTestReminder()
            awaitItem() shouldBe SettingsEvent.TestReminderSent
        }
        permissions.testReminders shouldBe 2
    }

    @Test
    fun `refreshing picks up permission changes made in system settings`() = runTest {
        val vm = viewModel()
        vm.state.test {
            expectMostRecentItem().permissions.notificationsGranted.shouldBeFalse()
            permissions.current = FakeNotificationPermissions.AllGranted
            vm.refreshPermissions()
            expectMostRecentItem().permissions.notificationsGranted.shouldBeTrue()
        }
    }
}
