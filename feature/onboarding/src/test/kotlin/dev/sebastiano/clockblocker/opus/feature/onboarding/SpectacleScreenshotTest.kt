package dev.sebastiano.clockblocker.opus.feature.onboarding

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import dev.sebastiano.clockblocker.opus.core.model.Chronotype
import dev.sebastiano.clockblocker.opus.core.model.UserProfile
import dev.sebastiano.clockblocker.opus.core.testing.captureRoboImageInvalidated
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Plays the first-time Sleep → chronotype spectacle and records [frames]: each golden's name and its time after Next
 * (the step moving on, as the ViewModel does). The last frame should come after the reveal has come to rest.
 */
internal fun ClockblockScreenshotTest.spectacleFrames(
    profile: UserProfile,
    frames: List<Pair<String, Long>>,
    dark: Boolean = false,
    fontScale: Float? = null,
    reduceMotion: Boolean = false,
) {
    var state by mutableStateOf(onboardingState(OnboardingStep.Sleep, profile = profile))
    val host = object : OnboardingActions by OnboardingActions.None {
        override fun next() {
            state = state.copy(step = OnboardingStep.entries[state.step.ordinal + 1])
        }
    }
    setContent(darkTheme = dark, fontScale = fontScale, reduceMotion = reduceMotion) {
        OnboardingContent(state, host, Modifier.fillMaxSize())
    }
    compose.waitForIdle()
    compose.mainClock.autoAdvance = false
    compose.onNodeWithTag(OnboardingTags.Next).performClick()
    var elapsed = 0L
    for ((name, at) in frames) {
        compose.mainClock.advanceTimeBy(at - elapsed)
        elapsed = at
        compose.captureRoboImageInvalidated("src/test/screenshots/$name.png")
    }
}

/** The spectacle's beats on a compact phone: an early (light) and an evening (dark) chronotype. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [37], qualifiers = "w400dp-h860dp-xxhdpi")
class SpectacleScreenshotTest : ClockblockScreenshotTest() {
    @Test
    fun intermediate() = spectacleFrames(
        SampleSleepProfile,
        listOf(
            "spectacle_intermediate_1_emerge" to 120L,
            "spectacle_intermediate_2_first_spin" to 420L,
            "spectacle_intermediate_3_second_spin" to 860L,
            "spectacle_intermediate_4_pop" to 1_300L,
            "spectacle_intermediate_5_layers" to 1_750L,
            "spectacle_intermediate_6_words" to 2_250L,
            "spectacle_intermediate_7_rest" to RestMillis,
        ),
    )

    @Test
    fun owlDark() = spectacleFrames(
        OwlProfile,
        listOf(
            "spectacle_owl_dark_1_spin" to 600L,
            "spectacle_owl_dark_2_pop" to 1_300L,
            "spectacle_owl_dark_3_stars" to 1_700L,
            "spectacle_owl_dark_4_constellation" to 2_050L,
            "spectacle_owl_dark_5_rest" to RestMillis,
        ),
        dark = true,
    )

    @Test
    fun earlyBird() = spectacleFrames(
        SampleSleepProfile.copy(chronotype = Chronotype.DefiniteMorning),
        listOf("spectacle_early_bird_1_layers" to 1_750L, "spectacle_early_bird_2_birds" to 2_050L),
    )

    @Test
    fun largeFontRest() = spectacleFrames(SampleSleepProfile, listOf("spectacle_fontscale_1_5_rest" to RestMillis), fontScale = 1.5f)

    /** Reduce motion: no spectacle; Next lands on the reveal at rest. */
    @Test
    fun reduceMotionRest() = spectacleFrames(SampleSleepProfile, listOf("spectacle_reduce_motion_rest" to RestMillis), reduceMotion = true)
}

/** Phone in landscape: the shape rises from the dial on the right to the card on the left. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [37], qualifiers = "w860dp-h400dp-land-xxhdpi")
class SpectacleLandscapeScreenshotTest : ClockblockScreenshotTest() {
    @Test
    fun frames() = spectacleFrames(
        SampleSleepProfile,
        listOf("spectacle_landscape_1_spin" to 600L, "spectacle_landscape_2_pop" to 1_300L, "spectacle_landscape_3_rest" to RestMillis),
    )
}

/** Expanded window: two panes. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [37], qualifiers = "w1280dp-h800dp-xhdpi")
class SpectacleExpandedScreenshotTest : ClockblockScreenshotTest() {
    @Test
    fun frames() = spectacleFrames(
        OwlProfile,
        listOf("spectacle_expanded_1_spin" to 600L, "spectacle_expanded_2_rest" to RestMillis),
        dark = true,
    )
}

/** Well after the spectacle (and its Calm-scaled worst case) has come to rest. */
private const val RestMillis = 5_000L
