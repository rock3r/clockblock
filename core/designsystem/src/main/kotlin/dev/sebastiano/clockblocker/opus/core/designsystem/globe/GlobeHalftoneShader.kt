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
        // The disc only: zoomed in, it is clipped to the canvas anyway.
        canvas.drawCircle(cx, cy, radius, paint)
    }

    companion object {
        /** Builds the land mask bitmap the shader samples, from [HalftoneGrid.bakeLandMask]. */
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
 * The halftone: every dot of the sea grid, brighter on the day side; over them land dots in six brightness levels
 * (daylight, plus a little for facing the viewer); and on the night side a sparse pick of land dots lit as cities.
 *
 * Cost per pixel, cheapest first, as most pixels stop early: the inverse projection and a polynomial arcsine for the
 * latitude; then, only near a row of either grid, a polynomial arctangent for the longitude; then, only near a dot,
 * the dot's centre (four sines and cosines), its light and its coverage. One fetch of a row's dot count per grid, and
 * one land-mask fetch.
 *
 * Dot sizes are radii on the unit sphere and match the drawPoints dots this replaced at the view centre: land dots
 * were radius / 60 px across, sea dots radius / 105, city lights three quarters of a land dot.
 */
private val HalftoneSource = """
uniform float2 center;   // the globe's centre, px
uniform float radius;    // the globe's radius, px (zoom included)
uniform float3 viewEast; // the view's axes on the unit sphere: screen right, screen up, towards the viewer
uniform float3 viewNorth;
uniform float3 viewOut;
uniform float3 sun;      // the subsolar point, as a unit vector

uniform shader seaCounts; // alpha × 255 at (row + 0.5, 0.5): the dots in that sea row
// alpha at (cell + 0.5, row + 0.5): 0 sea, 0.5 land, 1 land that lights up at night; at (${HalftoneGrid.LandColumns}.5, row + 0.5),
// alpha × 255 is the row's dot count.
uniform shader landMask;
layout(color) uniform half4 seaColor;
layout(color) uniform half4 landColor;
layout(color) uniform half4 cityColor;

const float SIN_SIX_DEGREES = 0.104528;
const float LAND_DOT = ${1.0 / 120};
const float SEA_DOT = ${1.0 / 210};
const float CITY_DOT = ${0.75 / 120};
const float DEGREES = 57.2957795;
const float RADIANS = 0.0174532925;

// Arcsine within 7e-5 rad (Abramowitz & Stegun 4.4.45): plenty to pick a row 1.8° or 3° wide, as the coverage itself
// is measured from the dot's exact centre. The built-in asin and atan are the dearest calls in the shader.
float fastAsin(float x) {
    float a = abs(x);
    float r = 1.5707963 - sqrt(1.0 - a) * (1.5707288 + a * (-0.2121144 + a * (0.0742610 - 0.0187293 * a)));
    return x < 0.0 ? -r : r;
}

// atan2 within 1e-5 rad (a minimax polynomial on [0, 1], folded into the eight octants).
float fastAtan2(float y, float x) {
    float ax = abs(x);
    float ay = abs(y);
    float a = min(ax, ay) / max(max(ax, ay), 1e-20);
    float s = a * a;
    float r = ((-0.0464964749 * s + 0.15931422) * s - 0.327622764) * s * a + a;
    if (ay > ax) r = 1.5707963 - r;
    if (x < 0.0) r = 3.1415927 - r;
    return y < 0.0 ? -r : r;
}

// HalftoneGrid.row and .cell: the nearest row, then the nearest of a row's count dots by longitude.
float nearestRow(float latitude, float firstLatitude, float step, float rows) {
    return clamp(floor((latitude - firstLatitude) / step + 0.5), 0.0, rows - 1.0);
}
float nearestCell(float longitude, float count) {
    return mod(floor((longitude + 180.0) * count / 360.0), count);
}
float cellLongitude(float cell, float count) { return -180.0 + (cell + 0.5) * 360.0 / count; }

// Degrees between two longitudes, the short way round.
float longitudeGap(float a, float b) {
    float d = abs(a - b);
    return min(d, 360.0 - d);
}

// The unit vector of a dot: on the circle of latitude with the given sine and cosine, at a longitude in degrees.
float3 dotCentre(float sinLatitude, float cosLatitude, float longitude) {
    float lambda = longitude * RADIANS;
    return float3(cosLatitude * cos(lambda), cosLatitude * sin(lambda), sinLatitude);
}

// Dots fade towards the limb (depth 0) so it reads as curving away rather than as a hard edge.
float depthFade(float depth) { return 0.4 + 0.6 * sqrt(depth); }

// Dots also shrink a little towards the limb, on top of being squashed into ellipses there: without it the limb
// reads as tightly packed dashes, brighter than the face.
float depthShrink(float depth) { return 0.5 + 0.5 * sqrt(depth); }

// How much of the pixel at disc point s the dot of radius size (shrunk by depth) centred on q, at the given depth,
// covers, antialiased over one pixel. The dot is a disc on the tangent plane at q. Seen from the front, the tangent
// plane's radial axis (towards the limb) is squashed by q's depth and the other axis isn't: so the dot is an ellipse,
// and t below is the tangent-plane distance of s from q. Dividing t - r by t's screen-space gradient gives the distance
// to the ellipse's edge in pixels (AGSL has no fwidth). Ellipses thinner than a pixel are dimmed to their area
// instead of widened. The geometry is shared by a land dot and its city light, which differ only in size.
float2 dotDistance(float2 s, float3 q, float depth) {
    float2 c = float2(dot(q, viewEast), dot(q, viewNorth));
    float2 v = s - c;
    float len = length(c);
    float2 n = len > 1e-5 ? c / len : float2(1.0, 0.0);
    float radial = dot(v, n);
    float tangential = dot(v, float2(-n.y, n.x));
    float t = length(float2(radial / depth, tangential));
    float gradient = length(float2(radial / (depth * depth), tangential)) / max(t, 1e-6);
    return float2(t, max(gradient, 1e-6));
}
float coverage(float2 distance, float depth, float size) {
    float r = size * depthShrink(depth);
    float cover = clamp(0.5 - (distance.x - r) / distance.y * radius, 0.0, 1.0);
    float halfMinorPx = r * depth * radius;
    return cover * min(1.0, 2.0 * halfMinorPx / (0.5 + halfMinorPx));
}

half4 main(float2 frag) {
    float2 s = float2(frag.x - center.x, center.y - frag.y) / radius;
    float rr = dot(s, s);
    if (rr >= 1.0) return half4(0.0);
    // The inverse orthographic projection: the point on the sphere under the pixel.
    float pixelDepth = sqrt(1.0 - rr);
    float3 p = s.x * viewEast + s.y * viewNorth + pixelDepth * viewOut;

    // The furthest (in degrees) the pixel can be from the centre of a dot it touches: the dot plus its antialiasing
    // fringe (half a pixel, which spans more of the sphere towards the limb), doubled to be safe. Latitude and
    // longitude gaps are each a lower bound on the distance, so a pixel further than this in either stops there.
    float fringe = 1.0 / (radius * max(pixelDepth, 0.02));
    float landReach = (LAND_DOT + fringe) * DEGREES;
    float seaReach = (SEA_DOT + fringe) * DEGREES;

    float latitude = fastAsin(clamp(p.z, -1.0, 1.0)) * DEGREES;
    float landRow = nearestRow(latitude, ${LandGrid.firstLatitude}, ${LandGrid.rowStep}, ${LandGrid.rows}.0);
    float landRowLatitude = ${LandGrid.firstLatitude} + ${LandGrid.rowStep} * landRow;
    float seaRow = nearestRow(latitude, ${SeaGrid.firstLatitude}, ${SeaGrid.rowStep}, ${SeaGrid.rows}.0);
    float seaRowLatitude = ${SeaGrid.firstLatitude} + ${SeaGrid.rowStep} * seaRow;
    float landGap = abs(latitude - landRowLatitude);
    float seaGap = abs(latitude - seaRowLatitude);
    bool nearLand = landGap <= landReach;
    bool nearSea = seaGap <= seaReach;
    if (!nearLand && !nearSea) return half4(0.0);

    float longitude = fastAtan2(p.y, p.x) * DEGREES;
    float cosLatitude = length(p.xy);

    half4 land = half4(0.0);
    if (nearLand) {
        float count = floor(landMask.eval(float2(${HalftoneGrid.LandColumns}.5, landRow + 0.5)).a * 255.0 + 0.5);
        if (count >= 1.0) { // else no land has loaded yet
            float cell = nearestCell(longitude, count);
            float dotLongitude = cellLongitude(cell, count);
            // cos(latitude) changes by at most the latitude gap (in radians) between the pixel and its row.
            float cosBound = max(cosLatitude - landGap * RADIANS, 0.0);
            if (longitudeGap(longitude, dotLongitude) * cosBound <= landReach) {
                float kind = landMask.eval(float2(cell + 0.5, landRow + 0.5)).a;
                if (kind > 0.25) {
                    float phi = landRowLatitude * RADIANS;
                    float3 q = dotCentre(sin(phi), cos(phi), dotLongitude);
                    float depth = dot(q, viewOut);
                    if (depth > 0.0) {
                        // Daylight: 0 in full night to 1 once the sun is 6° up (sin(elevation) = q · sun).
                        float light = clamp((dot(q, sun) + SIN_SIX_DEGREES) / (2.0 * SIN_SIX_DEGREES), 0.0, 1.0);
                        float fade = depthFade(depth);
                        float level = min(floor(light * 4.0 + min(depth, 1.0) * 1.2), 5.0);
                        float2 distance = dotDistance(s, q, depth);
                        float landAlpha = (0.14 + level * 0.17) * fade * coverage(distance, depth, LAND_DOT);
                        land = half4(landColor.rgb * landAlpha, landAlpha);
                        if (kind > 0.75 && light < 0.02) {
                            float cityAlpha = 0.9 * fade * coverage(distance, depth, CITY_DOT);
                            land = half4(cityColor.rgb * cityAlpha, cityAlpha) + land * (1.0 - cityAlpha);
                        }
                    }
                }
            }
        }
    }

    half4 sea = half4(0.0);
    if (nearSea && land.a < 0.999) {
        float count = floor(seaCounts.eval(float2(seaRow + 0.5, 0.5)).a * 255.0 + 0.5);
        float dotLongitude = cellLongitude(nearestCell(longitude, count), count);
        float cosBound = max(cosLatitude - seaGap * RADIANS, 0.0);
        if (longitudeGap(longitude, dotLongitude) * cosBound <= seaReach) {
            float phi = seaRowLatitude * RADIANS;
            float3 q = dotCentre(sin(phi), cos(phi), dotLongitude);
            float depth = dot(q, viewOut);
            if (depth > 0.0) {
                float lit = dot(q, sun) > -SIN_SIX_DEGREES ? 0.32 : 0.08;
                float alpha = lit * depthFade(depth) * coverage(dotDistance(s, q, depth), depth, SEA_DOT);
                sea = half4(seaColor.rgb * alpha, alpha);
            }
        }
    }
    return land + sea * (1.0 - land.a);
}
"""

// endregion
