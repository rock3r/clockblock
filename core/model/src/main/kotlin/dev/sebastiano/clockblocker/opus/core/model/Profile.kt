package dev.sebastiano.clockblocker.opus.core.model

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KeepGeneratedSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonTransformingSerializer
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import java.time.Duration
import java.time.LocalTime

/**
 * Self-reported chronotype. Maps to a shift of the estimated circadian phase relative to the habitual sleep
 * window (owls tend to have a later phase angle; see the circadian science notes in `docs/science.md`).
 */
@Serializable
enum class Chronotype {
    DefiniteMorning,
    ModerateMorning,
    Intermediate,
    ModerateEvening,
    DefiniteEvening,
}

/** How hard the plan is allowed to push. Trades daily effort against days-to-adapt. */
@Serializable
enum class Intensity { Gentle, Balanced, Max }

/** The user's habitual sleep, in their home time zone. Bedtime may be before or after midnight. */
@Serializable
data class SleepWindow(
    @Serializable(with = LocalTimeIsoSerializer::class) val bedtime: LocalTime,
    @Serializable(with = LocalTimeIsoSerializer::class) val wake: LocalTime,
) {
    /** Sleep duration, handling windows that wrap around midnight. */
    val duration: Duration
        get() {
            val raw = Duration.between(bedtime, wake)
            return if (raw.isNegative || raw.isZero) raw.plusHours(24) else raw
        }

    /** Mid-sleep clock time (used for chronotype-style phase estimates, cf. MCTQ MSF). */
    val midSleep: LocalTime get() = bedtime.plus(duration.dividedBy(2))

    companion object {
        val Default = SleepWindow(LocalTime.of(23, 0), LocalTime.of(7, 0))
    }
}

@OptIn(ExperimentalSerializationApi::class)
@KeepGeneratedSerializer
@Serializable(with = UserProfileSerializer::class)
data class UserProfile(
    val homeZoneId: String,
    /** Usual sleep on work days: the planner's habitual sleep. */
    val sleep: SleepWindow = SleepWindow.Default,
    val chronotype: Chronotype = Chronotype.Intermediate,
    val useMelatonin: Boolean = false,
    val useCaffeine: Boolean = true,
    val canSleepOnPlanes: Boolean = true,
    /**
     * Head start: on how many days before departure the plan may start shifting the body clock, 0 (none) to
     * [HeadStart.MaxDays]. The planner uses at most this many, and fewer when the intensity or the trip calls for
     * fewer. Replaces the old `adjustBeforeDeparture` switch (see [UserProfileSerializer]).
     */
    val headStartDays: Int = HeadStart.DefaultDays,
    val intensity: Intensity = Intensity.Balanced,
    /**
     * Usual sleep on days off (the nights you wake up without an alarm), or `null` while it's linked to [sleep]
     * (the same nights). It only feeds the chronotype estimate (MCTQ); plans start from [sleep]. Profiles saved
     * before this field existed load with it `null`.
     */
    val daysOffSleep: SleepWindow? = null,
    /**
     * Naps are OK: the plan may suggest optional naps. Off, it leaves them out and only suggests a nap when a long
     * day makes one important.
     */
    val napsOk: Boolean = true,
) {
    /** Days-off sleep, falling back to work-day sleep while the two are linked. */
    val daysOff: SleepWindow get() = daysOffSleep ?: sleep
}

/** The head start choices: None, 1, 2 or 3 days before departure. */
object HeadStart {
    const val MaxDays: Int = 3

    /** What the old "Adjust before I leave" switch did when on: up to three days, the planner's default. */
    const val DefaultDays: Int = MaxDays

    /** None, 1, 2 and 3 days, in the order the app shows them. */
    val Choices: List<Int> = (0..MaxDays).toList()
}

/**
 * [UserProfile]'s JSON form. It migrates profiles saved before the head start became a number of days: the old
 * `adjustBeforeDeparture` switch maps on to [HeadStart.DefaultDays] (what it did) and off to 0. The profile store
 * and backups both decode through it, so old files and old backups load the same way. Day counts outside the
 * choices are clamped.
 *
 * It also writes the old switch next to the day count, so an older build reading the on-device profile store (after
 * a downgrade) keeps the user's head start on or off. Backups don't rely on it: they are version 2
 * (`BackupCodec.CurrentVersion`), which older builds refuse rather than half-import.
 */
object UserProfileSerializer : JsonTransformingSerializer<UserProfile>(UserProfile.generatedSerializer()) {
    private const val Legacy = "adjustBeforeDeparture"
    private const val Days = "headStartDays"

    override fun transformSerialize(element: JsonElement): JsonElement {
        if (element !is JsonObject) return element
        val days = (element[Days] as? JsonPrimitive)?.intOrNull ?: HeadStart.DefaultDays
        return JsonObject(element + (Legacy to JsonPrimitive(days > 0)))
    }

    override fun transformDeserialize(element: JsonElement): JsonElement {
        if (element !is JsonObject || (Legacy !in element && Days !in element)) return element
        val days = (element[Days] as? JsonPrimitive)?.intOrNull
            ?: (element[Legacy] as? JsonPrimitive)?.booleanOrNull?.let { on -> if (on) HeadStart.DefaultDays else 0 }
            ?: HeadStart.DefaultDays
        return JsonObject(element - Legacy + (Days to JsonPrimitive(days.coerceIn(0, HeadStart.MaxDays))))
    }
}
