package dev.sebastiano.clockblocker.opus.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
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
    liveUpdatesSupported: Boolean = true,
) = SettingsUiState(
    settings = settings,
    profile = profile,
    permissions = NotificationPermissionState(
        notificationsGranted = notificationsGranted,
        exactAlarmsAllowed = exactAlarmsAllowed,
        promotedAllowed = promotedAllowed,
        batteryOptimizationIgnored = batteryOptimizationIgnored,
        liveUpdatesSupported = liveUpdatesSupported,
    ),
    widgetPinningSupported = widgetPinningSupported,
)

/** The whole Settings list on a tall phone (so one golden shows every section), plus dark and large-font variants. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w400dp-h3300dp-xxhdpi")
class SettingsScreenshotTest : OpusScreenshotTest() {
    private fun settings(name: String, state: SettingsUiState = settingsState(), dark: Boolean = false, fontScale: Float? = null) =
        snap(name, darkTheme = dark, fontScale = fontScale) {
            SettingsContent(state, SettingsActions.None, onBack = {}, modifier = Modifier.fillMaxSize(), dynamicColorSupported = true, now = DemoData.Now)
        }

    @Test fun full() = settings("settings_full")

    @Test fun fullDark() = settings("settings_full_dark", dark = true)

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
@Config(sdk = [36], qualifiers = "w1280dp-h1650dp-xhdpi")
class SettingsTwoPaneScreenshotTest : OpusScreenshotTest() {
    @Test fun full() = snap("settings_two_pane_full") {
        SettingsContent(settingsState(), SettingsActions.None, onBack = {}, modifier = Modifier.fillMaxSize(), dynamicColorSupported = true, now = DemoData.Now)
    }
}

/** Settings at phone height (top of the list) and the focused editors. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w400dp-h860dp-xxhdpi")
class SettingsPhoneScreenshotTest : OpusScreenshotTest() {
    @Test fun top() = snap("settings_phone") {
        SettingsContent(settingsState(), SettingsActions.None, onBack = null, modifier = Modifier.fillMaxSize(), dynamicColorSupported = true, now = DemoData.Now)
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
            SettingsContent(settingsState(), SettingsActions.None, onBack = null, modifier = Modifier.fillMaxSize(), dynamicColorSupported = true, now = DemoData.Now)
        }
        compose.onNodeWithTag(SettingsTags.pinWidget(PinnableWidget.NextUp)).performScrollTo()
        capture("settings_phone_widgets_fontscale_1_5")
    }

    @Test fun sleepEditor() = snap("settings_editor_sleep") {
        EditorSurface { SleepEditor(DemoData.profile.sleep, onSave = {}, onCancel = {}) }
    }

    @Test fun sleepEditorDark() = snap("settings_editor_sleep_dark", darkTheme = true) {
        EditorSurface { SleepEditor(DemoData.profile.sleep, onSave = {}, onCancel = {}) }
    }

    @Test fun chronotypeEditor() = snap("settings_editor_chronotype") {
        EditorSurface { ChronotypeEditor(Chronotype.ModerateEvening, DemoData.profile.sleep, onSave = {}, onCancel = {}) }
    }

    @Test fun chronotypeEditorFontScale() = snap("settings_editor_chronotype_fontscale_1_5", fontScale = 1.5f) {
        EditorSurface { ChronotypeEditor(Chronotype.ModerateEvening, DemoData.profile.sleep, onSave = {}, onCancel = {}) }
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

/** Settings, About and Licences on a large landscape screen: the content column stays readable. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w1280dp-h800dp-xhdpi")
class SettingsExpandedScreenshotTest : OpusScreenshotTest() {
    @Test fun settings() = snap("settings_expanded") {
        SettingsContent(settingsState(), SettingsActions.None, onBack = null, modifier = Modifier.fillMaxSize(), dynamicColorSupported = true, now = DemoData.Now)
    }

    @Test fun about() = snap("about_expanded") {
        AboutContent("1.0.0", onVersionTap = {}, onOpenSource = {}, onOpenLicenses = {}, onBack = {}, modifier = Modifier.fillMaxSize())
    }

    @Test fun licenses() = snap("licenses_expanded") { LicensesContent(onBack = {}, modifier = Modifier.fillMaxSize()) }

    @Test fun titleCard() = snap("opus_title_card_expanded") { TitleCardOverScrim() }
}

/** About (whole page on a tall phone), the Opus title card and Licences. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w400dp-h2300dp-xxhdpi")
class AboutScreenshotTest : OpusScreenshotTest() {
    private fun about(name: String, dark: Boolean = false, fontScale: Float? = null) = snap(name, darkTheme = dark, fontScale = fontScale) {
        AboutContent("1.0.0", onVersionTap = {}, onOpenSource = {}, onOpenLicenses = {}, onBack = {}, modifier = Modifier.fillMaxSize())
    }

    @Test fun light() = about("about")

    @Test fun dark() = about("about_dark", dark = true)

    @Test fun fontScale() = about("about_fontscale_1_5", fontScale = 1.5f)

    @Test fun licenses() = snap("licenses") { LicensesContent(onBack = {}, modifier = Modifier.fillMaxSize()) }

    @Test fun licensesDark() = snap("licenses_dark", darkTheme = true) { LicensesContent(onBack = {}, modifier = Modifier.fillMaxSize()) }

    @Test fun licensesFontScale() = snap("licenses_fontscale_1_5", fontScale = 1.5f) { LicensesContent(onBack = {}, modifier = Modifier.fillMaxSize()) }
}

/** The Opus mode title card at phone size. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w400dp-h860dp-xxhdpi")
class OpusTitleCardScreenshotTest : OpusScreenshotTest() {
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
