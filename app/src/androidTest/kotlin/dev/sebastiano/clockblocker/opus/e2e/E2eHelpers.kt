package dev.sebastiano.clockblocker.opus.e2e

import android.os.SystemClock
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.StaleObjectException
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
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

/**
 * Scrolls [this] into view when it sits in a scrollable container; a no-op for nodes that are always visible.
 *
 * `performScrollTo` stops as soon as the node's bottom touches the scroll container's bottom edge, which can leave
 * the target right in the system gesture-navigation inset at the bottom of the screen. When the node sits near the
 * bottom of a vertical scroll container and has room above it, nudge the container a little further so the tap
 * lands well clear of the bottom edge.
 */
fun SemanticsNodeInteraction.scrollToIfScrollable(): SemanticsNodeInteraction = apply {
    runCatching { performScrollTo() }
    runCatching {
        val node = fetchSemanticsNode()
        val scrollable = generateSequence(node.parent) { it.parent }.firstOrNull {
            it.config.contains(SemanticsActions.ScrollBy) && it.config.contains(SemanticsProperties.VerticalScrollAxisRange)
        } ?: return@runCatching
        val viewport = scrollable.boundsInRoot
        val box = node.boundsInRoot
        val clearance = (viewport.height * 0.15f).coerceAtMost(BottomEdgeClearancePx)
        val extraDy = minOf(box.bottom - (viewport.bottom - clearance), box.top - (viewport.top + clearance))
        val scrollBy = scrollable.config.getOrNull(SemanticsActions.ScrollBy)?.action
        if (extraDy > 1f && scrollBy != null) {
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            instrumentation.runOnMainSync { scrollBy(0f, extraDy) }
            instrumentation.waitForIdleSync()
        }
    }
}

private const val BottomEdgeClearancePx = 160f

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

/**
 * Finds an object with [find] and clicks it, retrying for up to [timeoutMillis] while nothing is found or the
 * found object goes stale before the click lands. Returns whether a click was delivered.
 *
 * System UI (the notification shade above all) rebuilds its views while it settles, e.g. right after a group is
 * expanded, so an object found a moment ago can throw [StaleObjectException] on `click()` (or on a nested
 * `findObject`). Each attempt therefore looks everything up again from the device.
 */
fun UiDevice.clickWhenFound(timeoutMillis: Long, find: UiDevice.() -> UiObject2?): Boolean {
    val deadline = SystemClock.uptimeMillis() + timeoutMillis
    while (true) {
        try {
            val target = find()
            if (target != null) {
                target.click()
                return true
            }
        } catch (ignored: StaleObjectException) {
            // The view behind it was replaced: look it up again.
        }
        if (SystemClock.uptimeMillis() >= deadline) return false
        waitForIdle(ShadePollMillis)
        SystemClock.sleep(ShadePollMillis)
    }
}

/** [clickWhenFound] for a single [selector]. */
fun UiDevice.clickWhenFound(selector: BySelector, timeoutMillis: Long): Boolean =
    clickWhenFound(timeoutMillis) { findObject(selector) }

private const val ShadePollMillis = 250L

