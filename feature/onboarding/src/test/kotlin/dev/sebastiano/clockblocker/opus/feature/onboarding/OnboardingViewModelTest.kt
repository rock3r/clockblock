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

    private fun viewModel() =
        OnboardingViewModel(profiles, places, permissions, clock, deviceZone, EasterEggGate(settings, plans, ticker))

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
        vm.updateProfile { it.copy(useCaffeine = false, canSleepOnPlanes = false, adjustBeforeDeparture = false) }
        val p = vm.state.value.profile
        p.sleep shouldBe window
        p.chronotype shouldBe Chronotype.ModerateMorning
        p.intensity shouldBe Intensity.Gentle
        p.useCaffeine.shouldBeFalse()
        p.canSleepOnPlanes.shouldBeFalse()
        p.adjustBeforeDeparture.shouldBeFalse()
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
        vm.state.test {
            awaitItem().isFinished.shouldBeFalse()
            vm.finish()
            var item = awaitItem()
            while (!item.isFinished) item = awaitItem()
            cancelAndIgnoreRemainingEvents()
        }
        vm.finish()
        profiles.saveCount shouldBe 1
        profiles.current?.chronotype shouldBe Chronotype.ModerateEvening
    }
}
