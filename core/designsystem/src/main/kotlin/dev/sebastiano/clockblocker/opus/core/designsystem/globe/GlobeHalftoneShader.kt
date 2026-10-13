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

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RuntimeShader
import android.graphics.Shader
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import java.nio.ByteBuffer

/**
 * The globe's halftone (sea dots, land dots, city lights) drawn by two AGSL shaders, one draw each: per pixel, the
 * inverse orthographic projection finds the point on the sphere, [HalftoneGrid]'s lookup finds the nearest dot, and
 * the dot's coverage comes from the distance in the tangent plane at the dot. A dot facing the viewer is a disc; one
 * towards the limb is squashed into an ellipse and fades, continuously, as a printed dot on a real globe would.
 * Nothing per dot happens on the CPU: a turning globe only updates a handful of uniforms.
 *
 * Two layers, because the back of the route is drawn between them (over the sea, under the land).
 *
 * One per globe on screen: the uniforms are the globe's own. Set and drawn in the draw phase; no allocation per frame.
 */
internal class GlobeHalftoneShader {
    private val sea = RuntimeShader(SeaSource)
    private val land = RuntimeShader(LandSource)
    private val seaPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = sea }
    private val landPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = land }
    private val shaders = arrayOf(sea, land)
    private var colors: GlobeColors? = null
    private var landMask: Bitmap? = null

    init {
        sea.setInputBuffer("seaCounts", buffer(SeaCounts))
    }

    /**
     * The view, for both layers: the globe at ([cx], [cy]) with [radius] px, seen from the latitude and longitude
     * given by their sines and cosines, the sun over the subsolar unit vector ([sunX], [sunY], [sunZ]).
     */
    @Suppress("LongParameterList")
    fun update(
        colors: GlobeColors,
        cx: Float,
        cy: Float,
        radius: Float,
        sinPhi0: Float,
        cosPhi0: Float,
        sinLambda0: Float,
        cosLambda0: Float,
        sunX: Float,
        sunY: Float,
        sunZ: Float,
    ) {
        if (colors != this.colors) {
            sea.setColorUniform("seaColor", opaque(colors.sea))
            land.setColorUniform("landColor", opaque(colors.land))
            land.setColorUniform("cityColor", opaque(colors.cityLight))
            this.colors = colors
        }
        for (shader in shaders) {
            shader.setFloatUniform("center", cx, cy)
            shader.setFloatUniform("radius", radius)
            // The view's axes on the sphere: screen right (east at the view centre), screen up, towards the viewer.
            shader.setFloatUniform("viewEast", -sinLambda0, cosLambda0, 0f)
            shader.setFloatUniform("viewNorth", -sinPhi0 * cosLambda0, -sinPhi0 * sinLambda0, cosPhi0)
            shader.setFloatUniform("viewOut", cosPhi0 * cosLambda0, cosPhi0 * sinLambda0, sinPhi0)
            shader.setFloatUniform("sun", sunX, sunY, sunZ)
        }
    }

    fun drawSea(canvas: Canvas, cx: Float, cy: Float, radius: Float) {
        canvas.drawCircle(cx, cy, radius, seaPaint)
    }

    fun drawLand(canvas: Canvas, cx: Float, cy: Float, radius: Float, mask: Bitmap) {
        if (mask !== landMask) {
            land.setInputBuffer("landMask", buffer(mask))
            landMask = mask
        }
        canvas.drawCircle(cx, cy, radius, landPaint)
    }

    companion object {
        /** Builds the land mask bitmap the land layer samples, from [HalftoneGrid.bakeLandMask]. */
        fun landMask(latitudes: ShortArray, longitudes: ShortArray): Bitmap {
            val bitmap = Bitmap.createBitmap(HalftoneGrid.LandColumns + 1, HalftoneGrid.Land.rows, Bitmap.Config.ALPHA_8)
            bitmap.copyPixelsFromBuffer(ByteBuffer.wrap(HalftoneGrid.bakeLandMask(latitudes, longitudes, bitmap.rowBytes)))
            return bitmap
        }

        /** The sea's dots per row, one texel each. */
        private val SeaCounts: Bitmap by lazy {
            val counts = HalftoneGrid.seaCounts()
            Bitmap.createBitmap(counts.size, 1, Bitmap.Config.ALPHA_8).apply {
                val bytes = ByteArray(rowBytes)
                counts.copyInto(bytes)
                copyPixelsFromBuffer(ByteBuffer.wrap(bytes))
            }
        }

        /** A bitmap read as raw data (no filtering, no colour conversion): the shader reads texel centres. */
        private fun buffer(bitmap: Bitmap) = BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)

        private fun opaque(color: Color): Int = color.copy(alpha = 1f).toArgb()
    }
}

// region AGSL

private val SeaGrid = HalftoneGrid.Sea
private val LandGrid = HalftoneGrid.Land

/**
 * What both layers share: the view uniforms, the inverse projection and a dot's coverage.
 *
 * Dot sizes are radii on the unit sphere and match the drawPoints dots this replaced at the view centre: land dots
 * were radius / 60 px across, sea dots radius / 105, city lights three quarters of a land dot.
 */
private val Common = """
uniform float2 center;   // the globe's centre, px
uniform float radius;    // the globe's radius, px (zoom included)
uniform float3 viewEast; // the view's axes on the unit sphere: screen right, screen up, towards the viewer
uniform float3 viewNorth;
uniform float3 viewOut;
uniform float3 sun;      // the subsolar point, as a unit vector

const float SIN_SIX_DEGREES = 0.104528;
const float LAND_DOT = ${1.0 / 120};
const float SEA_DOT = ${1.0 / 210};
const float CITY_DOT = ${0.75 / 120};

// The unit disc point (east right, north up) under a pixel.
float2 discPoint(float2 frag) {
    return float2(frag.x - center.x, center.y - frag.y) / radius;
}

// The point on the sphere under disc point s (|s| < 1): the inverse orthographic projection.
float3 spherePoint(float2 s) {
    return s.x * viewEast + s.y * viewNorth + sqrt(1.0 - dot(s, s)) * viewOut;
}

float latitudeOf(float3 p) { return degrees(asin(clamp(p.z, -1.0, 1.0))); }
float longitudeOf(float3 p) { return degrees(atan(p.y, p.x)); }

float3 unitVector(float latitude, float longitude) {
    float phi = radians(latitude);
    float lambda = radians(longitude);
    return float3(cos(phi) * cos(lambda), cos(phi) * sin(lambda), sin(phi));
}

// HalftoneGrid.row and .cell: the nearest row, then the nearest of a row's count dots by longitude.
float nearestRow(float latitude, float firstLatitude, float step, float rows) {
    return clamp(floor((latitude - firstLatitude) / step + 0.5), 0.0, rows - 1.0);
}
float nearestCell(float longitude, float count) {
    return mod(floor((longitude + 180.0) * count / 360.0), count);
}
float cellLongitude(float cell, float count) { return -180.0 + (cell + 0.5) * 360.0 / count; }

// Daylight at q: 0 in full night to 1 once the sun is 6° up (sin(elevation) = q · sun, civil twilight between).
float daylight(float3 q) { return clamp((dot(q, sun) + SIN_SIX_DEGREES) / (2.0 * SIN_SIX_DEGREES), 0.0, 1.0); }

// Dots fade towards the limb (depth 0) so it reads as curving away rather than as a hard edge.
float depthFade(float depth) { return 0.4 + 0.6 * sqrt(clamp(depth, 0.0, 1.0)); }

// Dots also shrink a little towards the limb, on top of being squashed into ellipses there: without it the limb
// reads as tightly packed dashes, brighter than the face.
float depthShrink(float depth) { return 0.5 + 0.5 * sqrt(clamp(depth, 0.0, 1.0)); }

// How much of the pixel at disc point s the dot of radius size (shrunk by depth) centred on q covers, antialiased over one pixel.
// The dot is a disc on the tangent plane at q. Seen from the front, the tangent plane's radial axis (towards the
// limb) is squashed by q's depth and the other axis isn't: so the dot is an ellipse, and t below is the tangent-plane
// distance of s from q. Dividing t - r by t's screen-space gradient gives the distance to the ellipse's edge in
// pixels (AGSL has no fwidth). Ellipses thinner than a pixel are dimmed to their area instead of widened.
float dotCoverage(float2 s, float3 q, float size) {
    float depth = dot(q, viewOut);
    if (depth <= 0.0) return 0.0;
    float r = size * depthShrink(depth);
    float2 c = float2(dot(q, viewEast), dot(q, viewNorth));
    float2 v = s - c;
    float len = length(c);
    float2 n = len > 1e-5 ? c / len : float2(1.0, 0.0);
    float radial = dot(v, n);
    float tangential = dot(v, float2(-n.y, n.x));
    float t = length(float2(radial / depth, tangential));
    float gradient = length(float2(radial / (depth * depth), tangential)) / max(t, 1e-6);
    float edgePx = (t - r) / max(gradient, 1e-6) * radius;
    float cover = clamp(0.5 - edgePx, 0.0, 1.0);
    float halfMinorPx = r * depth * radius;
    return cover * min(1.0, 2.0 * halfMinorPx / (0.5 + halfMinorPx));
}
"""

/** The sea layer: every dot of the sea grid (the land layer covers them), brighter on the day side. */
private val SeaSource = Common + """
uniform shader seaCounts;               // alpha × 255 at (row + 0.5, 0.5): the dots in that sea row
layout(color) uniform half4 seaColor;

half4 main(float2 frag) {
    float2 s = discPoint(frag);
    if (dot(s, s) >= 1.0) return half4(0.0);
    float3 p = spherePoint(s);
    float row = nearestRow(latitudeOf(p), ${SeaGrid.firstLatitude}, ${SeaGrid.rowStep}, ${SeaGrid.rows}.0);
    float count = floor(seaCounts.eval(float2(row + 0.5, 0.5)).a * 255.0 + 0.5);
    float cell = nearestCell(longitudeOf(p), count);
    float3 q = unitVector(${SeaGrid.firstLatitude} + ${SeaGrid.rowStep} * row, cellLongitude(cell, count));
    float cover = dotCoverage(s, q, SEA_DOT);
    if (cover <= 0.0) return half4(0.0);
    float lit = dot(q, sun) > -SIN_SIX_DEGREES ? 0.32 : 0.08;
    float alpha = lit * depthFade(dot(q, viewOut)) * cover;
    return half4(seaColor.rgb * alpha, alpha);
}
"""

/**
 * The land layer: land dots in six brightness levels (daylight, plus a little for facing the viewer), and on the night
 * side a sparse pick of them lit as cities.
 */
private val LandSource = Common + """
// alpha at (cell + 0.5, row + 0.5): 0 sea, 0.5 land, 1 land that lights up at night; at (${HalftoneGrid.LandColumns}.5, row + 0.5),
// alpha × 255 is the row's dot count.
uniform shader landMask;
layout(color) uniform half4 landColor;
layout(color) uniform half4 cityColor;

half4 main(float2 frag) {
    float2 s = discPoint(frag);
    if (dot(s, s) >= 1.0) return half4(0.0);
    float3 p = spherePoint(s);
    float row = nearestRow(latitudeOf(p), ${LandGrid.firstLatitude}, ${LandGrid.rowStep}, ${LandGrid.rows}.0);
    float count = floor(landMask.eval(float2(${HalftoneGrid.LandColumns}.5, row + 0.5)).a * 255.0 + 0.5);
    float cell = nearestCell(longitudeOf(p), count);
    float kind = landMask.eval(float2(cell + 0.5, row + 0.5)).a;
    if (kind < 0.25) return half4(0.0);
    float3 q = unitVector(${LandGrid.firstLatitude} + ${LandGrid.rowStep} * row, cellLongitude(cell, count));
    float depth = dot(q, viewOut);
    float light = daylight(q);
    float fade = depthFade(depth);
    float level = clamp(floor(light * 4.0 + clamp(depth, 0.0, 1.0) * 1.2), 0.0, 5.0);
    float landAlpha = (0.14 + level * 0.17) * fade * dotCoverage(s, q, LAND_DOT);
    half4 color = half4(landColor.rgb * landAlpha, landAlpha);
    if (kind > 0.75 && light < 0.02) {
        float cityAlpha = 0.9 * fade * dotCoverage(s, q, CITY_DOT);
        color = half4(cityColor.rgb * cityAlpha, cityAlpha) + color * (1.0 - cityAlpha);
    }
    return color;
}
"""

// endregion
