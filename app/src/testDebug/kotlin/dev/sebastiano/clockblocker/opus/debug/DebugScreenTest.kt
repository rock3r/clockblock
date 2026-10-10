package dev.sebastiano.clockblocker.opus.debug

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import dev.sebastiano.clockblocker.opus.TestApplication
import dev.sebastiano.clockblocker.opus.core.designsystem.theme.ClockblockTheme
import dev.sebastiano.clockblocker.opus.core.testing.captureRoboImageInvalidated
import dev.sebastiano.clockblocker.opus.core.notifications.NotificationPermissionState
import dev.sebastiano.clockblocker.opus.feature.settings.SettingsActions
import dev.sebastiano.clockblocker.opus.feature.settings.SettingsContent
import dev.sebastiano.clockblocker.opus.feature.settings.SettingsUiState
import dev.sebastiano.clockblocker.opus.widget.debug.RemoteComposeNotificationProbe.Result
import dev.sebastiano.clockblocker.opus.widget.debug.RemoteComposeNotificationProbe.Variant
import dev.sebastiano.clockblocker.opus.widget.debug.RemoteComposeNotificationProbe.Verdict
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The debug-only Debug screen: what it shows for each probe state, and that every control reaches its callback. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [37], application = TestApplication::class, qualifiers = "w400dp-h1100dp-xxhdpi")
class DebugScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val calls = mutableListOf<String>()

    private fun show(probe: ProbeState, allowed: Boolean = true, dark: Boolean = false) {
        compose.setContent {
            ClockblockTheme(darkTheme = dark, dynamicColor = false, reduceMotion = true) {
                DebugScreen(
                    probe = probe,
                    notificationsAllowed = allowed,
                    onRunProbe = { calls += "run" },
                    onClearProbe = { calls += "clear" },
                    onAllowNotifications = { calls += "allow" },
                    onOpenGallery = { calls += "gallery:${it.extra}" },
                    onBack = { calls += "back" },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }

    @Test
    fun idle() {
        show(ProbeState.Idle)
        compose.captureRoboImageInvalidated("src/test/screenshots/debug_screen_idle.png")
    }

    @Test
    fun results() {
        show(ProbeState.Done(SampleResults), dark = true)
        compose.captureRoboImageInvalidated("src/test/screenshots/debug_screen_results_dark.png")
    }

    @Test
    fun `notifications off disables the probe and offers to allow them`() {
        show(ProbeState.Idle, allowed = false)
        compose.onNodeWithTag(DebugTags.RunProbe).assertIsNotEnabled()
        compose.onNodeWithText("Allow notifications").performClick()
        calls shouldContainExactly listOf("allow")
        compose.captureRoboImageInvalidated("src/test/screenshots/debug_screen_notifications_off.png")
    }

    @Test
    fun `running disables the button`() {
        show(ProbeState.Running)
        compose.onNodeWithText("Running…").assertIsNotEnabled()
    }

    @Test
    fun `every control reaches its callback`() {
        show(ProbeState.Idle)
        compose.onNodeWithTag(DebugTags.RunProbe).performClick()
        compose.onNodeWithTag(DebugTags.ClearProbe).performClick()
        GalleryPage.entries.forEach { compose.onNodeWithTag(DebugTags.gallery(it)).performScrollTo().performClick() }
        compose.onNodeWithContentDescription("Back").performClick()
        calls shouldContainExactly
            listOf("run", "clear", "gallery:twoclocks", "gallery:nextup", "gallery:live", "gallery:all", "back")
    }

    private companion object {
        val SampleResults = listOf(
            Result(Variant.DecoratedCollapsed, Verdict.Kept, "still showing after 3000 ms"),
            Result(Variant.DecoratedBoth, Verdict.Kept, "still showing after 3000 ms"),
            Result(Variant.Undecorated, Verdict.Gone, "posted, then gone after 3000 ms (SystemUI inflation error? see logcat)"),
            Result(Variant.Promoted, Verdict.Kept, "still showing after 3000 ms, promoted=false, promotable=false"),
        )
    }
}

/** Debug builds end Settings with a Debug section whose row opens [DebugActivity]. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [37], application = TestApplication::class, qualifiers = "w400dp-h860dp-xxhdpi")
class DebugMenuTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `Settings in a debug build ends with a Debug section that opens the debug tools`() {
        val application = RuntimeEnvironment.getApplication()
        compose.setContent {
            ClockblockTheme(dynamicColor = false, reduceMotion = true) {
                SettingsContent(
                    state = SettingsUiState(permissions = NotificationPermissionState(true, true, true, true)),
                    actions = SettingsActions.None,
                    onBack = null,
                    modifier = Modifier.fillMaxSize(),
                    extraSection = DebugMenu.settingsSection,
                )
            }
        }
        compose.onNodeWithText("Debug tools").performScrollTo()
        compose.onNodeWithTag(DebugMenu.SettingsRowTag).performClick()
        shadowOf(application).nextStartedActivity.component?.className shouldBe DebugActivity::class.java.name
    }
}
