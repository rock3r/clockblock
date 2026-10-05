package dev.sebastiano.clockblocker.opus.feature.onboarding.profile

import dev.sebastiano.clockblocker.opus.core.model.SleepWindow
import java.time.LocalTime
import kotlin.math.abs
import kotlin.math.roundToInt

/** Which part of the sleep arc a gesture moves. */
enum class SleepHandle {
    /** The moon handle: bedtime moves, wake stays put. */
    Bedtime,

    /** The sun handle: wake moves, bedtime stays put. */
    Wake,

    /** The arc itself: the whole window slides, duration unchanged. */
    Both,
}

/**
 * Pure maths behind [SleepDial]: snapping, clamping, hit-testing and the 24.2 easter egg trigger. All values are
 * minutes of the day; the dial maps them to angles with `DialGeometry` (noon at the top, midnight at the bottom).
 */
object SleepDialMath {
    const val MinutesPerDay: Int = 1440

    /** Values snap to 5 minutes while dragging. */
    const val SnapMinutes: Int = 5

    /** Haptic tick and keyboard/TalkBack step. */
    const val StepMinutes: Int = 15

    const val MinDurationMinutes: Int = 60

    /** A full turn minus one snap: the longest a window can be (23 h 55 m). */
    const val MaxDurationMinutes: Int = MinutesPerDay - SnapMinutes

    /** Dragging the wake handle this far past [MaxDurationMinutes] peeks "24:12" (design.md §2.7 #4). */
    const val EggOvershootMinutes: Float = 40f

    /** The body's day (Czeisler 1999): 24.2 h = 24 h 12 m. */
    const val BodyDayMinutes: Int = 24 * 60 + 12

    fun minuteOf(time: LocalTime): Int = time.hour * 60 + time.minute

    fun timeOf(minute: Int): LocalTime = LocalTime.of(minute.mod(MinutesPerDay) / 60, minute.mod(MinutesPerDay) % 60)

    fun snap(minute: Float): Int = ((minute / SnapMinutes).roundToInt() * SnapMinutes).mod(MinutesPerDay)

    /** Window duration in minutes, (0, 1440]. */
    fun durationMinutes(window: SleepWindow): Int = window.duration.toMinutes().toInt()

    /** Moves one value of [window] by [deltaMinutes] (TalkBack/keyboard), respecting the duration limits. */
    fun nudge(window: SleepWindow, handle: SleepHandle, deltaMinutes: Int): SleepWindow {
        val drag = SleepDrag(handle, window)
        drag.moveBy(deltaMinutes.toFloat())
        return drag.window
    }

    /** True when going from [old] to [new] crossed a [StepMinutes] boundary (haptic tick). */
    fun crossedStep(old: Int, new: Int): Boolean = old / StepMinutes != new / StepMinutes

    /** Circular distance between two minutes of the day, [0, 720]. */
    fun distance(a: Float, b: Float): Float {
        val d = abs(a - b).mod(MinutesPerDay.toFloat())
        return if (d > MinutesPerDay / 2f) MinutesPerDay - d else d
    }

    /** True when [minute] lies on the arc from bedtime clockwise to wake. */
    fun isOnArc(minute: Float, window: SleepWindow): Boolean {
        val from = (minute - minuteOf(window.bedtime)).mod(MinutesPerDay.toFloat())
        return from <= durationMinutes(window)
    }

    /**
     * Which handle a touch at [touchMinute] grabs: the nearer handle within [toleranceMinutes], else the arc
     * (whole window) if the touch is on it, else nothing.
     */
    fun pick(touchMinute: Float, window: SleepWindow, toleranceMinutes: Float): SleepHandle? {
        val toBed = distance(touchMinute, minuteOf(window.bedtime).toFloat())
        val toWake = distance(touchMinute, minuteOf(window.wake).toFloat())
        return when {
            toBed <= toleranceMinutes || toWake <= toleranceMinutes -> if (toWake < toBed) SleepHandle.Wake else SleepHandle.Bedtime
            isOnArc(touchMinute, window) -> SleepHandle.Both
            else -> null
        }
    }
}

/**
 * One drag gesture. Accumulates unwrapped minutes so the clamp at [SleepDialMath.MaxDurationMinutes] behaves like
 * a wall: dragging further builds up [overshootMinutes] (drawn as rubber-band resistance) instead of wrapping.
 */
class SleepDrag(val handle: SleepHandle, start: SleepWindow) {
    private var bed: Float = SleepDialMath.minuteOf(start.bedtime).toFloat()
    private val fixedWake: Float = bed + SleepDialMath.durationMinutes(start)
    private var rawDuration: Float = SleepDialMath.durationMinutes(start).toFloat()

    /** How far past the longest window the user has pushed (only the wake handle builds this up). */
    var overshootMinutes: Float = 0f
        private set

    /** The current window, snapped. */
    var window: SleepWindow = start
        private set

    /** True once [overshootMinutes] reached [SleepDialMath.EggOvershootMinutes] during this gesture. */
    val eggTriggered: Boolean get() = eggFired
    private var eggFired = false

    fun moveBy(deltaMinutes: Float): SleepWindow {
        if (!deltaMinutes.isFinite()) return window
        val min = SleepDialMath.MinDurationMinutes.toFloat()
        val max = SleepDialMath.MaxDurationMinutes.toFloat()
        when (handle) {
            SleepHandle.Wake -> {
                rawDuration += deltaMinutes
                overshootMinutes = (rawDuration - max).coerceAtLeast(0f)
                if (overshootMinutes >= SleepDialMath.EggOvershootMinutes) eggFired = true
            }
            SleepHandle.Bedtime -> {
                rawDuration -= deltaMinutes
                bed = fixedWake - rawDuration.coerceIn(min, max)
            }
            SleepHandle.Both -> bed += deltaMinutes
        }
        val duration = rawDuration.coerceIn(min, max)
        val bedSnapped = SleepDialMath.snap(bed)
        val durationSnapped = SleepDialMath.snap(duration).let { if (it == 0) SleepDialMath.MinutesPerDay else it }
            .coerceIn(SleepDialMath.MinDurationMinutes, SleepDialMath.MaxDurationMinutes)
        window = SleepWindow(
            bedtime = SleepDialMath.timeOf(bedSnapped),
            wake = SleepDialMath.timeOf(bedSnapped + durationSnapped),
        )
        return window
    }
}
