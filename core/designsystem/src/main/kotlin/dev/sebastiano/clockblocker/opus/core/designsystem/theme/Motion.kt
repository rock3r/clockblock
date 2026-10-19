package dev.sebastiano.clockblocker.opus.core.designsystem.theme

import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.DecayAnimationSpec
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.InfiniteRepeatableSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.exponentialDecay
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

    /**
     * A time readout's digits rolling to a new value (the sleep dial's bedtime and wake pills). Data, so no bounce, but
     * on the slow spatial spring: on [dataSpatial] a one-digit roll was over in about 125 ms on a 120 Hz phone, too
     * quick to read as a change. Snaps when motion is reduced.
     */
    fun <T> timeMorph(): FiniteAnimationSpec<T> = dataScheme.slowSpatialSpec()

    /**
     * A camera move: the plan's dial ↔ world zoom settling after a pinch or a back gesture, a globe zooming or
     * re-centring. Standard (no bounce: a wobbling camera reads as the world wobbling); snaps when motion is reduced.
     */
    fun <T> zoomSpatial(): FiniteAnimationSpec<T> = dataScheme.defaultSpatialSpec()

    /**
     * A flung globe coasting to a stop: an exponential decay (no spring, so it never bounces back). Callers skip the
     * coast when [reduceMotion] (the globe stays where the finger left it).
     */
    fun <T> globeFling(): DecayAnimationSpec<T> = exponentialDecay(frictionMultiplier = GlobeFlingFriction)

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
     * One spin of the chronotype spectacle's shape (a rare, once-per-onboarding moment): a morph between two
     * `MaterialShapes` while it turns, on a duration, not a spring, so each spin reads (a spring covers most of
     * its travel in its first ~150 ms). Standard easing; ×1.5 under Calm; snaps when reduced (callers skip the
     * spectacle then anyway).
     */
    fun spectacleSpin(): FiniteAnimationSpec<Float> = if (reduceMotion) snap() else
        tween((SpectacleSpinMillis * navigationTimeScale).toInt(), easing = FastOutSlowInEasing)

    /** The spectacle's shape rising from the sleep dial to the card, eased once across both spins (standard easing). */
    fun spectacleRise(): FiniteAnimationSpec<Float> = if (reduceMotion) snap() else
        tween((SpectacleSpins * SpectacleSpinMillis * navigationTimeScale).toInt(), easing = FastOutSlowInEasing)

    /**
     * The spectacle's shape popping into the card: a deliberate Expressive overshoot by day (damping
     * [SpectaclePopDamping]: about 16 % of its travel past the card, then back); no bounce under Calm.
     */
    fun spectaclePop(): FiniteAnimationSpec<Float> = when {
        reduceMotion -> snap()
        isCalm -> CalmMotionScheme.defaultSpatialSpec()
        else -> spring(SpectaclePopDamping, SpectaclePopStiffness)
    }

    /**
     * The chronotype reveal's landscape entering layer by layer (after the spectacle, or on its own): on a duration so
     * the late layers get their time too. ×1.5 under Calm; snaps when reduced.
     */
    fun revealLayers(): FiniteAnimationSpec<Float> =
        if (reduceMotion) snap() else tween((RevealLayersMillis * navigationTimeScale).toInt(), easing = FastOutSlowInEasing)

    /**
     * The reveal's words staggering in: one linear clock, each line easing in over its own share of it on
     * [StaggerEasing]. ×1.5 under Calm; snaps when reduced.
     */
    fun revealWords(): FiniteAnimationSpec<Float> =
        if (reduceMotion) snap() else tween((RevealWordsMillis * navigationTimeScale).toInt(), easing = LinearEasing)

    /**
     * Chrome dimming out of the way of a rare moment, and back (the onboarding bars under the chronotype spectacle):
     * a [ChromeDimMillis] fade each way, on standard easing. ×1.5 under Calm.
     */
    fun <T> chromeDim(): FiniteAnimationSpec<T> =
        tween((ChromeDimMillis * navigationTimeScale).toInt(), easing = FastOutSlowInEasing)

    /**
     * The sun and moon swapping in a chronotype illustration (a rare pick): one linear [BodySwapMillis] clock that
     * the cross-fade, pop and wobble each shape their own way. ×1.5 under Calm; snaps when reduced (a plain swap).
     */
    fun bodySwap(): FiniteAnimationSpec<Float> =
        if (reduceMotion) snap() else tween((BodySwapMillis * navigationTimeScale).toInt(), easing = LinearEasing)

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

    /**
     * Shared-axis slide and button morph on a seekable onboarding step transition: spans the exact
     * [StepFadeOutMillis] + [StepFadeInMillis] fade-through window (no spring settle tail stretching the
     * predictive-back seek range), and snaps when motion is reduced.
     */
    fun <T> stepSpatial(): FiniteAnimationSpec<T> =
        if (reduceMotion) snap() else tween(((StepFadeOutMillis + StepFadeInMillis) * navigationTimeScale).toInt(), easing = StepSpatialEasing)

    /** Outgoing step (or chrome) in a seekable onboarding step transition: gone before [stepFadeIn] starts. */
    fun <T> stepFadeOut(): FiniteAnimationSpec<T> =
        tween((StepFadeOutMillis * navigationTimeScale).toInt(), easing = EmphasizedAccelerate)

    /**
     * Incoming step (or staggered chrome item [index]) in a seekable onboarding step transition: starts once
     * [stepFadeOut] has finished, each subsequent item delayed by [NavigationStaggerMillis]. [lagMillis] holds the
     * whole group back further, so one group of chrome reads as a beat after another (see [StepTopBarLagMillis]).
     */
    fun <T> stepFadeIn(index: Int = 0, lagMillis: Int = 0): FiniteAnimationSpec<T> = tween(
        durationMillis = (StepFadeInMillis * navigationTimeScale).toInt(),
        delayMillis = ((StepFadeOutMillis + lagMillis + index * NavigationStaggerMillis) * navigationTimeScale).toInt(),
        easing = StepFadeInEasing,
    )

    /** Night-safe / body-night surfaces are ~1.5× slower (see [CalmMotionScheme]). */
    private val navigationTimeScale: Float get() = if (isCalm) 1.5f else 1f

    /**
     * Chrome cross-fading across a whole seekable onboarding step transition (Skip leaving on the last step, the
     * runway plane fading out on Back): spans the same [StepFadeOutMillis] + [StepFadeInMillis] window as
     * [stepSpatial], so it never outlasts the step, but still fades when motion is reduced.
     */
    fun <T> stepCrossFade(): FiniteAnimationSpec<T> =
        tween(((StepFadeOutMillis + StepFadeInMillis) * navigationTimeScale).toInt(), easing = FastOutSlowInEasing)

    /**
     * The little plane taxiing along the onboarding progress line to its holding point on the last step: slowing to
     * a stop (emphasized decelerate) over [TaxiMillis]. ×1.5 under Calm; snaps when reduced.
     */
    fun taxi(): FiniteAnimationSpec<Float> =
        if (reduceMotion) snap() else tween((TaxiMillis * navigationTimeScale).toInt(), easing = EmphasizedDecelerate)

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

        /** How quickly a flung globe slows down (1 = Compose's default friction; higher stops sooner). */
        const val GlobeFlingFriction = 2.5f

        /** Material fade-through: the outgoing screen fades in 90 ms, then the incoming one in 210 ms. */
        const val NavigationFadeOutMillis = 90
        const val NavigationFadeInMillis = 210

        /** Seekable onboarding step fade-through: 60 ms out + 240 ms in (300 ms total), with 30 ms top-bar stagger. */
        const val StepFadeOutMillis = 60
        const val StepFadeInMillis = 240
        const val NavigationStaggerMillis = 30

        /**
         * Leaving Welcome, the onboarding top bar (step label, progress, Skip) drops in this much after the bottom
         * bar's Back / Next, so the two read as distinct beats. Returning to Welcome there is no lag.
         */
        const val StepTopBarLagMillis = 180

        /** One of the chronotype spectacle's [SpectacleSpins] spins (the rise spans them all). */
        const val SpectacleSpinMillis = 560
        const val SpectacleSpins = 2

        /** The spectacle's pop: damping 0.5 overshoots by e^(−πζ/√(1−ζ²)) ≈ 16 % of the travel. */
        const val SpectaclePopDamping = 0.5f
        const val SpectaclePopStiffness = 420f

        /** The chronotype reveal's layered landscape entrance, and its staggered words. */
        const val RevealLayersMillis = 1_100
        const val RevealWordsMillis = 800

        /** Chrome dimming for a rare moment, each way (see [chromeDim]). */
        const val ChromeDimMillis = 300

        /** The sun ⇄ moon swap in a chronotype illustration (see [bodySwap]). */
        const val BodySwapMillis = 600

        /** The runway plane's taxi to its holding point (see [taxi]). */
        const val TaxiMillis = 900

        private val EmphasizedAccelerate = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)
        private val EmphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
        private val StepFadeInEasing = CubicBezierEasing(0.3f, 0.2f, 0.7f, 0.95f)
        private val StepSpatialEasing = CubicBezierEasing(0.4f, 0f, 0.6f, 1f)

        /** Each staggered line's own ease in (emphasized decelerate). */
        val StaggerEasing: Easing = EmphasizedDecelerate
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
