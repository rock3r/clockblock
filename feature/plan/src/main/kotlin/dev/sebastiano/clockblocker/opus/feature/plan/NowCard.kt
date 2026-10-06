package dev.sebastiano.clockblocker.opus.feature.plan

import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.ui.platform.LocalView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SplitButtonDefaults
import androidx.compose.material3.SplitButtonLayout
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.sebastiano.clockblocker.opus.core.designsystem.advice.AdviceGlyph
import dev.sebastiano.clockblocker.opus.core.designsystem.advice.label
import dev.sebastiano.clockblocker.opus.core.designsystem.advice.labelRes
import dev.sebastiano.clockblocker.opus.core.designsystem.advice.shortInstruction
import dev.sebastiano.clockblocker.opus.core.designsystem.component.DualTimeText
import dev.sebastiano.clockblocker.opus.core.designsystem.illustration.AdviceArt
import dev.sebastiano.clockblocker.opus.core.designsystem.theme.NowCardShape
import dev.sebastiano.clockblocker.opus.core.designsystem.theme.OpusTheme
import dev.sebastiano.clockblocker.opus.core.designsystem.time.cityName
import dev.sebastiano.clockblocker.opus.core.designsystem.time.rememberTimeFormatter
import dev.sebastiano.clockblocker.opus.core.model.Advice
import dev.sebastiano.clockblocker.opus.core.model.AdviceOutcome
import dev.sebastiano.clockblocker.opus.core.model.AdviceType
import java.time.Duration

/**
 * The most emphasised surface on the screen (M3 Expressive: the current action wins). Shows the active advice in
 * its own colour with the glyph come alive, a precise "until 18:00 · 10:00 San Francisco" line, a countdown and
 * the outcome [SplitButtonLayout]: **Done** | Skipped · Can't do this · Snooze 15 min.
 *
 * While the dial previews another instant ([previewing]) the card shows that moment instead and the outcome
 * buttons go invisible and inert (their slot stays, so the card height never jolts): you can't log the future.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun NowCard(
    moment: PlanMoment,
    previewing: Boolean,
    outcome: AdviceOutcome?,
    flightRoute: String?,
    onOutcome: (AdviceOutcome) -> Unit,
    onSnooze: () -> Unit,
    onWhy: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val advice = moment.active ?: return
    val role = OpusTheme.adviceColors[advice.type]
    val formatter = rememberTimeFormatter()
    val resources = LocalContext.current.resources
    val heading = if (previewing) {
        stringResource(R.string.plan_previewing, formatter.formatFull(moment.instant.atZone(moment.zone).toLocalTime()))
    } else {
        stringResource(R.string.plan_now)
    }
    Surface(
        modifier = modifier.fillMaxWidth().testTag(PlanTags.NowCard),
        shape = NowCardShape,
        color = role.container,
        contentColor = role.onContainer,
    ) {
        Column(Modifier.padding(start = 20.dp, end = 12.dp, top = 12.dp, bottom = 20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AdviceGlyph(advice.type, active = true, size = 28.dp)
                Spacer(Modifier.width(10.dp))
                val headingText: @Composable () -> Unit = {
                    Text(
                        heading.uppercase(),
                        style = MaterialTheme.typography.labelLargeEmphasized,
                        modifier = Modifier.semantics { this.heading() },
                    )
                }
                Box(Modifier.weight(1f)) {
                    if (!advice.type.isMoment && !moment.remaining.isZero) {
                        val faint = role.onContainer.copy(alpha = 0.8f)
                        InlineOrStacked(
                            first = headingText,
                            second = {
                                Text(
                                    stringResource(R.string.plan_left, formatDuration(moment.remaining)),
                                    style = OpusTheme.textStyles.timeLabel,
                                    color = faint,
                                )
                            },
                            separator = {
                                Text(
                                    Separator,
                                    style = OpusTheme.textStyles.timeLabel,
                                    color = faint,
                                    modifier = Modifier.testTag(InlineSeparatorTag).clearAndSetSemantics {},
                                )
                            },
                        )
                    } else {
                        headingText()
                    }
                }
                TextButton(
                    onClick = onWhy,
                    colors = ButtonDefaults.textButtonColors(contentColor = role.onContainer),
                    modifier = Modifier.testTag(PlanTags.NowWhy),
                ) { Text(stringResource(R.string.plan_open_why), maxLines = 1) }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).padding(end = 8.dp)) {
                    Text(
                        advice.type.label(),
                        style = MaterialTheme.typography.headlineMediumEmphasized,
                    )
                    val detail = when {
                        advice.type == AdviceType.Flight -> flightDetails(flightRoute, advice.detail).joinToString(" · ")
                        advice.detail != null -> advice.detail
                        else -> null
                    }
                    if (!detail.isNullOrBlank()) {
                        Text(detail, style = MaterialTheme.typography.titleSmall, color = role.onContainer.copy(alpha = 0.85f))
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(advice.type.shortInstruction(), style = MaterialTheme.typography.bodyMedium)
                }
                AdviceArt(
                    advice.type,
                    // A 100+/day surface: the illustration rests on its still frame (MOTION.md frequency map).
                    animated = false,
                    modifier = Modifier.padding(top = 4.dp).size(104.dp),
                )
            }
            if (moment.concurrent.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.plan_also_now, moment.concurrent.map { it.type.labelString(resources) }.joinToString(", ")),
                    style = MaterialTheme.typography.bodySmall,
                    color = role.onContainer.copy(alpha = 0.8f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(14.dp))
            // "until 21:00 · 10:00 San Francisco" and Done | ▾ share one row (no dead space under the illustration,
            // and Done stays above the fold on compact phones); "until 21:00" stays one unit, and when both can't
            // fit (large font, 12-hour clock) the button moves under the time rather than squeezing it.
            TimeAndActions(
                time = {
                    DualTimeText(
                        instant = if (advice.type.isMoment) advice.start else advice.end,
                        zone = moment.zone,
                        secondaryZone = moment.secondaryZone,
                        style = OpusTheme.textStyles.timeHeadline,
                        secondaryColor = role.onContainer.copy(alpha = 0.8f),
                        inline = true,
                        prefix = stringResource(if (advice.type.isMoment) R.string.plan_at_label else R.string.plan_until_label),
                    )
                },
                actions = if (advice.type == AdviceType.Flight) {
                    null
                } else {
                    {
                        // While previewing, the slot stays (invisible, inert, hidden from TalkBack) so the card keeps
                        // its height under a scrubbing finger: you can't log the future, but nothing should jump.
                        Box(
                            Modifier
                                .graphicsLayer { alpha = if (previewing) 0f else 1f }
                                .then(if (previewing) Modifier.clearAndSetSemantics {} else Modifier),
                        ) {
                            OutcomeSplitButton(
                                outcome = outcome,
                                contentColor = role.container,
                                containerColor = role.onContainer,
                                onOutcome = onOutcome,
                                onSnooze = onSnooze,
                                enabled = !previewing,
                            )
                        }
                    }
                },
                modifier = Modifier.padding(end = 8.dp),
            )
        }
    }
}

/**
 * [time] on the start side and [actions] on the end side of one row, bottom-aligned, when [time]'s widest unbreakable
 * part ("until 21:00") fits next to [actions]; otherwise [actions] goes below [time], start-aligned.
 */
@Composable
internal fun TimeAndActions(time: @Composable () -> Unit, actions: (@Composable () -> Unit)?, modifier: Modifier = Modifier) {
    Layout(contents = listOf(time, actions ?: {}), modifier = modifier) { (times, actionList), constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val t = times.first()
        val a = actionList.firstOrNull()?.measure(loose)
        if (a == null) {
            val pt = t.measure(loose)
            return@Layout layout(constraints.maxWidth, pt.height) { pt.placeRelative(0, 0) }
        }
        val gap = TimeActionsGap.roundToPx()
        val room = constraints.maxWidth - a.width - gap
        val sideBySide = room > 0 && t.minIntrinsicWidth(Constraints.Infinity) <= room
        if (sideBySide) {
            val pt = t.measure(loose.copy(maxWidth = room))
            val height = maxOf(pt.height, a.height)
            layout(constraints.maxWidth, height) {
                pt.placeRelative(0, height - pt.height)
                a.placeRelative(constraints.maxWidth - a.width, height - a.height)
            }
        } else {
            val pt = t.measure(loose)
            val stackGap = TimeActionsStackGap.roundToPx()
            layout(constraints.maxWidth, pt.height + stackGap + a.height) {
                pt.placeRelative(0, 0)
                a.placeRelative(0, pt.height + stackGap)
            }
        }
    }
}

private val TimeActionsGap = 12.dp
private val TimeActionsStackGap = 16.dp

/** Done | ▾ (Skipped · Can't do this · Snooze 15 min). */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun OutcomeSplitButton(
    outcome: AdviceOutcome?,
    containerColor: Color,
    contentColor: Color,
    onOutcome: (AdviceOutcome) -> Unit,
    onSnooze: () -> Unit,
    enabled: Boolean = true,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val colors = ButtonDefaults.buttonColors(containerColor = containerColor, contentColor = contentColor)
    val doneLabel = stringResource(R.string.plan_done)
    val moreLabel = stringResource(R.string.plan_more_outcomes)
    val motion = OpusTheme.motion
    val view = LocalView.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        SplitButtonLayout(
            leadingButton = {
                SplitButtonDefaults.LeadingButton(
                    onClick = {
                        // A few times a day, so a small earned moment: the click, and the check grows in.
                        if (outcome != AdviceOutcome.Done && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                        }
                        onOutcome(AdviceOutcome.Done)
                    },
                    enabled = enabled,
                    colors = colors,
                    modifier = Modifier.testTag(PlanTags.Done),
                ) {
                    // Spatial tier only (one tier per block): the button widens rather than jumping.
                    AnimatedVisibility(
                        visible = outcome == AdviceOutcome.Done,
                        enter = expandHorizontally(motion.containerSpatial()) + scaleIn(motion.glyphMorph()),
                        exit = shrinkHorizontally(motion.dataSpatial()) + scaleOut(motion.dataSpatial()),
                    ) {
                        Row {
                            Icon(PlanIcons.Check, contentDescription = null, modifier = Modifier.size(SplitButtonDefaults.LeadingIconSize))
                            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                        }
                    }
                    Text(doneLabel)
                }
            },
            trailingButton = {
                Box {
                    // The chevron flips with the menu: a state indicator, so no bounce (dataSpatial), and the
                    // animated value is only read in the layer (F-001).
                    val rotation = animateFloatAsState(if (menuOpen) 180f else 0f, OpusTheme.motion.dataSpatial(), label = "chevron")
                    SplitButtonDefaults.TrailingButton(
                        checked = menuOpen,
                        onCheckedChange = { menuOpen = it },
                        enabled = enabled,
                        colors = colors,
                        modifier = Modifier.testTag(PlanTags.MoreOutcomes).semantics { contentDescription = moreLabel },
                    ) {
                        Icon(
                            PlanIcons.ArrowDropDown,
                            contentDescription = null,
                            modifier = Modifier.size(SplitButtonDefaults.TrailingIconSize).graphicsLayer { rotationZ = rotation.value },
                        )
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.plan_skipped)) },
                            onClick = { menuOpen = false; onOutcome(AdviceOutcome.Skipped) },
                            modifier = Modifier.testTag(PlanTags.Skipped),
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.plan_cant_do)) },
                            onClick = { menuOpen = false; onOutcome(AdviceOutcome.CantDo) },
                            modifier = Modifier.testTag(PlanTags.CantDo),
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.plan_snooze)) },
                            onClick = { menuOpen = false; onSnooze() },
                            modifier = Modifier.testTag(PlanTags.Snooze),
                        )
                    }
                }
            },
        )
        if (outcome != null && outcome != AdviceOutcome.Done) {
            Spacer(Modifier.width(12.dp))
            OutcomeBadge(outcome)
        }
    }
}

/**
 * Nothing to do right now: a calm card (no advice colour, no countdown) with what comes next. [next] null when the
 * plan has nothing further today.
 */
@Composable
internal fun FreeTimeCard(moment: PlanMoment, previewing: Boolean, modifier: Modifier = Modifier) {
    val formatter = rememberTimeFormatter()
    val next = moment.upNext.firstOrNull()
    Surface(
        modifier = modifier.fillMaxWidth().testTag(PlanTags.NowCard),
        shape = NowCardShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(
                (if (previewing) {
                    stringResource(R.string.plan_previewing, formatter.formatFull(moment.instant.atZone(moment.zone).toLocalTime()))
                } else {
                    stringResource(R.string.plan_now)
                }).uppercase(),
                style = MaterialTheme.typography.labelLargeEmphasized,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.semantics { heading() },
            )
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.plan_free_time), style = MaterialTheme.typography.headlineMediumEmphasized)
            Text(
                stringResource(R.string.plan_free_time_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (next != null) {
                Spacer(Modifier.height(16.dp))
                NextLine(next, moment)
            }
        }
    }
}

@Composable
private fun NextLine(next: Advice, moment: PlanMoment) {
    val formatter = rememberTimeFormatter()
    Row(verticalAlignment = Alignment.CenterVertically) {
        AdviceGlyph(next.type, active = false, size = 40.dp)
        Spacer(Modifier.width(12.dp))
        Column {
            Text(
                stringResource(R.string.plan_next_at, next.type.label(), formatter.formatFull(next.start.atZone(moment.zone).toLocalTime())),
                style = MaterialTheme.typography.titleSmall,
            )
            val inDuration = Duration.between(moment.instant, next.start)
            Text(
                formatDuration(inDuration) + " · " + formatter.formatFull(next.start.atZone(moment.secondaryZone).toLocalTime()) +
                    " " + moment.secondaryZone.cityName(),
                style = OpusTheme.textStyles.timeLabel,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Logged outcome label: "✓ Done", "Skipped", "Couldn't". Text always, never an icon alone. */
@Composable
internal fun OutcomeBadge(outcome: AdviceOutcome, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val (container, content) = when (outcome) {
        AdviceOutcome.Done -> scheme.tertiaryContainer to scheme.onTertiaryContainer
        AdviceOutcome.Skipped -> scheme.surfaceContainerHighest to scheme.onSurfaceVariant
        AdviceOutcome.CantDo -> scheme.surfaceContainerHighest to scheme.onSurfaceVariant
    }
    Surface(shape = MaterialTheme.shapes.small, color = container, contentColor = content, modifier = modifier) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
            if (outcome == AdviceOutcome.Done) {
                Icon(PlanIcons.Check, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
            }
            Text(
                stringResource(
                    when (outcome) {
                        AdviceOutcome.Done -> R.string.plan_outcome_done
                        AdviceOutcome.Skipped -> R.string.plan_outcome_skipped
                        AdviceOutcome.CantDo -> R.string.plan_outcome_cant
                    },
                ),
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

private fun AdviceType.labelString(resources: android.content.res.Resources): String =
    resources.getString(labelRes)
