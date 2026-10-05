package dev.sebastiano.clockblocker.opus.e2e

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import dev.sebastiano.clockblocker.opus.AppGraph
import dev.sebastiano.clockblocker.opus.core.data.demo.DemoData
import dev.sebastiano.clockblocker.opus.core.model.Advice
import dev.sebastiano.clockblocker.opus.core.model.AdviceType
import dev.sebastiano.clockblocker.opus.core.model.JetLagPlan
import dev.sebastiano.clockblocker.opus.core.model.Trip
import dev.sebastiano.clockblocker.opus.feature.plan.activeAdviceAt
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Matches nodes whose test tag starts with [prefix] (e.g. `trip_card_`, `plan_block_`). */
fun hasTestTagPrefix(prefix: String): SemanticsMatcher = SemanticsMatcher("TestTag starts with '$prefix'") { node ->
    node.config.getOrNull(SemanticsProperties.TestTag)?.startsWith(prefix) == true
}

/** The node's test tag. */
val SemanticsNodeInteraction.testTag: String
    get() = fetchSemanticsNode().config[SemanticsProperties.TestTag]

/** The node's state description (e.g. a sleep-dial handle's time). */
val SemanticsNodeInteraction.stateDescription: String?
    get() = fetchSemanticsNode().config.getOrNull(SemanticsProperties.StateDescription)

/** The text of an editable field. */
val SemanticsNodeInteraction.editableText: String
    get() = fetchSemanticsNode().config.getOrNull(SemanticsProperties.EditableText)?.text.orEmpty()

/** Scrolls [this] into view when it sits in a scrollable container; a no-op for nodes that are always visible. */
fun SemanticsNodeInteraction.scrollToIfScrollable(): SemanticsNodeInteraction = apply {
    runCatching { performScrollTo() }
}

/** A demo trip (SFO → LHR) together with the advice its plan's Now card shows right now. */
data class ActiveTrip(val trip: Trip, val plan: JetLagPlan, val advice: Advice)

/**
 * Saves a SFO → LHR trip departing on whichever nearby day gives its plan a block of advice that is active now
 * and stays active for at least [minRemaining] (so a test has time to act on it), and returns it. Trips that
 * don't qualify are deleted again. The profile must be seeded first (plans need it).
 *
 * The block must be the Now card's headline ([activeAdviceAt]'s first, the same pick as the plan screen), not
 * just any active block: otherwise a higher-priority block that ends within the minute (e.g. "See some light"
 * until 18:00 over a long "Avoid caffeine") would swap the card's content, and its height, mid-test.
 */
fun AppGraph.seedTripWithActiveAdvice(minRemaining: Duration = Duration.ofMinutes(10)): ActiveTrip = runBlocking {
    val today = LocalDate.now(ZoneId.systemDefault())
    for (offset in listOf(0L, -1L, 1L, -2L, 2L, -3L, 3L, -4L, 4L, -5L, 5L, -6L, 6L)) {
        val trip = DemoData.sfoToLhr(today.plusDays(offset), id = "e2e-active-$offset")
        tripRepository.upsert(trip)
        val plan = withTimeout(15_000) { planRepository.plan(trip.id).filterNotNull().first() }
        val now = Instant.now()
        val active = plan.activeAdviceAt(now)
        val inFlight = active.any { it.type == AdviceType.Flight }
        val advice = active.firstOrNull()?.takeIf { !it.type.isMoment && it.end.isAfter(now.plus(minRemaining)) }
        if (advice != null && !inFlight) return@runBlocking ActiveTrip(trip, plan, advice)
        tripRepository.delete(trip.id)
    }
    error("No demo trip within ±6 days has advice active now")
}

/**
 * Brings [this] into its scrolling container's viewport and taps it.
 *
 * A node inside a lazy-list item that is only partly on screen still exists, so `awaitTag` finds it, but when
 * the node itself lies entirely below the visible part of the list its clipped bounds are empty and
 * `performClick` taps nothing. Where the Now card's Done button ends up at rest depends on the card's text (a
 * 12-hour clock and a long secondary zone wrap the "until" line onto a second line) and on the device's system
 * bars and navigation bar, so always scroll first rather than relying on the layout at rest.
 */
fun SemanticsNodeInteraction.scrollIntoViewAndClick(): SemanticsNodeInteraction =
    scrollToIfScrollable().assertIsDisplayed().performClick()
