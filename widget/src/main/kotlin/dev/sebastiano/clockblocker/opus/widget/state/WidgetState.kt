package dev.sebastiano.clockblocker.opus.widget.state

import dev.sebastiano.clockblocker.opus.core.designsystem.dial.DialState
import dev.sebastiano.clockblocker.opus.core.model.AdviceOutcome
import dev.sebastiano.clockblocker.opus.core.model.AdviceType
import dev.sebastiano.clockblocker.opus.core.model.DayKind
import java.time.Instant

/**
 * Everything a widget needs, precomputed from the plan at capture time. Pure data: no Android types, so the
 * mapping is unit-testable on the JVM and both render backends (Remote Compose and classic RemoteViews) draw
 * exactly the same thing.
 *
 * "Dial minutes" are wall-clock minutes of the day (0..1439) in [Active.displayZoneId].
 */
sealed interface WidgetState {

    /** No current or upcoming plan: invite the user to plan a trip. */
    data object NoTrip : WidgetState

    data class Active(
        val tripId: String,
        /** Instant the state was computed for; host-evaluated time takes over from here. */
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
        /**
         * The Two skies state at [capturedAt], the same projection the plan screen's dial draws (`toDialState`): the
         * widgets lay out the shared dial spec (Two skies, Two strips) from it.
         */
        val dial: DialState,
        val stage: Stage,
        /** Name of the destination: the trip's city there (see [placeNames]), else its zone's city, e.g. "Tokyo". */
        val destinationName: String,
        /** "Up next" queue for the larger sizes: up to three blocks starting after [capturedAt], excluding [current]. */
        val upcoming: List<AdviceSlot> = emptyList(),
        /** What the user logged for [current] (Done from the widget, notification or app), if anything. */
        val currentOutcome: AdviceOutcome? = null,
        /** False when the advice log could not be read: [currentOutcome] is unknown, so no Done action is offered. */
        val outcomeKnown: Boolean = true,
        /** Kind of the plan day [capturedAt] falls in (null outside the plan's days). */
        val dayKind: DayKind? = null,
        /** Index of that day relative to departure: −2, −1, 0 (travel), 1, 2… */
        val dayIndex: Int? = null,
        /** Share of the planned shift the body clock has completed, 0..1 (see `adaptationProgressAt`). */
        val adaptation: Float? = null,
        /** The trip's airport codes, drawn in the dot-matrix face on the larger sizes; null when unknown. */
        val route: WidgetRoute? = null,
        /**
         * The trip's own city for each zone it visits (IANA id → "San Francisco"), so the header and the other-zone
         * times name the same place as [route]. Zones it doesn't name fall back to the zone's city (`DialMath.cityName`).
         */
        val placeNames: Map<String, String> = emptyMap(),
        /** The IATA code of each place in [placeNames] (IANA id → "SFO"): the shortest form of a long name. */
        val placeCodes: Map<String, String> = emptyMap(),
        /**
         * Shown on a lock screen with "Hide details on the lock screen" on (see [WidgetStateMapper.redact]): no places,
         * route or supplement names. Times and block kinds stay.
         */
        val redacted: Boolean = false,
    ) : WidgetState {
        /** The name of [zoneId]'s place: the trip's city when it has one there, else the zone's own city. */
        fun placeName(zoneId: String): String = placeNames[zoneId] ?: DialMath.cityName(zoneId)

        /** [placeName] and its shorter forms, full name first ([PlaceNames.options]): what small widgets fall back to. */
        fun placeNameOptions(zoneId: String): List<String> = PlaceNames.options(placeName(zoneId), placeCodes[zoneId])

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

/** Origin and destination codes of the trip ("LIS", "HND"): IATA codes, or the city's first letters. */
data class WidgetRoute(val origin: String, val destination: String)

/** Advice whose name stays off the lock screen when details are hidden (same rule as the notifications). */
val AdviceType.isPrivate: Boolean get() = this == AdviceType.Melatonin

/**
 * The advice the widget background and cards lean towards: the current block, unless it is private and the widget is
 * redacted (its colour would give it away).
 */
val WidgetState.tintType: AdviceType?
    get() = (this as? WidgetState.Active)?.let { s -> s.current?.type?.takeUnless { s.redacted && it.isPrivate } }

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
