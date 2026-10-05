package dev.sebastiano.clockblocker.opus.feature.settings

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import dev.sebastiano.clockblocker.opus.core.data.backup.ImportMode
import dev.sebastiano.clockblocker.opus.core.data.demo.DemoData
import dev.sebastiano.clockblocker.opus.core.designsystem.theme.OpusTheme
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
}

/** The stateless Settings UI forwards each interaction to [SettingsActions]. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w400dp-h860dp-xxhdpi")
class SettingsContentTest {
    @get:Rule
    val compose = createComposeRule()

    private val actions = RecordingSettingsActions()

    private fun show(state: SettingsUiState = settingsState()) {
        compose.setContent {
            OpusTheme(dynamicColor = false, reduceMotion = true) {
                SettingsContent(state, actions, onBack = null, modifier = Modifier.fillMaxSize(), dynamicColorSupported = true, now = DemoData.Now)
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
@Config(sdk = [36], qualifiers = "w400dp-h860dp-xxhdpi")
class AboutContentTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `version taps, source and licences forward`() {
        val calls = mutableListOf<String>()
        compose.setContent {
            OpusTheme(dynamicColor = false, reduceMotion = true) {
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
    fun `title card dismisses`() {
        var dismissed = false
        compose.setContent {
            OpusTheme(dynamicColor = false, reduceMotion = true) { OpusTitleCard(onDismiss = { dismissed = true }) }
        }
        compose.onNodeWithTag(AboutTags.OpusTitleDismiss).performClick()
        dismissed shouldBe true
    }

    @Test
    fun `title card dialog plays its exit before it leaves`() {
        var visible by mutableStateOf(true)
        compose.setContent {
            OpusTheme(dynamicColor = false, reduceMotion = false) {
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
