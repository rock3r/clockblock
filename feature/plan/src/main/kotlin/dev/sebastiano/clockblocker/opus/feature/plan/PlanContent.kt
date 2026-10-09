package dev.sebastiano.clockblocker.opus.feature.plan

import android.content.res.Resources
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarState
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.sebastiano.clockblocker.opus.core.circadian.bodyClockTimeAt
import dev.sebastiano.clockblocker.opus.core.circadian.secondaryZoneFor
import dev.sebastiano.clockblocker.opus.core.designsystem.component.ShapeLoadingIndicator
import dev.sebastiano.clockblocker.opus.core.designsystem.dial.TwoSkiesDial
import dev.sebastiano.clockblocker.opus.core.designsystem.dial.toDialState
import dev.sebastiano.clockblocker.opus.core.designsystem.illustration.SuitcaseOClockArt
import dev.sebastiano.clockblocker.opus.core.designsystem.theme.EightBitMode
import dev.sebastiano.clockblocker.opus.core.designsystem.theme.LocalReduceMotion
import dev.sebastiano.clockblocker.opus.core.designsystem.theme.NightSafeTheme
import dev.sebastiano.clockblocker.opus.core.designsystem.theme.ClockblockTheme
import dev.sebastiano.clockblocker.opus.core.designsystem.theme.toHourFloat
import dev.sebastiano.clockblocker.opus.core.model.AdviceOutcome
import dev.sebastiano.clockblocker.opus.core.model.AdviceType
import dev.sebastiano.clockblocker.opus.core.model.JetLagPlan
import dev.sebastiano.clockblocker.opus.core.model.Trip
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** Everything the plan UI can ask of its host. Trip-specific actions are already bound to the shown trip. */
@Immutable
internal class PlanActions(
    val onBack: (() -> Unit)? = null,
    val onEditTrip: () -> Unit = {},
    val onCreateReturnTrip: () -> Unit = {},
    val onExportCalendar: () -> Unit = {},
    val onShareSummary: () -> Unit = {},
    val onNewTrip: () -> Unit = {},
    val onLog: (adviceId: String, outcome: AdviceOutcome) -> Unit = { _, _ -> },
    /** Restores the outcome a block had before the last log (null = not logged). */
    val onUndo: (adviceId: String, previous: AdviceOutcome?) -> Unit = { _, _ -> },
    val onSnooze: (adviceId: String) -> Unit = {},
    val onCelebrationShown: () -> Unit = {},
)

/**
 * UI-only state of the plan screen, hoisted above the Night-safe theme switch so scroll positions, the open sheet
 * and the dial preview survive the screen dimming itself.
 */
@Stable
internal class PlanScreenState(
    val snackbar: SnackbarHostState,
    val appBar: TopAppBarState,
    val list: LazyListState,
    private val tripRail: TripRail,
    val pane: ScrollState,
) {
    /**
     * The two-pane rail's scroll state on [tripId]. Each trip gets a fresh one: when the current plan moves to another
     * trip, a position on the old trip's rows means nothing on the new trip's, so its rail starts over (at its Now row,
     * or at the top when it has none). The shown trip's state is the one saved across recreation ([TripRail.Saver]).
     * Plain fields: swapping is idempotent, so a discarded composition can't leave anything half-done.
     */
    fun rail(tripId: String): LazyListState {
        if (tripRail.tripId != tripId) {
            if (tripRail.tripId != null) tripRail.state = LazyListState()
            tripRail.tripId = tripId
        }
        return tripRail.state
    }

    /** The rail last brought to its Now row on first show (one per trip, see [rail]). */
    var railPositioned: LazyListState? = null

    /** Instant the dial is being scrubbed to (null = now, or the picked day's anchor). */
    var preview: Instant? by mutableStateOf(null)

    /**
     * The day picked in the day strip (`PlanDay.index`) and the trip it was picked on. Keyed by trip because the
     * top-level current plan can switch trip under the same screen, and a day index means nothing on another trip.
     */
    private var selection: DayPick? by mutableStateOf(null)

    /** The day picked on [tripId] (null = live, or the pick belonged to another trip). */
    fun selectedDay(tripId: String): Int? = selection?.takeIf { it.tripId == tripId }?.index

    /** Whether the day picked on [tripId] was still to come when picked (such a pick expires once its day starts). */
    fun pickedFuture(tripId: String): Boolean = selection?.takeIf { it.tripId == tripId }?.future == true

    /** Whether the stored pick was made on a trip other than [tripId] (the current plan has moved to another trip). */
    fun hasPickOnOtherTrip(tripId: String): Boolean = selection?.let { it.tripId != tripId } == true

    /** Picks [index] on [tripId] ([future]: that day hasn't started yet), or goes back to live when [index] is null. */
    fun pickDay(tripId: String, index: Int?, future: Boolean = false) {
        selection = index?.let { DayPick(tripId, it, future) }
    }

    private data class DayPick(val tripId: String, val index: Int, val future: Boolean)

    /**
     * Counts picks in the day strip: the two-pane rail follows each one (an event, so a pick mid-scroll counts).
     * One pane leaves them unfollowed, so a pick made there is shown once a resize brings the rail into view.
     */
    var dayPicks: Int by mutableIntStateOf(0)

    /** The last pick the rail followed. A plain field: consuming the event mustn't restart (and cancel) its scroll. */
    var dayPicksFollowed: Int = 0

    var whyAdviceId: String? by mutableStateOf(null)
    var showEarlier: Boolean by mutableStateOf(false)
    var pendingScrollKey: String? by mutableStateOf(null)
    var celebrationDismissed: Boolean by mutableStateOf(false)
    var celebrationStage: CelebrationStage by mutableStateOf(CelebrationStage.Waiting)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun rememberPlanScreenState(): PlanScreenState {
    val snackbar = remember { SnackbarHostState() }
    val appBar = rememberTopAppBarState()
    val list = rememberLazyListState()
    val rail = rememberSaveable(saver = TripRail.Saver) { TripRail(null, LazyListState()) }
    val pane = rememberScrollState()
    return remember(snackbar, appBar, list, rail, pane) { PlanScreenState(snackbar, appBar, list, rail, pane) }
}

/** The two-pane rail's scroll state and the trip it belongs to; saved together, so a restore can't mix trips. */
internal class TripRail(var tripId: String?, var state: LazyListState) {
    companion object {
        val Saver: Saver<TripRail, Any> = listSaver(
            save = { listOf(it.tripId.orEmpty(), it.state.firstVisibleItemIndex, it.state.firstVisibleItemScrollOffset) },
            restore = { TripRail((it[0] as String).ifEmpty { null }, LazyListState(it[1] as Int, it[2] as Int)) },
        )
    }
}

/** Width from which the plan splits into hero pane + timeline pane (M3 "expanded"). */
private val TwoPaneMinWidth = 840.dp

/** A load faster than this never shows the loader at all (no flash), and the plan simply appears. */
internal const val LoaderShowDelayMillis = 150L

/** Stateless plan screen: renders [state], reports intents through [actions]. */
@Composable
internal fun PlanContent(
    state: PlanUiState,
    actions: PlanActions,
    modifier: Modifier = Modifier,
    screenState: PlanScreenState = rememberPlanScreenState(),
) {
    val motion = ClockblockTheme.motion
    var loaderShown by remember { mutableStateOf(false) }
    AnimatedContent(
        targetState = state,
        modifier = modifier,
        contentKey = { it::class },
        transitionSpec = {
            // Nothing was on screen yet (fast load): no cross-fade, the plan arrives with the navigation transition.
            if (initialState is PlanUiState.Loading && !loaderShown) {
                EnterTransition.None togetherWith ExitTransition.None
            } else {
                fadeIn(motion.colour()) togetherWith fadeOut(motion.fade())
            }
        },
        label = "planState",
    ) { shown ->
        when (shown) {
            PlanUiState.Loading -> LoadingPlan(actions, onShown = { loaderShown = true })
            is PlanUiState.NoPlan -> NoPlan(shown.missing, actions, Modifier)
            // Always the same shape: Night-safe toggles re-theme (cross-fade) instead of rebuilding the screen.
            is PlanUiState.Ready -> NightSafeTheme(shown.nightSafe) {
                EightBitMode(EightBitSession.enabled && shown.easterEggs) {
                    ReadyPlan(shown, actions, screenState, Modifier)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlainScaffold(actions: PlanActions, modifier: Modifier, content: @Composable (PaddingValues) -> Unit) {
    Scaffold(
        modifier = modifier.testTag(PlanTags.Screen),
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    val onBack = actions.onBack
                    if (onBack != null) {
                        IconButton(onClick = onBack, modifier = Modifier.testTag(PlanTags.Back)) {
                            Icon(PlanIcons.ArrowBack, contentDescription = stringResource(R.string.plan_navigate_up))
                        }
                    }
                },
            )
        },
        content = content,
    )
}

@Composable
private fun LoadingPlan(actions: PlanActions, onShown: () -> Unit) {
    var show by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(LoaderShowDelayMillis)
        show = true
        onShown()
    }
    if (!show) {
        Box(Modifier.fillMaxSize())
        return
    }
    PlainScaffold(actions, Modifier) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).testTag(PlanTags.Loading),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            ShapeLoadingIndicator(contentDescription = stringResource(R.string.plan_loading))
            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.plan_loading), style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
private fun NoPlan(missing: Boolean, actions: PlanActions, modifier: Modifier) {
    PlainScaffold(actions, modifier) { padding ->
        PlanEmptyState(
            art = { SuitcaseOClockArt(Modifier.size(200.dp)) },
            title = stringResource(if (missing) R.string.plan_missing_title else R.string.plan_empty_title),
            body = stringResource(if (missing) R.string.plan_missing_body else R.string.plan_empty_body),
            onNewTrip = actions.onNewTrip,
            modifier = Modifier.padding(padding).testTag(PlanTags.Empty),
        )
    }
}

/** Editorial empty state: illustration, Fraunces headline, one line of body and the "Plan a trip" button. */
@Composable
internal fun PlanEmptyState(
    art: @Composable () -> Unit,
    title: String,
    body: String,
    onNewTrip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 32.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        art()
        Spacer(Modifier.height(24.dp))
        Text(title, style = ClockblockTheme.textStyles.editorialHeadline, textAlign = TextAlign.Center, modifier = Modifier.widthIn(max = 480.dp))
        Spacer(Modifier.height(12.dp))
        Text(
            body,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 480.dp),
        )
        Spacer(Modifier.height(28.dp))
        Button(onClick = onNewTrip, modifier = Modifier.heightIn(min = 56.dp).testTag(PlanTags.NewTrip)) {
            Text(stringResource(R.string.plan_new_trip), style = MaterialTheme.typography.titleMedium)
        }
    }
}

/**
 * The parts of a flight's detail line: the route, then the planner's detail unless it is just the same route
 * again (without a flight number the planner falls back to "MXP→SIN").
 */
internal fun flightDetails(route: String?, detail: String?): List<String> {
    fun String.squashed() = filterNot { it.isWhitespace() }
    val extra = detail?.takeUnless { route != null && it.squashed() == route.squashed() }
    return listOfNotNull(route, extra)
}

/**
 * A trip saved moments ago: its plan opens with "first light" (see `HeaderCelestial`). [now] is the plan's
 * minute-resolution clock, so a trip saved during the current minute reads as up to a minute "in the future".
 */
internal fun isFreshTrip(createdAt: Instant, now: Instant): Boolean {
    val age = Duration.between(createdAt, now)
    return age >= NowResolution.negated() && age <= FreshTripWindow
}

private val FreshTripWindow: Duration = Duration.ofSeconds(90)
private val NowResolution: Duration = Duration.ofMinutes(1)

/** "SFO → LHR" for each flight block, matched to the trip's legs by departure (or id suffix). */
internal fun flightRoutes(plan: JetLagPlan, trip: Trip?, resources: Resources): Map<String, String> {
    if (trip == null) return emptyMap()
    return plan.allAdvice.filter { it.type == AdviceType.Flight }.mapNotNull { advice ->
        val leg = trip.legs.firstOrNull { it.departure == advice.start }
            ?: trip.legs.firstOrNull { advice.id.endsWith("-${it.id}") }
            ?: return@mapNotNull null
        advice.id to resources.getString(R.string.plan_flight_route, leg.origin.displayCode, leg.destination.displayCode)
    }.toMap()
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ReadyPlan(state: PlanUiState.Ready, actions: PlanActions, screen: PlanScreenState, modifier: Modifier) {
    val plan = state.plan
    val resources = LocalContext.current.resources
    val reduce = LocalReduceMotion.current
    val motion = ClockblockTheme.motion
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

    val railDays = remember(plan, state.now, state.outcomes) { plan.railDays(state.now, state.outcomes) }
    // A day picked in the strip anchors everything to that day at the current time of day; the dial's scrub is
    // relative to that anchor and springs back to it.
    val selectedDay = screen.selectedDay(plan.tripId)
    val pickedFuture = screen.pickedFuture(plan.tripId)
    val dayBase = remember(railDays, selectedDay, pickedFuture, state.now, state.moment.zone) {
        selectedDayBase(railDays, selectedDay, state.now, state.moment.zone, pickedFuture)
    }
    // A pick that is (or has become) today, a look ahead whose day has come (even while the app was away), or a
    // day the plan no longer has, is live: forget it, so the screen can't jump back to it once that day is past.
    // Going live counts as a pick, so the two-pane rail follows back to the Now row too.
    if (selectedDay != null && dayBase == null) {
        SideEffect {
            screen.pickDay(plan.tripId, null)
            screen.dayPicks++
        }
    }
    // The current plan moved to another trip (A → B, or back to A once B is deleted or edited away). A pick made on
    // the other trip is hidden here; forget it too, or an A → B → A switch would bring A's old pick back instead of
    // live time. (The two-pane rail starts over on each trip, see PlanScreenState.rail.)
    if (screen.hasPickOnOtherTrip(plan.tripId)) {
        SideEffect { screen.pickDay(plan.tripId, null) }
    }
    val rail = screen.rail(plan.tripId)
    val anchor = dayBase ?: state.now
    val anchorZone = railDays.firstOrNull { dayBase != null && it.day.index == selectedDay }?.zone ?: state.moment.zone
    val preview = screen.preview?.takeIf { it != anchor } ?: dayBase
    val shown = remember(plan, state.moment, preview) { if (preview == null) state.moment else plan.momentAt(preview) }
    val todayIndex = remember(railDays, state.now) {
        railDays.firstOrNull { !state.now.isBefore(it.start) && state.now.isBefore(it.end) }?.day?.index
    }
    val dayOffsets = remember(plan, railDays) { dayStripOffsets(plan, railDays) }
    val rows = remember(railDays, state.now, screen.showEarlier) { buildRailRows(railDays, state.now, screen.showEarlier) }
    // Two panes: the day the rail is showing, for the strip to mark and keep in sight. Layout is read in the derived
    // state, so scrolling only recomposes the strip when the day changes. (Rows start after the rail's title.)
    val railDayInView = remember(rail, rows) {
        derivedStateOf { rows.dayInView(rail.layoutInfo, firstRow = 1, atEnd = !rail.canScrollForward && rail.canScrollBackward) }
    }
    val routes = remember(plan, state.trip, resources) { flightRoutes(plan, state.trip, resources) }
    val highlighted = remember(shown, preview) {
        if (preview == null) emptySet() else buildSet {
            shown.active?.let { add(it.id) }
            shown.concurrent.forEach { add(it.id) }
        }
    }
    val showSnack: (String) -> Unit = { message ->
        scope.launch {
            screen.snackbar.currentSnackbarData?.dismiss()
            screen.snackbar.showSnackbar(message)
        }
    }
    val showActionSnack: (String, String, () -> Unit) -> Unit = { message, action, onAction ->
        scope.launch {
            screen.snackbar.currentSnackbarData?.dismiss()
            if (screen.snackbar.showSnackbar(message, actionLabel = action) == SnackbarResult.ActionPerformed) onAction()
        }
    }
    // The renderer outlives recompositions; its check-off reads the latest log and actions.
    val currentOutcomes by rememberUpdatedState(state.outcomes)
    val currentActions by rememberUpdatedState(actions)
    val renderer = remember(highlighted, routes, plan, screen) {
        RailRenderer(
            highlighted = highlighted,
            flightRoute = { routes[it] },
            bodyHour = { plan.bodyClockTimeAt(it).toHourFloat() },
            onBlockClick = { screen.whyAdviceId = it },
            onToggleEarlier = { screen.showEarlier = !screen.showEarlier },
            // Ticking a row's circle is the Now card's Done (same snackbar and Undo, which puts back what the log
            // held before); unticking forgets the log.
            onCheckOff = { id, done ->
                if (done) {
                    val previous = currentOutcomes[id]
                    currentActions.onLog(id, AdviceOutcome.Done)
                    showActionSnack(resources.getString(R.string.plan_logged_done), resources.getString(R.string.plan_undo)) {
                        currentActions.onUndo(id, previous)
                    }
                } else {
                    currentActions.onUndo(id, null)
                }
            },
        )
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(screen.appBar)
    val title = planTitle(plan, state.trip)
    val nowOffsetPx = with(density) { -72.dp.roundToPx() }
    // Whether the rail is moving for the dial's scrub (not for the user). Plain: only read by the follow-up below.
    val scrubFollowing = remember { booleanArrayOf(false) }
    val timeColumn = rememberTimeColumnWidth()

    // Konami code on the rail → 8-bit mode for the session (only where easter eggs are allowed).
    val eightBitOn = stringResource(R.string.plan_eight_bit_on)
    val eightBitExit = stringResource(R.string.plan_eight_bit_exit)
    val currentEasterEggs by rememberUpdatedState(state.easterEggs)
    val onKonami = remember(screen) {
        {
            if (currentEasterEggs && !EightBitSession.enabled) {
                EightBitSession.enabled = true
                showActionSnack(eightBitOn, eightBitExit) { EightBitSession.enabled = false }
            }
        }
    }
    val railGestures = Modifier.konamiCode(enabled = state.easterEggs, onCode = onKonami)

    BoxWithConstraints(modifier.fillMaxSize()) {
        val shortWindow = maxHeight < ShortWindowMaxHeight
        // Compact phones: the dial gives up size before the Now card's Done drops below the fold (issue #11). Sized
        // from the window, never from the collapsing header, so scrolling never resizes it.
        // The day strip goes above the dial when that still leaves the dial its smallest size; otherwise it
        // follows the Now card so Done stays above the fold.
        val withStrip = railDays.size > 1
        val stripFirst = withStrip && maxHeight - HeroReserve - StripReserve >= MinDialSize
        val dialSize = (maxHeight - HeroReserve - if (stripFirst) StripReserve else 0.dp).coerceIn(MinDialSize, MaxDialSize)
        val strip = if (withStrip) {
            DayStripModel(railDays, dayOffsets, todayIndex, first = stripFirst)
        } else {
            null
        }
        val sections = PlanSections(
            state, shown, preview != null, routes, actions, screen, showSnack, showActionSnack, dialSize,
            anchor = anchor, anchorZone = anchorZone, strip = strip,
        )
        // The real day where the traveller is, for the header's sky and sun path (issue #21): once per place and date.
        val shownDate = shown.instant.atZone(shown.zone).toLocalDate()
        val daylight = remember(state.trip, shown.zone, shownDate) { headerDaylight(state.trip, shown) }
        Scaffold(
            modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection).testTag(PlanTags.Screen),
            topBar = {
                PlanHeader(
                    title = title,
                    moment = shown,
                    firstDay = plan.days.firstOrNull()?.date,
                    nightSafe = state.nightSafe,
                    canEdit = state.trip != null,
                    easterEggs = state.easterEggs,
                    actions = actions,
                    onMoonTip = { showSnack(resources.getString(R.string.plan_moon_tip)) },
                    scrollBehavior = scrollBehavior,
                    shortWindow = shortWindow,
                    firstLight = state.trip?.let { isFreshTrip(it.createdAt, state.now) } == true,
                    // What the screen shows, not the raw pick: a spent pick is live in this very frame (selectedDay
                    // is only cleared after it), so the fade starts here rather than over an already-live sky.
                    skyKey = selectedDay.takeIf { dayBase != null },
                    daylight = daylight,
                )
            },
            snackbarHost = { SnackbarHost(screen.snackbar, Modifier.padding(bottom = 72.dp)) },
            containerColor = MaterialTheme.colorScheme.surface,
        ) { padding ->
            BoxWithConstraints(Modifier.fillMaxSize()) {
                // Short windows always split when there is room: one pane there leaves the plan a sliver under
                // the dial.
                val expanded = maxWidth >= TwoPaneMinWidth || (shortWindow && maxWidth >= ShortTwoPaneMinWidth)
                val heroKeys = sections.heroKeys
                val railList = if (expanded) rail else screen.list
                val railStart = if (expanded) 1 else heroKeys.size + 1
                val bottomPadding = padding.calculateBottomPadding() + if (shortWindow) 80.dp else 104.dp

                suspend fun scrollRail(rowIndex: Int, offset: Int) {
                    val index = railStart + rowIndex
                    if (reduce) railList.scrollToItem(index, offset) else railList.animateScrollToItem(index, offset)
                }

                // One pane: the toolbar's tools (jump to now, pick a day, explain) are about the rail, and the Now
                // card already has its own "Why?", so it only appears once the Now card has scrolled away. That
                // also keeps it from covering the Done button at rest. One owner for its motion: this visibility
                // (no scroll-driven exit-always on top), on the calm navigation tier rather than a bouncy spring.
                // Keyed on the Now card's index too: a height change can move the strip above or below it.
                val nowIndex = heroKeys.indexOf(PlanSections.KeyNow)
                val toolbarVisible by remember(expanded, nowIndex) {
                    derivedStateOf { expanded || screen.list.firstVisibleItemIndex > nowIndex }
                }
                val toolbar: @Composable BoxScope.() -> Unit = {
                    val scrim = MaterialTheme.colorScheme.surface
                    AnimatedVisibility(
                        visible = toolbarVisible,
                        enter = fadeIn(motion.fade()),
                        exit = fadeOut(motion.fade()),
                        modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
                    ) {
                        // The scrim fades with the toolbar (one event): rows passing underneath read as behind it,
                        // and at rest the last visible row is veiled rather than cut by a floating pill.
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .drawBehind {
                                    drawRect(
                                        Brush.verticalGradient(
                                            0f to scrim.copy(alpha = 0f),
                                            0.5f to scrim.copy(alpha = 0.82f),
                                            1f to scrim.copy(alpha = 0.94f),
                                        ),
                                    )
                                }
                                .padding(
                                    top = if (shortWindow) 12.dp else 28.dp,
                                    bottom = padding.calculateBottomPadding() + if (shortWindow) 8.dp else 16.dp,
                                ),
                            contentAlignment = Alignment.BottomCenter,
                        ) {
                            PlanToolbar(
                                days = railDays,
                                canWhy = shown.active != null || shown.upNext.isNotEmpty(),
                                onNow = {
                                    screen.preview = null
                                    screen.pickDay(plan.tripId, null)
                                    scope.launch {
                                        val now = rows.nowRowIndex()
                                        if (now >= 0) {
                                            scrollRail(now, nowOffsetPx)
                                        } else if (reduce) {
                                            railList.scrollToItem(0)
                                        } else {
                                            railList.animateScrollToItem(0)
                                        }
                                    }
                                },
                                onDay = { day ->
                                    val key = RailRow.Header(day, isToday = false).key
                                    if (rows.none { it.key == key }) screen.showEarlier = true
                                    screen.pendingScrollKey = key
                                },
                                onWhy = { screen.whyAdviceId = (shown.active ?: shown.upNext.firstOrNull())?.id },
                                modifier = Modifier.animateEnterExit(
                                    enter = slideInVertically(motion.navigationSpatial()) { it / 2 },
                                    exit = slideOutVertically(motion.navigationSpatial()) { it / 2 },
                                ),
                            )
                        }
                    }
                }

                if (expanded) {
                    // Two panes: the rail is in view, so picking a day also brings that day's rows up (today:
                    // the Now line).
                    LaunchedEffect(screen.dayPicks) {
                        if (screen.dayPicks == screen.dayPicksFollowed) return@LaunchedEffect
                        screen.dayPicksFollowed = screen.dayPicks
                        val picked = railDays.firstOrNull { dayBase != null && it.day.index == selectedDay }
                        if (picked == null) {
                            val now = rows.nowRowIndex()
                            if (now >= 0) scrollRail(now, nowOffsetPx)
                        } else {
                            val key = RailRow.Header(picked, isToday = false).key
                            if (rows.none { it.key == key }) screen.showEarlier = true
                            screen.pendingScrollKey = key
                        }
                    }
                    Row(Modifier.fillMaxSize().padding(top = padding.calculateTopPadding())) {
                        Column(
                            Modifier
                                .weight(0.46f)
                                .fillMaxHeight()
                                .verticalScroll(screen.pane)
                                .padding(bottom = bottomPadding),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            heroKeys.forEach { key -> sections.Section(key, railDayInView) }
                            PlanFooter()
                        }
                        Box(Modifier.weight(0.54f).fillMaxHeight()) {
                            LazyColumn(
                                state = rail,
                                modifier = Modifier.fillMaxSize().then(railGestures).testTag(PlanTags.Rail),
                                contentPadding = PaddingValues(bottom = bottomPadding),
                            ) {
                                item("rail-title") {
                                    SectionTitle(
                                        stringResource(R.string.plan_rail_title),
                                        Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 4.dp),
                                    )
                                }
                                railRows(rows, renderer, timeColumn)
                            }
                            toolbar()
                        }
                    }
                    // First show of this trip's rail: bring up its Now row. With none (a trip that hasn't started), the
                    // fresh rail stays at the top, on the first day.
                    LaunchedEffect(rail) {
                        if (screen.railPositioned !== rail) {
                            // A day already picked (in one pane, before a resize) is the follow-up's to show.
                            val now = rows.nowRowIndex()
                            if (now > 0 && dayBase == null) rail.scrollToItem(1 + now, nowOffsetPx)
                            screen.railPositioned = rail
                        }
                    }
                    // Scrubbing the dial: the row under its hand (highlighted) comes into view. Keyed by the row, so
                    // it moves once per block crossed, never per frame of the scrub; only when the row is (partly)
                    // off screen; on dataSpatial (it is a time position), a jump under reduce motion; and never over
                    // a scroll of the user's own (a drag or fling already running).
                    val scrubbing = screen.preview?.takeIf { it != anchor } != null
                    val scrubRow = if (scrubbing) rows.highlightedRowIndex(highlighted) else -1
                    // A scrub back past midnight can land in a day folded behind "earlier days": unfold it, so its row
                    // exists to come up (the follow-up runs once the rows include it).
                    if (scrubbing && scrubRow < 0 && !screen.showEarlier &&
                        railDays.any { day -> day.isPast && day.items.any { it.advice.id in highlighted } }
                    ) {
                        SideEffect { screen.showEarlier = true }
                    }
                    LaunchedEffect(scrubRow) {
                        if (scrubRow < 0) {
                            scrubFollowing[0] = false
                            return@LaunchedEffect
                        }
                        if (rail.isScrollInProgress && !scrubFollowing[0]) return@LaunchedEffect
                        val index = 1 + scrubRow
                        val layout = rail.layoutInfo
                        val item = layout.visibleItemsInfo.firstOrNull { it.index == index }
                        // The pinned day header covers the top: a row is only in view below it (where rows land).
                        val visibleStart = -nowOffsetPx
                        val visibleEnd = layout.viewportEndOffset - layout.afterContentPadding
                        if (item != null && item.offset >= visibleStart && item.offset + item.size <= visibleEnd) return@LaunchedEffect
                        scrubFollowing[0] = true
                        try {
                            if (reduce) rail.scrollToItem(index, nowOffsetPx) else rail.animateScrollToItem(index, nowOffsetPx, motion.dataSpatial())
                            scrubFollowing[0] = false
                        } catch (e: CancellationException) {
                            // The user took the rail over (this effect lives on): their scroll is theirs from now on. A
                            // restart for the next row (this effect cancelled) keeps the flag for the follow-up.
                            if (isActive) scrubFollowing[0] = false
                            throw e
                        }
                    }
                } else {
                    LazyColumn(
                        state = screen.list,
                        // Padding (not contentPadding) on top so sticky day headers pin below the header.
                        modifier = Modifier.fillMaxSize().padding(top = padding.calculateTopPadding()).then(railGestures).testTag(PlanTags.Rail),
                        contentPadding = PaddingValues(bottom = bottomPadding),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        heroKeys.forEach { key -> item(key, contentType = key) { sections.Section(key) } }
                        item("rail-title") {
                            SectionTitle(
                                stringResource(R.string.plan_rail_title),
                                Modifier.widthIn(max = 640.dp).fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 4.dp),
                            )
                        }
                        railRows(rows, renderer, timeColumn)
                        item("footer") { PlanFooter() }
                    }
                    toolbar()
                }

                LaunchedEffect(screen.pendingScrollKey, rows) {
                    val key = screen.pendingScrollKey ?: return@LaunchedEffect
                    val index = rows.indexOfFirst { it.key == key }
                    if (index >= 0) {
                        scrollRail(index, 0)
                        screen.pendingScrollKey = null
                    }
                }

            }
        }

        screen.whyAdviceId?.let { id ->
            val target = remember(plan, id) {
                plan.days.firstNotNullOfOrNull { day -> day.advice.firstOrNull { it.id == id }?.let { day to it } }
            }
            if (target != null) {
                val (day, advice) = target
                val zone = ZoneId.of(day.zoneId)
                val secondary = plan.secondaryZoneFor(zone)
                WhySheet(advice, zone, secondary, routes[id], onDismiss = { screen.whyAdviceId = null })
            }
        }

        if (state.celebrate) {
            // One mover at a time: navigation settles → rings align (the dial reports) → overlay.
            LaunchedEffect(reduce) {
                if (reduce) {
                    if (screen.celebrationStage < CelebrationStage.Showing) screen.celebrationStage = CelebrationStage.Showing
                    return@LaunchedEffect
                }
                if (screen.celebrationStage == CelebrationStage.Waiting) {
                    delay(CelebrationNavSettleMillis)
                    screen.celebrationStage = CelebrationStage.Aligning
                }
                if (screen.celebrationStage == CelebrationStage.Aligning) {
                    delay(CelebrationAlignTimeoutMillis)
                    if (screen.celebrationStage == CelebrationStage.Aligning) screen.celebrationStage = CelebrationStage.Showing
                }
            }
            CelebrationOverlay(
                plan = plan,
                visible = screen.celebrationStage == CelebrationStage.Showing && !screen.celebrationDismissed,
                onDismiss = {
                    screen.celebrationDismissed = true
                    screen.celebrationStage = CelebrationStage.Done
                    actions.onCelebrationShown()
                },
            )
        }
    }
}

/** The hero sections above the rail (dial, Now, Up next, status), shared by the one- and two-pane layouts. */
@Stable
private class PlanSections(
    val state: PlanUiState.Ready,
    val shown: PlanMoment,
    val previewing: Boolean,
    val routes: Map<String, String>,
    val actions: PlanActions,
    val screen: PlanScreenState,
    val showSnack: (String) -> Unit,
    val showActionSnack: (message: String, action: String, onAction: () -> Unit) -> Unit,
    val dialSize: Dp = MaxDialSize,
    /** What the dial is anchored to: now, or the picked day at the current time of day. */
    val anchor: Instant = state.now,
    val anchorZone: ZoneId = state.moment.zone,
    val strip: DayStripModel? = null,
) {
    val heroKeys: List<String> = buildList {
        if (strip?.first == true) add(KeyDays)
        add(KeyDial)
        add(KeyNow)
        if (strip != null && !strip.first) add(KeyDays)
        if (shown.upNext.isNotEmpty() && shown.stage != PlanStage.Complete && shown.stage != PlanStage.Upcoming) add(KeyUpNext)
        if (shown.stage != PlanStage.Complete) add(KeyStatus)
    }

    /** [railDayInView]: the day the rail beside the hero shows (two panes only), for the day strip. */
    @Composable
    fun Section(key: String, railDayInView: State<Int?>? = null) {
        val width = Modifier.widthIn(max = 640.dp).fillMaxWidth()
        when (key) {
            // Keyed by trip: the current plan can move on to another trip, whose strip starts from its own days
            // rather than the old one's scroll position.
            KeyDays -> strip?.let { model ->
                key(state.plan.tripId) {
                    PlanDayStrip(
                        days = model.days,
                        offsets = model.offsets,
                        todayIndex = model.todayIndex,
                        selectedIndex = screen.selectedDay(state.plan.tripId)?.takeIf { picked -> model.days.any { it.day.index == picked } },
                        onSelect = { index ->
                            screen.preview = null
                            val future = model.days.firstOrNull { it.day.index == index }?.start?.isAfter(state.now) == true
                            screen.pickDay(state.plan.tripId, index, future)
                            screen.dayPicks++
                        },
                        modifier = width,
                        inViewIndex = railDayInView?.value,
                    )
                }
            }
            KeyDial -> Dial(width)
            KeyNow -> Now(width.padding(horizontal = 16.dp, vertical = 8.dp))
            KeyUpNext -> UpNextCard(shown, onClick = { screen.whyAdviceId = it.id }, modifier = width.padding(horizontal = 16.dp, vertical = 8.dp))
            KeyStatus -> Status(width.padding(horizontal = 16.dp, vertical = 8.dp))
        }
    }

    @Composable
    private fun Dial(modifier: Modifier) {
        val plan = state.plan
        val now = anchor
        val zone = anchorZone
        // The dial owns the scrub offset; its state stays at the anchor (now, or the picked day) so the offset
        // doesn't compound.
        // The sky rings follow the real sun where the traveller is in that zone (the default sun if no stop is).
        val place = remember(state.trip, zone, now) { state.trip?.placeIn(zone, at = now) }
        val nowState = remember(plan, now, zone, place) { plan.toDialState(now, zone, place) }
        // Celebration: the rings start where they were on arrival and turn into alignment once navigation settles.
        val holdAtArrival = state.celebrate && screen.celebrationStage == CelebrationStage.Waiting
        val dialState = if (holdAtArrival) {
            remember(nowState, plan) { nowState.copy(bodyAheadMinutes = plan.arrivalBodyAheadMinutes(zone)) }
        } else {
            nowState
        }
        val rewind = stringResource(R.string.plan_rewind)
        Box(modifier.padding(horizontal = 24.dp, vertical = 8.dp), contentAlignment = Alignment.Center) {
            // The dial keeps its scrub offset (e.g. after TalkBack's Next block) relative to the anchor: a new pick is a
            // new anchor, so the dial starts fresh there instead of carrying the old offset onto the new day.
            key(screen.selectedDay(plan.tripId)) {
                TwoSkiesDial(
                    state = dialState,
                    modifier = Modifier.widthIn(max = dialSize).fillMaxWidth().testTag(PlanTags.Dial),
                    onScrub = { instant ->
                        val minute = instant.truncatedTo(ChronoUnit.MINUTES)
                        screen.preview = if (minute == now) null else minute
                    },
                    onScrubEnd = { screen.preview = null },
                    onRewind = { showSnack(rewind) },
                    easterEggsEnabled = state.easterEggs,
                    returnOnRelease = true,
                    onRingsAligned = {
                        if (screen.celebrationStage == CelebrationStage.Aligning) screen.celebrationStage = CelebrationStage.Showing
                    },
                )
            }
        }
    }

    @Composable
    private fun Now(modifier: Modifier) {
        val active = shown.active
        val resources = LocalContext.current.resources
        // Previewing another day (picked, or scrubbed past midnight): the heading says which.
        val previewDay = shown.day?.takeIf { previewing && it.index != state.moment.day?.index }?.let { resources.dayShortTitle(it) }
        when {
            active != null -> NowCard(
                moment = shown,
                previewing = previewing,
                previewDay = previewDay,
                outcome = state.outcomes[active.id],
                flightRoute = routes[active.id],
                onOutcome = { outcome ->
                    val previous = state.outcomes[active.id]
                    actions.onLog(active.id, outcome)
                    showActionSnack(
                        resources.getString(
                            when (outcome) {
                                AdviceOutcome.Done -> R.string.plan_logged_done
                                AdviceOutcome.Skipped -> R.string.plan_logged_skipped
                                AdviceOutcome.CantDo -> R.string.plan_logged_cant
                            },
                        ),
                        resources.getString(R.string.plan_undo),
                    ) { actions.onUndo(active.id, previous) }
                },
                onSnooze = {
                    actions.onSnooze(active.id)
                    showSnack(resources.getString(R.string.plan_snoozed))
                },
                onWhy = { screen.whyAdviceId = active.id },
                modifier = modifier,
            )
            shown.stage == PlanStage.Complete -> CompleteCard(state.plan, state.outcomes, modifier)
            shown.stage == PlanStage.Upcoming -> UpcomingCard(state.plan, shown, modifier)
            else -> FreeTimeCard(shown, previewing, modifier, previewDay = previewDay)
        }
    }

    @Composable
    private fun Status(modifier: Modifier) {
        when (state.kind) {
            PlanKind.Adapt -> AdaptationCard(state.plan, shown, modifier)
            PlanKind.NoShift -> NoShiftCard(state.plan, state.sleep, modifier)
            PlanKind.StayOnHomeTime -> StayOnHomeCard(state.plan, modifier)
        }
    }

    companion object {
        const val KeyDays = "hero-days"
        const val KeyDial = "hero-dial"
        const val KeyNow = "hero-now"
        const val KeyUpNext = "hero-up-next"
        const val KeyStatus = "hero-status"
    }
}

@Composable
private fun PlanFooter() {
    Text(
        stringResource(R.string.plan_footer),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth().padding(horizontal = 32.dp, vertical = 24.dp),
    )
}

/**
 * Floating toolbar (M3 Expressive): jump to now, pick a day, explain the current block. Every action is labelled.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PlanToolbar(
    days: List<RailDay>,
    canWhy: Boolean,
    onNow: () -> Unit,
    onDay: (RailDay) -> Unit,
    onWhy: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val label = stringResource(R.string.plan_toolbar_label)
    var picking by remember { mutableStateOf(false) }
    HorizontalFloatingToolbar(
        expanded = true,
        colors = FloatingToolbarDefaults.standardFloatingToolbarColors(),
        modifier = modifier.testTag(PlanTags.Toolbar).semantics { contentDescription = label },
    ) {
        ToolbarAction(PlanIcons.MyLocation, stringResource(R.string.plan_toolbar_now), onNow, Modifier.testTag(PlanTags.ToolbarNow))
        Box {
            ToolbarAction(PlanIcons.CalendarToday, stringResource(R.string.plan_toolbar_day), { picking = true }, Modifier.testTag(PlanTags.ToolbarDay))
            DropdownMenu(expanded = picking, onDismissRequest = { picking = false }) {
                days.forEach { day ->
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text(dayTitle(day.day), style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    formatDayDate(day.day.date),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        },
                        onClick = {
                            picking = false
                            onDay(day)
                        },
                        modifier = Modifier.testTag(PlanTags.dayPick(day.day.index)),
                    )
                }
            }
        }
        ToolbarAction(PlanIcons.Help, stringResource(R.string.plan_toolbar_why), onWhy, Modifier.testTag(PlanTags.ToolbarWhy), enabled = canWhy)
    }
}

@Composable
private fun ToolbarAction(icon: ImageVector, label: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        contentPadding = PaddingValues(horizontal = 14.dp),
        colors = ButtonDefaults.textButtonColors(contentColor = LocalContentColor.current),
        modifier = modifier.heightIn(min = 48.dp),
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.labelLarge)
    }
}

/** The day strip's share of the hero (pills + padding). */
private val StripReserve = 80.dp

/** The day strip's data, and whether it sits above the dial ([first]) or after the Now card. */
@Immutable
internal class DayStripModel(
    val days: List<RailDay>,
    val offsets: Map<Int, Float>,
    val todayIndex: Int?,
    val first: Boolean,
)

/** The dial's size on roomy windows, and the smallest it gets on compact ones. */
private val MaxDialSize = 320.dp
private val MinDialSize = 200.dp

/**
 * Window height the expanded header, the Now card (with Done) and the paddings around the dial need, so the dial
 * takes what is left (between [MinDialSize] and [MaxDialSize]).
 */
private val HeroReserve = 456.dp

/** Below this window height (landscape phones) the header stays collapsed and the toolbar zone tightens. */
private val ShortWindowMaxHeight = 480.dp
private val ShortTwoPaneMinWidth = 600.dp
