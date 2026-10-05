package dev.sebastiano.clockblocker.opus.shell

import app.cash.turbine.test
import dev.sebastiano.clockblocker.opus.core.data.demo.DemoData
import dev.sebastiano.clockblocker.opus.core.model.AppSettings
import dev.sebastiano.clockblocker.opus.core.model.SleepWindow
import dev.sebastiano.clockblocker.opus.core.model.ThemeMode
import dev.sebastiano.clockblocker.opus.core.testing.FakeJetLagPlanner
import dev.sebastiano.clockblocker.opus.core.testing.FakePlanRepository
import dev.sebastiano.clockblocker.opus.core.testing.FakeProfileRepository
import dev.sebastiano.clockblocker.opus.core.testing.FakeSettingsRepository
import dev.sebastiano.clockblocker.opus.core.testing.FakeTripRepository
import dev.sebastiano.clockblocker.opus.core.testing.MainDispatcherRule
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import java.time.LocalTime

class ShellViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val profiles = FakeProfileRepository()
    private val trips = FakeTripRepository()
    private val settings = FakeSettingsRepository()
    private val plans = FakePlanRepository()

    private fun viewModel() = ShellViewModel(profiles, trips, settings, plans)

    @Test
    fun `combines profile, trips and settings`() = runTest {
        viewModel().uiState.test {
            awaitItem() shouldBe ShellUiState.Ready(hasProfile = false, hasTrips = false, settings = AppSettings())

            profiles.set(DemoData.profile)
            awaitItem() shouldBe ShellUiState.Ready(
                hasProfile = true,
                hasTrips = false,
                settings = AppSettings(),
                sleep = DemoData.profile.sleep,
            )

            trips.setTrips(DemoData.trips())
            awaitItem() shouldBe ShellUiState.Ready(true, true, AppSettings(), DemoData.profile.sleep)

            val dark = AppSettings(themeMode = ThemeMode.Dark)
            settings.set(dark)
            awaitItem() shouldBe ShellUiState.Ready(true, true, dark, DemoData.profile.sleep)
        }
    }

    @Test
    fun `trip edits that keep trips non-empty don't re-emit`() = runTest {
        trips.setTrips(DemoData.trips())
        viewModel().uiState.test {
            awaitItem() shouldBe ShellUiState.Ready(hasProfile = false, hasTrips = true, settings = AppSettings())

            trips.setTrips(DemoData.trips().take(1))
            expectNoEvents()
        }
    }

    @Test
    fun `carries the current plan and the sleep window for body night`() = runTest {
        profiles.set(DemoData.profile)
        viewModel().uiState.test {
            (awaitItem() as ShellUiState.Ready).currentPlan shouldBe null

            val plan = FakeJetLagPlanner().plan(DemoData.trips().first(), DemoData.profile, DemoData.Now)
            plans.setCurrentPlan(plan)
            (awaitItem() as ShellUiState.Ready).currentPlan shouldBe plan

            val owl = SleepWindow(LocalTime.of(1, 30), LocalTime.of(9, 30))
            profiles.set(DemoData.profile.copy(sleep = owl))
            (awaitItem() as ShellUiState.Ready).sleep shouldBe owl

            plans.setCurrentPlan(null)
            (awaitItem() as ShellUiState.Ready).currentPlan shouldBe null
        }
    }

    @Test
    fun `profile edits that keep the sleep window don't re-emit`() = runTest {
        profiles.set(DemoData.profile)
        viewModel().uiState.test {
            awaitItem()

            profiles.set(DemoData.profile.copy(useCaffeine = !DemoData.profile.useCaffeine))
            expectNoEvents()
        }
    }
}
