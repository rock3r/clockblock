package dev.sebastiano.clockblocker.opus.feature.settings

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import dev.sebastiano.clockblocker.opus.core.data.PinnableWidget
import dev.sebastiano.clockblocker.opus.core.data.backup.ImportMode
import dev.sebastiano.clockblocker.opus.core.data.demo.DemoData
import dev.sebastiano.clockblocker.opus.core.designsystem.theme.ClockblockTheme
import dev.sebastiano.clockblocker.opus.core.model.AppSettings
import dev.sebastiano.clockblocker.opus.core.model.Intensity
import dev.sebastiano.clockblocker.opus.core.model.Place
import dev.sebastiano.clockblocker.opus.core.model.ThemeMode
import dev.sebastiano.clockblocker.opus.core.model.UserProfile
import dev.sebastiano.clockblocker.opus.feature.onboarding.profile.ToolsTags
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import kotlinx.collections.immutable.persistentListOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Instant

private class RecordingSettingsActions : SettingsActions {
    val calls = mutableListOf<String>()
    var profile: UserProfile = DemoData.profile

    override fun updateProfile(transform: (UserProfile) -> UserProfile) {
        profile = transform(profile)
        calls += "profile"
    }
    override fun searchHome(query: String) { calls += "search:$query" }
    override fun selectHome(place: Place) { calls += "home:${place.code}" }
    override fun clearHomeSearch() { calls += "clearHome" }
    override fun setMelatonin(enabled: Boolean) { calls += "melatonin:$enabled" }
    override fun acknowledgeMelatonin(acknowledged: Boolean) { calls += "ack:$acknowledged" }
    override fun setThemeMode(mode: ThemeMode) { calls += "theme:$mode" }
    override fun setDynamicColor(enabled: Boolean) { calls += "dynamic:$enabled" }
    override fun setReduceMotion(enabled: Boolean) { calls += "reduce:$enabled" }
    override fun setNightSafeAuto(enabled: Boolean) { calls += "night:$enabled" }
    override fun setOpusModeEnabled(enabled: Boolean) { calls += "opus:$enabled" }
    override fun setRemindersEnabled(enabled: Boolean) { calls += "reminders:$enabled" }
    override fun setReminderLead(minutes: Int) { calls += "lead:$minutes" }
    override fun fixNotifications() { calls += "fix:notifications" }
    override fun fixExactAlarms() { calls += "fix:exact" }
    override fun fixLiveUpdates() { calls += "fix:live" }
    override fun fixBattery() { calls += "fix:battery" }
    override fun sendTestReminder() { calls += "test" }
    override fun export() { calls += "export" }
    override fun import() { calls += "import" }
    override fun confirmImport(mode: ImportMode) { calls += "confirm:$mode" }
    override fun cancelImport() { calls += "cancelImport" }
    override fun replayOnboarding() { calls += "replay" }
    override fun openAbout() { calls += "about" }
    override fun setHideLockScreenDetails(enabled: Boolean) { calls += "lock:$enabled" }
    override fun pinWidget(widget: PinnableWidget) { calls += "pin:$widget" }
}

/** The stateless Settings UI forwards each interaction to [SettingsActions]. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [37], qualifiers = "w400dp-h860dp-xxhdpi")
class SettingsContentTest {
    @get:Rule
    val compose = createComposeRule()

    private val actions = RecordingSettingsActions()

    private fun show(state: SettingsUiState = settingsState(), extraSection: (@Composable () -> Unit)? = null) {
        android.provider.Settings.System.putString(
            androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>().contentResolver,
            android.provider.Settings.System.TIME_12_24,
            "24",
        )
        compose.setContent {
            ClockblockTheme(dynamicColor = false, reduceMotion = true) {
                SettingsContent(
                    state,
                    actions,
                    onBack = null,
                    modifier = Modifier.fillMaxSize(),
                    now = DemoData.Now,
                    extraSection = extraSection,
                )
            }
        }
    }

    private fun click(tag: String) {
        compose.onNodeWithTag(tag).performScrollTo().performClick()
        compose.waitForIdle()
    }

    @Test
    fun `appearance controls forward their values`() {
        show()
        click(SettingsTags.theme(ThemeMode.Dark))
        click(SettingsTags.DynamicColor)
        click(SettingsTags.ReduceMotion)
        click(SettingsTags.NightSafe)
        click(SettingsTags.OpusMode)
        actions.calls shouldContainExactly listOf("theme:Dark", "dynamic:false", "reduce:true", "night:false", "opus:true")
    }

    @Test
    fun `opus mode toggle only exists once unlocked`() {
        show(settingsState(settings = AppSettings(opusModeUnlocked = false)))
        compose.onNodeWithTag(SettingsTags.OpusMode).assertDoesNotExist()
    }

    @Test
    fun `reminder controls forward lead, test and permission fixes`() {
        show(settingsState(notificationsGranted = false))
        click(SettingsTags.lead(30))
        click(SettingsTags.TestReminder)
        click("${SettingsTags.PermNotifications}_fix")
        click("${SettingsTags.PermExact}_fix")
        click("${SettingsTags.PermBattery}_fix")
        click(SettingsTags.RemindersEnabled)
        actions.calls shouldContainExactly listOf("lead:30", "test", "fix:notifications", "fix:exact", "fix:battery", "reminders:false")
    }

    @Test
    fun `lock screen switch forwards`() {
        show()
        click(SettingsTags.LockScreen)
        actions.calls shouldContainExactly listOf("lock:true")
    }

    @Test
    fun `permission details start collapsed when reminders are dependable and expand on tap`() {
        show(settingsState(exactAlarmsAllowed = true))
        compose.onNodeWithTag(SettingsTags.PermSummary).performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag(SettingsTags.PermBattery).assertDoesNotExist()
        compose.onNodeWithText("Reminders are ready").assertIsDisplayed()
        click(SettingsTags.PermSummary)
        click("${SettingsTags.PermBattery}_fix")
        actions.calls shouldContainExactly listOf("fix:battery")
    }

    @Test
    fun `permission details open by themselves when something needs attention`() {
        show(settingsState(exactAlarmsAllowed = false))
        compose.onNodeWithText("1 permission needs attention").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("${SettingsTags.PermExact}_fix").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `summary says everything is allowed when it is`() {
        show(settingsState(exactAlarmsAllowed = true, batteryOptimizationIgnored = true))
        compose.onNodeWithText("Everything is allowed").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `widget add buttons forward the widget`() {
        show()
        click(SettingsTags.pinWidget(PinnableWidget.TwoClocks))
        click(SettingsTags.pinWidget(PinnableWidget.NextUp))
        click(SettingsTags.pinWidget(PinnableWidget.RouteGlobe))
        actions.calls shouldContainExactly listOf("pin:TwoClocks", "pin:NextUp", "pin:RouteGlobe")
    }

    @Test
    fun `widgets card is hidden when the launcher cannot pin`() {
        show(settingsState(widgetPinningSupported = false))
        compose.onNodeWithTag(SettingsTags.pinWidget(PinnableWidget.TwoClocks)).assertDoesNotExist()
    }

    @Test
    fun `lead choice is disabled while reminders are off`() {
        show(settingsState(settings = AppSettings(remindersEnabled = false)))
        compose.onNodeWithTag(SettingsTags.lead(5)).performScrollTo().assertIsNotEnabled()
        compose.onNodeWithTag(SettingsTags.PermNotifications).assertDoesNotExist()
    }

    @Test
    fun `melatonin stays off until the safety note is acknowledged`() {
        show()
        click(ToolsTags.Melatonin)
        actions.calls shouldNotContain "melatonin:true"
        // The refused switch opens the safety note instead.
        click(ToolsTags.MelatoninAck)
        actions.calls shouldContain "ack:true"
    }

    @Test
    fun `melatonin switches on once acknowledged`() {
        show(settingsState().copy(melatoninAcknowledged = true))
        click(ToolsTags.Melatonin)
        actions.calls shouldContain "melatonin:true"
    }

    @Test
    fun `effort and tools update the profile`() {
        show()
        click(ToolsTags.effort(Intensity.Max))
        actions.profile.intensity shouldBe Intensity.Max
        click(ToolsTags.Caffeine)
        actions.profile.useCaffeine shouldBe false
        click(ToolsTags.Naps)
        actions.profile.napsOk shouldBe false
        click(ToolsTags.headStart(2))
        actions.profile.headStartDays shouldBe 2
        click(ToolsTags.headStart(0))
        actions.profile shouldBe DemoData.profile.copy(
            intensity = Intensity.Max,
            useCaffeine = false,
            napsOk = false,
            headStartDays = 0,
        )
    }

    @Test
    fun `you card shows chronotype, sleep, and lowest point, and edits sleep and chronotype`() {
        show()
        compose.onNodeWithTag(SettingsTags.You).assertIsDisplayed()
        compose.onNodeWithText("Somewhere in between", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Usual sleep · 23:00–07:00 (8 h)", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Body's lowest point around 04:00", substring = true).assertIsDisplayed()

        click(SettingsTags.You)
        compose.onNodeWithTag(SettingsTags.Chronotype).performClick()
        compose.onNodeWithTag(dev.sebastiano.clockblocker.opus.feature.onboarding.profile.ChronotypeTags.option(dev.sebastiano.clockblocker.opus.core.model.Chronotype.DefiniteEvening))
            .performScrollTo()
            .performClick()
        compose.onNodeWithTag(SettingsTags.EditorSave).performClick()
        actions.profile.chronotype shouldBe dev.sebastiano.clockblocker.opus.core.model.Chronotype.DefiniteEvening
    }

    @Test
    fun `you card shows both schedules when days off sleep differs`() {
        val withDaysOff = DemoData.profile.copy(
            daysOffSleep = dev.sebastiano.clockblocker.opus.core.model.SleepWindow(
                java.time.LocalTime.of(1, 0),
                java.time.LocalTime.of(9, 0),
            ),
        )
        show(settingsState(profile = withDaysOff))
        compose.onNodeWithText("Usual sleep · 23:00–07:00 (8 h) · Days off 01:00–09:00", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Body's lowest point around 04:00", substring = true).assertIsDisplayed()
    }

    @Test
    fun `data and more rows forward`() {
        show()
        click(SettingsTags.Export)
        click(SettingsTags.Import)
        click(SettingsTags.ReplayOnboarding)
        click(SettingsTags.About)
        actions.calls shouldContainExactly listOf("export", "import", "replay", "about")
    }

    @Test
    fun `an extra section from the app closes the list and its row opens`() {
        var opened = 0
        show(
            extraSection = {
                SettingsLinkSection(header = "Extra", title = "Extra row", supporting = "Opens something", tag = "extra") {
                    opened++
                }
            },
        )
        compose.onNodeWithText("Extra row").performScrollTo().assertIsDisplayed()
        click("extra")
        opened shouldBe 1
    }

    @Test
    fun `data rows are disabled while working`() {
        show(settingsState().copy(isWorking = true))
        compose.onNodeWithTag(SettingsTags.Export).performScrollTo().assertIsNotEnabled()
    }

    @Test
    fun `pending import offers replace and merge`() {
        val pending = PendingImport(tripCount = 2, exportedAt = Instant.parse("2026-06-01T09:30:00Z"), hasProfile = true)
        show(settingsState().copy(pendingImport = pending))
        compose.onNodeWithTag(SettingsTags.ImportMerge).performClick()
        compose.onNodeWithTag(SettingsTags.ImportReplace).performClick()
        actions.calls shouldContainExactly listOf("confirm:Merge", "confirm:Replace")
    }

    @Test
    fun `home row opens the search editor`() {
        show(settingsState().copy(homeQuery = "tok", homeResults = persistentListOf(DemoData.HND)))
        click(SettingsTags.Home)
        compose.onNodeWithTag(SettingsTags.homeResult(DemoData.HND)).performClick()
        actions.calls shouldContain "home:HND"
    }
}

/** Version taps reach the callback; the About screen links forward. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [37], qualifiers = "w400dp-h860dp-xxhdpi")
class AboutContentTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `version taps, source and licences forward`() {
        val calls = mutableListOf<String>()
        compose.setContent {
            ClockblockTheme(dynamicColor = false, reduceMotion = true) {
                AboutContent(
                    versionName = "1.0.0",
                    onVersionTap = { calls += "version" },
                    onOpenSource = { calls += "source" },
                    onOpenLicenses = { calls += "licences" },
                    onBack = { calls += "back" },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        repeat(3) { compose.onNodeWithTag(AboutTags.Version).performScrollTo().performClick() }
        compose.onNodeWithTag(AboutTags.Source).performScrollTo().performClick()
        compose.onNodeWithTag(AboutTags.Licenses).performScrollTo().performClick()
        calls shouldContainExactly listOf("version", "version", "version", "source", "licences")
    }

    @Test
    fun `licences lead with the app, then list everything else in order`() {
        val ordered = FixtureLibraries.appFirst().libraries
        ordered.first().uniqueId shouldBe "dev.sebastiano.clockblocker:clockblock"
        ordered.drop(1) shouldContainExactly FixtureLibraries.libraries.filterNot { it.tag == AppLicenseTag }
    }

    @Test
    fun `a licence entry opens its text, and back forwards`() {
        var backs = 0
        compose.setContent {
            ClockblockTheme(dynamicColor = false, reduceMotion = true) {
                LicensesScreen(libraries = FixtureLibraries, onBack = { backs++ }, modifier = Modifier.fillMaxSize())
            }
        }
        compose.onNodeWithTag(AboutTags.LicensesList).performScrollToNode(hasText("Torph for Jetpack Compose"))
        compose.onNodeWithText("Torph for Jetpack Compose").performClick()
        compose.onNodeWithText("Permission is hereby granted", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Close").performClick()
        compose.onNodeWithText("Permission is hereby granted", substring = true).assertDoesNotExist()
        compose.onNodeWithContentDescription("Back").performClick()
        backs shouldBe 1
    }

    @Test
    fun `title card dismisses`() {
        var dismissed = false
        compose.setContent {
            ClockblockTheme(dynamicColor = false, reduceMotion = true) { OpusTitleCard(onDismiss = { dismissed = true }) }
        }
        compose.onNodeWithTag(AboutTags.OpusTitleDismiss).performClick()
        dismissed shouldBe true
    }

    @Test
    fun `title card dialog plays its exit before it leaves`() {
        var visible by mutableStateOf(true)
        compose.setContent {
            ClockblockTheme(dynamicColor = false, reduceMotion = false) {
                OpusTitleCardDialog(visible = visible, onDismiss = { visible = false })
            }
        }
        compose.onNodeWithTag(AboutTags.OpusTitleCard).assertIsDisplayed()

        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag(AboutTags.OpusTitleDismiss).performClick()
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeByFrame()
        // Still composed mid-exit: the dialog waits for the fade-out instead of vanishing with the state.
        compose.onNodeWithTag(AboutTags.OpusTitleCard).assertExists()

        compose.mainClock.advanceTimeBy(2_000)
        compose.waitForIdle()
        compose.onNodeWithTag(AboutTags.OpusTitleCard).assertDoesNotExist()
    }
}
