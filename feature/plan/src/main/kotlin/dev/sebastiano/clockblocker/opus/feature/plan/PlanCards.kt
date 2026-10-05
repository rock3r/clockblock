package dev.sebastiano.clockblocker.opus.feature.plan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.sebastiano.clockblocker.opus.core.designsystem.advice.AdviceGlyph
import dev.sebastiano.clockblocker.opus.core.designsystem.advice.label
import dev.sebastiano.clockblocker.opus.core.designsystem.component.WavyAdaptationIndicator
import dev.sebastiano.clockblocker.opus.core.designsystem.illustration.BloomArt
import dev.sebastiano.clockblocker.opus.core.designsystem.illustration.PillowMoonArt
import dev.sebastiano.clockblocker.opus.core.designsystem.illustration.SuitcaseOClockArt
import dev.sebastiano.clockblocker.opus.core.designsystem.illustration.TwoClocksArt
import dev.sebastiano.clockblocker.opus.core.designsystem.theme.OpusTheme
import dev.sebastiano.clockblocker.opus.core.designsystem.time.cityName
import dev.sebastiano.clockblocker.opus.core.designsystem.time.rememberTimeFormatter
import dev.sebastiano.clockblocker.opus.core.model.Advice
import dev.sebastiano.clockblocker.opus.core.model.AdviceOutcome
import dev.sebastiano.clockblocker.opus.core.model.JetLagPlan
import dev.sebastiano.clockblocker.opus.core.model.SleepWindow
import java.time.ZoneId
import kotlin.math.roundToInt

/** Section title above a group ("Up next", "Your plan"). */
@Composable
internal fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmallEmphasized,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.semantics { heading() },
    )
}

/** The next two or three steps, each with a label, local time and the secondary zone. */
@Composable
internal fun UpNextCard(moment: PlanMoment, onClick: (Advice) -> Unit, modifier: Modifier = Modifier) {
    if (moment.upNext.isEmpty()) return
    val formatter = rememberTimeFormatter()
    val resources = LocalContext.current.resources
    Column(modifier.testTag(PlanTags.UpNext)) {
        SectionTitle(stringResource(R.string.plan_up_next), Modifier.padding(start = 4.dp, bottom = 8.dp))
        Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainerLow) {
            Column {
                moment.upNext.forEachIndexed { index, advice ->
                    if (index > 0) HorizontalDivider(Modifier.padding(start = 72.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                    Surface(
                        onClick = { onClick(advice) },
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp).testTag(PlanTags.upNext(advice.id)),
                    ) {
                        Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            AdviceGlyph(advice.type, active = false, size = 40.dp)
                            Spacer(Modifier.width(16.dp))
                            Column(Modifier.weight(1f)) {
                                Text(advice.type.label(), style = MaterialTheme.typography.titleMedium)
                                Text(
                                    formatter.range(advice.start, advice.end, moment.secondaryZone, resources) + " " + moment.secondaryZone.cityName(),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    formatter.formatFull(advice.start.atZone(moment.zone).toLocalTime()),
                                    style = OpusTheme.textStyles.timeTitle,
                                )
                                Text(
                                    formatDuration(java.time.Duration.between(moment.instant, advice.start)),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * "62% adapted · about 2 days to go" over the wavy line (wavy = jet-lagged; it calms as you adapt), plus what
 * going it alone would cost.
 */
@Composable
internal fun AdaptationCard(plan: JetLagPlan, moment: PlanMoment, modifier: Modifier = Modifier) {
    val percent = (moment.progress * 100).roundToInt()
    val adapted = moment.stage == PlanStage.Adapted || moment.stage == PlanStage.Complete || moment.progress >= 0.95f
    val toGo = if (adapted) null else daysToGoLabel(moment.daysToGo)
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier.fillMaxWidth().testTag(PlanTags.Adaptation),
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    stringResource(R.string.plan_adapted_percent, percent),
                    style = MaterialTheme.typography.titleLargeEmphasized,
                )
                if (toGo != null) {
                    Text(
                        " · $toGo",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            WavyAdaptationIndicator(
                progress = moment.progress,
                misalignment = moment.misalignment,
                remaining = toGo,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(14.dp))
            val supporting = when {
                adapted -> stringResource(R.string.plan_adapted_line, ZoneId.of(plan.destinationZoneId).cityName())
                moment.stage == PlanStage.Upcoming -> stringResource(R.string.plan_adaptation_upcoming)
                else -> roundDays(plan.estimatedDaysWithoutPlan).let { pluralStringResource(R.plurals.plan_without_plan, it, it) }
            }
            Text(supporting, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Editorial card used for the special plan kinds (no shift, short trip) and the trip summary. */
@Composable
private fun StoryCard(
    title: String,
    body: String,
    art: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    footer: (@Composable () -> Unit)? = null,
) {
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier.fillMaxWidth().testTag(PlanTags.Status),
    ) {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(title, style = OpusTheme.textStyles.editorialTitle, modifier = Modifier.semantics { heading() })
                Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                footer?.invoke()
            }
            Spacer(Modifier.width(12.dp))
            art()
        }
    }
}

/** "No jet lag expected": sleep suggestions instead of a plan to follow. */
@Composable
internal fun NoShiftCard(plan: JetLagPlan, sleep: SleepWindow, modifier: Modifier = Modifier) {
    val formatter = rememberTimeFormatter()
    StoryCard(
        title = stringResource(R.string.plan_no_shift_title),
        body = stringResource(
            R.string.plan_no_shift_body,
            ZoneId.of(plan.originZoneId).cityName(),
            ZoneId.of(plan.destinationZoneId).cityName(),
            formatter.formatFull(sleep.bedtime),
            formatter.formatFull(sleep.wake),
        ),
        art = { PillowMoonArt(Modifier.size(88.dp), animated = false) },
        modifier = modifier,
    )
}

/** Short trip: the body clock stays home, and here is why. */
@Composable
internal fun StayOnHomeCard(plan: JetLagPlan, modifier: Modifier = Modifier) {
    StoryCard(
        title = stringResource(R.string.plan_stay_home_title),
        body = stringResource(R.string.plan_stay_home_body, ZoneId.of(plan.originZoneId).cityName()),
        art = { TwoClocksArt(Modifier.size(88.dp), animated = false) },
        modifier = modifier,
    )
}

/** Before the first plan day: when it starts and what comes first. Takes the Now card's place. */
@Composable
internal fun UpcomingCard(plan: JetLagPlan, moment: PlanMoment, modifier: Modifier = Modifier) {
    val formatter = rememberTimeFormatter()
    val first = plan.days.firstOrNull()
    val next = moment.upNext.firstOrNull()
    StoryCard(
        title = stringResource(R.string.plan_upcoming_title, first?.date?.let(::formatDayDate).orEmpty()),
        body = if (next != null) {
            val zone = ZoneId.of(plan.days.firstOrNull { d -> d.advice.any { it.id == next.id } }?.zoneId ?: plan.originZoneId)
            stringResource(
                R.string.plan_upcoming_body,
                next.type.label(),
                formatDayDate(next.start.atZone(zone).toLocalDate()),
                formatter.formatFull(next.start.atZone(zone).toLocalTime()),
            )
        } else {
            stringResource(R.string.plan_free_time_body)
        },
        art = { SuitcaseOClockArt(Modifier.size(96.dp), animated = false) },
        modifier = modifier.testTag(PlanTags.NowCard),
    )
}

/** After the trip: what was done, and a kind sign-off. Takes the Now card's place. */
@Composable
internal fun CompleteCard(plan: JetLagPlan, outcomes: Map<String, AdviceOutcome>, modifier: Modifier = Modifier) {
    val values = outcomes.values
    StoryCard(
        title = stringResource(R.string.plan_complete_title),
        body = stringResource(R.string.plan_complete_adapted, ZoneId.of(plan.destinationZoneId).cityName()),
        art = { BloomArt(Modifier.size(96.dp), animated = false) },
        modifier = modifier.testTag(PlanTags.NowCard),
        footer = {
            Text(
                stringResource(
                    R.string.plan_complete_body,
                    values.count { it == AdviceOutcome.Done },
                    values.count { it == AdviceOutcome.Skipped },
                    values.count { it == AdviceOutcome.CantDo },
                ),
                style = OpusTheme.textStyles.timeLabel,
            )
        },
    )
}
