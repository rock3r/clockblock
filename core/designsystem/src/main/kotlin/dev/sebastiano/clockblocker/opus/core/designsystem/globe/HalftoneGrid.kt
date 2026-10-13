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
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * The two lat/lon dot grids of the globe's halftone, and how a point on the sphere finds its nearest dot. The
 * halftone shader ([GlobeHalftoneShader]) does the same lookup per pixel; this is the reference it is built from (its
 * constants are spliced into the shader source) and tested against.
 *
 * A grid is rows of dots at a fixed latitude step, each row with as many evenly spaced dots as fit round its
 * circumference, so dots sit about the same distance apart everywhere. The nearest dot to a point is always in the
 * nearest row (a dot is far narrower than the gap between rows), and within a row it's the nearest by longitude.
 */
internal class HalftoneGrid(
    /** The latitude of row 0, in degrees. */
    val firstLatitude: Double,
    /** Degrees of latitude between rows. */
    val rowStep: Double,
    val rows: Int,
    private val countOf: (latitude: Double) -> Int,
) {
    /** The number of dots in each row. */
    val counts: IntArray = IntArray(rows) { countOf(latitude(it)) }

    fun latitude(row: Int): Double = firstLatitude + rowStep * row

    /** The row nearest to [latitude]. */
    fun row(latitude: Double): Int = floor((latitude - firstLatitude) / rowStep + 0.5).toInt().coerceIn(0, rows - 1)

    /** The dot nearest to [longitude] in a row of [count] dots. */
    fun cell(longitude: Double, count: Int): Int = Math.floorMod(floor((longitude + 180.0) * count / 360.0).toInt(), count)

    /** The longitude of dot [cell] in a row of [count] dots. */
    fun longitude(cell: Int, count: Int): Double = -180.0 + (cell + 0.5) * 360.0 / count

    /** The dot nearest to [point], as the shader finds it: nearest row, then nearest dot in that row. */
    fun nearest(point: GeoPoint): GeoPoint {
        val row = row(point.latitude)
        val count = counts[row]
        return GeoPoint(latitude(row), longitude(cell(point.longitude, count), count))
    }

    companion object {
        /**
         * The point on the sphere under ([x], [y]) on the unit disc (east right, north up) seen from [view], or null
         * off the globe: the inverse of [GlobeGeo.orthographic], as the shader computes it per pixel. The view's
         * right, up and towards-the-viewer axes are the shader's `viewEast`, `viewNorth` and `viewOut` uniforms.
         */
        fun unproject(x: Double, y: Double, view: GlobeView): GeoPoint? {
            val r2 = x * x + y * y
            if (r2 > 1.0) return null
            val depth = kotlin.math.sqrt(1.0 - r2)
            val phi0 = Math.toRadians(view.latitude)
            val lambda0 = Math.toRadians(view.longitude)
            val (sinPhi, cosPhi) = kotlin.math.sin(phi0) to cos(phi0)
            val (sinLambda, cosLambda) = kotlin.math.sin(lambda0) to cos(lambda0)
            // p = x · east + y · north + depth · out
            val px = x * -sinLambda + y * -sinPhi * cosLambda + depth * cosPhi * cosLambda
            val py = x * cosLambda + y * -sinPhi * sinLambda + depth * cosPhi * sinLambda
            val pz = y * cosPhi + depth * sinPhi
            return GlobeGeo.fromVector(px, py, pz)
        }

        /**
         * Land: the grid `land_dots.bin` was sampled on (Prime Meridian's), rows every 1.8° from 89.1° S, with
         * round(200 cos φ) dots in a row. The file stores each land dot's position rounded to 0.01°.
         */
        val Land = HalftoneGrid(firstLatitude = -89.1, rowStep = 1.8, rows = 100) { lat ->
            (200 * cos(Math.toRadians(lat))).roundToInt()
        }

        /** Sea: sparser, rows every 3° from 88.5° S, one dot per 3° of circumference (at least one). */
        val Sea = HalftoneGrid(firstLatitude = -88.5, rowStep = 3.0, rows = 60) { lat ->
            maxOf(1, (360 * cos(Math.toRadians(lat)) / 3.0).toInt())
        }

        /** The widest land row: the land mask has one column per dot, plus one for the row's dot count. */
        val LandColumns: Int = Land.counts.max()

        /** Mask value for a land dot. */
        const val LandValue: Byte = 0x80.toByte()

        /** Mask value for a land dot that lights up at night (a city). */
        const val CityValue: Byte = 0xFF.toByte()

        /**
         * Bakes land dots into the mask the shader samples: [LandColumns] + 1 bytes per land row (padded to
         * [rowBytes]), byte `cell` of a row being 0 (sea), [LandValue] or [CityValue], and the last column the row's
         * dot count. The dots are given as centi-degrees, as stored in `land_dots.bin`; a sparse, fixed hash of a
         * dot's position in the file picks the ones that light up at night, as before the shader.
         */
        fun bakeLandMask(latitudes: ShortArray, longitudes: ShortArray, rowBytes: Int = LandColumns + 1): ByteArray {
            require(rowBytes > LandColumns)
            val mask = ByteArray(rowBytes * Land.rows)
            for (row in 0 until Land.rows) mask[row * rowBytes + LandColumns] = Land.counts[row].toByte()
            for (i in latitudes.indices) {
                val row = Land.row(latitudes[i] / 100.0)
                val cell = Land.cell(longitudes[i] / 100.0, Land.counts[row])
                val city = (i * 2654435761L ushr 7) % 6 == 0L
                mask[row * rowBytes + cell] = if (city) CityValue else LandValue
            }
            return mask
        }

        /** The sea's dot count per row, one byte each, for the shader to sample. */
        fun seaCounts(): ByteArray = ByteArray(Sea.rows) { Sea.counts[it].toByte() }
    }
}
