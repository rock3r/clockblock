package dev.sebastiano.clockblocker.opus.feature.onboarding

import androidx.activity.BackEventCompat
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import dev.sebastiano.clockblocker.opus.core.model.Chronotype
import dev.sebastiano.clockblocker.opus.core.model.Intensity
import dev.sebastiano.clockblocker.opus.core.model.Place
import dev.sebastiano.clockblocker.opus.core.model.SleepWindow
import dev.sebastiano.clockblocker.opus.core.model.UserProfile
import dev.sebastiano.clockblocker.opus.feature.onboarding.profile.ChronotypeTags
import dev.sebastiano.clockblocker.opus.feature.onboarding.profile.SleepDialTags
import dev.sebastiano.clockblocker.opus.feature.onboarding.profile.ToolsTags
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.collections.shouldNotContain
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Records every call so tests can assert on what the UI asked for. */
private class RecordingActions : OnboardingActions {
    val calls = mutableListOf<String>()
    override fun next() { calls += "next" }
    override fun back() { calls += "back" }
    override fun skip() { calls += "skip" }
    override fun queryChange(query: String) { calls += "query:$query" }
    override fun selectPlace(place: Place) { calls += "place:${place.code}" }
    override fun useDeviceZone() { calls += "deviceZone" }
    override fun sleepChange(window: SleepWindow) { calls += "sleep" }
    override fun chronotypeChange(chronotype: Chronotype) { calls += "chronotype:${chronotype.name}" }
    override fun profileChange(profile: UserProfile) { calls += "profile" }
    override fun melatoninChange(enabled: Boolean) { calls += "melatonin:$enabled" }
    override fun acknowledgeMelatonin(acknowledged: Boolean) { calls += "ack:$acknowledged" }
    override fun intensityChange(intensity: Intensity) { calls += "intensity:${intensity.name}" }
    override fun requestNotifications(thenFinish: Boolean) { calls += "notifications:$thenFinish" }
    override fun openExactAlarmSettings() { calls += "exact" }
    override fun finish() { calls += "finish" }
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [37], qualifiers = "w400dp-h860dp-xxhdpi")
class OnboardingContentTest : ClockblockScreenshotTest() {
    private val actions = RecordingActions()

    private fun show(state: OnboardingUiState) =
        setContent { OnboardingContent(state, actions, Modifier.fillMaxSize()) }

    @Test
    fun `get started moves on from welcome`() {
        show(onboardingState(OnboardingStep.Welcome))
        compose.onNodeWithTag(OnboardingTags.step(OnboardingStep.Welcome)).assertIsDisplayed()
        compose.onNodeWithTag(OnboardingTags.GetStarted).performClick()
        actions.calls shouldContainExactly listOf("next")
    }

    @Test
    fun `next, back and skip are wired on a middle step`() {
        show(onboardingState(OnboardingStep.Sleep))
        compose.onNodeWithTag(OnboardingTags.Next).performClick()
        compose.onNodeWithTag(OnboardingTags.Back).performClick()
        compose.onNodeWithTag(OnboardingTags.Skip).performClick()
        actions.calls shouldContainExactly listOf("next", "back", "skip")
    }

    @Test
    fun `picking a chronotype card reports it`() {
        show(onboardingState(OnboardingStep.Chronotype))
        compose.onNodeWithTag(ChronotypeTags.option(Chronotype.DefiniteEvening)).performScrollTo().performClick()
        actions.calls shouldContainExactly listOf("chronotype:DefiniteEvening")
    }

    @Test
    fun `melatonin cannot be switched on before the safety note is acknowledged`() {
        show(onboardingState(OnboardingStep.Tools))
        compose.onNodeWithTag(ToolsTags.Melatonin).performScrollTo().assertIsOff().performClick()
        actions.calls shouldNotContain "melatonin:true"
        // The tap opens the note instead, with its acknowledgement.
        compose.onNodeWithTag(ToolsTags.MelatoninAck).performScrollTo().performClick()
        actions.calls shouldContainExactly listOf("ack:true")
    }

    @Test
    fun `melatonin switches on once acknowledged`() {
        show(onboardingState(OnboardingStep.Tools, melatoninAcknowledged = true))
        compose.onNodeWithTag(ToolsTags.Melatonin).performScrollTo().performClick()
        actions.calls shouldContainExactly listOf("melatonin:true")
    }

    @Test
    fun `finish asks for notifications first, maybe later just finishes`() {
        show(onboardingState(OnboardingStep.Reminders))
        compose.onNodeWithTag(OnboardingTags.Skip).assertIsNotEnabled()
        compose.onNodeWithTag(OnboardingTags.Finish).performClick()
        compose.onNodeWithTag(OnboardingTags.MaybeLater).performClick()
        compose.onNodeWithTag(RemindersTags.ExactAlarm).performClick()
        actions.calls shouldContainExactly listOf("notifications:true", "finish", "exact")
    }

    @Test
    fun `finish goes straight through when notifications are already allowed`() {
        show(onboardingState(OnboardingStep.Reminders, notificationsGranted = true))
        compose.onNodeWithTag(OnboardingTags.Finish).performClick()
        actions.calls shouldContainExactly listOf("finish")
    }

    private var step by mutableStateOf(OnboardingStep.Chronotype)
    private lateinit var backDispatcher: OnBackPressedDispatcher

    /** A host whose Back really moves to the previous step, as the ViewModel does. */
    private fun showNavigable() {
        val navigating = object : OnboardingActions by actions {
            override fun back() {
                actions.back()
                step = OnboardingStep.entries[step.ordinal - 1]
            }
        }
        setContent(reduceMotion = false) {
            backDispatcher = requireNotNull(LocalOnBackPressedDispatcherOwner.current).onBackPressedDispatcher
            OnboardingContent(onboardingState(step), navigating, Modifier.fillMaxSize())
        }
        compose.waitForIdle()
    }

    private fun backGesture(progress: Float) = compose.runOnUiThread {
        backDispatcher.dispatchOnBackStarted(BackEventCompat(0f, 900f, 0f, BackEventCompat.EDGE_LEFT))
        backDispatcher.dispatchOnBackProgressed(BackEventCompat(120f, 900f, progress, BackEventCompat.EDGE_LEFT))
    }

    @Test
    fun `the predictive back gesture reveals the previous step, cancelling stays put`() {
        showNavigable()
        backGesture(progress = 0.3f)
        compose.waitForIdle()
        // Both steps are composed mid-gesture: the current one drifting off, the previous one underneath.
        compose.onNodeWithTag(OnboardingTags.step(OnboardingStep.Sleep)).assertExists()
        compose.onNodeWithTag(OnboardingTags.step(OnboardingStep.Chronotype)).assertExists()
        capture("onboarding_predictive_back_30")

        compose.runOnUiThread { backDispatcher.dispatchOnBackCancelled() }
        compose.mainClock.advanceTimeBy(2_000)
        compose.waitForIdle()
        compose.onNodeWithTag(OnboardingTags.step(OnboardingStep.Sleep)).assertDoesNotExist()
        compose.onNodeWithTag(OnboardingTags.step(OnboardingStep.Chronotype)).assertIsDisplayed()
        actions.calls shouldContainExactly emptyList()
    }

    @Test
    fun `committing the predictive back gesture goes to the previous step`() {
        showNavigable()
        backGesture(progress = 0.5f)
        compose.runOnUiThread { backDispatcher.onBackPressed() }
        compose.mainClock.advanceTimeBy(2_000)
        compose.waitForIdle()
        actions.calls shouldContainExactly listOf("back")
        compose.onNodeWithTag(OnboardingTags.step(OnboardingStep.Chronotype)).assertDoesNotExist()
        compose.onNodeWithTag(OnboardingTags.step(OnboardingStep.Sleep)).assertIsDisplayed()
    }

    @Test
    fun `the headline sits at the same height on every question step`() = assertHeadlineRhythm(fontScale = 1f)

    @Test
    fun `the headline sits at the same height on every question step at a large font`() = assertHeadlineRhythm(fontScale = 1.5f)

    /** Steps 2 to 5 share one header (art, headline, body): stepping through them never moves the headline. */
    private fun assertHeadlineRhythm(fontScale: Float) {
        var step by mutableStateOf(OnboardingStep.HomeZone)
        setContent(fontScale = fontScale) { OnboardingContent(onboardingState(step), actions, Modifier.fillMaxSize()) }
        val context = ApplicationProvider.getApplicationContext<Context>()
        val tops = listOf(
            OnboardingStep.HomeZone to R.string.home_headline,
            OnboardingStep.Sleep to R.string.sleep_headline,
            OnboardingStep.Chronotype to R.string.chronotype_headline,
            OnboardingStep.Tools to R.string.tools_headline,
        ).map { (target, headline) ->
            step = target
            compose.mainClock.advanceTimeBy(2_000)
            compose.waitForIdle()
            compose.onNodeWithText(context.getString(headline)).getUnclippedBoundsInRoot().top
        }
        tops.distinct() shouldHaveSize 1
    }

    /** The sleep dial has a minimum size; when it doesn't fit, it scrolls inside the step and never pushes Back/Next off. */
    @Test
    @Config(qualifiers = "w360dp-h640dp-xxhdpi")
    fun `back and next stay on screen on a small phone at the largest font`() {
        setContent(fontScale = 2f) { OnboardingContent(onboardingState(OnboardingStep.Sleep), actions, Modifier.fillMaxSize()) }
        compose.onNodeWithTag(OnboardingTags.Back).assertIsDisplayed()
        compose.onNodeWithTag(OnboardingTags.Next).assertIsDisplayed().performClick()
        compose.onNodeWithTag(SleepDialTags.WakePill).performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag(OnboardingTags.Next).assertIsDisplayed()
        actions.calls shouldContainExactly listOf("next")
    }
}
