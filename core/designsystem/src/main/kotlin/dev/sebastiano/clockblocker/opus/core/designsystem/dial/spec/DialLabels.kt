package dev.sebastiano.clockblocker.opus.core.designsystem.dial.spec

import dev.sebastiano.clockblocker.opus.core.designsystem.dial.DialGeometry
import dev.sebastiano.clockblocker.opus.core.designsystem.dial.formatHoursMagnitude
import dev.sebastiano.clockblocker.opus.core.model.AdviceType
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

/**
 * Every word the dial prints. Hosts implement it from their own strings (the app from string resources, so the
 * dial is localised); [DefaultDialLabels] is the English reference for tests and tools.
 */
interface DialLabels {
    /** "15:20" or "3:20": the clock digits, without an AM/PM marker. */
    fun time(minuteOfDay: Float): String

    /** "PM" on 12-hour clocks, null on 24-hour ones. */
    fun marker(minuteOfDay: Float): String?

    /** "15:20" or "3:20 PM": a time in running text (the narration). */
    fun fullTime(minuteOfDay: Float): String

    /** The 00 / 06 / 12 / 18 numerals ("00" or "12a"). */
    fun numeral(hour: Int): String

    /** "Tokyo day" / "Tokyo night": the local sky's halves. */
    fun placeDay(place: String): String
    fun placeNight(place: String): String

    /** "your body's day" / "your body's night": the body sky's halves. */
    fun bodyDay(): String
    fun bodyNight(): String

    /** "body": the body ring's name where only one word fits. */
    fun body(): String

    /** "08:20 body": the body clock under the local time. */
    fun bodyTime(time: String): String

    /** "7 h behind", "2½ h ahead", or [inSync] (the body clock relative to local time). */
    fun offset(bodyAheadMinutes: Float): String

    fun inSync(): String

    /** "Avoid light". */
    fun advice(type: AdviceType): String

    /** "Avoid light until 16:30": the block under the hand. */
    fun until(advice: String, time: String): String

    /** "Sleep at 23:00": the next block, when nothing is on. */
    fun at(advice: String, time: String): String

    /** "then melatonin": what follows the current block. */
    fun then(advice: String): String
}

/** English labels with the app's conventions (half hours, "in sync" under the alignment threshold). */
class DefaultDialLabels(private val is24Hour: Boolean = true, locale: Locale = Locale.ENGLISH) : DialLabels {
    private val digits = DateTimeFormatter.ofPattern(if (is24Hour) "HH:mm" else "h:mm", locale)
    private val markers = DateTimeFormatter.ofPattern("a", locale)

    override fun time(minuteOfDay: Float): String = digits.format(DialGeometry.timeOf(minuteOfDay.roundToMinute()))
    override fun marker(minuteOfDay: Float): String? =
        if (is24Hour) null else markers.format(DialGeometry.timeOf(minuteOfDay.roundToMinute()))
    override fun fullTime(minuteOfDay: Float): String = marker(minuteOfDay)?.let { "${time(minuteOfDay)} $it" } ?: time(minuteOfDay)
    override fun numeral(hour: Int): String = numeralFor(hour, is24Hour)
    override fun placeDay(place: String) = "$place day"
    override fun placeNight(place: String) = "$place night"
    override fun bodyDay() = "your body\u2019s day"
    override fun bodyNight() = "your body\u2019s night"
    override fun body() = "body"
    override fun bodyTime(time: String) = "$time body"
    override fun offset(bodyAheadMinutes: Float): String = when {
        isInSync(bodyAheadMinutes) -> inSync()
        bodyAheadMinutes < 0f -> "${formatHoursMagnitude(bodyAheadMinutes / 60f)} behind"
        else -> "${formatHoursMagnitude(bodyAheadMinutes / 60f)} ahead"
    }
    override fun inSync() = "in sync"
    override fun advice(type: AdviceType): String = when (type) {
        AdviceType.SeeBrightLight -> "See bright light"
        AdviceType.SeeLight -> "See some light"
        AdviceType.AvoidLight -> "Avoid light"
        AdviceType.Sleep -> "Sleep"
        AdviceType.Nap -> "Nap"
        AdviceType.OptionalNap -> "Nap if you\u2019re tired"
        AdviceType.Melatonin -> "Take melatonin"
        AdviceType.Caffeine -> "Caffeine OK"
        AdviceType.AvoidCaffeine -> "Avoid caffeine"
        AdviceType.PeakFatigue -> "Peak fatigue"
        AdviceType.Flight -> "Flight"
    }
    override fun until(advice: String, time: String) = "$advice until $time"
    override fun at(advice: String, time: String) = "$advice at $time"
    override fun then(advice: String) = "then ${advice.replaceFirstChar { it.lowercase() }}"

    companion object {
        /** The dial's numerals: "00"/"06"/"12"/"18", or "12a"/"6a"/"12p"/"6p" on 12-hour clocks. */
        fun numeralFor(hour: Int, is24Hour: Boolean): String =
            if (is24Hour) "%02d".format(hour) else "${if (hour % 12 == 0) 12 else hour % 12}${if (hour < 12) "a" else "p"}"

        /** Under the alignment threshold the rings count as in sync (the header says the same). */
        fun isInSync(bodyAheadMinutes: Float): Boolean =
            abs(bodyAheadMinutes) < dev.sebastiano.clockblocker.opus.core.designsystem.dial.DialState.AlignedThresholdMinutes
    }
}

/** Minutes are floats while the hand moves; labels show the minute the hand is in. */
internal fun Float.roundToMinute(): Float = kotlin.math.floor(this + 1e-3f)
