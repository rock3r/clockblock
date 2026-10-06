package dev.sebastiano.clockblocker.opus.widget

import android.app.Application
import android.appwidget.AppWidgetManager
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
import dev.sebastiano.clockblocker.opus.widget.state.WidgetStateMapper
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
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

    private fun place(kind: WidgetKind, id: Int, widthDp: Int = 300, heightDp: Int = 60): Int {
        manager.bindAppWidgetIdIfAllowed(id, WidgetUpdater.componentName(app, kind)).shouldBeTrue()
        manager.updateAppWidgetOptions(
            id,
            Bundle().apply {
                putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, widthDp)
                putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, heightDp)
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
        val clocks = place(WidgetKind.TwoClocks, 1)
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
}
