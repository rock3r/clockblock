package dev.sebastiano.clockblocker.opus.feature.onboarding

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import dev.sebastiano.clockblocker.opus.core.model.Chronotype
import dev.sebastiano.clockblocker.opus.core.model.UserProfile
import dev.sebastiano.clockblocker.opus.core.testing.captureRoboImageInvalidated
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Picking an evening type after a morning one: the sun and moon cross-fade with a pop and a wobble, frame by frame. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [37], qualifiers = "w411dp-h891dp-xxhdpi")
class ChronotypeSwapScreenshotTest : ClockblockScreenshotTest() {
    private var chronotype by mutableStateOf(Chronotype.ModerateMorning)

    @Test
    fun sunToMoon() {
        setContent(reduceMotion = false) {
            val profile = UserProfile("Europe/London", chronotype = chronotype)
            OnboardingContent(onboardingState(OnboardingStep.Chronotype, profile = profile, chronotypePicked = true), OnboardingActions.None, Modifier.fillMaxSize())
        }
        compose.mainClock.autoAdvance = false
        compose.mainClock.advanceTimeBy(4_000)
        chronotype = Chronotype.ModerateEvening
        listOf(1 to 150L, 2 to 150L, 3 to 150L, 4 to 3_000L).forEach { (frame, millis) ->
            compose.mainClock.advanceTimeBy(millis)
            compose.captureRoboImageInvalidated("src/test/screenshots/chronotype_swap_$frame.png")
        }
    }
}
