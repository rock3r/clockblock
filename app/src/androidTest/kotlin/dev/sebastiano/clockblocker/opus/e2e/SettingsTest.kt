package dev.sebastiano.clockblocker.opus.e2e

import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.isOn
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.sebastiano.clockblocker.opus.core.model.AppSettings
import dev.sebastiano.clockblocker.opus.core.model.ThemeMode
import dev.sebastiano.clockblocker.opus.feature.onboarding.OnboardingStep
import dev.sebastiano.clockblocker.opus.feature.onboarding.OnboardingTags
import dev.sebastiano.clockblocker.opus.feature.settings.AboutTags
import dev.sebastiano.clockblocker.opus.feature.settings.SettingsTags
import dev.sebastiano.clockblocker.opus.shell.ShellTestTags
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Settings: theme, the Opus-mode easter egg and replaying onboarding. */
@RunWith(AndroidJUnit4::class)
class SettingsTest : OpusE2eTest() {

    @Test
    fun darkThemeRepaintsTheApp() {
        seedOnboarded()
        launch()
        awaitTag(ShellTestTags.NavSettings, LongTimeoutMillis).performClick()
        awaitTag("route_settings")

        selectTheme(ThemeMode.Light)
        val light = meanLuminance()
        selectTheme(ThemeMode.Dark)
        val dark = meanLuminance()

        assertTrue("light theme should be bright (mean luminance $light)", light > 0.6)
        assertTrue("dark theme should be dark (mean luminance $dark)", dark < 0.3)
        assertTrue(settings().themeMode == ThemeMode.Dark)
    }

    @Test
    fun sevenTapsOnVersionUnlockOpusMode() {
        seedOnboarded()
        launch()
        awaitTag(ShellTestTags.NavSettings, LongTimeoutMillis).performClick()
        awaitTag(SettingsTags.About).scrollToIfScrollable().performClick()
        awaitTag("route_about")

        val version = awaitTag(AboutTags.Version).scrollToIfScrollable()
        repeat(7) { version.performClick() }

        awaitTag(AboutTags.OpusTitleCard).assertIsDisplayed()
        compose.waitUntil(DefaultTimeoutMillis) { settings().opusModeUnlocked }
    }

    @Test
    fun replayOnboardingFromSettingsReturnsToSettings() {
        seedOnboarded()
        launch()
        awaitTag(ShellTestTags.NavSettings, LongTimeoutMillis).performClick()
        awaitTag(SettingsTags.ReplayOnboarding).scrollToIfScrollable().performClick()

        awaitTag("route_onboarding").assertIsDisplayed()
        awaitTag(OnboardingTags.GetStarted).performClick()
        awaitTag(OnboardingTags.Skip).performClick()
        awaitTag(OnboardingTags.step(OnboardingStep.Reminders))
        awaitTag(OnboardingTags.MaybeLater).performClick()

        awaitGone("route_onboarding", LongTimeoutMillis)
        awaitTag("route_settings").assertIsDisplayed()
    }

    private fun selectTheme(mode: ThemeMode) {
        awaitTag(SettingsTags.theme(mode)).scrollToIfScrollable().performClick()
        compose.waitUntil(DefaultTimeoutMillis) { settings().themeMode == mode }
        awaitTag(SettingsTags.theme(mode)).assert(isOn() or isSelected())
        compose.waitForIdle()
    }

    /** Mean relative luminance (0–1) of the settings screen, sampled on a grid. */
    private fun meanLuminance(): Double {
        val pixels = awaitTag("route_settings").captureToImage().toPixelMap()
        var sum = 0.0
        var count = 0
        for (x in 0 until pixels.width step 16) {
            for (y in 0 until pixels.height step 16) {
                val c = pixels[x, y]
                sum += 0.2126 * c.red + 0.7152 * c.green + 0.0722 * c.blue
                count++
            }
        }
        return sum / count
    }

    private fun settings(): AppSettings = runBlocking { graph.settingsRepository.settings.first() }
}
