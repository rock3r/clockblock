package dev.sebastiano.clockblocker.opus.feature.onboarding

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.sebastiano.clockblocker.opus.core.model.SleepWindow
import dev.sebastiano.clockblocker.opus.core.model.UserProfile
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.sebastiano.clockblocker.opus.feature.onboarding.profile.SleepDialTags
import io.kotest.matchers.shouldBe
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalTime

/** A 13 h night: the "lot of sleep" hint. */
private val LongNight = SampleSleepProfile.copy(sleep = SleepWindow(LocalTime.of(22, 0), LocalTime.of(11, 0)))

/** A 4 h night: the "short night" hint. */
private val ShortNight = SampleSleepProfile.copy(sleep = SleepWindow(LocalTime.of(2, 0), LocalTime.of(6, 0)))

/** Days off still linked to work days. */
private val LinkedNights = SampleSleepProfile.copy(daysOffSleep = null)

/**
 * The sleep step on a typical phone in portrait (a Pixel 7 Pro: 411 × 891 dp), with the system bars' height taken off
 * as on the device (Robolectric draws no bars): everything, the hint line included, fits without scrolling at the
 * default font size; larger fonts may scroll but stay tidy.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [37], qualifiers = "w411dp-h891dp-xxhdpi")
class OnboardingSleepPhoneTest : ClockblockScreenshotTest() {
    private fun show(state: OnboardingUiState, dark: Boolean = false, fontScale: Float? = null) =
        setContent(darkTheme = dark, fontScale = fontScale) {
            OnboardingContent(state, OnboardingActions.None, Modifier.fillMaxSize().padding(top = StatusBar, bottom = NavigationBar))
        }

    private fun step(name: String, profile: UserProfile = SampleSleepProfile, dark: Boolean = false, fontScale: Float? = null) {
        show(onboardingState(OnboardingStep.Sleep, profile = profile), dark, fontScale)
        capture(name)
    }

    @Test fun phone() = step("onboarding_3_sleep_phone")

    @Test fun phoneDark() = step("onboarding_3_sleep_phone_dark", dark = true)

    @Test fun phoneFont13() = step("onboarding_3_sleep_phone_fontscale_1_3", fontScale = 1.3f)

    @Test fun phoneFont15() = step("onboarding_3_sleep_phone_fontscale_1_5", fontScale = 1.5f)

    @Test fun phoneLongNight() = step("onboarding_3_sleep_phone_hint_long", profile = LongNight)

    @Test fun phoneShortNight() = step("onboarding_3_sleep_phone_hint_short", profile = ShortNight)

    @Test fun phoneShortNightFont15() = step("onboarding_3_sleep_phone_hint_short_fontscale_1_5", profile = ShortNight, fontScale = 1.5f)

    @Test fun phoneLinkedDaysOff() {
        show(onboardingState(OnboardingStep.Sleep, profile = LinkedNights))
        compose.onNodeWithTag(SleepDialTags.DaysOff).performClick()
        capture("onboarding_3_sleep_phone_days_off_linked")
    }

    @Test fun the_long_night_hint_and_next_fit_without_scrolling() = assertFits(LongNight)

    @Test fun the_short_night_hint_and_next_fit_without_scrolling() = assertFits(ShortNight)

    @Test
    fun the_hint_slot_keeps_its_height_with_or_without_a_hint() {
        var profile by mutableStateOf(SampleSleepProfile)
        setContent {
            OnboardingContent(
                onboardingState(OnboardingStep.Sleep, profile = profile),
                OnboardingActions.None,
                Modifier.fillMaxSize().padding(top = StatusBar, bottom = NavigationBar),
            )
        }
        val empty = compose.onNodeWithTag(SleepDialTags.HintSlot).getUnclippedBoundsInRoot()
        val pills = compose.onNodeWithTag(SleepDialTags.WakePill).getUnclippedBoundsInRoot()
        compose.onNodeWithTag(SleepDialTags.Hint).assertDoesNotExist()
        // A hint appears: the slot, and everything above it, stay where they were.
        profile = LongNight
        compose.waitForIdle()
        compose.onNodeWithTag(SleepDialTags.Hint).assertExists()
        val filled = compose.onNodeWithTag(SleepDialTags.HintSlot).getUnclippedBoundsInRoot()
        filled shouldBe empty
        compose.onNodeWithTag(SleepDialTags.WakePill).getUnclippedBoundsInRoot().top shouldBe pills.top
    }

    private fun assertFits(profile: UserProfile) {
        show(onboardingState(OnboardingStep.Sleep, profile = profile))
        val viewportBottom = compose.onRoot().getUnclippedBoundsInRoot().bottom - NavigationBar
        val hint = compose.onNodeWithTag(SleepDialTags.Hint, useUnmergedTree = true).getUnclippedBoundsInRoot()
        val next = compose.onNodeWithTag(OnboardingTags.Next).getUnclippedBoundsInRoot()
        (hint.top >= StatusBar) shouldBe true
        // The hint ends above the bottom bar (Back / Next), which itself sits inside the window.
        (hint.bottom <= next.top) shouldBe true
        (next.bottom <= viewportBottom + 0.5.dp) shouldBe true
    }

    private companion object {
        /** A Pixel 7 Pro's status bar (with its camera cutout) and gesture navigation bar. */
        val StatusBar: Dp = 48.dp
        val NavigationBar: Dp = 24.dp
    }
}
