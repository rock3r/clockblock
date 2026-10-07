package dev.sebastiano.clockblocker.opus.widget

import android.app.AlarmManager
import android.app.Application
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import androidx.test.core.app.ApplicationProvider
import dev.sebastiano.clockblocker.opus.core.model.AdviceOutcome
import dev.sebastiano.clockblocker.opus.core.model.AppSettings
import dev.sebastiano.clockblocker.opus.core.model.DeepLinks
import dev.sebastiano.clockblocker.opus.core.model.ThemeMode
import dev.sebastiano.clockblocker.opus.widget.draw.WidgetTheme
import dev.sebastiano.clockblocker.opus.widget.preview.DemoPlans
import dev.sebastiano.clockblocker.opus.widget.rc.WidgetModel
import dev.sebastiano.clockblocker.opus.widget.state.WidgetState
import dev.sebastiano.clockblocker.opus.widget.state.WidgetRoute
import dev.sebastiano.clockblocker.opus.widget.state.WidgetStateMapper
import dev.sebastiano.clockblocker.opus.core.testing.FakeTripRepository
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain as shouldContainText
import io.kotest.matchers.string.shouldNotContain
import dev.sebastiano.clockblocker.opus.core.data.AdviceLogRepository
import dev.sebastiano.clockblocker.opus.core.data.SettingsRepository
import dev.sebastiano.clockblocker.opus.core.data.TripRepository
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
import org.robolectric.shadows.ShadowAlarmManager
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/**
 * Provider/updater update paths. A Remote Compose document has no view tree to read, so the assertions check the
 * model each widget id was rendered from ([WidgetUpdater.onRendered]): the texts it shows and speaks. The renderer
 * skips the capture (it shows the placeholder): [WidgetRendererTest] and the screenshot tests cover the documents.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [37])
class WidgetUpdaterTest {
    private val app: Application = ApplicationProvider.getApplicationContext()
    private val manager = AppWidgetManager.getInstance(app)
    private val now = Instant.parse("2026-10-06T09:00:00Z")
    private val plans = FakePlanRepository()
    private val rendered = mutableMapOf<Int, WidgetModel>()
    private val updater = updater()

    private fun updater(
        settings: SettingsRepository = FakeSettingsRepository(),
        logs: AdviceLogRepository = NoAdviceLogRepository,
        trips: TripRepository = NoTripRepository,
    ) = WidgetUpdater(app, plans, settings, logs, trips).apply {
        clock = Clock.fixed(now, ZoneOffset.UTC)
        rendererFactory = { WidgetRenderer(it, profileProvider = { null }) }
        onRendered = { id, model -> rendered[id] = model }
    }

    @Before
    fun setUp() {
        shadowOf(manager).setAllowedToBindAppWidgets(true)
    }

    private fun place(
        kind: WidgetKind,
        id: Int,
        category: Int = AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN,
    ): Int {
        manager.bindAppWidgetIdIfAllowed(id, WidgetUpdater.componentName(app, kind)).shouldBeTrue()
        manager.updateAppWidgetOptions(
            id,
            Bundle().apply { putInt(AppWidgetManager.OPTION_APPWIDGET_HOST_CATEGORY, category) },
        )
        return id
    }

    /** Every text widget [id] was rendered with: what its layouts show and what TalkBack reads. */
    private fun texts(id: Int): String {
        val t = rendered[id].shouldNotBeNull().texts
        return (
            listOfNotNull(t.title, t.subtitle, t.secondary, t.dialTitle, t.dialDetail, t.header, t.contentDescription, t.spokenNow) +
                t.subtitleLines + t.upcoming.flatMap { listOf(it.label, it.spoken) } + listOfNotNull(t.done?.label, t.done?.contentDescription)
            ).joinToString("\n")
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
        texts(clocks) shouldContainText app.getString(R.string.widget_no_trip_full)
        texts(next) shouldContainText app.getString(R.string.widget_no_trip_title)
    }

    @Test
    fun `with a plan Next up shows the current advice`() = runBlocking<Unit> {
        plans.current.value = DemoPlans.lisbonTokyo(now, DemoPlans.Scenario.AvoidLight)
        val next = place(WidgetKind.NextUp, 7)
        updater.update(WidgetKind.NextUp, intArrayOf(next))
        texts(next) shouldContainText app.getString(R.string.widget_advice_avoid_light)
    }

    @Test
    fun `plan changes are picked up on the next update`() = runBlocking<Unit> {
        val next = place(WidgetKind.NextUp, 7)
        updater.update(WidgetKind.NextUp, intArrayOf(next))
        texts(next) shouldContainText app.getString(R.string.widget_no_trip_title)
        plans.current.value = DemoPlans.lisbonTokyo(now, DemoPlans.Scenario.Sleep)
        updater.update(WidgetKind.NextUp, intArrayOf(next))
        texts(next) shouldContainText app.getString(R.string.widget_advice_sleep)
    }

    private fun scheduleRetiredLegacyRefresh(): ShadowAlarmManager {
        val alarms = app.getSystemService(AlarmManager::class.java)
        alarms.setInexactRepeating(
            AlarmManager.RTC,
            now.toEpochMilli(),
            AlarmManager.INTERVAL_FIFTEEN_MINUTES,
            RetiredLegacyRefresh.pendingIntent(app, 0)!!,
        )
        return shadowOf(alarms).also { it.scheduledAlarms.shouldNotBeEmpty() }
    }

    @Test
    fun `an update cancels the retired fallback's repeating refresh alarm`() = runBlocking<Unit> {
        val alarms = scheduleRetiredLegacyRefresh()
        updater.updateAll()
        alarms.scheduledAlarms.shouldBeEmpty()
        RetiredLegacyRefresh.pendingIntent(app, PendingIntent.FLAG_NO_CREATE).shouldBeNull()
    }

    @Test
    fun `the retired refresh alarm cancels itself when it fires`() {
        val alarms = scheduleRetiredLegacyRefresh()
        TwoClocksWidgetProvider(updater).onReceive(
            app,
            Intent(app, TwoClocksWidgetProvider::class.java).setAction(RetiredLegacyRefresh.action(app)),
        )
        alarms.scheduledAlarms.shouldBeEmpty()
        RetiredLegacyRefresh.pendingIntent(app, PendingIntent.FLAG_NO_CREATE).shouldBeNull()
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
        while (next !in rendered && System.currentTimeMillis() < deadline) {
            Thread.sleep(20)
        }
        texts(next) shouldContainText app.getString(R.string.widget_no_trip_title)
    }

    @Test
    fun `theme follows the setting, system night mode and night-safe advice`() {
        val day = Configuration().apply { uiMode = Configuration.UI_MODE_NIGHT_NO }
        val night = Configuration().apply { uiMode = Configuration.UI_MODE_NIGHT_YES }
        val noTrip = WidgetState.NoTrip
        fun state(s: DemoPlans.Scenario) = WidgetStateMapper.map(DemoPlans.lisbonTokyo(now, s), now)

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
    fun `a light-dark, font scale or density change triggers one refresh, other configuration changes none`() {
        val start = Configuration(app.resources.configuration)
        fun config(
            night: Boolean = false,
            fontScale: Float = start.fontScale,
            densityDpi: Int = start.densityDpi,
            orientation: Int = start.orientation,
        ) = Configuration(start).apply {
            val mode = if (night) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
            uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or mode
            this.fontScale = fontScale
            this.densityDpi = densityDpi
            this.orientation = orientation
        }
        val turned = if (start.orientation == Configuration.ORIENTATION_PORTRAIT) {
            Configuration.ORIENTATION_LANDSCAPE
        } else {
            Configuration.ORIENTATION_PORTRAIT
        }
        updater.renderConfigChanged(config(orientation = turned)).shouldBeFalse()
        updater.renderConfigChanged(config(night = true)).shouldBeTrue()
        updater.renderConfigChanged(config(night = true)).shouldBeFalse()
        updater.renderConfigChanged(config(night = false)).shouldBeTrue()
        // Labels are fitted for the font scale and the density (text snaps to whole pixels): re-fit on either.
        updater.renderConfigChanged(config(fontScale = 1.3f)).shouldBeTrue()
        updater.renderConfigChanged(config(fontScale = 1.3f)).shouldBeFalse()
        updater.renderConfigChanged(config(fontScale = 1.3f, densityDpi = start.densityDpi * 2)).shouldBeTrue()
        updater.renderConfigChanged(config(fontScale = 1.3f, densityDpi = start.densityDpi * 2)).shouldBeFalse()
    }

    @Test
    fun `Done shows on Next up and turns into a chip once logged`() = runBlocking<Unit> {
        val plan = DemoPlans.lisbonTokyo(now, DemoPlans.Scenario.AvoidLight)
        plans.current.value = plan
        val logs = FakeAdviceLogRepository()
        val updater = updater(logs = logs)
        val next = place(WidgetKind.NextUp, 9)
        updater.update(WidgetKind.NextUp, intArrayOf(next))
        val done = app.getString(R.string.widget_done)
        texts(next) shouldContainText done

        val current = (WidgetStateMapper.map(plan, now) as WidgetState.Active).current!!
        logs.log(plan.tripId, current.adviceId, AdviceOutcome.Skipped)
        updater.update(WidgetKind.NextUp, intArrayOf(next))
        texts(next) shouldContainText app.getString(R.string.widget_skipped)
    }

    @Test
    fun `Next up carries the time in the other zone`() = runBlocking<Unit> {
        plans.current.value = DemoPlans.lisbonTokyo(now, DemoPlans.Scenario.AvoidLight)
        val next = place(WidgetKind.NextUp, 11)
        updater.update(WidgetKind.NextUp, intArrayOf(next))
        texts(next) shouldContainText "in Lisbon"
    }

    @Test
    fun `keyguard widgets hide places and supplements when the setting is on`() = runBlocking<Unit> {
        // Melatonin is up next on the 4×3 Two Clocks; at the moment itself it is the current block.
        plans.current.value = DemoPlans.lisbonTokyo(now, DemoPlans.Scenario.AvoidLight)
        val settings = FakeSettingsRepository(AppSettings(hideLockScreenDetails = true))
        val updater = updater(settings)
        val keyguard = AppWidgetProviderInfo.WIDGET_CATEGORY_KEYGUARD
        val lock = place(WidgetKind.TwoClocks, 21, category = keyguard)
        val home = place(WidgetKind.TwoClocks, 22)
        updater.update(WidgetKind.TwoClocks, intArrayOf(lock, home))

        val locked = texts(lock)
        locked shouldNotContain "Lisbon"
        locked shouldNotContain "Tokyo"
        locked shouldNotContain app.getString(R.string.widget_advice_melatonin)
        locked shouldContainText app.getString(R.string.widget_advice_redacted)
        // The same widget on the home screen keeps every detail.
        texts(home) shouldContainText "in Lisbon"
        texts(home) shouldContainText app.getString(R.string.widget_advice_melatonin)
    }

    @Test
    fun `a host category that includes the keyguard bit counts as the lock screen`() = runBlocking<Unit> {
        plans.current.value = DemoPlans.lisbonTokyo(now, DemoPlans.Scenario.AvoidLight)
        val settings = FakeSettingsRepository(AppSettings(hideLockScreenDetails = true))
        val updater = updater(settings)
        // The host category is a bit mask; a host may report keyguard together with another category.
        val category = AppWidgetProviderInfo.WIDGET_CATEGORY_KEYGUARD or AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN
        val lock = place(WidgetKind.NextUp, 24, category = category)
        updater.update(WidgetKind.NextUp, intArrayOf(lock))
        texts(lock) shouldNotContain "Lisbon"
    }

    @Test
    fun `keyguard widgets stay redacted when the settings can't be read in time`() = runBlocking<Unit> {
        plans.current.value = DemoPlans.lisbonTokyo(now, DemoPlans.Scenario.AvoidLight)
        val stuck = object : SettingsRepository {
            override val settings: Flow<AppSettings> = flow { awaitCancellation() }
            override suspend fun update(transform: (AppSettings) -> AppSettings) = Unit
        }
        val updater = updater(stuck).apply { readTimeoutMs = 50 }
        val lock = place(WidgetKind.NextUp, 25, category = AppWidgetProviderInfo.WIDGET_CATEGORY_KEYGUARD)
        val home = place(WidgetKind.NextUp, 26)
        updater.update(WidgetKind.NextUp, intArrayOf(lock, home))
        // Privacy fails closed on the lock screen; the home screen keeps its details.
        texts(lock) shouldNotContain "Lisbon"
        texts(home) shouldContainText "in Lisbon"
    }

    @Test
    fun `keyguard widgets keep their details while the setting is off`() = runBlocking<Unit> {
        plans.current.value = DemoPlans.lisbonTokyo(now, DemoPlans.Scenario.AvoidLight)
        val lock = place(WidgetKind.NextUp, 23, category = AppWidgetProviderInfo.WIDGET_CATEGORY_KEYGUARD)
        updater.update(WidgetKind.NextUp, intArrayOf(lock))
        texts(lock) shouldContainText "in Lisbon"
    }

    @Test
    fun `the trip's airport codes reach the state`() = runBlocking<Unit> {
        val plan = DemoPlans.lisbonTokyo(now, DemoPlans.Scenario.AvoidLight)
        plans.current.value = plan
        val trips = FakeTripRepository(listOf(DemoPlans.trip()))
        val updater = updater(trips = trips)
        updater.state(plan, AppSettings(), keyguard = false).shouldBeInstanceOf<WidgetState.Active>().route shouldBe
            WidgetRoute("LIS", "HND")
        updater.state(plan, AppSettings(hideLockScreenDetails = true), keyguard = true)
            .shouldBeInstanceOf<WidgetState.Active>().route shouldBe null
    }

    @Test
    fun `the header names the trip's place, not the zone's city`() = runBlocking<Unit> {
        // The trip's own city names the place (an SFO trip reads San Francisco, not the zone's Los Angeles).
        val plan = DemoPlans.lisbonTokyo(now, DemoPlans.Scenario.AvoidLight)
        plans.current.value = plan
        val demo = DemoPlans.trip()
        val leg = demo.legs.single()
        val trip = demo.copy(legs = listOf(leg.copy(destination = leg.destination.copy(city = "Yokohama"))))
        val updater = updater(trips = FakeTripRepository(listOf(trip)))
        updater.state(plan, AppSettings(), keyguard = false).shouldBeInstanceOf<WidgetState.Active>().placeNames shouldBe
            mapOf("Europe/Lisbon" to "Lisbon", "Asia/Tokyo" to "Yokohama")
        val clocks = place(WidgetKind.TwoClocks, 31)
        updater.update(WidgetKind.TwoClocks, intArrayOf(clocks))
        val shown = texts(clocks)
        shown shouldContainText "Yokohama · Day 2"
        shown shouldNotContain "Tokyo"
    }

    @Test
    fun `the logged chip opens the plan instead of doing nothing`() = runBlocking<Unit> {
        val plan = DemoPlans.lisbonTokyo(now, DemoPlans.Scenario.AvoidLight)
        plans.current.value = plan
        val logs = FakeAdviceLogRepository()
        val updater = updater(logs = logs)
        val current = (WidgetStateMapper.map(plan, now) as WidgetState.Active).current!!
        logs.log(plan.tripId, current.adviceId, AdviceOutcome.Done)
        val next = place(WidgetKind.NextUp, 32)
        updater.update(WidgetKind.NextUp, intArrayOf(next))
        // A logged chip has no Done action: its tap falls through to the widget's click, which opens the plan.
        val texts = rendered[next].shouldNotBeNull().texts
        texts.done.shouldNotBeNull().logged shouldBe AdviceOutcome.Done
        texts.deepLink shouldBe DeepLinks.plan(plan.tripId)
    }
}
