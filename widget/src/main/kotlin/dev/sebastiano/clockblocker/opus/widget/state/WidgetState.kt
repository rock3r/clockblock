package dev.sebastiano.clockblocker.opus.widget.state

import dev.sebastiano.clockblocker.opus.core.model.AdviceType
import java.time.Instant

/**
 * Everything a widget needs, precomputed from the plan at capture time. Pure data: no Android types, so the
 * mapping is unit-testable on the JVM and both render backends (Remote Compose and classic RemoteViews) draw
 * exactly the same thing.
 *
 * "Dial minutes" are wall-clock minutes of the day (0..1439) in [Active.displayZoneId]. The dial puts noon at
 * the top and runs clockwise (see [DialMath]).
 */
sealed interface WidgetState {

    /** No current or upcoming plan: invite the user to plan a trip. */
    data object NoTrip : WidgetState

    data class Active(
        val tripId: String,
        /** Instant the state was computed for; host-evaluated time takes over from here on API 36+. */
        val capturedAt: Instant,
        /** Zone the dial and times are shown in: where the plan expects the user to be today. */
        val displayZoneId: String,
        /** Secondary zone shown next to times (home or destination, whichever isn't [displayZoneId]). */
        val secondaryZoneId: String?,
        /** UTC offset of [displayZoneId] at [capturedAt], minutes. */
        val displayOffsetMinutes: Int,
        /** Body clock expressed as a UTC offset at [capturedAt], minutes (see `PhasePoint`). */
        val bodyOffsetMinutes: Int,
        val current: AdviceSlot?,
        val next: AdviceSlot?,
        /** Advice in the 24 h ahead of [capturedAt], clipped to that window, in dial minutes. */
        val arcs: List<DialArc>,
        /** Biological night on the body ring, in dial minutes. */
        val bodyNight: DialArc?,
        /** Estimated core-body-temperature minimum nearest to now, in dial minutes. */
        val cbtMinMinute: Int?,
        val stage: Stage,
        /** City-ish name of the destination derived from its IANA id, e.g. "Tokyo". */
        val destinationName: String,
    ) : WidgetState {
        /**
         * Local (display) time minus body time, minutes: "+300" = local time is 5 h ahead of your body. Drives the dial
         * geometry (where body night lands on the local ring); labels use [bodyRelativeMinutes] instead.
         */
        val misalignmentMinutes: Int get() = displayOffsetMinutes - bodyOffsetMinutes

        /**
         * Body clock relative to local time, minutes, wrapped into [-720, 720): "−300" = your body is 5 h *behind*
         * local time. The app-wide convention every jet lag label uses (same as the in-app dial chip and header).
         */
        val bodyRelativeMinutes: Int get() = Math.floorMod(bodyOffsetMinutes - displayOffsetMinutes + 720, 1440) - 720

        /** Display wall-clock minute of [capturedAt]. */
        val nowMinute: Int get() = DialMath.minuteOfDay(capturedAt, displayOffsetMinutes)
    }

    enum class Stage {
        /** Plan exists but nothing has started yet. */
        Upcoming,

        /** Inside the plan's advice span. */
        InProgress,

        /** All advice is in the past: adapted. */
        Done,
    }
}

/** One piece of advice as the widgets show it. */
data class AdviceSlot(
    val adviceId: String,
    val type: AdviceType,
    val start: Instant,
    val end: Instant,
    /** Dial minute of [start] in the display zone. */
    val startMinute: Int,
    /** Dial minute of [end] in the display zone. */
    val endMinute: Int,
)

/** An arc on the dial. [sweepMinutes] is 0 for moments (melatonin), drawn as a dot. */
data class DialArc(
    val type: AdviceType?,
    val startMinute: Int,
    val sweepMinutes: Int,
)
