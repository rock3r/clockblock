package dev.sebastiano.clockblocker.opus.core.designsystem.globe

import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.doubles.shouldBeLessThan
import io.kotest.matchers.ints.shouldBeLessThanOrEqual
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.io.DataInputStream
import java.io.File
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** The halftone shader's maths, as its Kotlin reference ([HalftoneGrid]): inverse projection, cell lookup, land mask. */
class HalftoneGridTest {

    private val dots: Pair<ShortArray, ShortArray> by lazy {
        DataInputStream(File("src/main/assets/land_dots.bin").inputStream().buffered()).use { input ->
            val count = input.readInt()
            val lat = ShortArray(count)
            val lon = ShortArray(count)
            for (i in 0 until count) {
                lat[i] = input.readShort()
                lon[i] = input.readShort()
            }
            lat to lon
        }
    }

    @Test
    fun `unprojecting a projected point gives the point back`() {
        val random = Random(7)
        repeat(2_000) {
            val view = GlobeView(random.nextDouble(-80.0, 80.0), random.nextDouble(-180.0, 180.0))
            val point = GeoPoint(random.nextDouble(-89.0, 89.0), random.nextDouble(-180.0, 180.0))
            val p = GlobeGeo.orthographic(point, view)
            if (p.depth < 0.01) return@repeat
            val back = HalftoneGrid.unproject(p.x, p.y, view).shouldNotBeNull()
            GlobeGeo.centralAngle(back, point) shouldBeLessThan 1e-9
        }
    }

    @Test
    fun `off the disc there is no globe`() {
        HalftoneGrid.unproject(0.8, 0.8, GlobeView(10.0, 20.0)).shouldBeNull()
    }

    @Test
    fun `every land dot in the file sits on its own cell of the land grid`() {
        val (lat, lon) = dots
        val grid = HalftoneGrid.Land
        val seen = HashSet<Long>()
        for (i in lat.indices) {
            val point = GeoPoint(lat[i] / 100.0, lon[i] / 100.0)
            val row = grid.row(point.latitude)
            val cell = grid.cell(point.longitude, grid.counts[row])
            // The file rounds positions to 0.01°.
            grid.latitude(row) shouldBe (point.latitude plusOrMinus 0.006)
            grid.longitude(cell, grid.counts[row]) shouldBe (point.longitude plusOrMinus 0.006)
            seen.add(row.toLong() * 1000 + cell) shouldBe true
        }
    }

    @Test
    fun `the land mask marks each dot, its cities, and each row's dot count`() {
        val (lat, lon) = dots
        val columns = HalftoneGrid.LandColumns
        val rowBytes = columns + 4
        val mask = HalftoneGrid.bakeLandMask(lat, lon, rowBytes)
        val grid = HalftoneGrid.Land
        columns shouldBeLessThanOrEqual 255
        for (row in 0 until grid.rows) (mask[row * rowBytes + columns].toInt() and 0xFF) shouldBe grid.counts[row]
        var land = 0
        var cities = 0
        for (row in 0 until grid.rows) {
            for (cell in 0 until columns) {
                when (mask[row * rowBytes + cell]) {
                    HalftoneGrid.LandValue -> land++
                    HalftoneGrid.CityValue -> cities++
                }
            }
        }
        (land + cities) shouldBe lat.size
        // The same sparse pick as before the shader: about one dot in six.
        cities shouldBe lat.indices.count { (it * 2654435761L ushr 7) % 6 == 0L }
    }

    @Test
    fun `a point on a dot finds that dot, on both grids`() {
        val random = Random(11)
        for (grid in listOf(HalftoneGrid.Land, HalftoneGrid.Sea)) {
            repeat(5_000) {
                val row = random.nextInt(grid.rows)
                val count = grid.counts[row]
                val dot = GeoPoint(grid.latitude(row), grid.longitude(random.nextInt(count), count))
                // Anywhere within 1.5× the land dot's radius (1/120 rad), past its antialiased edge.
                val point = offset(dot, random.nextDouble(0.0, 1.5 / 120), random.nextDouble(0.0, 2 * PI))
                GlobeGeo.centralAngle(grid.nearest(point), dot) shouldBeLessThan 1e-9
            }
        }
    }

    @Test
    fun `the sea grid is the one the dots were drawn on before the shader`() {
        // The old SeaGrid: rows every 3° from 88.5° S, one dot per 3° of circumference.
        val old = ArrayList<GeoPoint>()
        var lat = -88.5
        while (lat < 90) {
            val n = maxOf(1, (360 * cos(Math.toRadians(lat)) / 3.0).toInt())
            for (k in 0 until n) old += GeoPoint(lat, -180.0 + (k + 0.5) * 360.0 / n)
            lat += 3.0
        }
        val grid = HalftoneGrid.Sea
        val new = (0 until grid.rows).flatMap { row -> (0 until grid.counts[row]).map { GeoPoint(grid.latitude(row), grid.longitude(it, grid.counts[row])) } }
        new.size shouldBe old.size
        new.zip(old).forEach { (a, b) -> GlobeGeo.centralAngle(a, b) shouldBeLessThan 1e-9 }
    }

    /** The point [distance] radians from [from] along [bearing]. */
    private fun offset(from: GeoPoint, distance: Double, bearing: Double): GeoPoint {
        val phi = Math.toRadians(from.latitude)
        val lambda = Math.toRadians(from.longitude)
        val phi2 = kotlin.math.asin(sin(phi) * cos(distance) + cos(phi) * sin(distance) * cos(bearing))
        val lambda2 = lambda + kotlin.math.atan2(sin(bearing) * sin(distance) * cos(phi), cos(distance) - sin(phi) * sin(phi2))
        return GeoPoint(Math.toDegrees(phi2), Math.toDegrees(lambda2))
    }
}
