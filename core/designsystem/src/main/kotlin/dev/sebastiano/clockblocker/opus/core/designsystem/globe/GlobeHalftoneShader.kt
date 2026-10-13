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
 * The globe's halftone (sea dots, land dots, city lights) drawn by one AGSL shader in one draw: per pixel, the inverse
 * orthographic projection finds the point on the sphere, [HalftoneGrid]'s lookup finds the nearest sea dot and the
 * nearest land dot, and each dot's coverage comes from the distance in the tangent plane at the dot. A dot facing the
 * viewer is a disc; one towards the limb is squashed into an ellipse, shrinks and fades, continuously, as a printed
 * dot on a real globe would. Nothing per dot happens on the CPU: a turning globe only updates a handful of uniforms.
 *
 * One per globe on screen: the uniforms are the globe's own. Set and drawn in the draw phase; no allocation per frame.
 */
internal class GlobeHalftoneShader {
    private val shader = RuntimeShader(HalftoneSource)
    private val paint = Paint().apply { shader = this@GlobeHalftoneShader.shader }
    private var colors: GlobeColors? = null
    private var landMask: Bitmap? = null

    init {
        shader.setInputBuffer("seaCounts", buffer(SeaCounts))
        shader.setInputBuffer("landMask", buffer(NoLand))
    }

    /**
     * Draws the halftone of the globe at ([cx], [cy]) with [radius] px, seen from the latitude and longitude given by
     * their sines and cosines, with the sun over the subsolar unit vector ([sunX], [sunY], [sunZ]); the land from
     * [landMask] (see [GlobeHalftoneShader.landMask]), or only the sea while it loads (null).
     */
    @Suppress("LongParameterList")
    fun draw(
        canvas: Canvas,
        colors: GlobeColors,
        landMask: Bitmap?,
        width: Float,
        height: Float,
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
            shader.setColorUniform("seaColor", opaque(colors.sea))
            shader.setColorUniform("landColor", opaque(colors.land))
            shader.setColorUniform("cityColor", opaque(colors.cityLight))
            this.colors = colors
        }
        if (landMask !== this.landMask) {
            shader.setInputBuffer("landMask", buffer(landMask ?: NoLand))
            this.landMask = landMask
        }
        shader.setFloatUniform("center", cx, cy)
        shader.setFloatUniform("radius", radius)
        // The view's axes on the sphere: screen right (east at the view centre), screen up, towards the viewer.
        shader.setFloatUniform("viewEast", -sinLambda0, cosLambda0, 0f)
        shader.setFloatUniform("viewNorth", -sinPhi0 * cosLambda0, -sinPhi0 * sinLambda0, cosPhi0)
        shader.setFloatUniform("viewOut", cosPhi0 * cosLambda0, cosPhi0 * sinLambda0, sinPhi0)
        shader.setFloatUniform("sun", sunX, sunY, sunZ)
        // The globe's square, within the canvas (zoomed in, the globe is bigger than it): the shader leaves the
        // corners outside the disc empty. A plain rectangle keeps the GPU pipeline the same at every zoom.
        canvas.drawRect(
            maxOf(cx - radius, 0f),
            maxOf(cy - radius, 0f),
            minOf(cx + radius, width),
            minOf(cy + radius, height),
            paint,
        )
    }

    companion object {
        /** Builds the land mask bitmap the land layer samples, from [HalftoneGrid.bakeLandMask]. */
        fun landMask(latitudes: ShortArray, longitudes: ShortArray): Bitmap {
            val bitmap = Bitmap.createBitmap(HalftoneGrid.LandColumns + 1, HalftoneGrid.Land.rows, Bitmap.Config.ALPHA_8)
            bitmap.copyPixelsFromBuffer(ByteBuffer.wrap(HalftoneGrid.bakeLandMask(latitudes, longitudes, bitmap.rowBytes)))
            return bitmap
        }

        /** No land yet (it loads off the main thread): every row has no dots. */
        private val NoLand: Bitmap by lazy { Bitmap.createBitmap(1, 1, Bitmap.Config.ALPHA_8) }

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
 * The view uniforms, the inverse projection and a dot's coverage.
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

// The unit vector at a longitude on the circle of latitude whose sine and cosine are given.
float3 unitVector(float sinLatitude, float cosLatitude, float longitude) {
    float lambda = radians(longitude);
    return float3(cosLatitude * cos(lambda), cosLatitude * sin(lambda), sinLatitude);
}

// The furthest, in degrees on the sphere, a pixel at disc point s can be from the centre of a dot of radius size it
// touches: the dot plus its antialiasing fringe (half a pixel, which spans more of the sphere towards the limb),
// doubled to be safe. Most pixels are further than this from their nearest dot in latitude or in longitude alone,
// and stop there, before the trigonometry and the coverage maths.
float reachDegrees(float2 s, float size) {
    float depth = sqrt(max(1.0 - dot(s, s), 0.0));
    return degrees(size + 1.0 / (radius * max(depth, 0.02)));
}

// Degrees between two longitudes, the short way round.
float longitudeGap(float a, float b) {
    float d = abs(a - b);
    return min(d, 360.0 - d);
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

/**
 * The halftone: every dot of the sea grid, brighter on the day side; over them land dots in six brightness levels
 * (daylight, plus a little for facing the viewer); and on the night side a sparse pick of land dots lit as cities.
 */
private val HalftoneSource = Common + """
uniform shader seaCounts;               // alpha × 255 at (row + 0.5, 0.5): the dots in that sea row
layout(color) uniform half4 seaColor;
// alpha at (cell + 0.5, row + 0.5): 0 sea, 0.5 land, 1 land that lights up at night; at (${HalftoneGrid.LandColumns}.5, row + 0.5),
// alpha × 255 is the row's dot count.
uniform shader landMask;
layout(color) uniform half4 landColor;
layout(color) uniform half4 cityColor;

// The sea dot at sphere point p (disc point s), premultiplied.
half4 seaDot(float2 s, float3 p) {
    float reach = reachDegrees(s, SEA_DOT);
    float latitude = latitudeOf(p);
    float row = nearestRow(latitude, ${SeaGrid.firstLatitude}, ${SeaGrid.rowStep}, ${SeaGrid.rows}.0);
    float rowLatitude = ${SeaGrid.firstLatitude} + ${SeaGrid.rowStep} * row;
    if (abs(latitude - rowLatitude) > reach) return half4(0.0);
    float count = floor(seaCounts.eval(float2(row + 0.5, 0.5)).a * 255.0 + 0.5);
    float longitude = longitudeOf(p);
    float dotLongitude = cellLongitude(nearestCell(longitude, count), count);
    float sinRow = sin(radians(rowLatitude));
    float cosRow = cos(radians(rowLatitude));
    if (longitudeGap(longitude, dotLongitude) * min(cosRow, length(p.xy)) > reach) return half4(0.0);
    float3 q = unitVector(sinRow, cosRow, dotLongitude);
    float cover = dotCoverage(s, q, SEA_DOT);
    if (cover <= 0.0) return half4(0.0);
    float lit = dot(q, sun) > -SIN_SIX_DEGREES ? 0.32 : 0.08;
    float alpha = lit * depthFade(dot(q, viewOut)) * cover;
    return half4(seaColor.rgb * alpha, alpha);
}

// The land dot (or city light) at sphere point p (disc point s), premultiplied.
half4 landDot(float2 s, float3 p) {
    float reach = reachDegrees(s, LAND_DOT);
    float latitude = latitudeOf(p);
    float row = nearestRow(latitude, ${LandGrid.firstLatitude}, ${LandGrid.rowStep}, ${LandGrid.rows}.0);
    float rowLatitude = ${LandGrid.firstLatitude} + ${LandGrid.rowStep} * row;
    if (abs(latitude - rowLatitude) > reach) return half4(0.0);
    float count = floor(landMask.eval(float2(${HalftoneGrid.LandColumns}.5, row + 0.5)).a * 255.0 + 0.5);
    if (count < 1.0) return half4(0.0); // no land loaded yet
    float longitude = longitudeOf(p);
    float cell = nearestCell(longitude, count);
    float kind = landMask.eval(float2(cell + 0.5, row + 0.5)).a;
    if (kind < 0.25) return half4(0.0);
    float dotLongitude = cellLongitude(cell, count);
    float sinRow = sin(radians(rowLatitude));
    float cosRow = cos(radians(rowLatitude));
    if (longitudeGap(longitude, dotLongitude) * min(cosRow, length(p.xy)) > reach) return half4(0.0);
    float3 q = unitVector(sinRow, cosRow, dotLongitude);
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

half4 main(float2 frag) {
    float2 s = discPoint(frag);
    if (dot(s, s) >= 1.0) return half4(0.0);
    float3 p = spherePoint(s);
    half4 land = landDot(s, p);
    return land + seaDot(s, p) * (1.0 - land.a);
}
"""

// endregion
