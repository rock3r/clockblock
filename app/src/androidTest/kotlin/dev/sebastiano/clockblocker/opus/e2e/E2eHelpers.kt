package dev.sebastiano.clockblocker.opus.e2e

import android.os.SystemClock
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
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
import kotlin.math.abs

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
 * Scrolls the lazy list tagged [listTag] until the node tagged [tag] is in the middle of its viewport, and returns
 * that node.
 *
 * `performScrollToNode` scrolls a lazy item to the very top of the list, where a sticky day header on the plan's
 * rail is pinned over it, so a tap on the row's top edge (a check-off circle) can land on the header instead. The
 * middle is clear of that header and of the floating toolbar and snackbars at the bottom.
 */
fun ClockblockE2eTest.scrollToMiddle(listTag: String, tag: String): SemanticsNodeInteraction {
    val list = compose.onNodeWithTag(listTag)
    list.performScrollToNode(hasTestTag(tag))
    compose.waitForIdle()
    val viewport = list.fetchSemanticsNode().boundsInRoot
    val dy = awaitTag(tag).fetchSemanticsNode().boundsInRoot.center.y - viewport.center.y
    if (abs(dy) > 1f) {
        list.performSemanticsAction(SemanticsActions.ScrollBy) { scrollBy -> scrollBy(0f, dy) }
        compose.waitForIdle()
    }
    return awaitTag(tag)
}

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

/**
 * Types [query] into the search field [fieldTag], then waits for the result [resultTag] to show up and for the
 * results to stop moving. Retries once, clearing the field and typing the query again, if the result never shows.
 *
 * Typing focuses the field and raises the keyboard. On a loaded emulator, the search can come back while the IME
 * insets animation is still moving the results, or a keystroke can be lost as the field takes focus, so the query
 * never matches (issue #54). Retyping a cleared field starts a new search; re-setting the same text wouldn't, as the
 * field's value wouldn't change. So: type, wait for the keyboard, wait for the result, wait for its bounds to
 * settle. The second attempt waits twice as long for the result before giving up.
 */
fun ClockblockE2eTest.searchUntilResult(fieldTag: String, query: String, resultTag: String) {
    repeat(SearchAttempts) { attempt ->
        val field = awaitTag(fieldTag).scrollToIfScrollable()
        if (attempt > 0) field.performTextClearance()
        field.performTextInput(query)
        awaitImeShown()
        val last = attempt == SearchAttempts - 1
        val timeout = if (last) 2 * ClockblockE2eTest.DefaultTimeoutMillis else ClockblockE2eTest.DefaultTimeoutMillis
        val shown = runCatching { awaitTag(resultTag, timeout) }
        if (shown.isSuccess) {
            awaitSettledBounds(resultTag)
            return
        }
        if (last) shown.getOrThrow()
    }
}

/**
 * Taps the search result [resultTag] once the keyboard and the layout have settled, then waits until [picked]
 * matches. Retries the tap once if the first one selected nothing.
 *
 * Typing into a search field raises the keyboard, and the results move up with the IME insets animation, which
 * can take well over a second on a cold CI emulator. A synthetic tap sent while the rows are still moving can land
 * between them and select nothing (issue #54). So: wait for the keyboard to show and the layout to stop moving,
 * bring the row into view (the viewport is final by then), let that scroll settle too, and only then tap.
 */
fun ClockblockE2eTest.pickSearchResult(resultTag: String, picked: SemanticsMatcher) {
    repeat(PickAttempts) { attempt ->
        awaitImeShown()
        awaitSettledBounds(resultTag)
        awaitTag(resultTag).scrollToIfScrollable()
        awaitSettledBounds(resultTag)
        awaitTag(resultTag).performClick()
        val last = attempt == PickAttempts - 1
        val selected = runCatching {
            await(picked, if (last) ClockblockE2eTest.DefaultTimeoutMillis else PickRetryAfterMillis)
        }
        if (selected.isSuccess) return
        if (last) selected.getOrThrow()
    }
}

/**
 * Waits up to [timeoutMillis] for the keyboard to be showing in the resumed activity's window. Carries on
 * silently when it never shows (e.g. a device with a hardware keyboard): the layout settle check still applies.
 */
fun awaitImeShown(timeoutMillis: Long = ImeShowTimeoutMillis) {
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    val deadline = SystemClock.uptimeMillis() + timeoutMillis
    while (SystemClock.uptimeMillis() < deadline) {
        var shown = false
        instrumentation.runOnMainSync {
            val activity = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).firstOrNull()
            val insets = activity?.window?.decorView?.let(ViewCompat::getRootWindowInsets)
            shown = insets?.isVisible(WindowInsetsCompat.Type.ime()) == true
        }
        if (shown) return
        SystemClock.sleep(SettlePollMillis)
    }
}

/**
 * Waits until the node tagged [tag] has kept the same bounds for [SettleStablePolls] polls in a row, i.e. the
 * IME insets animation (or a scroll) that moves it has finished. The root insets report the keyboard as visible
 * as soon as its animation starts, so this is what tells that the layout has caught up. Gives up after
 * [timeoutMillis] and lets the caller's retry handle a layout that never settles.
 */
fun ClockblockE2eTest.awaitSettledBounds(tag: String, timeoutMillis: Long = ClockblockE2eTest.DefaultTimeoutMillis) {
    val deadline = SystemClock.uptimeMillis() + timeoutMillis
    var last = awaitTag(tag).fetchSemanticsNode().boundsInWindow
    var stable = 0
    while (stable < SettleStablePolls && SystemClock.uptimeMillis() < deadline) {
        device.waitForIdle(SettlePollMillis)
        SystemClock.sleep(SettlePollMillis)
        val bounds = awaitTag(tag).fetchSemanticsNode().boundsInWindow
        stable = if (bounds == last) stable + 1 else 0
        last = bounds
    }
}

private const val SearchAttempts = 2
private const val PickAttempts = 2
private const val PickRetryAfterMillis = 3_000L
private const val ImeShowTimeoutMillis = 5_000L
private const val SettlePollMillis = 100L
private const val SettleStablePolls = 3

