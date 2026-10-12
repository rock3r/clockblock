package dev.sebastiano.clockblocker.opus.widget.rc

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.sebastiano.clockblocker.opus.core.designsystem.globe.GeoPoint
import dev.sebastiano.clockblocker.opus.core.designsystem.globe.GlobeLeg
import dev.sebastiano.clockblocker.opus.core.designsystem.globe.GlobeStop
import dev.sebastiano.clockblocker.opus.core.model.Sun
import dev.sebastiano.clockblocker.opus.widget.draw.WidgetPalette
import dev.sebastiano.clockblocker.opus.widget.draw.WidgetTheme
import dev.sebastiano.clockblocker.opus.widget.state.WidgetGlobe
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.doubles.shouldBeLessThan
import io.kotest.matchers.floats.shouldBeGreaterThanOrEqual
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Instant
import kotlin.math.hypot

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [37])
class WidgetGlobeRendererTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    private val globe = WidgetGlobe(
        legs = listOf(GlobeLeg(GlobeStop(GeoPoint(38.77, -9.13), "LIS"), GlobeStop(GeoPoint(35.55, 139.78), "HND"))),
        activeLeg = null,
        marker = null,
        subsolar = Sun.subsolarPoint(Instant.parse("2026-10-06T09:00:00Z")),
    )

    @Test
    fun `halftone dots shrink towards the horizon, and the ones too small to see are dropped`() {
        val size = 160f
        val spec = WidgetGlobeRenderer.spec(context, globe, WidgetPalette.of(WidgetTheme.Dark), size, size)
        val radius = size / 2f * 0.94f
        // (distance from the centre as a fraction of the globe radius, dot radius) for every halftone dot.
        val dots = spec.ops.filterIsInstance<GlobeOp.Dots>().flatMap { op ->
            (0 until op.count step 3).map { i ->
                hypot(op.xyr[i] - size / 2f, op.xyr[i + 1] - size / 2f) / radius to op.xyr[i + 2]
            }
        }
        val centre = dots.filter { it.first < 0.4f }.map { it.second }
        val limb = dots.filter { it.first > 0.9f }.map { it.second }
        centre.shouldNotBeEmpty()
        limb.shouldNotBeEmpty()

        limb.average() shouldBeLessThan centre.average() * 0.75
        dots.minOf { it.second } shouldBeGreaterThanOrEqual 0.4f
    }
}
