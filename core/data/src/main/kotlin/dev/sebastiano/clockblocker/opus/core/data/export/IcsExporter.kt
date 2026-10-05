package dev.sebastiano.clockblocker.opus.core.data.export

import dev.sebastiano.clockblocker.opus.core.model.Advice
import dev.sebastiano.clockblocker.opus.core.model.AdviceReason
import dev.sebastiano.clockblocker.opus.core.model.AdviceType
import dev.sebastiano.clockblocker.opus.core.model.JetLagPlan
import dev.sebastiano.clockblocker.opus.core.model.Trip
import dev.zacsweers.metro.Inject
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Text used in exports. The defaults are English; the app passes localised strings from resources.
 * Every event gets a text label (never an icon alone), per the product rules.
 */
data class ExportLabels(
    val adviceTitle: (AdviceType) -> String = ::defaultAdviceTitle,
    val reasonText: (AdviceReason) -> String = ::defaultReasonText,
    val disclaimer: String = "Opus Clockblock plan. Not medical advice.",
) {
    companion object {
        fun defaultAdviceTitle(type: AdviceType): String = when (type) {
            AdviceType.SeeBrightLight -> "See bright light"
            AdviceType.SeeLight -> "See some light"
            AdviceType.AvoidLight -> "Avoid light"
            AdviceType.Sleep -> "Sleep"
            AdviceType.Nap -> "Nap"
            AdviceType.OptionalNap -> "Nap if you're tired"
            AdviceType.Melatonin -> "Take melatonin"
            AdviceType.Caffeine -> "Caffeine OK"
            AdviceType.AvoidCaffeine -> "Avoid caffeine"
            AdviceType.PeakFatigue -> "Peak fatigue: take care"
            AdviceType.Flight -> "Flight"
        }

        fun defaultReasonText(reason: AdviceReason): String = when (reason) {
            AdviceReason.LightAdvancesClock -> "Light now moves your body clock earlier."
            AdviceReason.LightDelaysClock -> "Light now moves your body clock later."
            AdviceReason.AvoidCounterShift -> "Light now would push your body clock the wrong way."
            AdviceReason.MelatoninAdvances -> "Melatonin now helps move your body clock earlier."
            AdviceReason.MelatoninDelays -> "Melatonin now helps move your body clock later."
            AdviceReason.ShiftedSleep -> "Sleep on your shifting schedule."
            AdviceReason.DestinationSleep -> "Sleep at your destination's night."
            AdviceReason.StayOnHomeTime -> "Short trip: keep your body clock on home time."
            AdviceReason.AlertnessSupport -> "Helps you stay alert without disturbing your next sleep."
            AdviceReason.ProtectSleep -> "Caffeine now would disturb your upcoming sleep."
            AdviceReason.CircadianLow -> "Your body clock's low point: be careful driving."
            AdviceReason.SleepPressure -> "Pays down sleep pressure without anchoring the old time zone."
            AdviceReason.TravelMarker -> "Travel."
            AdviceReason.RestInFlight -> "Rest with eyes closed and the light blocked, even if you can't sleep."
        }
    }
}

/**
 * Exports a plan's advice as an iCalendar (RFC 5545) file: one VEVENT per advice, with times in the local
 * zone of the plan day they belong to (`DTSTART;TZID=Europe/London:…`) and a matching VTIMEZONE (built from
 * the device's tz rules for the plan's date range), so calendar apps show the right wall-clock times
 * wherever the user is. UIDs derive from the stable advice ids, so re-importing an updated plan updates
 * events instead of duplicating them.
 */
@Inject
class IcsExporter {

    /**
     * @param include which advice to export (default: everything).
     * @param reminderMinutesBefore adds a display alarm this many minutes before each event, or none if null.
     */
    fun export(
        plan: JetLagPlan,
        trip: Trip? = null,
        labels: ExportLabels = ExportLabels(),
        include: (Advice) -> Boolean = { true },
        reminderMinutesBefore: Int? = null,
    ): String {
        val events = plan.days.flatMap { day -> day.advice.filter(include).map { it to ZoneId.of(day.zoneId) } }
            .sortedBy { it.first.start }
        val zones = events.map { it.second }.distinct()
        val stamp = Utc.format(plan.generatedAt)
        val lines = mutableListOf(
            "BEGIN:VCALENDAR",
            "VERSION:2.0",
            "PRODID:-//Opus Clockblock//Jet lag plan//EN",
            "CALSCALE:GREGORIAN",
            "METHOD:PUBLISH",
            "X-WR-CALNAME:" + escape(trip?.title ?: "Jet lag plan"),
        )
        if (events.isNotEmpty()) {
            val from = events.minOf { it.first.start }
            val to = events.maxOf { it.first.end }
            zones.forEach { lines += timezone(it, from, to) }
        }
        for ((advice, zone) in events) {
            lines += "BEGIN:VEVENT"
            lines += "UID:" + escape("${advice.id}.${plan.tripId}@opusclockblock.app")
            lines += "DTSTAMP:$stamp"
            lines += "DTSTART;TZID=${zone.id}:${local(advice.start, zone)}"
            // Moments (melatonin) have no end: RFC 5545 then makes the event end at its start.
            if (!advice.type.isMoment && advice.end.isAfter(advice.start)) {
                lines += "DTEND;TZID=${zone.id}:${local(advice.end, zone)}"
            }
            val title = labels.adviceTitle(advice.type) + (advice.detail?.let { " ($it)" } ?: "")
            lines += "SUMMARY:" + escape(title)
            lines += "DESCRIPTION:" + escape(labels.reasonText(advice.reason) + "\n\n" + labels.disclaimer)
            lines += "CATEGORIES:" + escape(advice.type.name)
            lines += "TRANSP:" + if (advice.type == AdviceType.Flight || advice.type == AdviceType.Sleep) "OPAQUE" else "TRANSPARENT"
            if (reminderMinutesBefore != null) {
                lines += "BEGIN:VALARM"
                lines += "ACTION:DISPLAY"
                lines += "DESCRIPTION:" + escape(title)
                lines += "TRIGGER:-PT${reminderMinutesBefore}M"
                lines += "END:VALARM"
            }
            lines += "END:VEVENT"
        }
        lines += "END:VCALENDAR"
        return lines.joinToString(separator = "") { fold(it) + Crlf }
    }

    /**
     * VTIMEZONE with one observance per offset change touching [from]..[to] (plus the one in force at
     * [from]), without RRULEs: exact for the exported range, independent of future rule changes.
     */
    private fun timezone(zone: ZoneId, from: Instant, to: Instant): List<String> {
        val rules = zone.rules
        val out = mutableListOf("BEGIN:VTIMEZONE", "TZID:${zone.id}")
        val previous = rules.previousTransition(from.plusSeconds(1))
        val transitions = buildList {
            previous?.let(::add)
            var t = rules.nextTransition(from)
            while (t != null && !t.instant.isAfter(to)) {
                if (t != previous) add(t)
                t = rules.nextTransition(t.instant)
            }
        }
        if (transitions.isEmpty()) {
            val offset = offset(rules.getOffset(from))
            out += listOf("BEGIN:STANDARD", "DTSTART:19700101T000000", "TZOFFSETFROM:$offset", "TZOFFSETTO:$offset", "END:STANDARD")
        } else {
            for (t in transitions) {
                val kind = if (rules.isDaylightSavings(t.instant)) "DAYLIGHT" else "STANDARD"
                out += "BEGIN:$kind"
                out += "DTSTART:" + LocalFormat.format(t.dateTimeBefore)
                out += "TZOFFSETFROM:" + offset(t.offsetBefore)
                out += "TZOFFSETTO:" + offset(t.offsetAfter)
                out += "END:$kind"
            }
        }
        out += "END:VTIMEZONE"
        return out
    }

    private fun local(instant: Instant, zone: ZoneId): String =
        LocalFormat.format(LocalDateTime.ofInstant(instant, zone))

    private fun offset(o: ZoneOffset): String {
        val total = o.totalSeconds
        val sign = if (total < 0) "-" else "+"
        val abs = kotlin.math.abs(total)
        return String.format(Locale.ROOT, "%s%02d%02d", sign, abs / 3600, abs % 3600 / 60)
    }

    companion object {
        private const val Crlf = "\r\n"
        private val LocalFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss", Locale.ROOT)
        private val Utc: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'", Locale.ROOT).withZone(ZoneOffset.UTC)

        /** RFC 5545 §3.3.11 TEXT escaping. */
        fun escape(text: String): String = buildString(text.length) {
            for (c in text) when (c) {
                '\\' -> append("\\\\")
                ';' -> append("\\;")
                ',' -> append("\\,")
                '\n' -> append("\\n")
                '\r' -> Unit
                else -> append(c)
            }
        }

        /** RFC 5545 §3.1 line folding: at most 75 octets per line, continuation lines start with a space. */
        fun fold(line: String): String {
            val bytes = line.encodeToByteArray()
            if (bytes.size <= 75) return line
            val out = StringBuilder()
            var lineBytes = 0
            var limit = 75
            for (cp in line.codePoints()) {
                val chars = Character.toChars(cp)
                val size = String(chars).encodeToByteArray().size
                if (lineBytes + size > limit) {
                    out.append(Crlf).append(' ')
                    lineBytes = 1
                    limit = 75
                }
                out.append(chars)
                lineBytes += size
            }
            return out.toString()
        }
    }
}
