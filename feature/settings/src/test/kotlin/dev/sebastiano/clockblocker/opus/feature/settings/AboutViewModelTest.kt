package dev.sebastiano.clockblocker.opus.feature.settings

import app.cash.turbine.test
import dev.sebastiano.clockblocker.opus.core.data.EasterEggGate
import dev.sebastiano.clockblocker.opus.core.data.demo.DemoData
import dev.sebastiano.clockblocker.opus.core.model.AdviceType
import dev.sebastiano.clockblocker.opus.core.model.AppSettings
import dev.sebastiano.clockblocker.opus.core.testing.FakeJetLagPlanner
import dev.sebastiano.clockblocker.opus.core.testing.FakePlanRepository
import dev.sebastiano.clockblocker.opus.core.testing.FakeSettingsRepository
import dev.sebastiano.clockblocker.opus.core.testing.MainDispatcherRule
import dev.sebastiano.clockblocker.opus.core.testing.MutableClock
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

class AboutViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val settings = FakeSettingsRepository()
    private val plans = FakePlanRepository()
    private val clock = MutableClock()

    private fun viewModel() = AboutViewModel(settings, EasterEggGate(settings, plans, clock))

    @Test
    fun `the first taps are silent, then it counts down, and the seventh unlocks opus mode`() = runTest {
        val vm = viewModel()
        vm.events.test {
            repeat(3) { vm.onVersionTapped() }
            expectNoEvents()
            vm.onVersionTapped()
            awaitItem() shouldBe AboutEvent.TapsAway(3)
            vm.onVersionTapped()
            awaitItem() shouldBe AboutEvent.TapsAway(2)
            vm.onVersionTapped()
            awaitItem() shouldBe AboutEvent.TapsAway(1)
            settings.current.opusModeUnlocked.shouldBeFalse()
            vm.onVersionTapped()
            awaitItem() shouldBe AboutEvent.Unlocked
        }
        settings.current.opusModeUnlocked.shouldBeTrue()
        settings.current.opusModeEnabled.shouldBeTrue()
    }

    @Test
    fun `once unlocked, more taps just say so`() = runTest {
        settings.set(AppSettings(opusModeUnlocked = true))
        val vm = viewModel()
        vm.events.test {
            vm.onVersionTapped()
            awaitItem() shouldBe AboutEvent.AlreadyUnlocked
        }
    }

    @Test
    fun `the title card can be dismissed`() = runTest {
        val vm = viewModel()
        vm.showTitleCard.test {
            awaitItem().shouldBeFalse()
            repeat(AboutViewModel.TapsToUnlock) { vm.onVersionTapped() }
            awaitItem().shouldBeTrue()
            vm.dismissTitleCard()
            awaitItem().shouldBeFalse()
        }
    }

    @Test
    fun `the title card goes away when the gate closes`() = runTest {
        val vm = viewModel()
        vm.showTitleCard.test {
            awaitItem().shouldBeFalse()
            repeat(AboutViewModel.TapsToUnlock) { vm.onVersionTapped() }
            awaitItem().shouldBeTrue()
            settings.update { it.copy(reduceMotion = true) }
            awaitItem().shouldBeFalse()
        }
    }

    @Test
    fun `with reduce motion on, taps do nothing`() = runTest {
        settings.set(AppSettings(reduceMotion = true))
        val vm = viewModel()
        vm.events.test {
            repeat(AboutViewModel.TapsToUnlock) { vm.onVersionTapped() }
            expectNoEvents()
        }
        settings.current.opusModeUnlocked.shouldBeFalse()
    }

    @Test
    fun `while the current plan says sleep, taps do nothing`() = runTest {
        val plan = FakeJetLagPlanner().plan(DemoData.sfoToLhr(), DemoData.profile, clock.instant())
        plans.setCurrentPlan(plan)
        clock.set(plan.allAdvice.first { it.type == AdviceType.Sleep }.start)
        val vm = viewModel()
        vm.events.test {
            repeat(AboutViewModel.TapsToUnlock) { vm.onVersionTapped() }
            expectNoEvents()
        }
        settings.current.opusModeUnlocked.shouldBeFalse()
        vm.showTitleCard.value.shouldBeFalse()
    }
}
