package dev.sebastiano.clockblocker.opus.e2e

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performCustomAccessibilityActionWithLabel
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.sebastiano.clockblocker.opus.core.model.Chronotype
import dev.sebastiano.clockblocker.opus.core.model.Intensity
import dev.sebastiano.clockblocker.opus.core.model.UserProfile
import dev.sebastiano.clockblocker.opus.feature.onboarding.HomeZoneTags
import dev.sebastiano.clockblocker.opus.feature.onboarding.OnboardingStep
import dev.sebastiano.clockblocker.opus.feature.onboarding.OnboardingTags
import dev.sebastiano.clockblocker.opus.feature.onboarding.R as OnboardingR
import dev.sebastiano.clockblocker.opus.feature.onboarding.profile.ChronotypeRevealTags
import dev.sebastiano.clockblocker.opus.feature.onboarding.profile.ChronotypeTags
import dev.sebastiano.clockblocker.opus.feature.onboarding.profile.SleepDialTags
import dev.sebastiano.clockblocker.opus.feature.onboarding.profile.ToolsTags
import dev.sebastiano.clockblocker.opus.shell.ShellTestTags
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * First run from cleared data: all five onboarding steps (and the chronotype reveal), then the app lands on Trips (no
 * trips yet). The variant
 * that grants notifications through the system dialog lives in [NotificationsTest] (it must run before anything
 * grants the permission).
 */
@RunWith(AndroidJUnit4::class)
class FirstRunTest : ClockblockE2eTest() {

    @Test
    fun onboardingThroughAllStepsWithoutThePromptLandsOnTrips() {
        launch()
        val choices = walkOnboardingToReminders()

        finishOnboardingWithoutPrompt()

        awaitTag("route_trips", LongTimeoutMillis).assertIsDisplayed()
        awaitTag(ShellTestTags.NavTrips).assertIsDisplayed()
        choices.assertSaved(savedProfile())
    }

    @Test
    fun onboardingCanBeSkippedToRemindersAndBackStepsBack() {
        launch()
        awaitTag(OnboardingTags.GetStarted).performClick()
        awaitTag(OnboardingTags.step(OnboardingStep.HomeZone))

        // Back (system) from the second step returns to the first.
        awaitTag(OnboardingTags.Next)
        device.pressBack()
        awaitTag(OnboardingTags.step(OnboardingStep.Welcome))
        awaitTag(OnboardingTags.GetStarted).performClick()

        awaitTag(OnboardingTags.Skip).performClick()
        awaitTag(OnboardingTags.step(OnboardingStep.Reminders))
        finishOnboardingWithoutPrompt()

        awaitTag("route_trips", LongTimeoutMillis).assertIsDisplayed()
    }

    private fun savedProfile(): UserProfile = runBlocking {
        withTimeout(5_000) { graph.profileRepository.profile.filterNotNull().first() }
    }
}

/** What [walkOnboardingToReminders] chose, to check against the saved profile. */
data class OnboardingChoices(val bedtimeBefore: String?, val bedtimeAfter: String?, val wakeBefore: String?, val wakeAfter: String?) {
    fun assertSaved(profile: UserProfile) {
        assertEquals("Europe/Lisbon", profile.homeZoneId)
        assertEquals(Chronotype.ModerateEvening, profile.chronotype)
        assertNotNull("days off set on their own", profile.daysOffSleep)
        assertNotEquals("days off differ from work days", profile.sleep, profile.daysOffSleep)
        assertTrue("melatonin opted in after the safety note", profile.useMelatonin)
        assertEquals(Intensity.Gentle, profile.intensity)
        assertNotEquals("bedtime moved", bedtimeBefore, bedtimeAfter)
        assertNotEquals("wake time moved", wakeBefore, wakeAfter)
    }
}

/**
 * Welcome → home zone (search "lis", pick Lisbon) → sleep (both work-day handles moved through their accessibility
 * actions, as TalkBack would, then a later wake-up on days off) → the chronotype reveal (another one picked from the
 * "Doesn't sound like me" sheet) → tools (melatonin behind its safety note, effort) → reminders.
 */
@OptIn(ExperimentalTestApi::class)
fun ClockblockE2eTest.walkOnboardingToReminders(): OnboardingChoices {
    awaitTag("route_onboarding", 20_000)
    awaitTag(OnboardingTags.GetStarted).performClick()

    // Home zone.
    awaitTag(OnboardingTags.step(OnboardingStep.HomeZone))
    searchUntilResult(HomeZoneTags.Search, "lis", "home_zone_result_LIS")
    // With the keyboard up the results start below the fold (see docs/qa/device-qa-1.md), and they move while the
    // keyboard animates in: pickSearchResult waits for both to settle, scrolls, taps and retries once (issue #54).
    pickSearchResult("home_zone_result_LIS", hasTestTag(HomeZoneTags.Current) and hasAnyDescendant(hasText("Lisbon", substring = true)))
    awaitTag(OnboardingTags.Next).performClick()

    // Sleep: bedtime via the "Later" custom action, wake via SetProgress (the slider-style semantics).
    awaitTag(OnboardingTags.step(OnboardingStep.Sleep))
    val bedtime = awaitTag(SleepDialTags.Bedtime)
    val bedtimeBefore = bedtime.stateDescription
    bedtime.performCustomAccessibilityActionWithLabel(context.getString(OnboardingR.string.sleep_action_later))
    val bedtimeAfter = awaitTag(SleepDialTags.Bedtime).stateDescription
    val wake = awaitTag(SleepDialTags.Wake)
    val wakeBefore = wake.stateDescription
    val wakeMinute = wake.fetchSemanticsNode().config[SemanticsProperties.ProgressBarRangeInfo].current
    wake.performSemanticsAction(SemanticsActions.SetProgress) { setProgress -> setProgress(wakeMinute - 30f) }
    val wakeAfter = awaitTag(SleepDialTags.Wake).stateDescription
    // Days off: the tab switches the handles to the other ring; a lie-in unlinks them from work days.
    awaitTag(SleepDialTags.DaysOff).scrollToIfScrollable().performClick()
    awaitTag(SleepDialTags.DaysOff).assertIsOn()
    val daysOffWake = awaitTag(SleepDialTags.Wake)
    val daysOffMinute = daysOffWake.fetchSemanticsNode().config[SemanticsProperties.ProgressBarRangeInfo].current
    daysOffWake.performSemanticsAction(SemanticsActions.SetProgress) { setProgress -> setProgress(daysOffMinute + 90f) }
    awaitTag(OnboardingTags.Next).performClick()

    // The chronotype worked out from those nights, then another one picked from the sheet.
    awaitTag(OnboardingTags.step(OnboardingStep.Chronotype))
    awaitTag(ChronotypeRevealTags.Name).assertIsDisplayed()
    awaitTag(ChronotypeRevealTags.Other).scrollToIfScrollable().performClick()
    awaitTag(ChronotypeTags.option(Chronotype.ModerateEvening)).scrollToIfScrollable().performClick()
    awaitGone(ChronotypeRevealTags.Sheet)
    awaitTag(ChronotypeRevealTags.Name).assert(hasText("Evening person"))
    awaitTag(OnboardingTags.Next).performClick()

    // Tools: tapping melatonin first opens the safety note instead of switching it on.
    awaitTag(OnboardingTags.step(OnboardingStep.Tools))
    awaitTag(ToolsTags.Melatonin).scrollToIfScrollable().performClick()
    awaitTag(ToolsTags.MelatoninAck).scrollToIfScrollable()
    awaitTag(ToolsTags.Melatonin).assertIsOff()
    awaitTag(ToolsTags.MelatoninAck).performClick()
    awaitTag(ToolsTags.MelatoninAck).assertIsOn()
    awaitTag(ToolsTags.Melatonin).scrollToIfScrollable().performClick()
    awaitTag(ToolsTags.Melatonin).assertIsOn()
    awaitTag(ToolsTags.effort(Intensity.Gentle)).scrollToIfScrollable().performClick()
    awaitTag(OnboardingTags.Next).performClick()

    awaitTag(OnboardingTags.step(OnboardingStep.Reminders))
    return OnboardingChoices(bedtimeBefore, bedtimeAfter, wakeBefore, wakeAfter)
}
