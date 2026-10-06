package dev.sebastiano.clockblocker.opus.widget

import android.app.Application
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import dev.sebastiano.clockblocker.opus.core.model.AdviceOutcome
import dev.sebastiano.clockblocker.opus.core.model.AppSettings
import dev.sebastiano.clockblocker.opus.core.model.ThemeMode
import dev.sebastiano.clockblocker.opus.widget.draw.WidgetTheme
import dev.sebastiano.clockblocker.opus.widget.legacy.LegacyRefresh
import dev.sebastiano.clockblocker.opus.widget.preview.DemoPlans
import dev.sebastiano.clockblocker.opus.widget.state.WidgetState
import dev.sebastiano.clockblocker.opus.widget.state.WidgetRoute
import dev.sebastiano.clockblocker.opus.widget.state.WidgetStateMapper
import dev.sebastiano.clockblocker.opus.core.testing.FakeTripRepository
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain as shouldContainText
import io.kotest.matchers.string.shouldNotContain
import dev.sebastiano.clockblocker.opus.core.data.SettingsRepository
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

/** Provider/updater update paths on the classic RemoteViews backend (API < 36). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [30])
class WidgetUpdaterTest {
    private val app: Application = ApplicationProvider.getApplicationContext()
    private val manager = AppWidgetManager.getInstance(app)
    private val now = Instant.parse("2026-10-06T09:00:00Z")
    private val plans = FakePlanRepository()
    private val updater = WidgetUpdater(app, plans, FakeSettingsRepository()).apply {
        clock = Clock.fixed(now, ZoneOffset.UTC)
    }

    @Before
    fun setUp() {
        shadowOf(manager).setAllowedToBindAppWidgets(true)
    }

    private fun place(
        kind: WidgetKind,
        id: Int,
        widthDp: Int = 300,
        heightDp: Int = 60,
        category: Int = AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN,
    ): Int {
        manager.bindAppWidgetIdIfAllowed(id, WidgetUpdater.componentName(app, kind)).shouldBeTrue()
        manager.updateAppWidgetOptions(
            id,
            Bundle().apply {
                putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, widthDp)
                putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, heightDp)
                putInt(AppWidgetManager.OPTION_APPWIDGET_HOST_CATEGORY, category)
            },
        )
        return id
    }

    private fun texts(id: Int): List<String> {
        val root = shadowOf(manager).getViewFor(id).shouldNotBeNull()
        return buildList {
            fun walk(v: View) {
                if (v is TextView && v.visibility == View.VISIBLE) add(v.text.toString())
                if (v is ViewGroup) (0 until v.childCount).forEach { walk(v.getChildAt(it)) }
            }
            walk(root)
        }
    }

    @Test
    fun `ids are tracked per widget kind`() {
        place(WidgetKind.TwoClocks, 1)
        place(WidgetKind.NextUp, 2)
        place(WidgetKind.NextUp, 3)
        updater.ids(WidgetKind.TwoClocks).toList() shouldBe listOf(1)
        updater.ids(WidgetKind.NextUp).toList() shouldContainExactlyInAnyOrder listOf(2, 3)
    }

    @Test
    fun `without a plan both widgets show the empty state`() = runBlocking<Unit> {
        val clocks = place(WidgetKind.TwoClocks, 1, widthDp = 176, heightDp = 176)
        val next = place(WidgetKind.NextUp, 2)
        updater.updateAll()
        texts(clocks) shouldContain app.getString(R.string.widget_no_trip_full)
        texts(next) shouldContain app.getString(R.string.widget_no_trip_title)
    }

    @Test
    fun `with a plan Next up shows the current advice`() = runBlocking<Unit> {
        plans.current.value = DemoPlans.lisbonTokyo(now, DemoPlans.Scenario.AvoidLight)
        val next = place(WidgetKind.NextUp, 7)
        updater.update(WidgetKind.NextUp, intArrayOf(next))
        texts(next) shouldContain app.getString(R.string.widget_advice_avoid_light)
    }

    @Test
    fun `plan changes are picked up on the next update`() = runBlocking<Unit> {
        val next = place(WidgetKind.NextUp, 7)
        updater.update(WidgetKind.NextUp, intArrayOf(next))
        texts(next) shouldContain app.getString(R.string.widget_no_trip_title)
        plans.current.value = DemoPlans.lisbonTokyo(now, DemoPlans.Scenario.Sleep)
        updater.update(WidgetKind.NextUp, intArrayOf(next))
        texts(next) shouldContain app.getString(R.string.widget_advice_sleep)
    }

    @Test
    fun `classic Two Clocks schedules the inexact refresh and onDisabled cancels it`() = runBlocking<Unit> {
        val clocks = place(WidgetKind.TwoClocks, 1)
        updater.update(WidgetKind.TwoClocks, intArrayOf(clocks))
        LegacyRefresh.isScheduled(app).shouldBeTrue()
        shadowOf(app.getSystemService(android.app.AlarmManager::class.java)).scheduledAlarms.size shouldBe 1

        TwoClocksWidgetProvider(updater).onDisabled(app)
        LegacyRefresh.isScheduled(app).shouldBeFalse()
    }

    @Test
    fun `no refresh alarm when only Next up is placed`() = runBlocking<Unit> {
        val next = place(WidgetKind.NextUp, 2)
        updater.update(WidgetKind.NextUp, intArrayOf(next))
        LegacyRefresh.isScheduled(app).shouldBeFalse()
    }

    @Test
    fun `provider onUpdate renders through the updater`() {
        val next = place(WidgetKind.NextUp, 4)
        NextUpWidgetProvider(updater).onReceive(
            app,
            Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, intArrayOf(next)),
        )
        // The provider renders on a background coroutine (goAsync): wait for it.
        val deadline = System.currentTimeMillis() + 10_000
        while (shadowOf(manager).getViewFor(next) == null && System.currentTimeMillis() < deadline) {
            Thread.sleep(20)
        }
        texts(next) shouldContain app.getString(R.string.widget_no_trip_title)
    }

    @Test
    fun `theme follows the setting, system night mode and night-safe advice`() {
        val day = Configuration().apply { uiMode = Configuration.UI_MODE_NIGHT_NO }
        val night = Configuration().apply { uiMode = Configuration.UI_MODE_NIGHT_YES }
        val noTrip = WidgetState.NoTrip
        fun state(s: DemoPlans.Scenario) = WidgetStateMapper.map(DemoPlans.lisbonTokyo(now, s), now, ZoneId.of("UTC"))

        WidgetUpdater.theme(AppSettings(), noTrip, day) shouldBe WidgetTheme.Light
        WidgetUpdater.theme(AppSettings(), noTrip, night) shouldBe WidgetTheme.Dark
        WidgetUpdater.theme(AppSettings(themeMode = ThemeMode.Dark), noTrip, day) shouldBe WidgetTheme.Dark
        WidgetUpdater.theme(AppSettings(themeMode = ThemeMode.Light), noTrip, night) shouldBe WidgetTheme.Light
        // Night-safe: avoid-light / sleep windows switch to the black + dim amber palette, unless turned off.
        WidgetUpdater.theme(AppSettings(themeMode = ThemeMode.Light), state(DemoPlans.Scenario.AvoidLight), day) shouldBe
            WidgetTheme.NightSafe
        WidgetUpdater.theme(AppSettings(), state(DemoPlans.Scenario.Sleep), day) shouldBe WidgetTheme.NightSafe
        WidgetUpdater.theme(AppSettings(nightSafeAuto = false), state(DemoPlans.Scenario.Sleep), day) shouldBe
            WidgetTheme.Light
        WidgetUpdater.theme(AppSettings(), state(DemoPlans.Scenario.SeeBrightLight), day) shouldBe WidgetTheme.Light
    }

    @Test
    fun `preview key changes with the app version and with night mode`() {
        WidgetUpdater.previewKey(12, night = false) shouldBe WidgetUpdater.previewKey(12, night = false)
        WidgetUpdater.previewKey(12, night = false) shouldNotBe WidgetUpdater.previewKey(12, night = true)
        WidgetUpdater.previewKey(12, night = true) shouldNotBe WidgetUpdater.previewKey(13, night = true)
    }

    @Test
    fun `a light-dark switch triggers one refresh, other configuration changes none`() {
        fun config(night: Boolean, fontScale: Float = 1f) = Configuration(app.resources.configuration).apply {
            val mode = if (night) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
            uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or mode
            this.fontScale = fontScale
        }
        updater.nightModeChanged(config(night = false, fontScale = 1.3f)).shouldBeFalse()
        updater.nightModeChanged(config(night = true)).shouldBeTrue()
        updater.nightModeChanged(config(night = true)).shouldBeFalse()
        updater.nightModeChanged(config(night = false)).shouldBeTrue()
    }

    @Test
    fun `Done shows on Next up and turns into a chip once logged`() = runBlocking<Unit> {
        val plan = DemoPlans.lisbonTokyo(now, DemoPlans.Scenario.AvoidLight)
        plans.current.value = plan
        val logs = FakeAdviceLogRepository()
        val updater = WidgetUpdater(app, plans, FakeSettingsRepository(), logs).apply {
            clock = Clock.fixed(now, ZoneOffset.UTC)
        }
        // 4×2: Next up with its Done button.
        val next = place(WidgetKind.NextUp, 9, widthDp = 300, heightDp = 130)
        updater.update(WidgetKind.NextUp, intArrayOf(next))
        val done = app.getString(R.string.widget_done)
        texts(next) shouldContain done

        val current = (WidgetStateMapper.map(plan, now, ZoneId.of("UTC")) as WidgetState.Active).current!!
        logs.log(plan.tripId, current.adviceId, AdviceOutcome.Skipped)
        updater.update(WidgetKind.NextUp, intArrayOf(next))
        texts(next) shouldContain app.getString(R.string.widget_skipped)
    }

    @Test
    fun `the 2x1 size shows the time in the other zone`() = runBlocking<Unit> {
        plans.current.value = DemoPlans.lisbonTokyo(now, DemoPlans.Scenario.AvoidLight)
        val next = place(WidgetKind.NextUp, 11, widthDp = 180, heightDp = 70)
        updater.update(WidgetKind.NextUp, intArrayOf(next))
        texts(next).joinToString("\n") shouldContainText "in Lisbon"
    }

    @Test
    fun `keyguard widgets hide places and supplements when the setting is on`() = runBlocking<Unit> {
        // Melatonin is up next on the 4×3 Two Clocks; at the moment itself it is the current block.
        plans.current.value = DemoPlans.lisbonTokyo(now, DemoPlans.Scenario.AvoidLight)
        val settings = FakeSettingsRepository(AppSettings(hideLockScreenDetails = true))
        val updater = WidgetUpdater(app, plans, settings).apply { clock = Clock.fixed(now, ZoneOffset.UTC) }
        val keyguard = AppWidgetProviderInfo.WIDGET_CATEGORY_KEYGUARD
        val lock = place(WidgetKind.TwoClocks, 21, widthDp = 300, heightDp = 300, category = keyguard)
        val home = place(WidgetKind.TwoClocks, 22, widthDp = 300, heightDp = 300)
        updater.update(WidgetKind.TwoClocks, intArrayOf(lock, home))

        val locked = texts(lock).joinToString("\n")
        locked shouldNotContain "Lisbon"
        locked shouldNotContain "Tokyo"
        locked shouldNotContain app.getString(R.string.widget_advice_melatonin)
        locked shouldContainText app.getString(R.string.widget_advice_redacted)
        // The same widget on the home screen keeps every detail.
        texts(home).joinToString("\n") shouldContainText "in Lisbon"
        texts(home).joinToString("\n") shouldContainText app.getString(R.string.widget_advice_melatonin)
    }

    @Test
    fun `a host category that includes the keyguard bit counts as the lock screen`() = runBlocking<Unit> {
        plans.current.value = DemoPlans.lisbonTokyo(now, DemoPlans.Scenario.AvoidLight)
        val settings = FakeSettingsRepository(AppSettings(hideLockScreenDetails = true))
        val updater = WidgetUpdater(app, plans, settings).apply { clock = Clock.fixed(now, ZoneOffset.UTC) }
        // The host category is a bit mask; a host may report keyguard together with another category.
        val category = AppWidgetProviderInfo.WIDGET_CATEGORY_KEYGUARD or AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN
        val lock = place(WidgetKind.NextUp, 24, category = category)
        updater.update(WidgetKind.NextUp, intArrayOf(lock))
        texts(lock).joinToString("\n") shouldNotContain "Lisbon"
    }

    @Test
    fun `keyguard widgets stay redacted when the settings can't be read in time`() = runBlocking<Unit> {
        plans.current.value = DemoPlans.lisbonTokyo(now, DemoPlans.Scenario.AvoidLight)
        val stuck = object : SettingsRepository {
            override val settings: Flow<AppSettings> = flow { awaitCancellation() }
            override suspend fun update(transform: (AppSettings) -> AppSettings) = Unit
        }
        val updater = WidgetUpdater(app, plans, stuck).apply {
            clock = Clock.fixed(now, ZoneOffset.UTC)
            readTimeoutMs = 50
        }
        val lock = place(WidgetKind.NextUp, 25, category = AppWidgetProviderInfo.WIDGET_CATEGORY_KEYGUARD)
        val home = place(WidgetKind.NextUp, 26)
        updater.update(WidgetKind.NextUp, intArrayOf(lock, home))
        // Privacy fails closed on the lock screen; the home screen keeps its details.
        texts(lock).joinToString("\n") shouldNotContain "Lisbon"
        texts(home).joinToString("\n") shouldContainText "in Lisbon"
    }

    @Test
    fun `keyguard widgets keep their details while the setting is off`() = runBlocking<Unit> {
        plans.current.value = DemoPlans.lisbonTokyo(now, DemoPlans.Scenario.AvoidLight)
        val lock = place(WidgetKind.NextUp, 23, category = AppWidgetProviderInfo.WIDGET_CATEGORY_KEYGUARD)
        updater.update(WidgetKind.NextUp, intArrayOf(lock))
        texts(lock).joinToString("\n") shouldContainText "in Lisbon"
    }

    @Test
    fun `the trip's airport codes reach the state`() = runBlocking<Unit> {
        val plan = DemoPlans.lisbonTokyo(now, DemoPlans.Scenario.AvoidLight)
        plans.current.value = plan
        val trips = FakeTripRepository(listOf(DemoPlans.trip()))
        val updater = WidgetUpdater(app, plans, FakeSettingsRepository(), tripRepository = trips).apply {
            clock = Clock.fixed(now, ZoneOffset.UTC)
        }
        updater.state(plan, AppSettings(), keyguard = false).shouldBeInstanceOf<WidgetState.Active>().route shouldBe
            WidgetRoute("LIS", "HND")
        updater.state(plan, AppSettings(hideLockScreenDetails = true), keyguard = true)
            .shouldBeInstanceOf<WidgetState.Active>().route shouldBe null
    }
}
