package dev.sebastiano.clockblocker.opus.feature.onboarding

import android.content.Context
import android.view.accessibility.AccessibilityManager
import androidx.activity.BackEventCompat
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.LocalSaveableStateRegistry
import androidx.compose.runtime.saveable.SaveableStateRegistry
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpRect
import androidx.test.core.app.ApplicationProvider
import dev.sebastiano.clockblocker.opus.feature.onboarding.profile.ChronotypeRevealTags
import dev.sebastiano.clockblocker.opus.feature.onboarding.profile.SleepDialTags
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import kotlin.math.abs
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The first-time Sleep → chronotype spectacle, driven through [OnboardingContent] with a host whose Next and Back
 * really move the step, as the ViewModel does.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [37], qualifiers = "w400dp-h860dp-xxhdpi")
class OnboardingSpectacleTest : ClockblockScreenshotTest() {
    private val calls = mutableListOf<String>()
    private var hostStep by mutableStateOf(OnboardingStep.Sleep)
    private lateinit var backDispatcher: OnBackPressedDispatcher

    private val host = object : OnboardingActions by OnboardingActions.None {
        override fun next() {
            calls += "next"
            hostStep = OnboardingStep.entries[hostStep.ordinal + 1]
        }

        override fun back() {
            calls += "back"
            hostStep = OnboardingStep.entries[hostStep.ordinal - 1]
        }
    }

    private fun showSleep() {
        setContent(reduceMotion = false) {
            backDispatcher = requireNotNull(LocalOnBackPressedDispatcherOwner.current).onBackPressedDispatcher
            OnboardingContent(onboardingState(hostStep, profile = SampleSleepProfile), host, Modifier.fillMaxSize())
        }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
    }

    /** Taps Next on the sleep step and, once the spectacle shows (a few frames), lets it run for [millis]. */
    private fun startSpectacle(millis: Long = 400) {
        compose.onNodeWithTag(OnboardingTags.Next).performClick()
        var frames = 0
        while (!spectacleShown() && frames++ < MaxFramesToStart) compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeBy(millis)
    }

    private fun spectacleShown() = compose.onAllNodesWithTag(ChronotypeRevealTags.Spectacle).fetchSemanticsNodes().isNotEmpty()

    private fun settle() {
        compose.mainClock.advanceTimeBy(4_000)
        compose.waitForIdle()
    }

    private fun backGesture(progress: Float) = compose.runOnUiThread {
        backDispatcher.dispatchOnBackStarted(BackEventCompat(0f, 900f, 0f, BackEventCompat.EDGE_LEFT))
        backDispatcher.dispatchOnBackProgressed(BackEventCompat(120f, 900f, progress, BackEventCompat.EDGE_LEFT))
    }

    /** How many colours the node's pixels show (sampled): one flat colour when it's laid out under a zero alpha. */
    private fun SemanticsNodeInteraction.paintedColours(): Int {
        val pixels = captureToImage().toPixelMap()
        return buildSet {
            for (x in 0 until pixels.width step 4) for (y in 0 until pixels.height step 4) add(pixels[x, y])
        }.size
    }

    /** How far the node's strongest pixel stands out from its top-left one (the background). */
    private fun SemanticsNodeInteraction.ink(): Float {
        val pixels = captureToImage().toPixelMap()
        val background = pixels[0, 0]
        var ink = 0f
        for (x in 0 until pixels.width) for (y in 0 until pixels.height) {
            val c = pixels[x, y]
            ink = maxOf(ink, abs(c.red - background.red) + abs(c.green - background.green) + abs(c.blue - background.blue))
        }
        return ink
    }

    /** The node is really painted, not only laid out under a zero alpha. */
    private fun SemanticsNodeInteraction.assertPainted() {
        paintedColours() shouldBeGreaterThan 8
    }

    private fun assertSleepIsBack() {
        hostStep shouldBe OnboardingStep.Sleep
        compose.onNodeWithTag(OnboardingTags.step(OnboardingStep.HomeZone)).assertDoesNotExist()
        compose.onNodeWithTag(OnboardingTags.step(OnboardingStep.Chronotype)).assertDoesNotExist()
        compose.onNodeWithTag(SleepDialTags.Dial).assertPainted()
        compose.onNodeWithTag(OnboardingTags.Next).assertPainted()
    }

    @Test
    fun `back during the spectacle returns to the sleep step`() {
        showSleep()
        startSpectacle()
        compose.runOnUiThread { backDispatcher.onBackPressed() }
        settle()
        assertSleepIsBack()
    }

    @Test
    fun `the spectacle plays once per session, counted as soon as it starts`() {
        showSleep()
        startSpectacle()
        compose.runOnUiThread { backDispatcher.onBackPressed() }
        settle()
        hostStep shouldBe OnboardingStep.Sleep
        compose.onNodeWithTag(OnboardingTags.Next).performClick()
        repeat(MaxFramesToStart) {
            compose.mainClock.advanceTimeByFrame()
            spectacleShown() shouldBe false
        }
        settle()
        hostStep shouldBe OnboardingStep.Chronotype
        compose.onNodeWithTag(ChronotypeRevealTags.Name).assertPainted()
    }

    @Test
    fun `committing a predictive back during the spectacle returns to the sleep step`() {
        showSleep()
        startSpectacle()
        backGesture(progress = 0.5f)
        compose.mainClock.advanceTimeBy(100)
        compose.runOnUiThread { backDispatcher.onBackPressed() }
        settle()
        assertSleepIsBack()
    }

    @Test
    fun `cancelling a predictive back during the spectacle lets it finish on the reveal`() {
        showSleep()
        startSpectacle()
        backGesture(progress = 0.3f)
        compose.mainClock.advanceTimeBy(100)
        compose.runOnUiThread { backDispatcher.dispatchOnBackCancelled() }
        settle()
        hostStep shouldBe OnboardingStep.Chronotype
        compose.onNodeWithTag(OnboardingTags.step(OnboardingStep.Sleep)).assertDoesNotExist()
        compose.onNodeWithTag(ChronotypeRevealTags.Name).assertPainted()
        compose.onNodeWithTag(OnboardingTags.Next).assertPainted()
        calls shouldContainExactly listOf("next")
    }

    @Test
    fun `the hidden back and next take no taps during the spectacle`() {
        showSleep()
        val back = compose.onNodeWithTag(OnboardingTags.Back).getUnclippedBoundsInRoot()
        val next = compose.onNodeWithTag(OnboardingTags.Next).getUnclippedBoundsInRoot()
        startSpectacle()
        compose.onRoot().performTouchInput { click(back.centreIn(this)) }
        compose.mainClock.advanceTimeBy(100)
        // Still hidden: the tap didn't cancel the spectacle.
        compose.onNodeWithTag(OnboardingTags.Next).assertDoesNotExist()
        compose.onRoot().performTouchInput { click(next.centreIn(this)) }
        compose.mainClock.advanceTimeBy(100)
        compose.onNodeWithTag(OnboardingTags.Next).assertDoesNotExist()
        settle()
        hostStep shouldBe OnboardingStep.Chronotype
        calls shouldContainExactly listOf("next")
    }

    /**
     * Saved instance state, as an activity recreation (rotation) or process death keeps it: the content is composed
     * afresh from what it saved, with nothing it only remembered.
     */
    private var savedState: Map<String, List<Any?>>? = null
    private var registry: SaveableStateRegistry? = null
    private var shown by mutableStateOf(true)

    private fun showRestorableSleep() {
        setContent(reduceMotion = false) {
            if (shown) {
                val current = remember { SaveableStateRegistry(savedState) { true } }
                registry = current
                CompositionLocalProvider(LocalSaveableStateRegistry provides current) {
                    OnboardingContent(onboardingState(hostStep, profile = SampleSleepProfile), host, Modifier.fillMaxSize())
                }
            }
        }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
    }

    private fun recreate() {
        compose.runOnUiThread {
            savedState = requireNotNull(registry).performSave()
            shown = false
        }
        compose.mainClock.advanceTimeByFrame()
        compose.runOnUiThread { shown = true }
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
    }

    @Test
    fun `an interrupted spectacle doesn't replay after process death`() {
        showRestorableSleep()
        startSpectacle()
        // A new process starts the ViewModel over, on Welcome.
        hostStep = OnboardingStep.Welcome
        calls.clear()
        recreate()
        settle()
        hostStep shouldBe OnboardingStep.Welcome
        calls shouldContainExactly emptyList()
        compose.onNodeWithTag(ChronotypeRevealTags.Spectacle).assertDoesNotExist()
        compose.onNodeWithTag(OnboardingTags.GetStarted).assertPainted()
    }

    @Test
    fun `an interrupted spectacle doesn't resume after a rotation and the reveal is at rest`() {
        showRestorableSleep()
        startSpectacle()
        calls.clear()
        // The ViewModel survives a rotation, on the step the spectacle had moved to.
        recreate()
        compose.mainClock.advanceTimeBy(100)
        compose.onNodeWithTag(ChronotypeRevealTags.Spectacle).assertDoesNotExist()
        compose.onNodeWithTag(ChronotypeRevealTags.Name).assertPainted()
        compose.onNodeWithTag(OnboardingTags.Next).assertPainted()
        settle()
        hostStep shouldBe OnboardingStep.Chronotype
        calls shouldContainExactly emptyList()
    }

    @Test
    fun `with touch exploration on the spectacle is skipped and the reveal is at rest`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        shadowOf(context.getSystemService(AccessibilityManager::class.java)).setTouchExplorationEnabled(true)
        showSleep()
        startSpectacle(millis = 0)
        compose.onNodeWithTag(OnboardingTags.Next).assertExists()
        // What TalkBack reads is already on screen: no words still fading in.
        compose.onNodeWithTag(ChronotypeRevealTags.Name).assertPainted()
        calls shouldContainExactly listOf("next")
        compose.onNodeWithTag(ChronotypeRevealTags.Spectacle).assertDoesNotExist()
    }

    @Test
    fun `the spectacle offers TalkBack a labelled skip that lands on the reveal at rest`() {
        showSleep()
        startSpectacle()
        val skip = compose.onNodeWithTag(ChronotypeRevealTags.Spectacle).fetchSemanticsNode().config[SemanticsActions.OnClick]
        skip.label shouldBe ApplicationProvider.getApplicationContext<Context>().getString(R.string.chronotype_spectacle_skip)
        compose.onNodeWithTag(ChronotypeRevealTags.Spectacle).performSemanticsAction(SemanticsActions.OnClick)
        compose.mainClock.advanceTimeBy(500)
        assertRevealAtRest()
    }

    @Test
    fun `a tap anywhere skips the spectacle`() {
        showSleep()
        startSpectacle()
        compose.onRoot().performTouchInput { click(center) }
        compose.mainClock.advanceTimeBy(500)
        assertRevealAtRest()
        compose.onNodeWithTag(ChronotypeRevealTags.Sheet).assertDoesNotExist()
        calls shouldContainExactly listOf("next")
    }

    @Test
    fun `a tap where the link will be, before it shows, only skips`() {
        showSleep()
        startSpectacle()
        compose.onNodeWithTag(ChronotypeRevealTags.Other).performClick()
        compose.mainClock.advanceTimeBy(500)
        assertRevealAtRest()
        compose.onNodeWithTag(ChronotypeRevealTags.Sheet).assertDoesNotExist()
    }

    @Test
    fun `a first visit without the spectacle (TalkBack on) still counts as its one time`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val accessibility = shadowOf(context.getSystemService(AccessibilityManager::class.java))
        accessibility.setTouchExplorationEnabled(true)
        showSleep()
        startSpectacle(millis = 0)
        compose.runOnUiThread { backDispatcher.onBackPressed() }
        settle()
        compose.runOnUiThread { accessibility.setTouchExplorationEnabled(false) }
        compose.onNodeWithTag(OnboardingTags.Next).performClick()
        repeat(MaxFramesToStart) {
            compose.mainClock.advanceTimeByFrame()
            spectacleShown() shouldBe false
        }
    }

    @Test
    fun `touch exploration turning on before the reveal composes brings the bars straight back, for good`() {
        showSleep()
        compose.onNodeWithTag(OnboardingTags.Next).performClick()
        val context = ApplicationProvider.getApplicationContext<Context>()
        val accessibility = shadowOf(context.getSystemService(AccessibilityManager::class.java))
        compose.runOnUiThread { accessibility.setTouchExplorationEnabled(true) }
        settle()
        assertRevealAtRest()
        // Turning it off again doesn't bring back a spectacle that never started.
        compose.runOnUiThread { accessibility.setTouchExplorationEnabled(false) }
        settle()
        assertRevealAtRest()
    }

    @Test
    fun `turning touch exploration on during the spectacle lands it at rest`() {
        showSleep()
        startSpectacle()
        val context = ApplicationProvider.getApplicationContext<Context>()
        compose.runOnUiThread {
            shadowOf(context.getSystemService(AccessibilityManager::class.java)).setTouchExplorationEnabled(true)
        }
        compose.mainClock.advanceTimeBy(500)
        assertRevealAtRest()
    }

    @Test
    fun `a keyboard or switch click on the link before it shows only skips`() {
        showSleep()
        startSpectacle()
        compose.onNodeWithTag(ChronotypeRevealTags.Other).performSemanticsAction(SemanticsActions.OnClick)
        compose.mainClock.advanceTimeBy(500)
        assertRevealAtRest()
        compose.onNodeWithTag(ChronotypeRevealTags.Sheet).assertDoesNotExist()
    }

    @Test
    fun `the link opens the sheet once it shows, even while the spectacle still plays`() {
        var round by mutableIntStateOf(0)
        setContent(reduceMotion = false) {
            key(round) {
                OnboardingContent(onboardingState(hostStep, profile = SampleSleepProfile), host, Modifier.fillMaxSize())
            }
        }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        val link = compose.onNodeWithTag(ChronotypeRevealTags.Other)
        // How strongly the link reads at rest.
        startSpectacle()
        settle()
        val atRest = link.ink()
        // Afresh: tap it once it reads more than half as strongly, while the spectacle still plays.
        hostStep = OnboardingStep.Sleep
        round++
        compose.mainClock.advanceTimeByFrame()
        startSpectacle(millis = 0)
        do {
            compose.mainClock.advanceTimeByFrame()
            compose.onNodeWithTag(ChronotypeRevealTags.Spectacle).assertExists()
        } while (link.ink() < atRest * 0.6f)
        link.performClick()
        compose.mainClock.advanceTimeBy(1_000)
        compose.onNodeWithTag(ChronotypeRevealTags.Sheet).assertExists()
    }

    @Test
    fun `the spectacle hands off to exactly the reveal's layout`() {
        var round by mutableIntStateOf(0)
        setContent(reduceMotion = false) {
            key(round) {
                OnboardingContent(onboardingState(hostStep, profile = SampleSleepProfile), host, Modifier.fillMaxSize())
            }
        }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        startSpectacle(millis = 0)
        // The spectacle's last frame.
        var last = revealBounds()
        while (spectacleShown()) {
            last = revealBounds()
            compose.mainClock.advanceTimeByFrame()
        }
        // The reveal composed afresh, at rest.
        round++
        settle()
        compose.onNodeWithTag(ChronotypeRevealTags.Spectacle).assertDoesNotExist()
        revealBounds() shouldBe last
    }

    private fun assertRevealAtRest() {
        compose.onNodeWithTag(ChronotypeRevealTags.Spectacle).assertDoesNotExist()
        compose.onNodeWithTag(ChronotypeRevealTags.Name).assertPainted()
        compose.onNodeWithTag(OnboardingTags.Next).assertPainted()
    }

    private fun revealBounds(): List<DpRect> =
        listOf(ChronotypeRevealTags.Art, ChronotypeRevealTags.Name, ChronotypeRevealTags.Other)
            .map { compose.onNodeWithTag(it).getUnclippedBoundsInRoot() }

    private companion object {
        const val MaxFramesToStart = 20
    }

    private fun DpRect.centreIn(density: Density) = with(density) { Offset(((left + right) / 2).toPx(), ((top + bottom) / 2).toPx()) }
}
