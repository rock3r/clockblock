package dev.sebastiano.clockblocker.opus.feature.onboarding

import androidx.compose.foundation.clickable
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
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
import dev.sebastiano.clockblocker.opus.core.designsystem.illustration.WindowLightArt
import dev.sebastiano.clockblocker.opus.core.designsystem.theme.ClockblockTheme
import dev.sebastiano.clockblocker.opus.core.designsystem.time.cityName
import dev.sebastiano.clockblocker.opus.core.designsystem.time.rememberTimeFormatter
import dev.sebastiano.clockblocker.opus.core.model.Place
import dev.sebastiano.clockblocker.opus.core.model.ZoneLabels
import dev.sebastiano.clockblocker.opus.feature.onboarding.profile.ChronotypeHelperDialog
import dev.sebastiano.clockblocker.opus.feature.onboarding.profile.ChronotypePicker
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

    /** `home_zone_result_LHR` etc. */
    fun result(place: Place): String = "home_zone_result_${place.code.ifBlank { place.city }}"
}

/** Test tags for the e2e suite. */
object RemindersTags {
    const val AllowNotifications = "reminders_allow_notifications"
    const val ExactAlarm = "reminders_exact_alarm"
}

@Composable
internal fun OnboardingStepBody(step: OnboardingStep, state: OnboardingUiState, actions: OnboardingActions, wide: Boolean) {
    val animated = !ClockblockTheme.reduceMotion
    when (step) {
        OnboardingStep.Welcome -> WelcomeStep(wide)
        OnboardingStep.HomeZone -> StepLayout(
            wide = wide,
            art = { GreatCircleArt(it, progress = 0.62f, animated = animated) },
            headline = stringResource(R.string.home_headline),
            body = stringResource(R.string.home_body),
        ) { HomeZoneControls(state, actions) }
        OnboardingStep.Sleep -> StepLayout(
            wide = wide,
            art = { PillowMoonArt(it, animated = animated) },
            headline = stringResource(R.string.sleep_headline),
            body = stringResource(R.string.sleep_body),
        ) { availableHeight ->
            // The dial takes the height left under the header (measured, so it holds at any font scale), down to a
            // floor where scrolling is the better trade.
            SleepDial(
                state.profile.sleep,
                actions::sleepChange,
                Modifier.fillMaxWidth().padding(top = 8.dp),
                easterEggEnabled = state.easterEggs,
                maxHeight = (availableHeight - 8.dp).coerceAtLeast(if (wide) 0.dp else CompactMinSleepControls),
            )
        }
        OnboardingStep.Chronotype -> StepLayout(
            wide = wide,
            art = { WindowLightArt(it, animated = animated) },
            headline = stringResource(R.string.chronotype_headline),
            body = stringResource(R.string.chronotype_body),
        ) {
            var showHelper by rememberSaveable { mutableStateOf(false) }
            ChronotypePicker(
                selected = state.profile.chronotype,
                onSelect = actions::chronotypeChange,
                onNotSure = { showHelper = true },
            )
            if (showHelper) {
                ChronotypeHelperDialog(
                    workSleep = state.profile.sleep,
                    onUse = { actions.chronotypeChange(it); showHelper = false },
                    onDismiss = { showHelper = false },
                )
            }
        }
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
 * Shared step layout. Compact: one scrolling column (small art, headline, body, controls); every step has the same
 * header so the headline never jumps between steps. Wide: art and words in the left pane, controls in a scrolling
 * right pane. Either way, [controls] gets the height it can use without scrolling.
 */
@Composable
private fun StepLayout(
    wide: Boolean,
    art: @Composable (Modifier) -> Unit,
    headline: String,
    body: String,
    controls: @Composable ColumnScope.(availableHeight: Dp) -> Unit,
) {
    if (wide) BoxWithConstraints(Modifier.fillMaxSize()) {
        // Phones in landscape: no art, a smaller headline and tighter padding, so the controls get the height.
        val short = maxHeight < ShortPaneHeight
        val verticalPadding = if (short) 8.dp else 24.dp
        val controlsHeight = maxHeight - verticalPadding * 2
        Row(Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
            Column(
                Modifier.weight(0.42f).fillMaxHeight().verticalScroll(rememberScrollState()).padding(end = 24.dp, top = verticalPadding, bottom = verticalPadding),
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
            Column(
                Modifier.weight(0.58f).fillMaxHeight().verticalScroll(rememberScrollState()).padding(vertical = verticalPadding),
                verticalArrangement = Arrangement.Center,
            ) {
                Column(Modifier.widthIn(max = 560.dp)) { controls(controlsHeight) }
            }
        }
    } else BoxWithConstraints(Modifier.fillMaxSize()) {
        val viewport = maxHeight - CompactVerticalPadding * 2
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = CompactVerticalPadding),
        ) {
            HeaderThenControls(
                viewportHeight = viewport,
                header = {
                    art(Modifier.size(88.dp))
                    Spacer(Modifier.size(8.dp))
                    StepHeadline(headline)
                    Spacer(Modifier.size(8.dp))
                    StepBody(body)
                    Spacer(Modifier.size(24.dp))
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

@Composable
private fun HomeZoneControls(state: OnboardingUiState, actions: OnboardingActions) {
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
    Spacer(Modifier.size(16.dp))
    OutlinedTextField(
        value = state.query,
        onValueChange = actions::queryChange,
        label = { Text(stringResource(R.string.home_search_label)) },
        leadingIcon = { Icon(painterResource(R.drawable.onboarding_ic_search), contentDescription = null) },
        trailingIcon = if (state.query.isNotEmpty()) {
            {
                IconButton(onClick = { actions.queryChange("") }) {
                    Icon(painterResource(R.drawable.onboarding_ic_close), contentDescription = stringResource(R.string.home_search_clear))
                }
            }
        } else {
            null
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        shape = MaterialTheme.shapes.extraLarge,
        modifier = Modifier.fillMaxWidth().testTag(HomeZoneTags.Search),
    )
    Spacer(Modifier.size(12.dp))
    // With the keyboard up, results start below the fold (under the IME and the Back/Next bar): scroll the first
    // result (or the "no results" line) into view whenever the search answer changes.
    val feedback = remember { BringIntoViewRequester() }
    LaunchedEffect(state.query.isNotBlank(), state.results.firstOrNull()) {
        if (state.query.isNotBlank()) feedback.bringIntoView()
    }
    if (state.query.isNotBlank() && state.results.isEmpty()) {
        Text(
            stringResource(R.string.home_no_results, state.query.trim()),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp).bringIntoViewRequester(feedback),
        )
    }
    Column(verticalArrangement = Arrangement.spacedBy(SegmentedGap)) {
        state.results.forEachIndexed { index, place ->
            PlaceRow(
                place,
                state.now,
                segmentedShape(index, state.results.size),
                onClick = { actions.selectPlace(place) },
                modifier = if (index == 0) Modifier.bringIntoViewRequester(feedback) else Modifier,
            )
        }
    }
}

@Composable
private fun PlaceRow(
    place: Place,
    now: Instant,
    shape: androidx.compose.ui.graphics.Shape,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val formatter = rememberTimeFormatter()
    val colors = MaterialTheme.colorScheme
    val local = formatter.format(LocalTime.ofInstant(now, place.zone))
    val description = stringResource(R.string.home_result_description, place.city, place.name, place.zoneId)
    Surface(shape = shape, color = colors.surfaceContainer, modifier = modifier.fillMaxWidth()) {
        Row(
            Modifier
                .clip(shape)
                .clickable(onClick = onClick)
                .semantics(mergeDescendants = true) { contentDescription = description }
                .heightIn(min = 64.dp)
                .padding(horizontal = 16.dp, vertical = 10.dp)
                .testTag(HomeZoneTags.result(place)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(shape = MaterialTheme.shapes.medium, color = colors.primaryContainer, modifier = Modifier.widthIn(min = 56.dp)) {
                Text(
                    place.displayCode,
                    style = ClockblockTheme.textStyles.timeLabel,
                    color = colors.onPrimaryContainer,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                )
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(place.city, style = MaterialTheme.typography.titleMedium, color = colors.onSurface, maxLines = 1)
                Text(place.name, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant, maxLines = 1)
            }
            Spacer(Modifier.width(12.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(local, style = ClockblockTheme.textStyles.timeLabel, color = colors.onSurface)
                Text(gmtLabel(place.zone, now), style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
            }
        }
    }
}

/** "GMT", "GMT+2", "GMT+5:30", "GMT−3" for the zone's offset at [at]. */
internal fun gmtLabel(zone: ZoneId, at: Instant): String = ZoneLabels.offset(zone, at)

@Composable
private fun RemindersControls(state: OnboardingUiState, actions: OnboardingActions) {
    val permissions = state.permissions
    Column(verticalArrangement = Arrangement.spacedBy(SegmentedGap)) {
        PermissionRow(
            title = stringResource(R.string.reminders_notifications),
            description = stringResource(R.string.reminders_notifications_description),
            granted = permissions.notificationsGranted,
            actionLabel = stringResource(R.string.reminders_allow),
            onAction = { actions.requestNotifications(thenFinish = false) },
            shape = segmentedShape(0, 2),
            tag = RemindersTags.AllowNotifications,
        )
        PermissionRow(
            title = stringResource(R.string.reminders_exact),
            description = stringResource(R.string.reminders_exact_description),
            granted = permissions.exactAlarmsAllowed,
            actionLabel = stringResource(R.string.reminders_open_settings),
            onAction = actions::openExactAlarmSettings,
            shape = segmentedShape(1, 2),
            tag = RemindersTags.ExactAlarm,
        )
    }
    Spacer(Modifier.size(16.dp))
    Text(
        stringResource(R.string.reminders_later_note),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

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
) {
    val colors = MaterialTheme.colorScheme
    Surface(shape = shape, color = colors.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
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
private val ShortPaneHeight: Dp = 480.dp
private val CompactVerticalPadding: Dp = 16.dp

/** On phones in portrait the dial may scroll rather than shrink below this (dial plus time pills). */
private val CompactMinSleepControls: Dp = 320.dp
