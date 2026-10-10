package dev.sebastiano.clockblocker.opus.core.designsystem.dial.spec

import dev.sebastiano.clockblocker.opus.core.designsystem.dial.Daylight
import dev.sebastiano.clockblocker.opus.core.designsystem.dial.DialArc
import dev.sebastiano.clockblocker.opus.core.designsystem.dial.DialGeometry
import dev.sebastiano.clockblocker.opus.core.designsystem.dial.DialState
import dev.sebastiano.clockblocker.opus.core.model.BodyRingMode

/** How much the dial shows, picked from its actual size in dp (not the widget bucket it sits in). */
enum class DetailLevel {
    /** Under 110 dp (a 1×1 widget): the two skies, the needle and the two times. */
    Glance,

    /** 110–250 dp (a 2×2 widget, a compact hero): adds ring labels and the advice arc with its glyph. */
    Simple,

    /** 250 dp and up (large widgets, the in-app hero): adds day/night labels, numerals and the narration rim. */
    Full,
    ;

    companion object {
        const val SimpleMinDp = 110f
        const val FullMinDp = 250f

        fun forSize(minSideDp: Float): DetailLevel = when {
            minSideDp < SimpleMinDp -> Glance
            minSideDp < FullMinDp -> Simple
            else -> Full
        }

        /** Two strips' narrowest Simple box: below it there is only room for the bars between the two times. */
        const val StripSimpleMinWidthDp = 150f
        const val StripFullMinWidthDp = 280f
        const val StripFullMinHeightDp = 128f

        /** The level of a Two strips box: narrow is Glance, wide and tall is Full, the rest Simple. */
        fun forStrip(widthDp: Float, heightDp: Float): DetailLevel = when {
            widthDp < StripSimpleMinWidthDp -> Glance
            widthDp >= StripFullMinWidthDp && heightDp >= StripFullMinHeightDp -> Full
            else -> Simple
        }
    }
}

/**
 * A night on the dial in local minutes of the day, running clockwise from [start] to [end]. On a polar day or night
 * ([daylight]) both hold solar noon: the night fills the dial centred on solar midnight, or the day centred on noon.
 */
data class NightSpan(val start: Float, val end: Float, val daylight: Daylight = Daylight.RisesAndSets) {
    val lengthMinutes: Float get() = when (daylight) {
        Daylight.RisesAndSets -> (end - start).mod(DialGeometry.MinutesPerDay)
        Daylight.AlwaysUp -> 0f
        Daylight.AlwaysDown -> DialGeometry.MinutesPerDay
    }
    val centre: Float get() = (start + lengthMinutes / 2f).mod(DialGeometry.MinutesPerDay)
    val dayCentre: Float get() = when (daylight) {
        Daylight.AlwaysUp -> end
        else -> (end + (DialGeometry.MinutesPerDay - lengthMinutes) / 2f).mod(DialGeometry.MinutesPerDay)
    }
}

/** Where the body ring's night falls. */
object BodySky {
    /**
     * The body ring's sunset → sunrise in **body** minutes: the local sun's times for [BodyRingMode.Simple], the
     * biological night for [BodyRingMode.Precise]. The ring is painted in body minutes and turned by the jet lag.
     */
    fun nightInBody(state: DialState, mode: BodyRingMode): NightSpan = when (mode) {
        BodyRingMode.Simple -> NightSpan(state.sunsetMinute, state.sunriseMinute, state.daylight)
        BodyRingMode.Precise -> NightSpan(state.biologicalNightStartBodyMinute, state.biologicalNightEndBodyMinute)
    }

    /** [nightInBody] placed on the local dial for a body clock [bodyAheadMinutes] ahead of local time. */
    fun nightInLocal(state: DialState, mode: BodyRingMode, bodyAheadMinutes: Float = state.bodyAheadMinutes): NightSpan {
        val body = nightInBody(state, mode)
        return NightSpan(
            (body.start - bodyAheadMinutes).mod(DialGeometry.MinutesPerDay),
            (body.end - bodyAheadMinutes).mod(DialGeometry.MinutesPerDay),
            body.daylight,
        )
    }

    /** The local sky's night. */
    fun localNight(state: DialState): NightSpan = NightSpan(state.sunsetMinute, state.sunriseMinute, state.daylight)
}

/** The advice the dial shows at one minute: the block under the hand, and the next one to start after it. */
data class DialFocus(val current: DialArc?, val next: DialArc?)

/**
 * [DialFocus] at local [minute]: a moment due at that minute, else the window under it (the highest-priority type
 * when windows overlap, the same choice as the narration); and the earliest advice starting after it.
 *
 * "After" is measured inside the dial's window ([DialGeometry.relativeMinute] from now), not round the clock face:
 * scrubbed towards the end of the window, advice from earlier today is in the past, not 24 h minus a bit ahead.
 */
fun DialState.focusAt(minute: Float): DialFocus {
    val at = DialGeometry.relativeMinute(localMinute, minute)
    fun ahead(arc: DialArc) = DialGeometry.relativeMinute(localMinute, arc.startMinute) - at
    val due = arcs
        .filter { it.sweepMinutes == 0f && ahead(it) > -MomentDueMinutes && ahead(it) <= MomentLeadMinutes }
        .minByOrNull { it.type.ordinal }
    val current = due ?: arcs
        .filter { it.sweepMinutes > 0f && (minute - it.startMinute).mod(DialGeometry.MinutesPerDay) < it.sweepMinutes }
        .minByOrNull { it.type.ordinal }
    val next = arcs
        .filter { it.adviceId != current?.adviceId }
        .map { it to ahead(it) }
        // A moment within its lead is already due (current); a block is next until the minute it starts.
        .filter { (arc, ahead) -> ahead > if (arc.sweepMinutes == 0f) MomentLeadMinutes else 0f }
        .minWithOrNull(compareBy<Pair<DialArc, Float>> { it.second }.thenBy { it.first.type.ordinal })
        ?.first
    return DialFocus(current, next)
}

/** A moment (melatonin, a nap cue) is due from half a minute before it until [MomentDueMinutes] after. */
private const val MomentLeadMinutes = 0.5f

/**
 * How long after its minute a moment stays due, and featured on the dial. Widgets only move the hand between captures,
 * so the scheduler refreshes them this long after each moment (`TransitionPlanner.nextMomentEnd`).
 */
const val MomentDueMinutes = 1f
