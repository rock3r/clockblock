/*
 * Copyright 2026 Chris Sinco (c5inco)
 * Copyright 2026 Sebastiano Poggi and contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * The seeded star field and the sun's radial glow are adapted from Prime Meridian's SkyCanvas
 * (https://github.com/c5inco/m3e-vibes/tree/main/apps/prime-meridian, Apache-2.0). Here they are keyed to our
 * body-clock sky (its hour and the day's real sunrise and sunset) instead of a solar elevation, the twinkle runs
 * only inside a short ambient session (and rests exactly at each star's brightness), and the glow takes the
 * theme's colours.
 */
package dev.sebastiano.clockblocker.opus.core.designsystem.component

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * One star: where it sits in its box (fractions), its [radius] in dp, its resting [brightness] (alpha) and the
 * [phase] of its twinkle. Only stars that [twinkles] ever change; the rest stay put.
 */
@Immutable
data class SkyStar(
    val x: Float,
    val y: Float,
    val radius: Float,
    val brightness: Float,
    val phase: Float,
    val twinkles: Boolean,
) {
    /**
     * Its alpha [sessionMillis] into an ambient session (negative = no session: at rest). A twinkle dips below the
     * resting brightness and comes back, slowly (one breath every [TwinklePeriodMillis] or so), and eases in and
     * out over the session's edges (its start, and its end at [sessionLengthMillis], the caller's real session length),
     * so nothing pops: at the end it is back at its resting brightness, the same as at rest.
     */
    fun alphaAt(sessionMillis: Long, sessionLengthMillis: Long): Float {
        if (!twinkles || sessionMillis < 0L) return brightness
        val envelope = min(
            (sessionMillis / TwinkleEaseMillis.toFloat()).coerceIn(0f, 1f),
            ((sessionLengthMillis - sessionMillis) / TwinkleEaseMillis.toFloat()).coerceIn(0f, 1f),
        )
        val period = TwinklePeriodMillis * (0.75f + 0.5f * (phase / (2f * PI.toFloat())))
        val wave = 0.5f + 0.5f * sin(2f * PI.toFloat() * sessionMillis / period + phase)
        return brightness * (1f - TwinkleDepth * envelope * wave)
    }

    companion object {
        /** About how long one star's breath takes (each star runs a little faster or slower). */
        const val TwinklePeriodMillis = 7_000f

        /** How far a breath dims a star (a fraction of its brightness): it fades, never quite out. */
        const val TwinkleDepth = 0.85f

        const val TwinkleEaseMillis = 3_000L
    }
}

/** A seeded, stable star field (the same [seed] always lays out the same sky). */
@Immutable
class SkyStars(val points: List<SkyStar>) {
    companion object {
        fun generate(seed: Int, count: Int): SkyStars {
            val random = Random(seed)
            return SkyStars(
                List(count) { i ->
                    SkyStar(
                        x = random.nextFloat(),
                        // Squared: more stars high up, thinning towards the horizon.
                        y = random.nextFloat().let { it * it },
                        radius = 0.6f + random.nextFloat() * 1.0f,
                        brightness = 0.35f + random.nextFloat() * 0.6f,
                        phase = random.nextFloat() * 2f * PI.toFloat(),
                        twinkles = i % 3 == 0,
                    )
                },
            )
        }
    }
}

/**
 * How visible the stars are at [hour] (body time) on a day with this sunrise and sunset: 0 by day, rising through the
 * first ~1½ h after sunset to 1 in the night, and back to 0 over the last ~1½ h before sunrise.
 */
fun starVisibility(hour: Float, sunriseHour: Float, sunsetHour: Float): Float {
    val h = hour.mod(24f)
    val daySpan = (sunsetHour - sunriseHour).mod(24f)
    val sinceSunrise = (h - sunriseHour).mod(24f)
    if (sinceSunrise <= daySpan) return 0f
    val afterSunset = (h - sunsetHour).mod(24f)
    val beforeSunrise = (sunriseHour - h).mod(24f)
    return ((min(afterSunset, beforeSunrise) - StarDelayHours) / StarRampHours).coerceIn(0f, 1f)
}

/**
 * How strong the glow around the sun is at [hour]: 1 at sunrise and sunset (the golden hours), easing to a soft
 * [NoonGlow] through the day, and fading out within [GlowAfterSetHours] of the sun going down. 0 at night.
 */
fun sunGlowStrength(hour: Float, sunriseHour: Float, sunsetHour: Float): Float {
    val h = hour.mod(24f)
    val daySpan = (sunsetHour - sunriseHour).mod(24f)
    val sinceSunrise = (h - sunriseHour).mod(24f)
    val fromEdge = min(circular(h, sunriseHour), circular(h, sunsetHour))
    val golden = (1f - fromEdge / GoldenHours).coerceIn(0f, 1f)
    return if (sinceSunrise <= daySpan) {
        maxOf(golden, NoonGlow)
    } else {
        golden * (1f - fromEdge / GlowAfterSetHours).coerceIn(0f, 1f)
    }
}

private fun circular(a: Float, b: Float): Float = abs(a - b).mod(24f).let { if (it > 12f) 24f - it else it }

private const val StarDelayHours = 0.25f
private const val StarRampHours = 1.25f
private const val GoldenHours = 2.5f
private const val NoonGlow = 0.35f
private const val GlowAfterSetHours = 0.75f

/**
 * The stars of [stars] inside [area], in [color] scaled by [visibility] (see [starVisibility]) and each star's own
 * alpha [sessionMillis] into an ambient session [sessionLengthMillis] long (negative = at rest). A few of the brighter
 * ones get a faint halo.
 */
fun DrawScope.drawSkyStars(stars: SkyStars, area: Rect, color: Color, visibility: Float, sessionMillis: Long, sessionLengthMillis: Long) {
    if (visibility <= 0f) return
    stars.points.forEachIndexed { i, star ->
        val alpha = star.alphaAt(sessionMillis, sessionLengthMillis) * visibility * color.alpha
        val centre = Offset(area.left + star.x * area.width, area.top + star.y * area.height)
        val r = star.radius * density
        drawCircle(color.copy(alpha = alpha), r, centre)
        if (i % 9 == 0) drawCircle(color.copy(alpha = alpha * 0.14f), r * 3.5f, centre)
    }
}

/** A soft radial glow of [color] round the sun at [centre], [strength] 0–1 (see [sunGlowStrength]). */
fun DrawScope.drawSunGlow(centre: Offset, radius: Float, strength: Float, color: Color) {
    if (strength <= 0f || radius <= 0f) return
    val s = strength.coerceIn(0f, 1f)
    val fade = (s / NoonGlow).coerceIn(0f, 1f)
    val r = radius * (0.6f + 0.4f * s)
    drawCircle(
        Brush.radialGradient(listOf(color.copy(alpha = color.alpha * (0.25f + 0.45f * s) * fade), Color.Transparent), centre, r),
        r,
        centre,
    )
}
