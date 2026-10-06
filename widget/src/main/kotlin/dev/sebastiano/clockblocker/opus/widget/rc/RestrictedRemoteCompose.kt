@file:Suppress("RestrictedApiAndroidX")

package dev.sebastiano.clockblocker.opus.widget.rc

import androidx.compose.remote.creation.compose.action.Action
import androidx.compose.remote.creation.compose.layout.RemoteTime
import androidx.compose.remote.creation.compose.state.RemoteFloat
import androidx.compose.remote.creation.compose.state.RemoteString
import androidx.compose.remote.creation.compose.state.floor
import androidx.compose.remote.creation.compose.state.rf
import androidx.compose.remote.creation.compose.state.rs
import androidx.compose.remote.creation.compose.state.selectIfLt
import androidx.compose.remote.creation.profile.Profile
import androidx.compose.remote.core.RcProfiles
import androidx.compose.remote.core.operations.utilities.AnimatedFloatExpression
import androidx.compose.remote.creation.CreationDisplayInfo
import androidx.compose.remote.creation.RemoteComposeWriterAndroid
import androidx.compose.remote.creation.platform.AndroidxRcPlatformServices
import dev.sebastiano.clockblocker.opus.widget.draw.TwoClocksDial
import dev.sebastiano.clockblocker.opus.widget.text.CountdownWords
import java.lang.reflect.Constructor
import java.text.DecimalFormat

/*
 * THE ONLY FILE IN :widget THAT TOUCHES @RestrictTo(LIBRARY_GROUP) REMOTE COMPOSE API (alpha20).
 *
 * - `RemoteTime` (host-evaluated clock: minutes since local midnight, device UTC offset). There is no public time
 *   source yet besides `RemoteTimeDefaults.defaultTimeString()`; the official canvas samples use this path.
 * - `RcPlatformProfiles.WIDGETS_V6/V7`: the document profiles the platform widget host understands.
 *   KDoc: "will be moved to the glance module when creation APIs are public, before stable APIs."
 *   We re-create them ([WidgetProfiles]) because the alpha20 ones drop the writer callback (no click actions).
 * - `selectIfLt` (host-side conditional strings) for the countdown format.
 * - The Kotlin-`internal` compose `HostAction(id, …)`, via reflection ([idHostAction]): the only way to write the id
 *   host action the platform player maps to `RemoteViews` clicks.
 *
 * Kotlin does not enforce @RestrictTo (lint does), so this compiles; keep usages here so the migration to the
 * public API is a one-file change.
 */

/** Host-evaluated time expressions. Re-evaluated by the launcher every minute without waking the app. */
internal object HostTime {
    private val time = RemoteTime()

    /** Minutes since UTC midnight, 0..1439, from the host clock. */
    fun utcMinuteOfDay(): RemoteFloat {
        // FLOAT_TIME_IN_MIN = device-local minutes since midnight; FLOAT_OFFSET_TO_UTC = local − UTC in seconds.
        // +2880 keeps the operand positive for every offset (−12 h … +14 h).
        return (time.Minutes() - time.UtcOffset() / 60f.rf + 2880f.rf) % 1440f.rf
    }

    /** Wall-clock minute of the day at a fixed UTC offset (display zone or body clock), 0..1439. */
    fun minuteOfDayAt(offsetMinutes: Int): RemoteFloat =
        (utcMinuteOfDay() + (offsetMinutes + 1440).toFloat().rf) % 1440f.rf

    /** Minutes elapsed since [capturedUtcMinute] (UTC minute of day at capture), wrapping after 24 h. */
    fun minutesSince(capturedUtcMinute: Int): RemoteFloat =
        (utcMinuteOfDay() - capturedUtcMinute.toFloat().rf + 1440f.rf) % 1440f.rf

    /**
     * 1 while [minuteOfDay] is between sunrise and sunset ([TwoClocksDial.SUNRISE_MINUTE]..[TwoClocksDial.SUNSET_MINUTE]),
     * else (almost) 0: a scale factor that shows the sun head by day. Never exactly 0 so no draw gets a singular matrix.
     */
    fun dayFactor(minuteOfDay: RemoteFloat): RemoteFloat = selectIfLt(
        minuteOfDay,
        TwoClocksDial.SUNRISE_MINUTE.toFloat().rf,
        HIDDEN.rf,
        selectIfLt(minuteOfDay, TwoClocksDial.SUNSET_MINUTE.toFloat().rf, 1f.rf, HIDDEN.rf),
    )

    /** The opposite of [dayFactor]: shows the moon head at night. */
    fun nightFactor(minuteOfDay: RemoteFloat): RemoteFloat = selectIfLt(
        minuteOfDay,
        TwoClocksDial.SUNRISE_MINUTE.toFloat().rf,
        1f.rf,
        selectIfLt(minuteOfDay, TwoClocksDial.SUNSET_MINUTE.toFloat().rf, HIDDEN.rf, 1f.rf),
    )

    private const val HIDDEN = 0.0001f
}

/** Host-evaluated text formatting. */
internal object HostText {
    private val oneDigit = DecimalFormat("0")
    private val twoDigits = DecimalFormat("00")

    /** "07:05" (24 h) or "7:05" (12 h, no marker to stay compact) from a minute-of-day expression. */
    fun clock(minuteOfDay: RemoteFloat, is24Hour: Boolean): RemoteString {
        val hours = floor(minuteOfDay / 60f.rf)
        val minutes = floor(minuteOfDay % 60f.rf).toRemoteString(twoDigits)
        return if (is24Hour) {
            hours.toRemoteString(twoDigits) + ":".rs + minutes
        } else {
            val h12 = (hours + 11f.rf) % 12f.rf + 1f.rf
            h12.toRemoteString(oneDigit) + ":".rs + minutes
        }
    }

    /**
     * Live countdown: "42m", "2h 10m" ("2h10m" when [compact]). [totalMinutes] is the distance from capture to the
     * end of the block, [capturedUtcMinute] the UTC minute of day at capture. Clamps at "0m" if the widget was not
     * refreshed. See [countdownWidest] for capture-time measuring.
     */
    fun countdown(totalMinutes: Int, capturedUtcMinute: Int, compact: Boolean = false): RemoteString {
        val raw = totalMinutes.toFloat().rf - HostTime.minutesSince(capturedUtcMinute)
        val left = selectIfLt(raw, 0f.rf, 0f.rf, raw)
        val hours = floor(left / 60f.rf).toRemoteString(oneDigit)
        val minutes = floor(left % 60f.rf).toRemoteString(oneDigit)
        val hoursSuffix = if (compact) "h" else "h "
        return selectIfLt(left, 60f.rf, minutes + "m".rs, hours + hoursSuffix.rs + minutes + "m".rs)
    }

    /**
     * The widest text [countdown] can show until the next refresh: hours only go down, minutes may take two digits
     * ("2h 1m" becomes "1h 59m"). Used to size the countdown at capture time.
     */
    fun countdownWidest(totalMinutes: Int, compact: Boolean = false): String {
        val hours = totalMinutes.coerceAtLeast(0) / 60
        return if (hours == 0) "59m" else "${hours}h${if (compact) "" else " "}59m"
    }

    /**
     * The same live countdown, spoken: "1 hour 10 minutes left", "45 minutes left". Words, so a screen reader doesn't
     * have to guess what "1h 10m" means. Same clock and clamping as [countdown].
     */
    fun countdownSpoken(totalMinutes: Int, capturedUtcMinute: Int, words: CountdownWords): RemoteString {
        val raw = totalMinutes.toFloat().rf - HostTime.minutesSince(capturedUtcMinute)
        val left = selectIfLt(raw, 0f.rf, 0f.rf, raw)
        val hours = floor(left / 60f.rf)
        val minutes = floor(left % 60f.rf)
        // Singular for exactly one: below 1 (zero) and from 2 on it is plural.
        fun unit(value: RemoteFloat, one: String, many: String): RemoteString =
            selectIfLt(value, 1f.rf, " $many".rs, selectIfLt(value, 2f.rf, " $one".rs, " $many".rs))
        val minutePart = minutes.toRemoteString(oneDigit) + unit(minutes, words.minute, words.minutes)
        val hourPart = hours.toRemoteString(oneDigit) + unit(hours, words.hour, words.hours) + " ".rs
        val duration = selectIfLt(left, 60f.rf, minutePart, hourPart + minutePart)
        val prefixed = if (words.leftPrefix.isEmpty()) duration else words.leftPrefix.rs + duration
        return if (words.leftSuffix.isEmpty()) prefixed else prefixed + words.leftSuffix.rs
    }
}

/** Picks the document profile the *platform* player supports (`RemoteViews.DrawInstructions.getSupportedVersion()`). */
internal fun widgetProfileFor(supportedVersion: Int): Profile? = when {
    supportedVersion >= 7 -> WidgetProfiles.V7
    supportedVersion >= 6 -> WidgetProfiles.V6
    else -> null
}

/**
 * `RcPlatformProfiles.WIDGETS_V6/V7` with one fix: in alpha20 their writer factories drop the `writerCallback`
 * (`new RemoteComposeWriterAndroid(info, null, profile)`), so `captureSingleRemoteDocument`'s `WriterEvents` never
 * reaches the document and every `pendingIntentAction` click throws "a WriterEvents is required". These are
 * otherwise identical copies (same API level, operation profile, platform services and writer behaviour) that
 * forward the callback. Drop them once the upstream profiles pass it through.
 */
internal object WidgetProfiles {
    val V7: Profile = Profile(7, RcProfiles.PROFILE_WIDGETS, AndroidxRcPlatformServices()) { info, profile, callback ->
        RemoteComposeWriterAndroid(info, null, profile, callback)
    }

    val V6: Profile = Profile(6, 0, AndroidxRcPlatformServices()) { info, profile, callback ->
        WidgetsV6Writer(info, profile, callback)
    }

    /** Mirrors `WidgetsProfileWriterV6` (expression op ceiling + raw root content behaviour), plus the callback. */
    private class WidgetsV6Writer(info: CreationDisplayInfo, profile: Profile, callback: Any?) :
        RemoteComposeWriterAndroid(info, null, profile, callback) {
        init {
            mMaxValidFloatExpressionOperation = AnimatedFloatExpression.getMaxOpForLevel(profile.apiLevel)
        }

        override fun setRootContentBehavior(scroll: Int, alignment: Int, sizing: Int, mode: Int) {
            mBuffer.setRootContentBehavior(scroll, alignment, sizing, mode)
        }
    }
}

/**
 * A click [Action] that the *platform* widget player turns into a `RemoteViews` click: an id host action
 * (`HostActionOperation`). On API 36/37 `RemoteViews` forwards `runAction(id, metadata)` to the
 * `setOnClickPendingIntent(id, …)` response with the same id (metadata lands in the fill-in intent extra
 * `remotecompose_metadata`). The public `pendingIntentAction()` writes a *named* "SendPendingIntent" action instead,
 * which the platform player hands to its named-action handler and drops, so taps did nothing on device.
 *
 * alpha20 only exposes id host actions through the Kotlin-`internal` `HostAction(id, name, value)` constructor, so
 * this goes through reflection (kept by `consumer-rules.pro`). Drop it once an id action is public.
 */
internal fun idHostAction(id: Int, metadata: String): Action {
    require(id != 0) { "id 0 makes HostAction write a named action" }
    return IdHostAction.constructor.newInstance(id, "open".rs, metadata.rs) as Action
}

private object IdHostAction {
    val constructor: Constructor<*> by lazy {
        Class.forName("androidx.compose.remote.creation.compose.action.HostAction")
            .getConstructor(Int::class.javaPrimitiveType, RemoteString::class.java, RemoteString::class.java)
    }
}
