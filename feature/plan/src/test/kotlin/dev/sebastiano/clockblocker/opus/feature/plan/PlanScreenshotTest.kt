package dev.sebastiano.clockblocker.opus.feature.plan

import android.content.Context
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.unit.Density
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import dev.sebastiano.clockblocker.opus.core.designsystem.theme.OpusTheme
import dev.sebastiano.clockblocker.opus.core.model.AdviceOutcome
import dev.sebastiano.clockblocker.opus.core.model.AdviceType
import dev.sebastiano.clockblocker.opus.feature.plan.PlanFixtures.ready
import dev.sebastiano.clockblocker.opus.feature.plan.PlanFixtures.realPlan
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import dev.sebastiano.clockblocker.opus.core.circadian.DefaultJetLagPlanner
import dev.sebastiano.clockblocker.opus.core.data.demo.DemoData
import dev.sebastiano.clockblocker.opus.core.model.AdaptationStrategy
import dev.sebastiano.clockblocker.opus.core.model.FlightLeg
import dev.sebastiano.clockblocker.opus.core.model.Trip
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Goldens for the plan screen (reduce motion on: deterministic, and proves every state reads without motion).
 * Record: `./gradlew :feature:plan:recordRoborazziDebug`; verify: `verifyRoborazziDebug`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w400dp-h880dp-xhdpi")
class PlanScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private fun snap(
        name: String,
        darkTheme: Boolean = false,
        fontScale: Float? = null,
        capture: Boolean = true,
        content: @Composable () -> Unit,
    ) {
        Settings.System.putString(
            ApplicationProvider.getApplicationContext<Context>().contentResolver,
            Settings.System.TIME_12_24,
            "24",
        )
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides if (fontScale != null) Density(density.density, fontScale) else density) {
                OpusTheme(darkTheme = darkTheme, dynamicColor = false, reduceMotion = true) {
                    Box(Modifier.background(MaterialTheme.colorScheme.surface)) { content() }
                }
            }
        }
        if (capture) compose.onRoot().captureRoboImage("src/test/screenshots/plan_$name.png")
    }

    private val actions = PlanActions(onBack = {})

    @Test
    fun preTrip() = snap("pre_trip") { PlanContent(ready(PlanFixtures.PreTrip), actions) }

    @Test
    fun inFlight() = snap("in_flight") { PlanContent(ready(PlanFixtures.InFlight), actions) }

    @Test
    fun midAdaptation() = snap("mid_adaptation") {
        val done = realPlan.days.first { it.index == 2 }.advice.first { it.type == AdviceType.Sleep }.id
        PlanContent(ready(PlanFixtures.MidAdaptation, outcomes = mapOf(done to AdviceOutcome.Done)), actions)
    }

    @Test
    fun midAdaptationDark() = snap("mid_adaptation_dark", darkTheme = true) {
        PlanContent(ready(PlanFixtures.MidAdaptation), actions)
    }

    @Test
    fun nightSafe() = snap("night_safe") { PlanContent(ready(PlanFixtures.Evening, nightSafe = true), actions) }

    @Test
    fun celebration() = snap("adapted_celebration") { PlanContent(ready(PlanFixtures.Adapted, celebrate = true), actions) }

    @Test
    fun adapted() = snap("adapted") { PlanContent(ready(PlanFixtures.Adapted), actions) }

    @Test
    fun eightBit() {
        EightBitSession.enabled = true
        try {
            snap("mid_adaptation_8bit") { PlanContent(ready(PlanFixtures.MidAdaptation, easterEggs = true), actions) }
        } finally {
            EightBitSession.enabled = false
        }
    }

    @Test
    fun upcoming() = snap("upcoming") { PlanContent(ready(Instant.parse("2026-06-10T16:00:00Z")), actions) }

    @Test
    fun complete() = snap("complete") {
        val done = realPlan.allAdvice.take(6).associate { it.id to AdviceOutcome.Done }
        PlanContent(ready(Instant.parse("2026-06-24T12:00:00Z"), outcomes = done), actions)
    }

    @Test
    fun freeTime() = snap("free_time") {
        PlanContent(ready(Instant.parse("2026-06-17T11:00:00Z"), plan = PlanFixtures.fakePlan), actions)
    }

    @Test
    fun stayOnHomeTime() = snap("stay_on_home_time") {
        val trip = PlanFixtures.trip.copy(strategyOverride = AdaptationStrategy.StayOnHomeTime)
        val plan = DefaultJetLagPlanner().plan(trip, DemoData.profile, DemoData.Now)
        PlanContent(ready(PlanFixtures.MidAdaptation, plan = plan, trip = trip), actions)
    }

    @Test
    fun noShift() = snap("no_shift") {
        val trip = Trip(
            id = "lhr-lis",
            title = "Lisbon",
            legs = listOf(
                FlightLeg(
                    "lhr-lis-1",
                    DemoData.LHR,
                    DemoData.LIS,
                    LocalDateTime.of(2026, 6, 15, 9, 0),
                    LocalDateTime.of(2026, 6, 15, 11, 45),
                    "TP 1351",
                ),
            ),
            createdAt = DemoData.Now,
        )
        val plan = DefaultJetLagPlanner().plan(trip, DemoData.profile, DemoData.Now)
        PlanContent(ready(Instant.parse("2026-06-15T13:00:00Z"), plan = plan, trip = trip), actions)
    }

    @Test
    fun noTrip() = snap("no_trip") { PlanContent(PlanUiState.NoPlan(missing = false), actions) }

    @Test
    fun loading() {
        snap("loading", capture = false) { PlanContent(PlanUiState.Loading, actions) }
        // Nothing shows during the show-delay (fast loads never flash a loader); capture once it's up.
        compose.mainClock.advanceTimeBy(LoaderShowDelayMillis + 100)
        compose.onRoot().captureRoboImage("src/test/screenshots/plan_loading.png")
    }

    @Test
    @Config(qualifiers = "w400dp-h880dp-xhdpi")
    fun fontScale() = snap("pre_trip_font_1_5", fontScale = 1.5f) { PlanContent(ready(PlanFixtures.PreTrip), actions) }

    @Test
    @Config(qualifiers = "w1280dp-h800dp-xhdpi")
    fun expandedTwoPane() = snap("expanded_two_pane") { PlanContent(ready(PlanFixtures.MidAdaptation), PlanActions()) }

    @Test
    @Config(qualifiers = "w1280dp-h800dp-xhdpi")
    fun emptyPane() = snap("empty_pane") { PlanEmptyPane(onNewTrip = {}) }

    @Test
    @Config(qualifiers = "w400dp-h2400dp-xhdpi")
    fun railTall() = snap("rail_tall") { PlanContent(ready(PlanFixtures.MidAdaptation), actions) }

    @Test
    fun railScrolled() {
        snap("rail_scrolled", capture = false) { PlanContent(ready(PlanFixtures.MidAdaptation), actions) }
        compose.onNodeWithTag(PlanTags.Rail).performScrollToIndex(9)
        compose.onNodeWithTag(PlanTags.Rail).performTouchInput { swipeDown(startY = centerY, endY = centerY + 40f) }
        compose.onRoot().captureRoboImage("src/test/screenshots/plan_rail_scrolled.png")
    }

    /** Compact phone: the dial gives up size so the Now card's Done is on screen at rest (issue #11). */
    /** Day 4 picked in the day strip: dial, Now card and header show Day 4 at the current time of day. */
    @Test
    fun dayPicked() {
        snap("day_picked", capture = false) { PlanContent(ready(PlanFixtures.MidAdaptation), actions) }
        compose.onNodeWithTag(PlanTags.dayPill(4)).performClick()
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("src/test/screenshots/plan_day_picked.png")
    }

    @Test
    fun dayPickedDark() {
        snap("day_picked_dark", darkTheme = true, capture = false) { PlanContent(ready(PlanFixtures.MidAdaptation), actions) }
        compose.onNodeWithTag(PlanTags.DayStrip).performScrollToNode(hasTestTag(PlanTags.dayPill(-1)))
        compose.onNodeWithTag(PlanTags.dayPill(-1)).performClick()
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("src/test/screenshots/plan_day_picked_dark.png")
    }

    @Test
    @Config(qualifiers = "w411dp-h640dp-xhdpi")
    fun compactPhone() = snap("compact_phone") { PlanContent(ready(PlanFixtures.MidAdaptation), actions) }

    /** Travel day on the rail: blocks on board carry "In flight", and a divider marks the switch to London time. */
    @Test
    fun railTravelDay() {
        snap("rail_travel_day", capture = false) { PlanContent(ready(PlanFixtures.InFlight), actions) }
        // Land on the zone switch into London time, then back off a little so the in-flight rows above it show too.
        compose.onNodeWithTag(PlanTags.Rail).performScrollToNode(hasTestTag(PlanTags.zoneSwitch(1)))
        compose.onNodeWithTag(PlanTags.Rail).performTouchInput {
            swipeDown(startY = centerY - 300f, endY = centerY + 300f, durationMillis = 1_000)
        }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("src/test/screenshots/plan_rail_travel_day.png")
    }

    @Test
    @Config(qualifiers = "w400dp-h1100dp-xhdpi")
    fun whySheet() = snap("why_sheet") {
        val advice = realPlan.days.first { it.index == 2 }.advice.first { it.type == AdviceType.SeeBrightLight }
        WhySheetContent(advice, ZoneId.of("Europe/London"), ZoneId.of("America/Los_Angeles"), flightRoute = null)
    }
}
