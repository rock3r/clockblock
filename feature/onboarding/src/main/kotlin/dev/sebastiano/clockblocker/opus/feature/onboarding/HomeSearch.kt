package dev.sebastiano.clockblocker.opus.feature.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imeAnimationTarget
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.unit.Constraints
import dev.sebastiano.clockblocker.opus.core.designsystem.component.PlaceResultRow
import dev.sebastiano.clockblocker.opus.core.designsystem.component.placeResultRowHeight
import dev.sebastiano.clockblocker.opus.core.designsystem.theme.ClockblockTheme
import dev.sebastiano.clockblocker.opus.core.model.Place
import dev.sebastiano.clockblocker.opus.feature.onboarding.profile.SegmentedGap
import dev.sebastiano.clockblocker.opus.feature.onboarding.profile.segmentedShape
import java.time.Instant
import kotlin.math.roundToInt

/**
 * The state a [HomeSearch] shares with its host: whether the search has the screen to itself, so the host can fold
 * its own chrome away (onboarding's bars, the Settings editor's dialog frame).
 */
@Stable
class HomeSearchState {
    /**
     * True while the field is focused, the keyboard is up and the normal layout left room for fewer than
     * [MinVisibleResults] results: the field goes to the top and the results fill the space down to the keyboard.
     */
    var fullHeight: Boolean by mutableStateOf(false)
        internal set
}

@Composable
fun rememberHomeSearchState(): HomeSearchState = remember { HomeSearchState() }

/**
 * The home time zone search, the same in onboarding and Settings: the field, the result count (a polite live region)
 * and the results, plus [above] (the current zone and anything else that makes way for the results) and [below].
 *
 * When focusing the field with the keyboard up leaves too little room ([HomeSearchState.fullHeight], measured: fewer
 * than [MinVisibleResults] result rows fit under [above], the field and the count in [room] minus the keyboard),
 * [above] and [below] fold away and the search takes [room] down to the keyboard, its results scrolling on their own.
 * On a [wide] window (a landscape phone, whose keyboard leaves room for barely one row under the field) the field and
 * the count move to a start column and the results fill the rest, in as many columns as fit, from the top.
 * The field's arrow, Back, the keyboard's Search key, picking a result or hiding the keyboard leave it. The field
 * never leaves the composition, so it keeps focus and the keyboard throughout.
 *
 * [room] is the height the host gives the search without the keyboard; the keyboard is taken off in layout, frame
 * by frame, so its animation never recomposes the search. [resultsQuery] is the query [results] answer: while a
 * newer query is in flight the count keeps its last text and its line, so the rows never move and TalkBack only hears
 * settled counts.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeSearch(
    state: HomeSearchState,
    query: String,
    results: List<Place>,
    resultsQuery: String,
    now: Instant,
    room: Dp,
    onQueryChange: (String) -> Unit,
    onPick: (Place) -> Unit,
    modifier: Modifier = Modifier,
    wide: Boolean = false,
    above: @Composable () -> Unit = {},
    below: @Composable () -> Unit = {},
) {
    val motion = ClockblockTheme.motion
    val density = LocalDensity.current
    val focusManager = LocalFocusManager.current
    var focused by remember { mutableStateOf(false) }
    // The keyboard's target height only changes as it starts and ends a move; its current height is read in layout.
    val keyboardTarget = keyboardAboveBars(WindowInsets.imeAnimationTarget)
    val keyboardNow = rememberKeyboardAboveBars()
    val measured = remember { MeasuredRoom() }
    val needed = measured.aboveResults + measured.below +
        with(density) { (placeResultRowHeight() * MinVisibleResults + SegmentedGap * (MinVisibleResults - 1)).roundToPx() }
    val roomPx = with(density) { room.roundToPx() }
    // Once full height, it stays so until the search ends: the room it measures changes as the host folds away.
    val fullHeight = focused && keyboardTarget > 0 && (state.fullHeight || roomPx - keyboardTarget < needed)
    SideEffect { state.fullHeight = fullHeight }
    DisposableEffect(state) { onDispose { state.fullHeight = false } }

    // Hiding the keyboard (its own button, or Back, which the keyboard takes first) ends the search too.
    var keyboardSeen by remember { mutableStateOf(false) }
    LaunchedEffect(focused, keyboardTarget > 0) {
        when {
            !focused -> keyboardSeen = false
            keyboardTarget > 0 -> keyboardSeen = true
            keyboardSeen -> focusManager.clearFocus()
        }
    }
    // The first Back leaves the full-height search, the next one the screen (registered after the screen's own
    // handler, so it wins).
    NavigationBackHandler(
        state = rememberNavigationEventState(currentInfo = NavigationEventInfo.None),
        isBackEnabled = fullHeight,
        onBackCompleted = { focusManager.clearFocus() },
    )

    val sideBySide = wide && fullHeight
    Layout(
        content = {
            AnimatedVisibility(
                visible = !fullHeight,
                enter = expandVertically(motion.navigationSpatial()) + fadeIn(motion.fade()),
                exit = shrinkVertically(motion.navigationSpatial()) + fadeOut(motion.fade()),
                modifier = Modifier.layoutId(SearchSlot.Above),
            ) { above() }
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                label = { Text(stringResource(R.string.home_search_label)) },
                leadingIcon = {
                    AnimatedContent(
                        targetState = fullHeight,
                        transitionSpec = { fadeIn(motion.fade()) togetherWith fadeOut(motion.fade()) },
                        label = "homeSearchLeadingIcon",
                    ) { inSearch ->
                        if (inSearch) {
                            // The arrow is auto-mirrored: it points to the start edge in RTL by itself.
                            IconButton(onClick = { focusManager.clearFocus() }, modifier = Modifier.testTag(HomeZoneTags.CloseSearch)) {
                                Icon(painterResource(R.drawable.onboarding_ic_arrow_back), contentDescription = stringResource(R.string.home_search_close))
                            }
                        } else {
                            Icon(painterResource(R.drawable.onboarding_ic_search), contentDescription = null)
                        }
                    }
                },
                trailingIcon = if (query.isNotEmpty()) {
                    {
                        IconButton(onClick = { onQueryChange("") }) {
                            Icon(painterResource(R.drawable.onboarding_ic_close), contentDescription = stringResource(R.string.home_search_clear))
                        }
                    }
                } else {
                    null
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                // The results are already live: Search only puts the keyboard away (it never picks a result for you).
                keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                shape = MaterialTheme.shapes.extraLarge,
                modifier = Modifier
                    .layoutId(SearchSlot.Field)
                    .fillMaxWidth()
                    .onFocusChanged { focused = it.isFocused }
                    .testTag(HomeZoneTags.Search),
            )
            ResultCount(query, results, resultsQuery, rowHeight = placeResultRowHeight(), modifier = Modifier.layoutId(SearchSlot.Count))
            Box(Modifier.layoutId(SearchSlot.Results).then(if (fullHeight) Modifier.verticalScroll(rememberScrollState()) else Modifier)) {
                val pick = { place: Place ->
                    onPick(place)
                    focusManager.clearFocus()
                }
                if (sideBySide) ResultGrid(results, now, pick) else ResultList(results, now, pick)
            }
            AnimatedVisibility(
                visible = !fullHeight,
                enter = expandVertically(motion.navigationSpatial()) + fadeIn(motion.fade()),
                exit = shrinkVertically(motion.navigationSpatial()) + fadeOut(motion.fade()),
                modifier = Modifier.layoutId(SearchSlot.Below),
            ) { below() }
        },
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (fullHeight) {
                    Modifier.layout { measurable, constraints ->
                        val height = (roomPx - keyboardNow()).coerceAtLeast(0)
                        val placeable = measurable.measure(constraints.copy(minHeight = height, maxHeight = height))
                        layout(placeable.width, height) { placeable.place(0, 0) }
                    }
                } else {
                    Modifier
                },
            ),
    ) { measurables, constraints ->
        // The folding slots leave the composition once folded away.
        fun slot(id: SearchSlot, constraints: Constraints): Placeable? = measurables.firstOrNull { it.layoutId == id }?.measure(constraints)
        val width = constraints.maxWidth
        val gap = FieldGap.roundToPx()
        val column = Constraints(maxWidth = width)
        if (sideBySide) {
            // The field and the count in a start column, the results beside them, from the top, as tall as the room.
            val pane = (width * FieldPaneFraction).roundToInt().coerceAtLeast(FieldPaneMinWidth.roundToPx()).coerceAtMost(width)
            val paneConstraints = Constraints(maxWidth = pane)
            val above = slot(SearchSlot.Above, paneConstraints)
            val field = slot(SearchSlot.Field, paneConstraints)
            val count = slot(SearchSlot.Count, paneConstraints)
            val resultsWidth = (width - pane - PaneGap.roundToPx()).coerceAtLeast(0)
            val height = constraints.maxHeight
            val results = slot(SearchSlot.Results, Constraints.fixed(resultsWidth, height))
            slot(SearchSlot.Below, Constraints.fixed(0, 0))
            layout(width, height) {
                val fieldTop = above.heightOrZero
                above?.placeRelative(0, 0)
                field?.placeRelative(0, fieldTop)
                count?.placeRelative(0, fieldTop + field.heightOrZero + gap)
                results?.placeRelative(width - resultsWidth, 0)
            }
        } else {
            val above = slot(SearchSlot.Above, column)
            val field = slot(SearchSlot.Field, column)
            val count = slot(SearchSlot.Count, column)
            val below = slot(SearchSlot.Below, column)
            val resultsTop = above.heightOrZero + field.heightOrZero + gap + count.heightOrZero
            val bounded = fullHeight && constraints.hasBoundedHeight
            val results = slot(
                SearchSlot.Results,
                if (bounded) Constraints.fixed(width, (constraints.maxHeight - resultsTop - below.heightOrZero).coerceAtLeast(0)) else column,
            )
            if (!fullHeight) {
                measured.aboveResults = resultsTop
                measured.below = below.heightOrZero
            }
            val height = if (bounded) constraints.maxHeight else resultsTop + results.heightOrZero + below.heightOrZero
            layout(width, height) {
                above?.placeRelative(0, 0)
                field?.placeRelative(0, above.heightOrZero)
                count?.placeRelative(0, above.heightOrZero + field.heightOrZero + gap)
                results?.placeRelative(0, resultsTop)
                below?.placeRelative(0, resultsTop + results.heightOrZero)
            }
        }
    }
}

/** A slot's height, 0 when it isn't composed. */
private val Placeable?.heightOrZero: Int get() = this?.height ?: 0

private enum class SearchSlot { Above, Field, Count, Results, Below }

/** The results in one segmented list. */
@Composable
private fun ResultList(results: List<Place>, now: Instant, onPick: (Place) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(SegmentedGap)) {
        results.forEachIndexed { index, place -> ResultRow(place, now, segmentedShape(index, results.size), onPick) }
    }
}

/**
 * The results in rows of as many columns as fit at [GridMinColumnWidth] (scaled with the font, so a result never
 * stacks its time under its name), each in its own card: the side-by-side search of a wide window.
 */
@Composable
private fun ResultGrid(results: List<Place>, now: Instant, onPick: (Place) -> Unit) {
    val shape = segmentedShape(0, 1)
    Layout(content = { results.forEach { ResultRow(it, now, shape, onPick) } }) { measurables, constraints ->
        val gap = GridGap.roundToPx()
        val minColumn = (GridMinColumnWidth * fontScale.coerceAtLeast(1f)).roundToPx()
        val columns = ((constraints.maxWidth + gap) / (minColumn + gap)).coerceAtLeast(1)
        val cell = ((constraints.maxWidth - gap * (columns - 1)) / columns).coerceAtLeast(0)
        val placeables = measurables.map { it.measure(Constraints(minWidth = cell, maxWidth = cell)) }
        val rows = placeables.chunked(columns)
        val height = rows.sumOf { row -> row.maxOf { it.height } } + gap * (rows.size - 1).coerceAtLeast(0)
        layout(constraints.maxWidth, height) {
            var y = 0
            rows.forEach { row ->
                row.forEachIndexed { column, placeable -> placeable.placeRelative(column * (cell + gap), y) }
                y += row.maxOf { it.height } + gap
            }
        }
    }
}

@Composable
private fun ResultRow(place: Place, now: Instant, shape: Shape, onPick: (Place) -> Unit) {
    // The shared airport row (same as the trip editor).
    Surface(shape = shape, color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
        PlaceResultRow(place, now, onClick = { onPick(place) }, modifier = Modifier.clip(shape).testTag(HomeZoneTags.result(place)))
    }
}

/** Between the field and the count. */
private val FieldGap = 12.dp

/** The side-by-side search's start column (field and count): this share of the width, at least [FieldPaneMinWidth]. */
private const val FieldPaneFraction = 0.22f
private val FieldPaneMinWidth = 180.dp
private val PaneGap = 24.dp
private val GridGap = 4.dp

/** A result in the side-by-side grid is at least this wide (at font scale 1): wide enough to keep its time beside its name ([PlaceResultRow] stacks it under below 260 dp of text, inside 16 dp padding each side). */
private val GridMinColumnWidth = 292.dp

/**
 * How many places match, or that none does, as a polite live region. The line is always there (empty with no query),
 * so the rows under it never move; while a newer query is in flight it keeps its last settled text, so TalkBack hears
 * each settled count once. Whenever a new answer settles it scrolls itself and the first result into view (in the
 * normal layout the results can start below the fold, under the keyboard and the host's bars).
 */
@Composable
private fun ResultCount(query: String, results: List<Place>, resultsQuery: String, rowHeight: Dp, modifier: Modifier = Modifier) {
    val settled = query.isBlank() || resultsQuery == query
    val text = when {
        query.isBlank() -> ""
        !settled -> null
        results.isEmpty() -> stringResource(R.string.home_no_results, query.trim())
        else -> pluralStringResource(R.plurals.home_results_count, results.size, results.size)
    }
    var lastText by remember { mutableStateOf("") }
    if (text != null) SideEffect { lastText = text }
    val requester = remember { BringIntoViewRequester() }
    var size by remember { mutableStateOf(IntSize.Zero) }
    val rowPx = with(LocalDensity.current) { rowHeight.toPx() }
    val answer = if (settled && query.isNotBlank()) results.firstOrNull() ?: NoResults else null
    LaunchedEffect(answer) {
        if (answer != null) requester.bringIntoView(Rect(0f, 0f, size.width.toFloat(), size.height + if (answer == NoResults) 0f else rowPx))
    }
    Text(
        text ?: lastText,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 4.dp, end = 4.dp, bottom = 8.dp)
            .onSizeChanged { size = it }
            .bringIntoViewRequester(requester)
            .semantics { liveRegion = LiveRegionMode.Polite }
            .testTag(HomeZoneTags.ResultCount),
    )
}

private object NoResults

/** The heights a [HomeSearch] measured in its normal layout, to decide when to go full height (not observed). */
private class MeasuredRoom {
    var aboveResults = 0
    var below = 0
}

/** Below this many visible result rows, the search goes full height. */
internal const val MinVisibleResults = 3

/** The keyboard's [ime] height over the navigation bar (which the screens already keep clear of), in px. */
@Composable
private fun keyboardAboveBars(ime: WindowInsets): Int {
    val density = LocalDensity.current
    return (ime.getBottom(density) - WindowInsets.systemBars.union(WindowInsets.displayCutout).getBottom(density)).coerceAtLeast(0)
}

/**
 * The keyboard's current height over the navigation bar, in px, as a function to call in layout or draw (it changes
 * every frame while the keyboard moves).
 */
@Composable
fun rememberKeyboardAboveBars(): Density.() -> Int {
    val ime = WindowInsets.ime
    val bars = WindowInsets.systemBars.union(WindowInsets.displayCutout)
    return remember(ime, bars) { { (ime.getBottom(this) - bars.getBottom(this)).coerceAtLeast(0) } }
}
