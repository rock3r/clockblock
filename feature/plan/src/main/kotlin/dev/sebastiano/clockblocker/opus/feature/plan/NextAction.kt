package dev.sebastiano.clockblocker.opus.feature.plan

import dev.sebastiano.clockblocker.opus.core.designsystem.time.TimeFormatter
import dev.sebastiano.clockblocker.opus.core.designsystem.time.cityName
import dev.sebastiano.clockblocker.opus.core.model.Advice
import dev.sebastiano.clockblocker.opus.core.model.AdviceType
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/** The next thing to do, [startsIn] from now; [soon] = within a day, so it reads as a countdown. */
internal data class NextAction(val advice: Advice, val startsIn: Duration) {
    val soon: Boolean get() = startsIn <= CountdownWindow
}

/** A countdown for actions starting within this; beyond it, their day and time. */
internal val CountdownWindow: Duration = Duration.ofHours(24)

/**
 * The next action at [now]: the earliest advice still to start (the more important one on a tie). Flights are not
 * actions (they just happen). Null once the last action has started.
 */
internal fun nextAction(advice: List<Advice>, now: Instant): NextAction? =
    advice.asSequence()
        .filter { it.type != AdviceType.Flight && it.start.isAfter(now) }
        .minWithOrNull(compareBy<Advice> { it.start }.thenBy { it.type.ordinal })
        ?.let { NextAction(it, Duration.between(now, it.start)) }

/** When the next action starts: [local] on the local clock, [secondary] on the other zone's ("12:00 Tokyo +1"). */
internal data class NextActionTime(val local: String, val secondary: String)

/**
 * [start] as "20:00" in [zone] ("Sun 14 Jun 20:00" [withDay]), and in [secondaryZone] as "12:00 Tokyo", marked
 * "+1" / "−1" when that falls on another day than the local time.
 */
internal fun nextActionTime(start: Instant, zone: ZoneId, secondaryZone: ZoneId, formatter: TimeFormatter, withDay: Boolean): NextActionTime {
    val local = start.atZone(zone)
    val other = start.atZone(secondaryZone)
    val time = formatter.formatFull(local.toLocalTime())
    val localText = if (withDay) NextDayFormat.withLocale(formatter.locale).format(local) + " " + time else time
    val days = ChronoUnit.DAYS.between(local.toLocalDate(), other.toLocalDate())
    val marker = when {
        days > 0 -> " +$days"
        days < 0 -> " \u2212${-days}"
        else -> ""
    }
    return NextActionTime(localText, formatter.formatFull(other.toLocalTime()) + " " + secondaryZone.cityName() + marker)
}

private val NextDayFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE d MMM")
