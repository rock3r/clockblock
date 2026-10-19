package dev.sebastiano.clockblocker.opus.feature.onboarding

import app.cash.turbine.test
import dev.sebastiano.clockblocker.opus.core.data.EasterEggGate
import dev.sebastiano.clockblocker.opus.core.data.demo.DemoData
import dev.sebastiano.clockblocker.opus.core.data.time.DeviceZone
import dev.sebastiano.clockblocker.opus.core.model.AdviceType
import dev.sebastiano.clockblocker.opus.core.model.AppSettings
import dev.sebastiano.clockblocker.opus.core.model.Chronotype
import dev.sebastiano.clockblocker.opus.core.model.Intensity
import dev.sebastiano.clockblocker.opus.core.model.SleepWindow
import dev.sebastiano.clockblocker.opus.core.model.UserProfile
import dev.sebastiano.clockblocker.opus.core.notifications.NowNotificationContent
import dev.sebastiano.clockblocker.opus.core.testing.FakeJetLagPlanner
import dev.sebastiano.clockblocker.opus.core.testing.FakePlaceSearch
import dev.sebastiano.clockblocker.opus.core.testing.FakePlanRepository
import dev.sebastiano.clockblocker.opus.core.testing.FakeProfileRepository
import dev.sebastiano.clockblocker.opus.core.testing.FakeSettingsRepository
import dev.sebastiano.clockblocker.opus.core.testing.MainDispatcherRule
import dev.sebastiano.clockblocker.opus.core.testing.MutableClock
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

class OnboardingViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val profiles = FakeProfileRepository()
    private val places = FakePlaceSearch()
    private val permissions = FakeNotificationPermissions()
    // Like production: the injected Clock is UTC; the device zone comes only from DeviceZone.
    private val clock = Clock.fixed(Instant.parse("2026-10-04T10:00:00Z"), java.time.ZoneOffset.UTC)
    private val deviceZone = DeviceZone { ZoneId.of("Europe/Rome") }

    private val settings = FakeSettingsRepository()
    private val plans = FakePlanRepository()
    private val ticker = MutableClock()
    private var content: NowNotificationContent = NowNotificationContent.None

    private fun viewModel() =
        OnboardingViewModel(profiles, places, permissions, clock, deviceZone, EasterEggGate(settings, plans, ticker), content)

    @Test
    fun `the sleep dial egg is on by default`() = runTest {
        viewModel().state.value.easterEggs.shouldBeTrue()
    }

    @Test
    fun `the sleep dial egg is off with reduce motion on`() = runTest {
        settings.set(AppSettings(reduceMotion = true))
        viewModel().state.value.easterEggs.shouldBeFalse()
    }

    @Test
    fun `the sleep dial egg is off while the current plan says sleep, and back after`() = runTest {
        val plan = FakeJetLagPlanner().plan(DemoData.sfoToLhr(), DemoData.profile, ticker.instant())
        val sleep = plan.allAdvice.first { it.type == AdviceType.Sleep }
        plans.setCurrentPlan(plan)
        ticker.set(sleep.start)
        val vm = viewModel()
        vm.state.value.easterEggs.shouldBeFalse()
        ticker.set(sleep.end)
        vm.state.value.easterEggs.shouldBeTrue()
    }

    @Test
    fun `starts on welcome with the device zone and sensible defaults`() = runTest {
        val state = viewModel().state.value
        state.step shouldBe OnboardingStep.Welcome
        state.profile.homeZoneId shouldBe "Europe/Rome"
        state.deviceZoneId shouldBe "Europe/Rome"
        state.profile.sleep shouldBe SleepWindow.Default
        state.profile.useMelatonin.shouldBeFalse()
        state.usingDeviceZone.shouldBeTrue()
    }

    @Test
    fun `replaying onboarding starts from the saved profile`() = runTest {
        val saved = UserProfile("Asia/Tokyo", chronotype = Chronotype.DefiniteEvening, intensity = Intensity.Max)
        profiles.set(saved)
        val state = viewModel().state.value
        state.profile shouldBe saved
        state.usingDeviceZone.shouldBeFalse()
    }

    @Test
    fun `next and back walk the steps, back on welcome is not consumed`() = runTest {
        val vm = viewModel()
        vm.back().shouldBeFalse()
        vm.next()
        vm.state.value.step shouldBe OnboardingStep.HomeZone
        vm.next()
        vm.state.value.step shouldBe OnboardingStep.Sleep
        vm.back().shouldBeTrue()
        vm.state.value.step shouldBe OnboardingStep.HomeZone
    }

    @Test
    fun `skip jumps to reminders keeping defaults`() = runTest {
        val vm = viewModel()
        vm.next()
        vm.skipToEnd()
        vm.state.value.step shouldBe OnboardingStep.Reminders
    }

    @Test
    fun `searching places and picking one sets the home zone`() = runTest {
        val vm = viewModel()
        vm.onQueryChange("tok")
        vm.state.value.results.map { it.code } shouldContain "HND"
        vm.selectPlace(DemoData.HND)
        val state = vm.state.value
        state.profile.homeZoneId shouldBe "Asia/Tokyo"
        state.homePlace shouldBe DemoData.HND
        state.query shouldBe ""
        state.results.shouldBeEmpty()
        state.usingDeviceZone.shouldBeFalse()

        vm.useDeviceZone()
        vm.state.value.profile.homeZoneId shouldBe "Europe/Rome"
        vm.state.value.homePlace.shouldBeNull()
    }

    @Test
    fun `blank queries clear the results`() = runTest {
        val vm = viewModel()
        vm.onQueryChange("lon")
        vm.onQueryChange("  ")
        vm.state.value.results.shouldBeEmpty()
    }

    @Test
    fun `profile edits update the draft`() = runTest {
        val vm = viewModel()
        val window = SleepWindow(LocalTime.of(0, 30), LocalTime.of(8, 30))
        vm.setSleep(window)
        vm.setChronotype(Chronotype.ModerateMorning)
        vm.setIntensity(Intensity.Gentle)
        vm.updateProfile { it.copy(useCaffeine = false, canSleepOnPlanes = false, headStartDays = 0) }
        val p = vm.state.value.profile
        p.sleep shouldBe window
        p.chronotype shouldBe Chronotype.ModerateMorning
        p.intensity shouldBe Intensity.Gentle
        p.useCaffeine.shouldBeFalse()
        p.canSleepOnPlanes.shouldBeFalse()
        p.headStartDays shouldBe 0
    }

    @Test
    fun `melatonin needs the safety note acknowledged first`() = runTest {
        val vm = viewModel()
        vm.setMelatonin(true)
        vm.state.value.profile.useMelatonin.shouldBeFalse()
        vm.acknowledgeMelatoninNote(true)
        vm.setMelatonin(true)
        vm.state.value.profile.useMelatonin.shouldBeTrue()
        // Withdrawing the acknowledgement switches melatonin back off.
        vm.acknowledgeMelatoninNote(false)
        vm.state.value.profile.useMelatonin.shouldBeFalse()
    }

    @Test
    fun `permission state refreshes on demand`() = runTest {
        val vm = viewModel()
        vm.state.value.permissions.notificationsGranted.shouldBeFalse()
        permissions.current = FakeNotificationPermissions.AllGranted
        vm.refreshPermissions()
        vm.state.value.permissions.notificationsGranted.shouldBeTrue()
    }

    @Test
    fun `finish saves the profile once and reports completion`() = runTest {
        val vm = viewModel()
        vm.setChronotype(Chronotype.ModerateEvening)
        vm.finished.test {
            awaitItem().shouldBeFalse()
            vm.finish()
            awaitItem().shouldBeTrue()
            cancelAndIgnoreRemainingEvents()
        }
        vm.finish()
        profiles.saveCount shouldBe 1
        profiles.current?.chronotype shouldBe Chronotype.ModerateEvening
    }

    @Test
    fun `finishing leaves the UI state alone, so the take-off doesn't recompose the screen`() = runTest {
        val vm = viewModel()
        val before = vm.state.value
        vm.finish()
        vm.finished.first { it }
        vm.state.value shouldBeSameInstanceAs before
    }

    @Test
    fun `five steps are counted, the chronotype reveal sharing the sleep step's place`() {
        OnboardingStep.Count shouldBe 5
        OnboardingStep.Sleep.index shouldBe 2
        OnboardingStep.Chronotype.index shouldBe 2
        OnboardingStep.Tools.index shouldBe 3
        OnboardingStep.Reminders.index shouldBe OnboardingStep.Count - 1
    }

    @Test
    fun `next from the sleep step reveals the chronotype, then goes on to tools`() = runTest {
        val vm = viewModel()
        repeat(3) { vm.next() }
        vm.state.value.step shouldBe OnboardingStep.Chronotype
        vm.next()
        vm.state.value.step shouldBe OnboardingStep.Tools
        vm.back().shouldBeTrue()
        vm.state.value.step shouldBe OnboardingStep.Chronotype
    }

    @Test
    fun `the chronotype follows the usual nights, days off linked to work days by default`() = runTest {
        val vm = viewModel()
        vm.state.value.profile.chronotype shouldBe Chronotype.Intermediate // the default night, 23:00–07:00
        vm.setSleep(SleepWindow(LocalTime.of(3, 0), LocalTime.of(11, 0)))
        vm.state.value.profile.chronotype shouldBe Chronotype.DefiniteEvening
        vm.state.value.profile.daysOffSleep.shouldBeNull()
        vm.state.value.chronotypePicked.shouldBeFalse()
        vm.state.value.showsPick.shouldBeFalse()
        vm.setSleep(SleepWindow(LocalTime.of(21, 30), LocalTime.of(5, 30)))
        vm.state.value.profile.chronotype shouldBe Chronotype.DefiniteMorning
        vm.state.value.inferred.midSleep shouldBe LocalTime.of(1, 30)
    }

    @Test
    fun `days off unlink on their first change and feed the chronotype, corrected for catch-up sleep`() = runTest {
        val vm = viewModel()
        val daysOff = SleepWindow(LocalTime.of(1, 0), LocalTime.of(10, 0))
        vm.setDaysOffSleep(daysOff)
        val p = vm.state.value.profile
        p.sleep shouldBe SleepWindow.Default // work days stay the planner's sleep
        p.daysOffSleep shouldBe daysOff
        p.chronotype shouldBe Chronotype.ModerateEvening // MSF 05:30, corrected to about 05:09
        vm.state.value.inferred.midSleep shouldBe LocalTime.of(5, 8)
    }

    @Test
    fun `a picked chronotype stays when the night changes, until the inferred one is picked again`() = runTest {
        val vm = viewModel()
        vm.setChronotype(Chronotype.ModerateMorning)
        vm.state.value.chronotypePicked.shouldBeTrue()
        vm.setSleep(SleepWindow(LocalTime.of(2, 0), LocalTime.of(10, 0)))
        vm.state.value.profile.chronotype shouldBe Chronotype.ModerateMorning

        vm.setChronotype(vm.state.value.inferred.chronotype)
        vm.state.value.chronotypePicked.shouldBeFalse()
        vm.setSleep(SleepWindow(LocalTime.of(23, 0), LocalTime.of(7, 0)))
        vm.state.value.profile.chronotype shouldBe Chronotype.Intermediate
    }

    @Test
    fun `a replayed profile keeps its chronotype, shown as picked when the night says otherwise`() = runTest {
        profiles.set(UserProfile("Asia/Tokyo", chronotype = Chronotype.DefiniteEvening))
        val vm = viewModel()
        vm.state.value.chronotypePicked.shouldBeTrue()
        vm.state.value.showsPick.shouldBeTrue()
        vm.setSleep(SleepWindow(LocalTime.of(22, 0), LocalTime.of(6, 0)))
        vm.state.value.profile.chronotype shouldBe Chronotype.DefiniteEvening
    }

    @Test
    fun `a replayed chronotype that matches the nights still isn't overwritten when they change`() = runTest {
        profiles.set(UserProfile("Asia/Tokyo", chronotype = Chronotype.Intermediate)) // the default nights say the same
        val vm = viewModel()
        vm.state.value.showsPick.shouldBeFalse()
        vm.setSleep(SleepWindow(LocalTime.of(3, 0), LocalTime.of(11, 0)))
        vm.state.value.profile.chronotype shouldBe Chronotype.Intermediate
        vm.state.value.showsPick.shouldBeTrue()
    }

    @Test
    fun `dragging the nights through the picked chronotype's band doesn't hand it back to the nights`() = runTest {
        val vm = viewModel()
        vm.setChronotype(Chronotype.ModerateMorning)
        vm.setSleep(SleepWindow(LocalTime.of(22, 0), LocalTime.of(6, 0))) // passes through Morning person (02:00)
        vm.setSleep(SleepWindow(LocalTime.of(3, 0), LocalTime.of(11, 0)))
        vm.state.value.profile.chronotype shouldBe Chronotype.ModerateMorning
        vm.state.value.chronotypePicked.shouldBeTrue()
    }

    @Test
    fun `skipping after setting the night saves the chronotype that night gives`() = runTest {
        val vm = viewModel()
        vm.next()
        vm.next()
        vm.setSleep(SleepWindow(LocalTime.of(1, 30), LocalTime.of(9, 30)))
        vm.skipToEnd()
        vm.finish()
        profiles.current?.chronotype shouldBe Chronotype.ModerateEvening
        profiles.current?.sleep shouldBe SleepWindow(LocalTime.of(1, 30), LocalTime.of(9, 30))
    }

    @Test
    fun `reminders are handled once notifications are allowed or the prompt has answered`() = runTest {
        val vm = viewModel()
        vm.state.value.remindersHandled.shouldBeFalse()
        vm.onNotificationsResult() // denied
        vm.state.value.remindersHandled.shouldBeTrue()
        vm.state.value.permissions.notificationsGranted.shouldBeFalse()

        val granted = viewModel()
        permissions.current = permissions.current.copy(notificationsGranted = true)
        granted.refreshPermissions()
        granted.state.value.remindersHandled.shouldBeTrue()
    }

    @Test
    fun `exact timing is offered only after notifications, and only where it can be granted`() = runTest {
        val vm = viewModel()
        permissions.current = permissions.current.copy(notificationsGranted = false, exactAlarmsAllowed = false)
        vm.refreshPermissions()
        vm.state.value.showExactTiming.shouldBeFalse()

        // A build outside Google Play: exact alarms come with the install, nothing to offer.
        permissions.current = permissions.current.copy(notificationsGranted = true, exactAlarmsAllowed = true)
        vm.refreshPermissions()
        vm.state.value.showExactTiming.shouldBeFalse()

        // Google Play, denied: offered; granting it keeps the row (now "Allowed") instead of removing it.
        permissions.current = permissions.current.copy(exactAlarmsAllowed = false)
        vm.refreshPermissions()
        vm.state.value.showExactTiming.shouldBeTrue()
        permissions.current = permissions.current.copy(exactAlarmsAllowed = true)
        vm.refreshPermissions()
        vm.state.value.showExactTiming.shouldBeTrue()
    }

    @Test
    fun `exact timing is never offered where the build can't ask for it`() = runTest {
        permissions.current = permissions.current.copy(notificationsGranted = true, exactAlarmsAllowed = false, exactAlarmsRequestable = false)
        val vm = viewModel()
        vm.refreshPermissions()
        vm.state.value.showExactTiming.shouldBeFalse()
    }

    @Test
    fun `a notification preview that fails leaves the standard look`() = runTest {
        content = object : NowNotificationContent by NowNotificationContent.None {
            override suspend fun preview(now: Instant, widthDp: Int): android.widget.RemoteViews? = error("no player")
        }
        val vm = viewModel()
        vm.loadNotificationPreview(300)
        vm.state.value.notificationPreview.shouldBeNull()
    }
}
