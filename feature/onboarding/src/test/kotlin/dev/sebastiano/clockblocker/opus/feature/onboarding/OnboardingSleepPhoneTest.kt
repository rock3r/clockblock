package dev.sebastiano.clockblocker.opus.feature.onboarding

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import dev.sebastiano.clockblocker.opus.core.designsystem.dial.DialGeometry
import dev.sebastiano.clockblocker.opus.core.model.SleepWindow
import dev.sebastiano.clockblocker.opus.core.model.UserProfile
import dev.sebastiano.clockblocker.opus.feature.onboarding.profile.SleepDialTags
import io.kotest.matchers.shouldBe
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalTime
import kotlin.math.cos
import kotlin.math.sin

/** A 13 h night: the "lot of sleep" hint. */
private val LongNight = SampleSleepProfile.copy(sleep = SleepWindow(LocalTime.of(22, 0), LocalTime.of(11, 0)))

/** A 4 h night: the "short night" hint. */
private val ShortNight = SampleSleepProfile.copy(sleep = SleepWindow(LocalTime.of(2, 0), LocalTime.of(6, 0)))

/** Days off still linked to work days. */
private val LinkedNights = SampleSleepProfile.copy(daysOffSleep = null)

/**
 * The sleep step on the owner's phone in portrait (a Pixel 7 Pro at its default 1080 × 2340 resolution: 411 × 891 dp),
 * with the system bars' height taken off as on the device (Robolectric draws no bars): everything, any hint line
 * included, fits above the bottom bar without scrolling at the default font size and at 1.3× and 1.5×.
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

    @Test fun long_night_hint_fits() = assertFits(LongNight)

    @Test fun long_night_hint_fits_at_1_3() = assertFits(LongNight, 1.3f)

    @Test fun long_night_hint_fits_at_1_5() = assertFits(LongNight, 1.5f)

    @Test fun short_night_hint_fits() = assertFits(ShortNight)

    @Test fun short_night_hint_fits_at_1_3() = assertFits(ShortNight, 1.3f)

    @Test fun short_night_hint_fits_at_1_5() = assertFits(ShortNight, 1.5f)

    @Test fun linked_hint_fits() = assertFits(LinkedNights, daysOff = true)

    @Test fun linked_hint_fits_at_1_3() = assertFits(LinkedNights, 1.3f, daysOff = true)

    @Test fun linked_hint_fits_at_1_5() = assertFits(LinkedNights, 1.5f, daysOff = true)

    @Test fun egg_line_fits() = assertFits(SampleSleepProfile, egg = true)

    @Test fun egg_line_fits_at_1_3() = assertFits(SampleSleepProfile, 1.3f, egg = true)

    @Test fun egg_line_fits_at_1_5() = assertFits(SampleSleepProfile, 1.5f, egg = true)

    /**
     * At 1.5× the egg's line takes the pills' place while it shows. The faded-out pills under it must be inert: out of
     * TalkBack's tree, and a tap where the bedtime pill sits opens no picker. Once the line goes, they're back.
     */
    @Test
    fun pills_under_the_egg_line_are_inert_until_it_goes() {
        show(onboardingState(OnboardingStep.Sleep, profile = SampleSleepProfile).copy(easterEggs = true), fontScale = 1.5f)
        val pill = compose.onNodeWithTag(SleepDialTags.BedtimePill).getUnclippedBoundsInRoot()
        pushPastADay()
        compose.onNodeWithTag(SleepDialTags.Dial).performTouchInput { up() }
        compose.mainClock.advanceTimeBy(EggFadeMillis)
        compose.onNode(hasText("24.2 hours", substring = true), useUnmergedTree = true).assertExists()
        compose.onNodeWithTag(SleepDialTags.BedtimePill).assertDoesNotExist()
        val centre = with(compose.density) { Offset(((pill.left + pill.right) / 2).toPx(), ((pill.top + pill.bottom) / 2).toPx()) }
        compose.onRoot().performTouchInput { click(centre) }
        compose.mainClock.advanceTimeBy(EggFadeMillis)
        compose.onNodeWithTag(SleepDialTags.PickerConfirm).assertDoesNotExist()
        // The egg holds its line for 4.5 s.
        compose.mainClock.advanceTimeBy(EggGoneMillis)
        compose.mainClock.autoAdvance = true
        compose.onNodeWithTag(SleepDialTags.BedtimePill).assertHasClickAction().performClick()
        compose.onNodeWithTag(SleepDialTags.PickerConfirm).assertExists()
    }

    /** An unusual night's hint matters more than why a linked switch changed nothing. */
    @Test
    fun a_short_night_on_linked_days_off_shows_the_short_night_hint() {
        show(onboardingState(OnboardingStep.Sleep, profile = ShortNight.copy(daysOffSleep = null)))
        compose.onNodeWithTag(SleepDialTags.DaysOff).performClick()
        compose.onNode(hasText("Is this your usual night?")).assertExists()
    }

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
        profile = ShortNight
        compose.waitForIdle()
        compose.onNodeWithTag(SleepDialTags.Hint).assertExists()
        val filled = compose.onNodeWithTag(SleepDialTags.HintSlot).getUnclippedBoundsInRoot()
        filled shouldBe empty
        compose.onNodeWithTag(SleepDialTags.WakePill).getUnclippedBoundsInRoot().top shouldBe pills.top
    }

    /**
     * A larger font never makes the duration readout smaller: the phone at 1× and at 1.5×, side by side in one window
     * (each 411 dp wide), so both lay out in the same pass.
     */
    @Test
    @Config(qualifiers = "w822dp-h891dp-xxhdpi")
    fun the_readout_is_no_smaller_at_a_large_font() {
        setContent {
            Row {
                listOf(1f, 1.5f).forEach { scale ->
                    val density = LocalDensity.current
                    CompositionLocalProvider(LocalDensity provides Density(density.density, scale)) {
                        Box(Modifier.width(PhoneWidth).fillMaxHeight().padding(top = StatusBar, bottom = NavigationBar)) {
                            OnboardingContent(onboardingState(OnboardingStep.Sleep, profile = SampleSleepProfile), OnboardingActions.None)
                        }
                    }
                }
            }
        }
        val (default, large) = compose.onAllNodesWithTag(SleepDialTags.Duration, useUnmergedTree = true).fetchSemanticsNodes()
            .map { it.boundsInRoot.height }
        (large >= default) shouldBe true
    }

    /**
     * The step at [fontScale] with [profile]'s hint showing (Days off selected when [daysOff]; the 24.2 egg's line,
     * pushed out by a drag past a full day, when [egg]): the line starts under the status bar and ends above the bottom
     * bar (Back / Next, with its padding), which itself sits inside the window.
     */
    private fun assertFits(profile: UserProfile, fontScale: Float = 1f, daysOff: Boolean = false, egg: Boolean = false) {
        show(onboardingState(OnboardingStep.Sleep, profile = profile).copy(easterEggs = egg), fontScale = fontScale)
        if (daysOff) compose.onNodeWithTag(SleepDialTags.DaysOff).performClick()
        if (egg) pushPastADay()
        val viewportBottom = compose.onRoot().getUnclippedBoundsInRoot().bottom - NavigationBar
        val hint = hintBounds(egg)
        val next = compose.onNodeWithTag(OnboardingTags.Next).getUnclippedBoundsInRoot()
        // The bar starts its vertical padding above Next, and draws over anything below that.
        val barTop = next.top - bottomBarVerticalPadding(short = false)
        (hint.top >= StatusBar) shouldBe true
        (hint.bottom <= barTop) shouldBe true
        (next.bottom <= viewportBottom + 0.5.dp) shouldBe true
    }

    private fun hintBounds(egg: Boolean): DpRect =
        if (egg) {
            compose.onNode(hasText("24.2 hours", substring = true), useUnmergedTree = true).getUnclippedBoundsInRoot()
        } else {
            compose.onNodeWithTag(SleepDialTags.Hint, useUnmergedTree = true).getUnclippedBoundsInRoot()
        }

    /** Holds the wake handle (07:15) pushed round past the longest night until the egg fires. */
    private fun pushPastADay() {
        compose.onNodeWithTag(SleepDialTags.Dial).performTouchInput {
            val r = 122f * minOf(width, height) / 320f
            fun at(minute: Float): Offset {
                val a = Math.toRadians(DialGeometry.angleForMinute(minute).toDouble())
                return Offset(center.x + r * cos(a).toFloat(), center.y + r * sin(a).toFloat())
            }
            var minute = 7 * 60f + 15f
            down(at(minute))
            while (minute < 7 * 60f + 15f + 18 * 60f) {
                minute += 20f
                moveTo(at(minute))
            }
        }
        compose.mainClock.autoAdvance = false
        compose.mainClock.advanceTimeBy(EggFadeMillis)
    }

    private companion object {
        /**
         * The Pixel 7 Pro's status bar (with its camera cutout) and gesture navigation bar, read from the phone's window
         * insets at 420 dpi: 108 px and 63 px.
         */
        val StatusBar: Dp = 41.dp
        val NavigationBar: Dp = 24.dp
        val PhoneWidth: Dp = 411.dp

        /** Long enough for the hint's cross-fade, far short of the egg's 4.5 s hold. */
        const val EggFadeMillis = 600L

        /** Past the egg's 4.5 s hold and the fade back. */
        const val EggGoneMillis = 5_000L
    }
}
