package dev.sebastiano.clockblocker.opus.widget.config

import android.app.Application
import android.appwidget.AppWidgetManager
import android.os.Bundle
import android.util.SizeF
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.test.core.app.ApplicationProvider
import dev.sebastiano.clockblocker.opus.core.model.BodyRingMode
import dev.sebastiano.clockblocker.opus.core.model.WidgetConfig
import dev.sebastiano.clockblocker.opus.core.testing.MainDispatcherRule
import dev.sebastiano.clockblocker.opus.widget.FakePlanRepository
import dev.sebastiano.clockblocker.opus.widget.FakeSettingsRepository
import dev.sebastiano.clockblocker.opus.widget.FakeWidgetConfigRepository
import dev.sebastiano.clockblocker.opus.widget.NoAdviceLogRepository
import dev.sebastiano.clockblocker.opus.widget.NoTripRepository
import dev.sebastiano.clockblocker.opus.widget.WidgetKind
import dev.sebastiano.clockblocker.opus.widget.WidgetRenderer
import dev.sebastiano.clockblocker.opus.widget.WidgetUpdater
import dev.sebastiano.clockblocker.opus.widget.preview.DemoPlans
import dev.sebastiano.clockblocker.opus.widget.rc.WidgetModel
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.ArrayList

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [37])
class WidgetConfigViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val app: Application = ApplicationProvider.getApplicationContext()
    private val manager = AppWidgetManager.getInstance(app)
    private val now = Instant.parse("2026-10-06T09:00:00Z")
    private val plans = FakePlanRepository()
    private val configs = FakeWidgetConfigRepository()
    private val rendered = mutableMapOf<Int, WidgetModel>()
    private val updater = WidgetUpdater(app, plans, FakeSettingsRepository(), NoAdviceLogRepository, NoTripRepository, configs).apply {
        clock = Clock.fixed(now, ZoneOffset.UTC)
        rendererFactory = { WidgetRenderer(it, profileProvider = { null }) }
        onRendered = { id, model -> rendered[id] = model }
    }
    private val id = 7

    @Before
    fun setUp() {
        shadowOf(manager).setAllowedToBindAppWidgets(true)
        manager.bindAppWidgetIdIfAllowed(id, WidgetUpdater.componentName(app, WidgetKind.TwoClocks))
    }

    private fun viewModel() =
        WidgetConfigViewModel(id, WidgetKind.TwoClocks, updater, configs, FakeSettingsRepository(), main.dispatcher, CoroutineScope(main.dispatcher))

    private fun settle() = main.dispatcher.scheduler.advanceUntilIdle()

    @Test
    fun `shows the saved body ring, and a new choice is saved and drawn on the widget`() {
        configs.current.value = mapOf(id to WidgetConfig(bodyRing = BodyRingMode.Precise))
        val vm = viewModel()
        settle()
        vm.state.value.shouldNotBeNull().bodyRing shouldBe BodyRingMode.Precise

        vm.setBodyRing(BodyRingMode.Simple)
        settle()

        configs.current.value[id] shouldBe WidgetConfig(bodyRing = BodyRingMode.Simple)
        vm.state.value.shouldNotBeNull().bodyRing shouldBe BodyRingMode.Simple
        rendered[id].shouldNotBeNull().bodyRing shouldBe BodyRingMode.Simple
    }

    @Test
    fun `a choice made just as the screen closes is still saved and drawn`() {
        val store = ViewModelStore()
        val vm = ViewModelProvider.create(store, viewModelFactory { initializer { viewModel() } })[WidgetConfigViewModel::class]
        settle()
        configs.writeGate = CompletableDeferred()

        vm.setBodyRing(BodyRingMode.Precise)
        store.clear() // Done: the activity finishes while the write is still in flight.
        configs.writeGate?.complete(Unit)
        settle()

        configs.current.value[id] shouldBe WidgetConfig(bodyRing = BodyRingMode.Precise)
        rendered[id].shouldNotBeNull().bodyRing shouldBe BodyRingMode.Precise
    }

    @Test
    fun `quick taps are saved in the order they were made`() {
        val vm = viewModel()
        settle()
        configs.writeGate = CompletableDeferred()

        vm.setBodyRing(BodyRingMode.Precise)
        vm.setBodyRing(BodyRingMode.Simple)
        configs.writeGate?.complete(Unit)
        settle()

        configs.current.value[id] shouldBe WidgetConfig(bodyRing = BodyRingMode.Simple)
    }

    @Test
    fun `the preview size follows a rotation`() {
        manager.updateAppWidgetOptions(
            id,
            Bundle().apply {
                putParcelableArrayList(AppWidgetManager.OPTION_APPWIDGET_SIZES, arrayListOf(SizeF(300f, 150f), SizeF(180f, 240f)))
            },
        )
        val vm = viewModel()
        vm.setLandscape(false)
        settle()
        vm.state.value.shouldNotBeNull().let { (it.widthDp to it.heightDp) shouldBe (180f to 240f) }

        // The ViewModel outlives the rotated activity, which reports the new orientation.
        vm.setLandscape(true)
        settle()
        vm.state.value.shouldNotBeNull().let { (it.widthDp to it.heightDp) shouldBe (300f to 150f) }
    }

    @Test
    fun `without a trip the preview shows the sample trip`() {
        val vm = viewModel()
        settle()
        val state = vm.state.value.shouldNotBeNull()
        state.sample.shouldBeTrue()
        state.dial.shouldNotBeNull()
    }

    @Test
    fun `with a plan the preview shows the real trip at the widget's size`() {
        plans.current.value = DemoPlans.lisbonTokyo(now, DemoPlans.Scenario.AvoidLight)
        manager.updateAppWidgetOptions(
            id,
            Bundle().apply { putParcelableArrayList(AppWidgetManager.OPTION_APPWIDGET_SIZES, ArrayList(listOf(SizeF(250f, 240f)))) },
        )
        val vm = viewModel()
        settle()
        val state = vm.state.value.shouldNotBeNull()
        state.sample.shouldBeFalse()
        state.widthDp shouldBe 250f
        state.heightDp shouldBe 240f
    }
}
