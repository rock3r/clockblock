package dev.sebastiano.clockblocker.opus.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable
import dev.sebastiano.clockblocker.opus.core.model.SleepWindow
import io.kotest.matchers.shouldBe
import dev.sebastiano.clockblocker.opus.core.data.PinnableWidget
import dev.sebastiano.clockblocker.opus.core.data.demo.DemoData
import dev.sebastiano.clockblocker.opus.core.model.AppSettings
import dev.sebastiano.clockblocker.opus.core.model.Chronotype
import dev.sebastiano.clockblocker.opus.core.model.UserProfile
import dev.sebastiano.clockblocker.opus.core.notifications.NotificationPermissionState
import kotlinx.collections.immutable.persistentListOf
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Instant
import java.time.ZoneOffset

internal fun settingsState(
    profile: UserProfile? = DemoData.profile,
    settings: AppSettings = AppSettings(opusModeUnlocked = true),
    notificationsGranted: Boolean = true,
    exactAlarmsAllowed: Boolean = false,
    promotedAllowed: Boolean = true,
    batteryOptimizationIgnored: Boolean = false,
    widgetPinningSupported: Boolean = true,
) = SettingsUiState(
    settings = settings,
    profile = profile,
    permissions = NotificationPermissionState(
        notificationsGranted = notificationsGranted,
        exactAlarmsAllowed = exactAlarmsAllowed,
        promotedAllowed = promotedAllowed,
        batteryOptimizationIgnored = batteryOptimizationIgnored,
    ),
    widgetPinningSupported = widgetPinningSupported,
)

/** The whole Settings list on a tall phone (so one golden shows every section), plus dark and large-font variants. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [37], qualifiers = "w400dp-h3300dp-xxhdpi")
class SettingsScreenshotTest : ClockblockScreenshotTest() {
    private fun settings(name: String, state: SettingsUiState = settingsState(), dark: Boolean = false, fontScale: Float? = null) =
        snap(name, darkTheme = dark, fontScale = fontScale) {
            SettingsContent(state, SettingsActions.None, onBack = {}, modifier = Modifier.fillMaxSize(), now = DemoData.Now)
        }

    @Test fun full() = settings("settings_full")

    @Test fun fullDark() = settings("settings_full_dark", dark = true)

    @Test fun fullFontScale13() = settings("settings_full_fontscale_1_3", fontScale = 1.3f)

    @Test fun fullFontScale() = settings("settings_full_fontscale_1_5", fontScale = 1.5f)

    @Test fun beforeOnboarding() = settings(
        "settings_no_profile",
        settingsState(profile = null, settings = AppSettings(remindersEnabled = false), notificationsGranted = false),
    )

    @Test fun allPermissionsGranted() = settings(
        "settings_permissions_ok",
        settingsState(notificationsGranted = true, exactAlarmsAllowed = true).let {
            it.copy(permissions = it.permissions.copy(batteryOptimizationIgnored = true))
        },
    )

    @Test fun permissionsReadyWithOptionalExtrasOff() = settings(
        "settings_permissions_ready_dark",
        settingsState(exactAlarmsAllowed = true, promotedAllowed = false),
        dark = true,
    )
}

/** Both Settings columns in full on a tall expanded window (sections split into two balanced columns). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [37], qualifiers = "w1280dp-h1650dp-xhdpi")
class SettingsTwoPaneScreenshotTest : ClockblockScreenshotTest() {
    @Test fun full() = snap("settings_two_pane_full") {
        SettingsContent(settingsState(), SettingsActions.None, onBack = {}, modifier = Modifier.fillMaxSize(), now = DemoData.Now)
    }
}

/** Settings at phone height (top of the list) and the focused editors. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [37], qualifiers = "w400dp-h860dp-xxhdpi")
class SettingsPhoneScreenshotTest : ClockblockScreenshotTest() {
    @Test fun top() = snap("settings_phone") {
        SettingsContent(settingsState(), SettingsActions.None, onBack = null, modifier = Modifier.fillMaxSize(), now = DemoData.Now)
    }

    @Test fun topDark() = snap("settings_phone_dark", darkTheme = true) {
        SettingsContent(settingsState(), SettingsActions.None, onBack = null, modifier = Modifier.fillMaxSize(), now = DemoData.Now)
    }

    @Test fun topFontScale13() = snap("settings_phone_fontscale_1_3", fontScale = 1.3f) {
        SettingsContent(settingsState(), SettingsActions.None, onBack = null, modifier = Modifier.fillMaxSize(), now = DemoData.Now)
    }

    @Test fun reminders() {
        setContent {
            SettingsContent(settingsState(notificationsGranted = false), SettingsActions.None, onBack = null, modifier = Modifier.fillMaxSize(), now = DemoData.Now)
        }
        compose.onNodeWithTag(SettingsTags.TestReminder).performScrollTo()
        capture("settings_phone_reminders")
    }

    @Test fun widgetsFontScale() {
        setContent(fontScale = 1.5f) {
            SettingsContent(settingsState(), SettingsActions.None, onBack = null, modifier = Modifier.fillMaxSize(), now = DemoData.Now)
        }
        compose.onNodeWithTag(SettingsTags.pinWidget(PinnableWidget.NextUp)).performScrollTo()
        capture("settings_phone_widgets_fontscale_1_5")
    }

    /** The editor the You card opens, on its Sleep tab (the dial, both schedules) and its Chronotype tab. */
    @Composable
    private fun You(daysOff: SleepWindow? = DaysOff) {
        EditorSurface {
            YouEditor(
                initialSleep = DemoData.profile.sleep,
                initialDaysOff = daysOff,
                initialChronotype = Chronotype.ModerateEvening,
                onSave = { _, _, _ -> },
                onCancel = {},
            )
        }
    }

    @Test fun youEditorSleep() = snap("settings_editor_you_sleep") { You() }

    @Test fun youEditorSleepDark() = snap("settings_editor_you_sleep_dark", darkTheme = true) { You() }

    @Test fun youEditorSleepFontScale() = snap("settings_editor_you_sleep_fontscale_1_5", fontScale = 1.5f) { You() }

    @Test fun youEditorChronotype() {
        setContent { You() }
        compose.onNodeWithTag(SettingsTags.Chronotype).performClick()
        capture("settings_editor_you_chronotype")
    }

    @Test fun youEditorChronotypeFontScale() {
        setContent(fontScale = 1.5f) { You() }
        compose.onNodeWithTag(SettingsTags.Chronotype).performClick()
        capture("settings_editor_you_chronotype_fontscale_1_5")
    }

    @Test fun homeEditor() = snap("settings_editor_home") {
        EditorSurface {
            HomeZoneEditor(
                profile = DemoData.profile,
                query = "tok",
                results = persistentListOf(DemoData.HND, DemoData.NRT),
                onQueryChange = {},
                onSelect = {},
                onClose = {},
                now = DemoData.Now,
            )
        }
    }

    @Test fun homeEditorEmpty() = snap("settings_editor_home_empty", darkTheme = true) {
        EditorSurface {
            HomeZoneEditor(DemoData.profile, query = "", results = persistentListOf(), onQueryChange = {}, onSelect = {}, onClose = {}, now = DemoData.Now)
        }
    }

    @Test fun importConfirm() = snap("settings_import_confirm") {
        EditorSurface {
            ImportConfirm(
                PendingImport(tripCount = 3, exportedAt = Instant.parse("2026-06-01T09:30:00Z"), hasProfile = true),
                onReplace = {},
                onMerge = {},
                onCancel = {},
                zone = ZoneOffset.UTC,
            )
        }
    }

    @Test fun importConfirmFontScale() = snap("settings_import_confirm_fontscale_1_5", fontScale = 1.5f) {
        EditorSurface {
            ImportConfirm(
                PendingImport(tripCount = 1, exportedAt = Instant.parse("2026-06-01T09:30:00Z"), hasProfile = false),
                onReplace = {},
                onMerge = {},
                onCancel = {},
                zone = ZoneOffset.UTC,
            )
        }
    }
}

/** The You editor on a landscape phone: the dial makes room, so Save stays on screen without scrolling. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [37], qualifiers = "w860dp-h400dp-land-xxhdpi")
class SettingsLandscapeScreenshotTest : ClockblockScreenshotTest() {
    @Test fun youEditor() {
        setContent {
            EditorSurface {
                YouEditor(DemoData.profile.sleep, DaysOff, Chronotype.ModerateEvening, onSave = { _, _, _ -> }, onCancel = {})
            }
        }
        capture("settings_editor_you_landscape")
        val save = compose.onNodeWithTag(SettingsTags.EditorSave).getUnclippedBoundsInRoot()
        (save.bottom <= 400.dp) shouldBe true
    }

    @Test fun youEditorLargeFont() {
        setContent(fontScale = 1.5f) {
            EditorSurface {
                YouEditor(DemoData.profile.sleep, DaysOff, Chronotype.ModerateEvening, onSave = { _, _, _ -> }, onCancel = {})
            }
        }
        capture("settings_editor_you_landscape_fontscale_1_5")
        val save = compose.onNodeWithTag(SettingsTags.EditorSave).getUnclippedBoundsInRoot()
        (save.bottom <= 400.dp) shouldBe true
    }
}

/** Days off that differ from the demo profile's work days, so both rings show. */
private val DaysOff = SleepWindow(java.time.LocalTime.of(0, 30), java.time.LocalTime.of(8, 45))

/** Settings, About and Licences on a large landscape screen: the content column stays readable. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [37], qualifiers = "w1280dp-h800dp-xhdpi")
class SettingsExpandedScreenshotTest : ClockblockScreenshotTest() {
    @Test fun settings() = snap("settings_expanded") {
        SettingsContent(settingsState(), SettingsActions.None, onBack = null, modifier = Modifier.fillMaxSize(), now = DemoData.Now)
    }

    @Test fun about() = snap("about_expanded") {
        AboutContent("1.0.0", onVersionTap = {}, onOpenSource = {}, onOpenLicenses = {}, onBack = {}, modifier = Modifier.fillMaxSize())
    }

    @Test fun licenses() = snap("licenses_expanded") { LicensesScreen(FixtureLibraries, onBack = {}, modifier = Modifier.fillMaxSize()) }

    @Test fun titleCard() = snap("opus_title_card_expanded") { TitleCardOverScrim() }
}

/** About (whole page on a tall phone), the Opus title card and Licences. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [37], qualifiers = "w400dp-h2300dp-xxhdpi")
class AboutScreenshotTest : ClockblockScreenshotTest() {
    private fun about(name: String, dark: Boolean = false, fontScale: Float? = null) = snap(name, darkTheme = dark, fontScale = fontScale) {
        AboutContent("1.0.0", onVersionTap = {}, onOpenSource = {}, onOpenLicenses = {}, onBack = {}, modifier = Modifier.fillMaxSize())
    }

    @Test fun light() = about("about")

    @Test fun dark() = about("about_dark", dark = true)

    @Test fun fontScale() = about("about_fontscale_1_5", fontScale = 1.5f)

    @Test fun licenses() = snap("licenses") { LicensesScreen(FixtureLibraries, onBack = {}, modifier = Modifier.fillMaxSize()) }

    @Test fun licensesDark() = snap("licenses_dark", darkTheme = true) { LicensesScreen(FixtureLibraries, onBack = {}, modifier = Modifier.fillMaxSize()) }

    @Test fun licensesFontScale() = snap("licenses_fontscale_1_5", fontScale = 1.5f) { LicensesScreen(FixtureLibraries, onBack = {}, modifier = Modifier.fillMaxSize()) }
}

/** The Opus mode title card at phone size. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [37], qualifiers = "w400dp-h860dp-xxhdpi")
class OpusTitleCardScreenshotTest : ClockblockScreenshotTest() {
    @Test fun titleCard() = snap("opus_title_card") { TitleCardOverScrim() }

    @Test fun titleCardFontScale() = snap("opus_title_card_fontscale_1_5", fontScale = 1.5f) {
        TitleCardOverScrim()
    }
}

/** The title card as the About screen shows it: centred over a scrim. */
@androidx.compose.runtime.Composable
private fun TitleCardOverScrim() {
    androidx.compose.foundation.layout.Box(
        Modifier.fillMaxSize().background(androidx.compose.material3.MaterialTheme.colorScheme.scrim.copy(alpha = 0.6f)),
        contentAlignment = androidx.compose.ui.Alignment.Center,
    ) { OpusTitleCard(onDismiss = {}) }
}
