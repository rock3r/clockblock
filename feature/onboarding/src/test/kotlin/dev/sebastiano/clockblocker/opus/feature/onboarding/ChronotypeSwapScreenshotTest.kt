package dev.sebastiano.clockblocker.opus.feature.onboarding

import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import dev.sebastiano.clockblocker.opus.core.model.Chronotype
import dev.sebastiano.clockblocker.opus.core.model.UserProfile
import dev.sebastiano.clockblocker.opus.core.testing.captureRoboImageInvalidated
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Picking an evening type after a morning one: the sun and moon cross-fade with a pop, and a haptic at its top. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [37], qualifiers = "w411dp-h891dp-xxhdpi")
class ChronotypeSwapScreenshotTest : ClockblockScreenshotTest() {
    private var chronotype by mutableStateOf(Chronotype.ModerateMorning)
    private lateinit var view: View

    private fun showMorning() {
        setContent(reduceMotion = false) {
            view = LocalView.current
            val profile = UserProfile("Europe/London", chronotype = chronotype)
            OnboardingContent(onboardingState(OnboardingStep.Chronotype, profile = profile, chronotypePicked = true), OnboardingActions.None, Modifier.fillMaxSize())
        }
        compose.mainClock.autoAdvance = false
        compose.mainClock.advanceTimeBy(4_000)
    }

    @Test
    fun `the swap buzzes once, at the top of the pop`() {
        showMorning()
        shadowOf(view).lastHapticFeedbackPerformed() shouldNotBe HapticFeedbackConstants.CONFIRM
        chronotype = Chronotype.ModerateEvening
        Snapshot.sendApplyNotifications()
        compose.mainClock.advanceTimeBy(200)
        shadowOf(view).lastHapticFeedbackPerformed() shouldNotBe HapticFeedbackConstants.CONFIRM
        compose.mainClock.advanceTimeBy(250)
        shadowOf(view).lastHapticFeedbackPerformed() shouldBe HapticFeedbackConstants.CONFIRM
    }

    @Test
    fun sunToMoon() {
        showMorning()
        chronotype = Chronotype.ModerateEvening
        listOf(1 to 150L, 2 to 150L, 3 to 150L, 4 to 3_000L).forEach { (frame, millis) ->
            compose.mainClock.advanceTimeBy(millis)
            compose.captureRoboImageInvalidated("src/test/screenshots/chronotype_swap_$frame.png")
        }
    }
}
