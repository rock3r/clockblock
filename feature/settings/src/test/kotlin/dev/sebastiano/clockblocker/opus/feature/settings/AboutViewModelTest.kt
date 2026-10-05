package dev.sebastiano.clockblocker.opus.feature.settings

import app.cash.turbine.test
import dev.sebastiano.clockblocker.opus.core.model.AppSettings
import dev.sebastiano.clockblocker.opus.core.testing.FakeSettingsRepository
import dev.sebastiano.clockblocker.opus.core.testing.MainDispatcherRule
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

    @Test
    fun `the first taps are silent, then it counts down, and the seventh unlocks opus mode`() = runTest {
        val vm = AboutViewModel(settings)
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
        val vm = AboutViewModel(settings)
        vm.events.test {
            vm.onVersionTapped()
            awaitItem() shouldBe AboutEvent.AlreadyUnlocked
        }
    }

    @Test
    fun `the title card can be dismissed`() = runTest {
        val vm = AboutViewModel(settings)
        vm.showTitleCard.test {
            awaitItem().shouldBeFalse()
            repeat(AboutViewModel.TapsToUnlock) { vm.onVersionTapped() }
            awaitItem().shouldBeTrue()
            vm.dismissTitleCard()
            awaitItem().shouldBeFalse()
        }
    }
}
