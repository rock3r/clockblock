package dev.sebastiano.clockblocker.opus.core.designsystem.theme

import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.InfiniteRepeatableSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.material3.MotionScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext

/*
 * Token definitions. This file (and only this file) may contain literal tween()/spring() values;
 * every call site resolves to `MaterialTheme.motionScheme` or to [ClockblockMotion]. See MOTION.md.
 */

/**
 * "Syrupy by night": no overshoot (damping 1.0) and ~1.5× slower than Expressive (stiffness ÷ 2.25,
 * settling time scales with 1/√stiffness). Used by Night-safe surfaces.
 */
object CalmMotionScheme : MotionScheme {
    override fun <T> fastSpatialSpec(): FiniteAnimationSpec<T> = spring(Spring.DampingRatioNoBouncy, 355f)
    override fun <T> defaultSpatialSpec(): FiniteAnimationSpec<T> = spring(Spring.DampingRatioNoBouncy, 170f)
    override fun <T> slowSpatialSpec(): FiniteAnimationSpec<T> = spring(Spring.DampingRatioNoBouncy, 90f)
    override fun <T> fastEffectsSpec(): FiniteAnimationSpec<T> = spring(Spring.DampingRatioNoBouncy, 1690f)
    override fun <T> defaultEffectsSpec(): FiniteAnimationSpec<T> = spring(Spring.DampingRatioNoBouncy, 711f)
    override fun <T> slowEffectsSpec(): FiniteAnimationSpec<T> = spring(Spring.DampingRatioNoBouncy, 355f)
}

/**
 * In-app "Reduce motion": nothing travels, scales or morphs (spatial specs snap), but colour and opacity still
 * cross-fade on the calm effects springs. The system "Remove animations" setting additionally snaps everything.
 */
object StillMotionScheme : MotionScheme {
    override fun <T> fastSpatialSpec(): FiniteAnimationSpec<T> = snap()
    override fun <T> defaultSpatialSpec(): FiniteAnimationSpec<T> = snap()
    override fun <T> slowSpatialSpec(): FiniteAnimationSpec<T> = snap()
    override fun <T> fastEffectsSpec(): FiniteAnimationSpec<T> = CalmMotionScheme.fastEffectsSpec()
    override fun <T> defaultEffectsSpec(): FiniteAnimationSpec<T> = CalmMotionScheme.defaultEffectsSpec()
    override fun <T> slowEffectsSpec(): FiniteAnimationSpec<T> = CalmMotionScheme.slowEffectsSpec()
}

/**
 * Named motion intents. Bind every animation to one of these (or directly to `MaterialTheme.motionScheme`).
 *
 * [scheme] is the surface's scheme (Expressive by day, [CalmMotionScheme] at night, [StillMotionScheme] when the
 * user reduced motion). Anything whose *position is read as data* (dial rings, timeline positions, numbers) binds
 * to the Standard scheme via [dataSpatial] / [dialDayRotation] so Expressive bounce never wobbles a value.
 */
@Immutable
class ClockblockMotion internal constructor(
    val scheme: MotionScheme,
    /** True when animations are off (system scale 0) or the user asked for reduced motion. */
    val reduceMotion: Boolean,
) {
    private val dataScheme: MotionScheme = if (reduceMotion) StillMotionScheme else if (scheme === CalmMotionScheme) CalmMotionScheme else MotionScheme.standard()

    /** Advice glyph Circle → MaterialShape morph when an action becomes "now". */
    fun <T> glyphMorph(): FiniteAnimationSpec<T> = scheme.fastSpatialSpec()

    /** A card expanding into the Now card; containers around data. */
    fun <T> containerSpatial(): FiniteAnimationSpec<T> = scheme.defaultSpatialSpec()

    /** Colour and opacity changes (never bounce). */
    fun <T> colour(): FiniteAnimationSpec<T> = scheme.defaultEffectsSpec()

    /** Quick fades for small in-place swaps. */
    fun <T> fade(): FiniteAnimationSpec<T> = scheme.fastEffectsSpec()

    /** Elements read as data: settle without overshoot. */
    fun <T> dataSpatial(): FiniteAnimationSpec<T> = dataScheme.defaultSpatialSpec()

    /** Inner body ring rotating towards alignment on a day change. Slow, Standard (near-critically damped). */
    fun <T> dialDayRotation(): FiniteAnimationSpec<T> = dataScheme.slowSpatialSpec()

    /**
     * The Rewind easter egg springing home from a full turn back: a deliberate, rare Expressive overshoot by day;
     * no bounce under the calm scheme (Night-safe or body night).
     */
    fun <T> rewindReturn(): FiniteAnimationSpec<T> = when {
        reduceMotion -> snap()
        scheme === CalmMotionScheme -> CalmMotionScheme.slowSpatialSpec()
        else -> MotionScheme.expressive().slowSpatialSpec()
    }

    /** Confetti flight: time-driven (physics are evaluated from t), linear clock. */
    fun <T> celebrationClock(): FiniteAnimationSpec<T> = tween(CelebrationMillis, easing = LinearEasing)

    /** Illustration entrance / state progress (art `progress` parameters). */
    fun <T> artEntrance(): FiniteAnimationSpec<T> = scheme.slowSpatialSpec()

    /**
     * Screen-level navigation movement: push/pop slides, the fade-through scale, list-detail pane bounds.
     * Navigation is a 100+/day surface, so it binds to the Standard scheme (never bounces) and snaps when motion
     * is reduced; opacity still cross-fades via [navigationFadeIn] / [navigationFadeOut].
     */
    fun <T> navigationSpatial(): FiniteAnimationSpec<T> = dataScheme.defaultSpatialSpec()

    /**
     * Outgoing screen in a fade-through or shared-axis transition: gone within the first ~third of the
     * transition (emphasized accelerate), so two screens never double-expose.
     */
    fun <T> navigationFadeOut(): FiniteAnimationSpec<T> =
        tween((NavigationFadeOutMillis * navigationTimeScale).toInt(), easing = EmphasizedAccelerate)

    /** Incoming screen in a fade-through or shared-axis transition: starts as the outgoing one has faded. */
    fun <T> navigationFadeIn(): FiniteAnimationSpec<T> = tween(
        durationMillis = (NavigationFadeInMillis * navigationTimeScale).toInt(),
        delayMillis = (NavigationFadeOutMillis * navigationTimeScale).toInt(),
        easing = EmphasizedDecelerate,
    )

    /** Night-safe / body-night surfaces are ~1.5× slower (see [CalmMotionScheme]). */
    private val navigationTimeScale: Float get() = if (isCalm) 1.5f else 1f

    /** True under [CalmMotionScheme] (Night-safe colours or the body clock's night). */
    val isCalm: Boolean get() = scheme === CalmMotionScheme

    /**
     * Whole-theme colour change (Night-safe on/off, light/dark, Opus mode): the slow calm effects spring in every
     * scheme, so waking the screen from true black never flashes. Snaps only when animations are removed.
     */
    fun <T> themeCrossFade(): FiniteAnimationSpec<T> = CalmMotionScheme.slowEffectsSpec()

    /**
     * Ambient loop for rare/decorative surfaces; callers must skip it when [reduceMotion]. Under the calm scheme
     * the period stretches ×1.5 like everything else at night.
     */
    fun ambientLoop(periodMillis: Int, reverse: Boolean = false): InfiniteRepeatableSpec<Float> =
        infiniteRepeatable(
            tween((periodMillis * navigationTimeScale).toInt(), easing = LinearEasing),
            if (reverse) RepeatMode.Reverse else RepeatMode.Restart,
        )

    /** One pass of an ambient cycle (frequent surfaces play once on change, then rest). Calm-scaled. */
    fun ambientOnce(periodMillis: Int): FiniteAnimationSpec<Float> =
        tween((periodMillis * navigationTimeScale).toInt(), easing = LinearEasing)

    /**
     * A rare easter-egg morph chain (the header moon's phases): one continuous progress across [steps] shapes,
     * ~[EggStepMillis] each, eased once over the whole chain rather than a spring per step. Snaps when reduced.
     */
    fun eggChain(steps: Int): FiniteAnimationSpec<Float> =
        if (reduceMotion) snap() else tween((steps * EggStepMillis * navigationTimeScale).toInt(), easing = FastOutSlowInEasing)

    companion object {
        /** VerySunny glyph: one 45° step of its slow rotation. */
        const val SunRotationMillis = 9_000
        const val TwinkleMillis = 1_800
        const val PulseMillis = 2_400
        const val SteamMillis = 3_200
        const val SwayMillis = 4_200
        const val DriftMillis = 3_600
        const val CelebrationMillis = 2_200

        /** One shape of the moon egg's phase chain. */
        const val EggStepMillis = 320

        /** Material fade-through: the outgoing screen fades in 90 ms, then the incoming one in 210 ms. */
        const val NavigationFadeOutMillis = 90
        const val NavigationFadeInMillis = 210

        private val EmphasizedAccelerate = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)
        private val EmphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
    }
}

/**
 * `true` when motion must degrade to static end states: the system "Remove animations" setting
 * (`ANIMATOR_DURATION_SCALE == 0`) or the in-app Reduce motion toggle. Provided by [ClockblockTheme].
 */
val LocalReduceMotion = staticCompositionLocalOf { false }

/** Observes `Settings.Global.ANIMATOR_DURATION_SCALE`; `false` when the user removed animations. */
@Composable
fun rememberSystemAnimationsEnabled(): Boolean {
    val context = LocalContext.current
    val resolver = context.contentResolver
    var enabled by remember { mutableStateOf(readAnimatorScale(context) != 0f) }
    DisposableEffect(resolver) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                enabled = readAnimatorScale(context) != 0f
            }
        }
        resolver.registerContentObserver(
            Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), false, observer,
        )
        onDispose { resolver.unregisterContentObserver(observer) }
    }
    return enabled
}

private fun readAnimatorScale(context: android.content.Context): Float =
    try {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE)
    } catch (_: Settings.SettingNotFoundException) {
        1f
    }
