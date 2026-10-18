package dev.sebastiano.clockblocker.opus.feature.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.sebastiano.clockblocker.opus.core.designsystem.illustration.GreatCircleArt
import dev.sebastiano.clockblocker.opus.core.designsystem.illustration.LittleAndOftenArt
import dev.sebastiano.clockblocker.opus.core.designsystem.illustration.PillowMoonArt
import dev.sebastiano.clockblocker.opus.core.designsystem.illustration.SuitcaseOClockArt
import dev.sebastiano.clockblocker.opus.core.designsystem.illustration.TwoClocksArt
import dev.sebastiano.clockblocker.opus.core.designsystem.theme.ClockblockTheme
import dev.sebastiano.clockblocker.opus.core.designsystem.time.cityName
import dev.sebastiano.clockblocker.opus.core.designsystem.time.rememberTimeFormatter
import dev.sebastiano.clockblocker.opus.core.model.Place
import dev.sebastiano.clockblocker.opus.core.model.ZoneLabels
import dev.sebastiano.clockblocker.opus.feature.onboarding.profile.ChronotypeReveal
import dev.sebastiano.clockblocker.opus.feature.onboarding.profile.EffortSelector
import dev.sebastiano.clockblocker.opus.feature.onboarding.profile.SegmentedGap
import dev.sebastiano.clockblocker.opus.feature.onboarding.profile.SleepDial
import dev.sebastiano.clockblocker.opus.feature.onboarding.profile.ToolsEditor
import dev.sebastiano.clockblocker.opus.feature.onboarding.profile.segmentedShape
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

/** Test tags for the e2e suite. */
object HomeZoneTags {
    const val Search = "home_zone_search"
    const val UseDevice = "home_zone_use_device"
    const val Current = "home_zone_current"
    const val CloseSearch = "home_zone_close_search"
    const val ResultCount = "home_zone_result_count"

    /** `home_zone_result_LHR` etc. */
    fun result(place: Place): String = "home_zone_result_${place.code.ifBlank { place.city }}"
}

/** Test tags for the e2e suite. */
object RemindersTags {
    const val AllowNotifications = "reminders_allow_notifications"
    const val NotificationsAllowed = "reminders_notifications_allowed"
    const val NotificationSettings = "reminders_notification_settings"
    const val ExactAlarm = "reminders_exact_alarm"
    const val ExactAlarmRow = "reminders_exact_alarm_row"
}

@Composable
internal fun OnboardingStepBody(
    step: OnboardingStep,
    state: OnboardingUiState,
    actions: OnboardingActions,
    wide: Boolean,
    homeSearch: HomeSearchState = rememberHomeSearchState(),
    spectacleFrom: Rect? = null,
    onSpectacleFinished: () -> Unit = {},
    onSpectacleName: () -> Unit = {},
) {
    val animated = !ClockblockTheme.reduceMotion
    when (step) {
        OnboardingStep.Welcome -> WelcomeStep(wide)
        OnboardingStep.HomeZone -> StepLayout(
            wide = wide,
            art = { GreatCircleArt(it, progress = 0.62f, animated = animated) },
            headline = stringResource(R.string.home_headline),
            body = stringResource(R.string.home_body),
            collapsed = homeSearch.fullHeight,
        ) { availableHeight -> HomeZoneControls(state, actions, homeSearch, availableHeight, wide) }
        OnboardingStep.Sleep -> StepLayout(
            wide = wide,
            art = { PillowMoonArt(it, animated = animated) },
            headline = stringResource(R.string.sleep_headline),
            body = stringResource(R.string.sleep_body),
        ) { availableHeight ->
            // The dial takes the height left under the header (measured, so it holds at any font scale), down to a
            // floor where scrolling is the better trade.
            SleepDial(
                work = state.profile.sleep,
                daysOff = state.profile.daysOffSleep,
                onWorkChange = actions::sleepChange,
                onDaysOffChange = actions::daysOffSleepChange,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                easterEggEnabled = state.easterEggs,
                maxHeight = (availableHeight - 8.dp).coerceAtLeast(if (wide) 0.dp else CompactMinSleepControls),
            )
        }
        OnboardingStep.Chronotype -> ChronotypeReveal(
            chronotype = state.profile.chronotype,
            picked = state.showsPick,
            suggested = state.inferred.chronotype,
            midSleep = state.inferred.midSleep,
            onPick = actions::chronotypeChange,
            wide = wide,
            daysOffLinked = state.profile.daysOffSleep == null,
            spectacleFrom = spectacleFrom,
            onSpectacleFinished = onSpectacleFinished,
            onSpectacleName = onSpectacleName,
        )
        OnboardingStep.Tools -> StepLayout(
            wide = wide,
            art = { LittleAndOftenArt(it, animated = animated) },
            headline = stringResource(R.string.tools_headline),
            body = stringResource(R.string.tools_body),
        ) {
            ToolsEditor(
                profile = state.profile,
                melatoninAcknowledged = state.melatoninAcknowledged,
                onProfileChange = actions::profileChange,
                onMelatoninChange = actions::melatoninChange,
                onAcknowledgeMelatonin = actions::acknowledgeMelatonin,
            )
            Spacer(Modifier.size(28.dp))
            Text(
                stringResource(R.string.effort_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.semantics { heading() },
            )
            Spacer(Modifier.size(12.dp))
            EffortSelector(state.profile.intensity, actions::intensityChange)
        }
        OnboardingStep.Reminders -> StepLayout(
            wide = wide,
            art = { SuitcaseOClockArt(it, animated = animated, contentDescription = null) },
            headline = stringResource(R.string.reminders_headline),
            body = stringResource(R.string.reminders_body),
        ) { RemindersControls(state, actions) }
    }
}

/**
 * Shared step layout. Compact: one scrolling column (small art beside the headline, body, controls); every step has
 * the same header so the headline never jumps between steps. Wide: art and words in the left pane, controls in a scrolling
 * right pane. Either way, [controls] gets the height it can use without scrolling and without the keyboard (the
 * keyboard only shrinks the scrolling viewport, in layout, so it never recomposes the step).
 *
 * [collapsed] folds the header away, so the controls get the whole step: the home step's full-height search. Compact,
 * the header shrinks vertically; wide, the art and words pane shrinks horizontally, so the controls take the whole
 * width (where the home search lays its field beside the results). Both on `navigationSpatial()` (Standard, no
 * bounce; it snaps under reduced motion) with a `fade()`.
 */
@Composable
private fun StepLayout(
    wide: Boolean,
    art: @Composable (Modifier) -> Unit,
    headline: String,
    body: String,
    collapsed: Boolean = false,
    controls: @Composable ColumnScope.(availableHeight: Dp) -> Unit,
) {
    val motion = ClockblockTheme.motion
    if (wide) BoxWithConstraints(Modifier.fillMaxSize()) {
        // Phones in landscape: no art, a smaller headline and tighter padding, so the controls get the height.
        val short = maxHeight < ShortPaneHeight
        val verticalPadding = if (short) 8.dp else 24.dp
        // Collapsed, the results run down to the keyboard: no bottom padding.
        val bottomPadding = if (collapsed) 0.dp else verticalPadding
        val controlsHeight = maxHeight - verticalPadding - bottomPadding
        val paneWidth = (maxWidth - WidePadding * 2) * WordsPaneFraction
        Row(Modifier.fillMaxSize().padding(horizontal = WidePadding)) {
            AnimatedVisibility(
                visible = !collapsed,
                enter = expandHorizontally(motion.navigationSpatial()) + fadeIn(motion.fade()),
                exit = shrinkHorizontally(motion.navigationSpatial()) + fadeOut(motion.fade()),
            ) {
                Column(
                    Modifier.width(paneWidth).fillMaxHeight().verticalScroll(rememberScrollState()).padding(end = WidePadding, top = verticalPadding, bottom = verticalPadding),
                    verticalArrangement = Arrangement.Center,
                ) {
                    if (!short) {
                        art(Modifier.size(200.dp))
                        Spacer(Modifier.size(24.dp))
                    }
                    StepHeadline(headline, if (short) ClockblockTheme.textStyles.editorialTitle else ClockblockTheme.textStyles.editorialHeadline)
                    Spacer(Modifier.size(12.dp))
                    StepBody(body)
                }
            }
            Column(
                Modifier.weight(1f).fillMaxHeight().windowInsetsPadding(WindowInsets.ime).verticalScroll(rememberScrollState()).padding(top = verticalPadding, bottom = bottomPadding),
                verticalArrangement = Arrangement.Center,
            ) {
                // Collapsed, the controls take the whole width (the full-height search lays out side by side).
                Column(if (collapsed) Modifier.fillMaxWidth() else Modifier.widthIn(max = 560.dp)) { controls(controlsHeight) }
            }
        }
    } else BoxWithConstraints(Modifier.fillMaxSize()) {
        val viewport = maxHeight - CompactVerticalPadding * 2
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.ime)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = CompactVerticalPadding),
        ) {
            HeaderThenControls(
                viewportHeight = viewport,
                header = {
                    AnimatedVisibility(
                        visible = !collapsed,
                        enter = expandVertically(motion.navigationSpatial()) + fadeIn(motion.fade()),
                        exit = shrinkVertically(motion.navigationSpatial()) + fadeOut(motion.fade()),
                    ) {
                        Column {
                            // The art sits beside the headline, not above it: the controls (the sleep dial most of all) need
                            // the height.
                            Row(verticalAlignment = Alignment.Top) {
                                art(Modifier.size(CompactSideArtSize))
                                Spacer(Modifier.size(12.dp))
                                StepHeadline(headline)
                            }
                            Spacer(Modifier.size(8.dp))
                            StepBody(body)
                            Spacer(Modifier.size(24.dp))
                        }
                    }
                },
                controls = controls,
            )
        }
    }
}

/**
 * [header] then [controls], stacked. The header is measured first, so [controls] learns how much of
 * [viewportHeight] is left under it (at the current font scale and text wrap) before it composes.
 */
@Composable
private fun HeaderThenControls(
    viewportHeight: Dp,
    header: @Composable ColumnScope.() -> Unit,
    controls: @Composable ColumnScope.(availableHeight: Dp) -> Unit,
) {
    SubcomposeLayout { constraints ->
        val loose = constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity)
        val top = subcompose(HeaderSlot) { Column(Modifier.fillMaxWidth(), content = header) }.map { it.measure(loose) }
        val topHeight = top.sumOf { it.height }
        val available = (viewportHeight - topHeight.toDp()).coerceAtLeast(0.dp)
        val bottom = subcompose(ControlsSlot) { Column(Modifier.fillMaxWidth()) { controls(available) } }.map { it.measure(loose) }
        val width = (top + bottom).maxOfOrNull { it.width } ?: constraints.minWidth
        layout(width, topHeight + bottom.sumOf { it.height }) {
            var y = 0
            (top + bottom).forEach { it.place(0, y); y += it.height }
        }
    }
}

private const val HeaderSlot = "header"
private const val ControlsSlot = "controls"

@Composable
private fun StepHeadline(text: String, style: TextStyle = ClockblockTheme.textStyles.editorialHeadline) {
    Text(
        text,
        style = style,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.semantics { heading() },
    )
}

@Composable
private fun StepBody(text: String) {
    Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WelcomeStep(wide: Boolean) {
    val reduce = ClockblockTheme.reduceMotion
    // The two clocks start apart and slide into line: the whole promise in one gesture (rare, first-run delight).
    var aligned by rememberSaveable { mutableStateOf(reduce) }
    LaunchedEffect(reduce) {
        if (!reduce) delay(WelcomeArtDelayMillis)
        aligned = true
    }
    val art: @Composable (Dp) -> Unit = { size ->
        TwoClocksArt(
            Modifier.size(size),
            progress = if (aligned) 1f else 0f,
            animated = !reduce,
            contentDescription = stringResource(R.string.welcome_art_description),
        )
    }
    val words: @Composable ColumnScope.(TextStyle, TextAlign) -> Unit = { headlineStyle, align ->
        Text(
            stringResource(R.string.welcome_kicker),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            textAlign = align,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.size(12.dp))
        Text(
            stringResource(R.string.welcome_headline),
            style = headlineStyle,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = align,
            modifier = Modifier.fillMaxWidth().semantics { heading() },
        )
        Spacer(Modifier.size(16.dp))
        Text(
            stringResource(R.string.welcome_body),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = align,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.size(20.dp))
        FlowRow(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, if (align == TextAlign.Center) Alignment.CenterHorizontally else Alignment.Start),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Badge(stringResource(R.string.welcome_badge_offline))
            Badge(stringResource(R.string.welcome_badge_free))
            Badge(stringResource(R.string.welcome_badge_private))
        }
    }
    if (wide) BoxWithConstraints(Modifier.fillMaxSize()) {
        val short = maxHeight < ShortPaneHeight
        val artSize = minOf(300.dp, maxHeight * 0.7f)
        Row(Modifier.fillMaxSize().padding(horizontal = 48.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(0.45f), contentAlignment = Alignment.Center) { art(artSize) }
            Spacer(Modifier.width(48.dp))
            Column(Modifier.weight(0.55f).verticalScroll(rememberScrollState()).padding(vertical = 24.dp)) {
                words(if (short) ClockblockTheme.textStyles.editorialHeadline else ClockblockTheme.textStyles.editorialDisplay, TextAlign.Start)
            }
        }
    } else {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            art(220.dp)
            Spacer(Modifier.size(16.dp))
            words(ClockblockTheme.textStyles.editorialDisplay.copy(fontSize = 40.sp, lineHeight = 46.sp), TextAlign.Center)
        }
    }
}

@Composable
private fun Badge(text: String) {
    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer) {
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
        )
    }
}

/**
 * The home step's controls: the current zone card over the shared [HomeSearch]. [search] tells the host when the
 * search goes full height (the card folds away, the field rises to the top and the results fill [availableHeight]
 * down to the keyboard), so it can fold the step's header and the bars too.
 */
@Composable
private fun HomeZoneControls(state: OnboardingUiState, actions: OnboardingActions, search: HomeSearchState, availableHeight: Dp, wide: Boolean) {
    HomeSearch(
        state = search,
        query = state.query,
        results = state.results,
        resultsQuery = state.searchMatchingQuery,
        now = state.now,
        room = availableHeight,
        onQueryChange = actions::queryChange,
        onPick = actions::selectPlace,
        wide = wide,
        above = {
            Column {
                CurrentZoneCard(state, actions)
                Spacer(Modifier.size(16.dp))
            }
        },
    )
}

@Composable
private fun CurrentZoneCard(state: OnboardingUiState, actions: OnboardingActions) {
    val formatter = rememberTimeFormatter()
    val zone = ZoneId.of(state.profile.homeZoneId)
    val colors = MaterialTheme.colorScheme
    Surface(shape = MaterialTheme.shapes.extraLarge, color = colors.surfaceContainerHigh, modifier = Modifier.fillMaxWidth().testTag(HomeZoneTags.Current)) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(painterResource(R.drawable.onboarding_ic_place), contentDescription = null, tint = colors.primary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.home_current_label), style = MaterialTheme.typography.labelLarge, color = colors.onSurfaceVariant)
            }
            Spacer(Modifier.size(8.dp))
            Text(state.homePlace?.city ?: zone.cityName(), style = MaterialTheme.typography.headlineMedium, color = colors.onSurface)
            val longName = ZoneLabels.longName(zone, state.now, LocalConfiguration.current.locales[0])
            Text(
                stringResource(R.string.home_local_time, formatter.format(LocalTime.ofInstant(state.now, zone)), gmtLabel(zone, state.now)) +
                    longName?.let { " · $it" }.orEmpty(),
                style = ClockblockTheme.textStyles.timeLabel,
                color = colors.onSurfaceVariant,
            )
            Spacer(Modifier.size(12.dp))
            if (state.usingDeviceZone) {
                Surface(shape = CircleShape, color = colors.secondaryContainer) {
                    Row(Modifier.padding(start = 10.dp, end = 14.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(painterResource(R.drawable.onboarding_ic_check), contentDescription = null, tint = colors.onSecondaryContainer, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.home_device_chip), style = MaterialTheme.typography.labelLarge, color = colors.onSecondaryContainer)
                    }
                }
            } else {
                TextButton(onClick = actions::useDeviceZone, modifier = Modifier.heightIn(min = 48.dp).testTag(HomeZoneTags.UseDevice)) {
                    Text(stringResource(R.string.home_use_device))
                }
            }
        }
    }
}

/** "GMT", "GMT+2", "GMT+5:30", "GMT−3" for the zone's offset at [at]. */
internal fun gmtLabel(zone: ZoneId, at: Instant): String = ZoneLabels.offset(zone, at)

/**
 * The Reminders step's card: a sample of the real Now notification, and the permission request right under it (the
 * step has no Allow button of its own). Once notifications are allowed, Exact timing follows as an optional row, only
 * where it can be granted ([OnboardingUiState.showExactTiming]).
 */
@Composable
private fun RemindersControls(state: OnboardingUiState, actions: OnboardingActions) {
    val colors = MaterialTheme.colorScheme
    val motion = ClockblockTheme.motion
    val permissions = state.permissions
    Column(verticalArrangement = Arrangement.spacedBy(SegmentedGap)) {
        Surface(shape = segmentedShape(0, if (state.showExactTiming) 2 else 1), color = colors.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp)) {
                NotificationPreview(state.notificationPreview, now = state.now, onContentWidth = actions::loadNotificationPreview)
                Text(
                    stringResource(R.string.reminders_preview_caption),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.padding(start = 8.dp, top = 8.dp, end = 8.dp),
                )
                Spacer(Modifier.size(12.dp))
                // One slot for the three states, so the card never jumps: ask, allowed, or off after a refusal.
                AnimatedContent(
                    targetState = when {
                        permissions.notificationsGranted -> NotificationAsk.Allowed
                        state.notificationsAsked -> NotificationAsk.Off
                        else -> NotificationAsk.Ask
                    },
                    transitionSpec = { fadeIn(motion.fade()) togetherWith fadeOut(motion.fade()) },
                    label = "notificationAsk",
                ) { ask ->
                    when (ask) {
                        NotificationAsk.Ask -> Button(
                            onClick = actions::requestNotifications,
                            enabled = !state.isSaving,
                            contentPadding = ButtonDefaults.contentPaddingFor(ButtonDefaults.MediumContainerHeight),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = ButtonDefaults.MediumContainerHeight)
                                .testTag(RemindersTags.AllowNotifications),
                        ) { Text(stringResource(R.string.reminders_allow_notifications), style = MaterialTheme.typography.titleMedium) }
                        NotificationAsk.Allowed -> Row(
                            Modifier.fillMaxWidth().heightIn(min = ButtonDefaults.MediumContainerHeight).testTag(RemindersTags.NotificationsAllowed),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                        ) {
                            Icon(painterResource(R.drawable.onboarding_ic_check), contentDescription = null, tint = colors.primary, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.reminders_notifications_allowed), style = MaterialTheme.typography.titleMedium, color = colors.primary)
                        }
                        NotificationAsk.Off -> Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
                            Text(stringResource(R.string.reminders_notifications_off), style = MaterialTheme.typography.bodyMedium, color = colors.onSurface)
                            Spacer(Modifier.size(8.dp))
                            FilledTonalButton(
                                onClick = actions::openNotificationSettings,
                                modifier = Modifier.heightIn(min = 48.dp).testTag(RemindersTags.NotificationSettings),
                            ) { Text(stringResource(R.string.reminders_open_settings)) }
                        }
                    }
                }
            }
        }
        AnimatedVisibility(
            visible = state.showExactTiming,
            // One tier: the row grows in place, so its fade rides the same container spring as its height.
            enter = expandVertically(motion.containerSpatial()) + fadeIn(motion.containerSpatial()),
            exit = shrinkVertically(motion.containerSpatial()) + fadeOut(motion.containerSpatial()),
        ) {
            // The row appears under the card, often below the fold: bring it into view once it has grown in.
            val exactRow = remember { BringIntoViewRequester() }
            val grown = transition.currentState == EnterExitState.Visible && !transition.isRunning
            LaunchedEffect(grown) { if (grown) exactRow.bringIntoView() }
            PermissionRow(
                title = stringResource(R.string.reminders_exact),
                description = stringResource(R.string.reminders_exact_description),
                granted = permissions.exactAlarmsAllowed,
                actionLabel = stringResource(R.string.reminders_open_settings),
                onAction = actions::openExactAlarmSettings,
                shape = segmentedShape(1, 2),
                tag = RemindersTags.ExactAlarm,
                modifier = Modifier.bringIntoViewRequester(exactRow).testTag(RemindersTags.ExactAlarmRow),
            )
        }
    }
    Spacer(Modifier.size(16.dp))
    Text(
        stringResource(R.string.reminders_later_note),
        style = MaterialTheme.typography.bodyMedium,
        color = colors.onSurfaceVariant,
    )
}

private enum class NotificationAsk { Ask, Allowed, Off }

/**
 * Mirrors the Settings permission row: "Allowed" with a check beside the text when granted, otherwise a tonal
 * button under the text, so long descriptions and large fonts never squeeze the words into a narrow column.
 */
@Composable
private fun PermissionRow(
    title: String,
    description: String,
    granted: Boolean,
    actionLabel: String,
    onAction: () -> Unit,
    shape: androidx.compose.ui.graphics.Shape,
    tag: String,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Surface(shape = shape, color = colors.surfaceContainer, modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, color = colors.onSurface)
                    Text(description, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                }
                if (granted) {
                    Spacer(Modifier.width(16.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.heightIn(min = 48.dp)) {
                        Icon(painterResource(R.drawable.onboarding_ic_check), contentDescription = null, tint = colors.primary, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.reminders_granted), style = MaterialTheme.typography.labelLarge, color = colors.primary)
                    }
                }
            }
            if (!granted) {
                Spacer(Modifier.size(8.dp))
                FilledTonalButton(onClick = onAction, modifier = Modifier.heightIn(min = 48.dp).testTag(tag)) { Text(actionLabel) }
            }
        }
    }
}

private const val WelcomeArtDelayMillis = 450L
private val WidePadding: Dp = 24.dp

/** The art and words pane's share of a wide step (the controls take the rest). */
private const val WordsPaneFraction = 0.42f
private val ShortPaneHeight: Dp = 480.dp
private val CompactVerticalPadding: Dp = 16.dp

/** The art beside the headline on compact steps. */
private val CompactSideArtSize: Dp = 64.dp

/** On phones in portrait the dial may scroll rather than shrink below this (dial plus time pills). */
private val CompactMinSleepControls: Dp = 320.dp
