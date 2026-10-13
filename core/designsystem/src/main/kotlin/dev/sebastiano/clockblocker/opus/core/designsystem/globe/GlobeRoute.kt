/*
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
 */
package dev.sebastiano.clockblocker.opus.core.designsystem.globe

import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The globe's view rotation, applied to unit vectors: three multiply-adds per point. [x] is right, [y] up, [depth]
 * towards the viewer (negative behind the globe), all on the unit disc.
 */
internal class GlobeRotation {
    var sinPhi0 = 0f
        private set
    var cosPhi0 = 1f
        private set
    var sinLambda0 = 0f
        private set
    var cosLambda0 = 1f
        private set

    fun update(latitude: Float, longitude: Float) {
        sinPhi0 = sin(Math.toRadians(latitude.toDouble())).toFloat()
        cosPhi0 = cos(Math.toRadians(latitude.toDouble())).toFloat()
        sinLambda0 = sin(Math.toRadians(longitude.toDouble())).toFloat()
        cosLambda0 = cos(Math.toRadians(longitude.toDouble())).toFloat()
    }

    fun x(px: Float, py: Float): Float = -px * sinLambda0 + py * cosLambda0
    fun y(px: Float, py: Float, pz: Float): Float = cosPhi0 * pz - sinPhi0 * (px * cosLambda0 + py * sinLambda0)
    fun depth(px: Float, py: Float, pz: Float): Float = sinPhi0 * pz + cosPhi0 * (px * cosLambda0 + py * sinLambda0)
}

/** Where [traceRoute] draws: the front (visible face) and back (see-through) parts of one leg. */
internal interface RouteSink {
    fun moveTo(front: Boolean, x: Float, y: Float)
    fun lineTo(front: Boolean, x: Float, y: Float)
}

/** 0 below [from], 1 above [to], smooth in between (the GLSL smoothstep). */
internal fun smoothstep(from: Float, to: Float, value: Float): Float {
    val t = ((value - from) / (to - from)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

/**
 * Traces a leg's great circle ([vectors], lifted by [lift] mid-flight) into [sink], for a globe of [radius] px at
 * ([cx], [cy]) seen through [rotation]: the front part (lifted) where it faces the viewer, the back part (on the
 * surface) where it doesn't.
 *
 * The two parts meet exactly: the lift eases to nothing as the arc nears the horizon ([LiftEaseDepth]), and where the
 * leg crosses it, between two samples, both parts end at the same interpolated point on the limb. No gap, no overlap,
 * no step between a raised front and a flat back.
 */
@Suppress("LongParameterList")
internal fun traceRoute(
    vectors: UnitVectors,
    lift: FloatArray,
    rotation: GlobeRotation,
    cx: Float,
    cy: Float,
    radius: Float,
    sink: RouteSink,
) {
    var prevFront = false
    var prevDepth = 0f
    var prevX = 0f
    var prevY = 0f
    for (i in 0 until vectors.size) {
        val px = vectors.x[i]
        val py = vectors.y[i]
        val pz = vectors.z[i]
        val depth = rotation.depth(px, py, pz)
        val x = rotation.x(px, py)
        val y = rotation.y(px, py, pz)
        val front = depth >= 0f
        if (i > 0 && front != prevFront) {
            // The crossing: between the two samples where the depth is 0, pushed onto the limb (the chord between two
            // samples dips inside the circle).
            val t = prevDepth / (prevDepth - depth)
            var lx = prevX + (x - prevX) * t
            var ly = prevY + (y - prevY) * t
            val length = sqrt(lx * lx + ly * ly)
            if (length > 0f) {
                lx /= length
                ly /= length
            }
            sink.lineTo(prevFront, cx + lx * radius, cy - ly * radius)
            sink.moveTo(front, cx + lx * radius, cy - ly * radius)
        }
        val scale = if (front) 1f + (lift[i] - 1f) * smoothstep(0f, LiftEaseDepth, depth) else 1f
        val ox = cx + x * scale * radius
        val oy = cy - y * scale * radius
        if (i == 0) sink.moveTo(front, ox, oy) else sink.lineTo(front, ox, oy)
        prevFront = front
        prevDepth = depth
        prevX = x
        prevY = y
    }
}

/** How close to the horizon (in depth) an arc's lift starts easing to nothing. */
internal const val LiftEaseDepth = 0.25f

/**
 * The day side's glow, by the subsolar point's depth [sunDepth]: full while the sun faces the viewer, fading out as it
 * goes round the horizon (no pop when it crosses it).
 */
internal fun glowAlpha(sunDepth: Float): Float = 0.95f * smoothstep(-0.35f, 0.35f, sunDepth)

/**
 * How far from the centre (a fraction of the radius, before the glow's own 0.85 inset) the glow sits towards the
 * subsolar point, whose disc position is ([sunX], [sunY]) at depth [sunDepth]: where the sun is, while it faces the
 * viewer; held on the limb once it's behind, rather than sliding back to the middle of the night side.
 */
internal fun glowReach(sunX: Float, sunY: Float, sunDepth: Float): Float {
    if (sunDepth >= 0f) return 1f
    val length = sqrt(sunX * sunX + sunY * sunY)
    return if (length > 1e-4f) 1f / length else 1f
}
