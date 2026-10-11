package dev.sebastiano.clockblocker.opus.debug

import app.cash.turbine.test
import dev.sebastiano.clockblocker.opus.core.testing.MainDispatcherRule
import dev.sebastiano.clockblocker.opus.widget.debug.RemoteComposeNotificationProbe.Result
import dev.sebastiano.clockblocker.opus.widget.debug.RemoteComposeNotificationProbe.Variant
import dev.sebastiano.clockblocker.opus.widget.debug.RemoteComposeNotificationProbe.Verdict
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

/** The probe's state lives outside the activity: a theme switch recreating it no longer loses the results. */
class ProbeViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private class FakeProbe(var active: List<Variant> = emptyList()) : NotificationProbe {
        val gate = CompletableDeferred<Unit>()
        var runs = 0
        var cancels = 0
        override suspend fun run(): List<Result> {
            runs++
            gate.await()
            return Results
        }
        override fun activeVariants() = active
        override fun cancelAll() {
            cancels++
            active = emptyList()
        }
    }

    @Test
    fun `a run goes from running to its results, and running again meanwhile does nothing`() = runTest {
        val probe = FakeProbe()
        val viewModel = ProbeViewModel(probe)
        viewModel.state.test {
            awaitItem() shouldBe ProbeState.Idle
            viewModel.run()
            awaitItem() shouldBe ProbeState.Running
            viewModel.run()
            viewModel.clear()
            probe.gate.complete(Unit)
            awaitItem() shouldBe ProbeState.Done(Results)
        }
        probe.runs shouldBe 1
        probe.cancels shouldBe 0
    }

    @Test
    fun `clearing removes the notifications and the results`() = runTest {
        val probe = FakeProbe().apply { gate.complete(Unit) }
        val viewModel = ProbeViewModel(probe)
        viewModel.run()
        viewModel.state.value shouldBe ProbeState.Done(Results)
        viewModel.clear()
        viewModel.state.value shouldBe ProbeState.Idle
        probe.cancels shouldBe 1
    }

    @Test
    fun `a fresh process picks up the probe's notifications still in the shade`() {
        ProbeViewModel(FakeProbe(active = listOf(Variant.DecoratedBoth))).state.value shouldBe
            ProbeState.Showing(listOf(Variant.DecoratedBoth))
        ProbeViewModel(FakeProbe()).state.value shouldBe ProbeState.Idle
    }

    private companion object {
        val Results = listOf(Result(Variant.DecoratedBoth, Verdict.Kept, "still showing after 3000 ms"))
    }
}
