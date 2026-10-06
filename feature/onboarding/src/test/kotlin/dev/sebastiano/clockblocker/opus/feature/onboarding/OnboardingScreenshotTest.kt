package dev.sebastiano.clockblocker.opus.feature.onboarding

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import dev.sebastiano.clockblocker.opus.core.data.demo.DemoData
import dev.sebastiano.clockblocker.opus.core.model.Chronotype
import dev.sebastiano.clockblocker.opus.core.model.Intensity
import dev.sebastiano.clockblocker.opus.core.model.UserProfile
import dev.sebastiano.clockblocker.opus.core.notifications.NotificationPermissionState
import dev.sebastiano.clockblocker.opus.feature.onboarding.profile.ToolsTags
import kotlinx.collections.immutable.persistentListOf
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import androidx.compose.ui.test.onNodeWithTag

internal fun onboardingState(
    step: OnboardingStep,
    profile: UserProfile = UserProfile(homeZoneId = "Europe/London", chronotype = Chronotype.ModerateMorning, intensity = Intensity.Balanced),
    query: String = "",
    notificationsGranted: Boolean = false,
    melatoninAcknowledged: Boolean = false,
) = OnboardingUiState(
    step = step,
    profile = profile,
    deviceZoneId = "Europe/London",
    query = query,
    results = if (query.isBlank()) persistentListOf() else persistentListOf(DemoData.HND, DemoData.NRT),
    melatoninAcknowledged = melatoninAcknowledged,
    permissions = NotificationPermissionState(
        notificationsGranted = notificationsGranted,
        exactAlarmsAllowed = false,
        promotedAllowed = true,
        batteryOptimizationIgnored = false,
    ),
    now = DemoData.Now,
)

/** Every onboarding step on a compact phone, plus dark and large-font variants. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w400dp-h860dp-xxhdpi")
class OnboardingScreenshotTest : OpusScreenshotTest() {
    private fun step(name: String, state: OnboardingUiState, dark: Boolean = false, fontScale: Float? = null) =
        snap(name, darkTheme = dark, fontScale = fontScale) {
            OnboardingContent(state, OnboardingActions.None, Modifier.fillMaxSize())
        }

    @Test fun welcome() = step("onboarding_1_welcome", onboardingState(OnboardingStep.Welcome))

    @Test fun homeZone() = step("onboarding_2_home", onboardingState(OnboardingStep.HomeZone))

    @Test fun homeZoneSearching() = step("onboarding_2_home_search", onboardingState(OnboardingStep.HomeZone, query = "tok"))

    @Test fun sleep() = step("onboarding_3_sleep", onboardingState(OnboardingStep.Sleep))

    @Test fun chronotype() = step("onboarding_4_chronotype", onboardingState(OnboardingStep.Chronotype))

    @Test fun tools() = step("onboarding_5_tools", onboardingState(OnboardingStep.Tools))

    @Test fun toolsMelatoninNote() {
        setContent { OnboardingContent(onboardingState(OnboardingStep.Tools), OnboardingActions.None, Modifier.fillMaxSize()) }
        compose.onNodeWithTag(ToolsTags.MelatoninNote).performScrollTo().performClick()
        compose.onNodeWithTag(ToolsTags.MelatoninAck).performScrollTo()
        capture("onboarding_5_tools_melatonin_note")
    }

    @Test fun toolsEffort() {
        setContent { OnboardingContent(onboardingState(OnboardingStep.Tools), OnboardingActions.None, Modifier.fillMaxSize()) }
        compose.onNodeWithTag(ToolsTags.effort(Intensity.Max)).performScrollTo()
        capture("onboarding_5_tools_effort")
    }

    @Test fun reminders() = step("onboarding_6_reminders", onboardingState(OnboardingStep.Reminders))

    @Test fun remindersGranted() =
        step("onboarding_6_reminders_granted", onboardingState(OnboardingStep.Reminders, notificationsGranted = true))

    @Test fun welcomeDark() = step("onboarding_1_welcome_dark", onboardingState(OnboardingStep.Welcome), dark = true)

    @Test fun sleepDark() = step("onboarding_3_sleep_dark", onboardingState(OnboardingStep.Sleep), dark = true)

    @Test fun toolsDark() = step("onboarding_5_tools_dark", onboardingState(OnboardingStep.Tools), dark = true)

    @Test fun remindersDark() = step("onboarding_6_reminders_dark", onboardingState(OnboardingStep.Reminders), dark = true)

    @Test fun welcomeLargeFont() = step("onboarding_1_welcome_fontscale_1_5", onboardingState(OnboardingStep.Welcome), fontScale = 1.5f)

    @Test fun homeLargeFont() = step("onboarding_2_home_fontscale_1_5", onboardingState(OnboardingStep.HomeZone, query = "tok"), fontScale = 1.5f)

    @Test fun chronotypeLargeFont() =
        step("onboarding_4_chronotype_fontscale_1_5", onboardingState(OnboardingStep.Chronotype), fontScale = 1.5f)

    @Test fun remindersLargeFont() =
        step("onboarding_6_reminders_fontscale_1_5", onboardingState(OnboardingStep.Reminders), fontScale = 1.5f)

    @Test fun sleepLargeFont() = step("onboarding_3_sleep_fontscale_1_5", onboardingState(OnboardingStep.Sleep), fontScale = 1.5f)

    @Test fun toolsLargeFont() = step("onboarding_5_tools_fontscale_1_5", onboardingState(OnboardingStep.Tools), fontScale = 1.5f)

    @Test fun toolsAllOn() = step("onboarding_5_tools_all_on", allToolsOn())

    @Test fun toolsAllOnDark() = step("onboarding_5_tools_all_on_dark", allToolsOn(), dark = true)

    private fun allToolsOn() = onboardingState(
        OnboardingStep.Tools,
        profile = UserProfile(
            homeZoneId = "Europe/London",
            chronotype = Chronotype.ModerateMorning,
            intensity = Intensity.Balanced,
            useCaffeine = true,
            canSleepOnPlanes = true,
            adjustBeforeDeparture = true,
            useMelatonin = true,
        ),
        melatoninAcknowledged = true,
    )
}

/** Expanded window: two panes, art and words on the left, controls on the right. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w1280dp-h800dp-xhdpi")
class OnboardingExpandedScreenshotTest : OpusScreenshotTest() {
    private fun step(name: String, state: OnboardingUiState, dark: Boolean = false) =
        snap(name, darkTheme = dark) { OnboardingContent(state, OnboardingActions.None, Modifier.fillMaxSize()) }

    @Test fun welcome() = step("onboarding_expanded_1_welcome", onboardingState(OnboardingStep.Welcome))

    @Test fun home() = step("onboarding_expanded_2_home", onboardingState(OnboardingStep.HomeZone, query = "tok"))

    @Test fun sleep() = step("onboarding_expanded_3_sleep", onboardingState(OnboardingStep.Sleep))

    @Test fun chronotype() = step("onboarding_expanded_4_chronotype", onboardingState(OnboardingStep.Chronotype), dark = true)

    @Test fun tools() = step("onboarding_expanded_5_tools", onboardingState(OnboardingStep.Tools))

    @Test fun reminders() = step("onboarding_expanded_6_reminders", onboardingState(OnboardingStep.Reminders))
}

/** Phone in landscape: also two panes, so the dial is never squeezed under the headline. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w860dp-h400dp-land-xxhdpi")
class OnboardingLandscapeScreenshotTest : OpusScreenshotTest() {
    @Test fun sleep() = snap("onboarding_landscape_3_sleep") {
        OnboardingContent(onboardingState(OnboardingStep.Sleep), OnboardingActions.None, Modifier.fillMaxSize())
    }

    @Test fun welcome() = snap("onboarding_landscape_1_welcome") {
        OnboardingContent(onboardingState(OnboardingStep.Welcome), OnboardingActions.None, Modifier.fillMaxSize())
    }
}
