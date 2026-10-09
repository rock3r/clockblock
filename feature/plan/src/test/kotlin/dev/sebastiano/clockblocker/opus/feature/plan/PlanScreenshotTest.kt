package dev.sebastiano.clockblocker.opus.feature.plan

import android.content.Context
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.unit.Density
import androidx.test.core.app.ApplicationProvider
import dev.sebastiano.clockblocker.opus.core.designsystem.theme.ClockblockTheme
import dev.sebastiano.clockblocker.opus.core.model.AdviceOutcome
import dev.sebastiano.clockblocker.opus.core.model.AdviceType
import dev.sebastiano.clockblocker.opus.core.testing.captureRoboImageInvalidated
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
@Config(sdk = [37], qualifiers = "w400dp-h880dp-xhdpi")
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
                ClockblockTheme(darkTheme = darkTheme, dynamicColor = false, reduceMotion = true) {
                    Box(Modifier.background(MaterialTheme.colorScheme.surface)) { content() }
                }
            }
        }
        if (capture) compose.captureRoboImageInvalidated("src/test/screenshots/plan_$name.png")
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
        compose.captureRoboImageInvalidated("src/test/screenshots/plan_loading.png")
    }

    @Test
    @Config(qualifiers = "w400dp-h880dp-xhdpi")
    fun fontScale() = snap("pre_trip_font_1_5", fontScale = 1.5f) { PlanContent(ready(PlanFixtures.PreTrip), actions) }

    @Test
    @Config(qualifiers = "w1280dp-h800dp-xhdpi")
    fun expandedTwoPane() = snap("expanded_two_pane") { PlanContent(ready(PlanFixtures.MidAdaptation), PlanActions()) }

    /** Two panes, the rail scrolled by hand to Day 4: the strip marks Day 4's pill (a bar at its foot), today stays selected. */
    @Test
    @Config(qualifiers = "w1280dp-h800dp-xhdpi")
    fun expandedTwoPaneRailScrolled() {
        snap("expanded_two_pane_rail_scrolled", capture = false) { PlanContent(ready(PlanFixtures.MidAdaptation), PlanActions()) }
        compose.onNodeWithTag(PlanTags.Rail).performScrollToNode(hasTestTag(PlanTags.day(4)))
        compose.waitForIdle()
        compose.captureRoboImageInvalidated("src/test/screenshots/plan_expanded_two_pane_rail_scrolled.png")
    }

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
        compose.captureRoboImageInvalidated("src/test/screenshots/plan_rail_scrolled.png")
    }

    /** Compact phone: the dial gives up size so the Now card's Done is on screen at rest (issue #11). */
    /** The adaptation card: with/without hero, journey chart (dot on Day 2) and legend. */
    @Test
    @Config(qualifiers = "w400dp-h700dp-xhdpi")
    fun adaptationCard() = snap("adaptation_card") {
        Box(Modifier.padding(16.dp)) { AdaptationCard(realPlan, realPlan.momentAt(PlanFixtures.MidAdaptation)) }
    }

    @Test
    @Config(qualifiers = "w400dp-h1000dp-xhdpi")
    fun adaptationCardFontScale() = snap("adaptation_card_font_1_5", fontScale = 1.5f) {
        Box(Modifier.padding(16.dp)) { AdaptationCard(realPlan, realPlan.momentAt(PlanFixtures.MidAdaptation)) }
    }

    /** An 11 h eastward trip delayed instead: the card explains the long way round. */
    @Test
    @Config(qualifiers = "w400dp-h1000dp-xhdpi")
    fun adaptationLongWayDark() = snap("adaptation_long_way_dark", darkTheme = true) {
        val plan = PlanFixtures.longWayPlan
        Box(Modifier.padding(16.dp)) { AdaptationCard(plan, plan.momentAt(plan.landing!!.plusSeconds(36 * 3600))) }
    }

    /** Day 4 picked in the day strip: dial, Now card and header show Day 4 at the current time of day. */
    @Test
    fun dayPicked() {
        snap("day_picked", capture = false) { PlanContent(ready(PlanFixtures.MidAdaptation), actions) }
        compose.onNodeWithTag(PlanTags.dayPill(4)).performClick()
        compose.waitForIdle()
        compose.captureRoboImageInvalidated("src/test/screenshots/plan_day_picked.png")
    }

    @Test
    fun dayPickedDark() {
        snap("day_picked_dark", darkTheme = true, capture = false) { PlanContent(ready(PlanFixtures.MidAdaptation), actions) }
        compose.onNodeWithTag(PlanTags.DayStrip).performScrollToNode(hasTestTag(PlanTags.dayPill(-1)))
        compose.onNodeWithTag(PlanTags.dayPill(-1)).performClick()
        compose.waitForIdle()
        compose.captureRoboImageInvalidated("src/test/screenshots/plan_day_picked_dark.png")
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
        compose.captureRoboImageInvalidated("src/test/screenshots/plan_rail_travel_day.png")
    }

    @Test
    @Config(qualifiers = "w400dp-h1600dp-xhdpi")
    fun whySheet() = snap("why_sheet") {
        val advice = realPlan.days.first { it.index == 2 }.advice.first { it.type == AdviceType.SeeBrightLight }
        // The sheet's container (and so its content colour), as in WhySheet.
        Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
            WhySheetContent(
                advice,
                ZoneId.of("Europe/London"),
                ZoneId.of("America/Los_Angeles"),
                flightRoute = null,
                curveWindow = lightCurveWindow(realPlan, advice),
            )
        }
    }

    @Test
    @Config(qualifiers = "w400dp-h1600dp-xhdpi")
    fun whySheetAvoidLightDark() = snap("why_sheet_avoid_light_dark", darkTheme = true) {
        val advice = realPlan.allAdvice.first { it.type == AdviceType.AvoidLight }
        // The sheet's container (and so its content colour), as in WhySheet.
        Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
            WhySheetContent(
                advice,
                ZoneId.of("Europe/London"),
                ZoneId.of("America/Los_Angeles"),
                flightRoute = null,
                curveWindow = lightCurveWindow(realPlan, advice),
            )
        }
    }
}
