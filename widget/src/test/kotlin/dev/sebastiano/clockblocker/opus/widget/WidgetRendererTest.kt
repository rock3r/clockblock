package dev.sebastiano.clockblocker.opus.widget

import android.content.Context
import android.os.Looper
import android.util.SizeF
import androidx.test.core.app.ApplicationProvider
import dev.sebastiano.clockblocker.opus.widget.draw.WidgetTheme
import dev.sebastiano.clockblocker.opus.widget.preview.DemoPlans
import dev.sebastiano.clockblocker.opus.widget.rc.WidgetSizes
import dev.sebastiano.clockblocker.opus.widget.rc.widgetProfileFor
import dev.sebastiano.clockblocker.opus.widget.state.WidgetStateMapper
import io.kotest.matchers.floats.shouldBeGreaterThanOrEqual
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.xmlpull.v1.XmlPullParser
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [37])
@OptIn(ExperimentalCoroutinesApi::class) // Deferred.getCompleted
class WidgetRendererTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val now = Instant.parse("2026-10-06T09:00:00Z")
    private val active = WidgetStateMapper.map(DemoPlans.lisbonTokyo(now), now)

    @Test
    fun `document profile follows the platform player version`() {
        widgetProfileFor(5).shouldBeNull()
        widgetProfileFor(6).shouldNotBeNull()
        widgetProfileFor(7).shouldNotBeNull()
    }

    @Test
    fun `a supported player gets a Remote Compose document`() {
        val renderer = WidgetRenderer(context, profileProvider = { widgetProfileFor(7) })
        // The capture hops to Dispatchers.Main: render off the test (main) thread and keep its looper turning.
        val render = CoroutineScope(Dispatchers.Default).async { renderer.render(WidgetKind.NextUp, active, WidgetTheme.Light) }
        while (!render.isCompleted) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(5)
        }
        render.getCompleted().layoutId shouldNotBe R.layout.widget_placeholder
    }

    @Test
    fun `without a supported player the widget shows the placeholder`() = runBlocking<Unit> {
        val renderer = WidgetRenderer(context, profileProvider = { null })
        renderer.render(WidgetKind.TwoClocks, active, WidgetTheme.Light).layoutId shouldBe R.layout.widget_placeholder
        renderer.render(WidgetKind.NextUp, active, WidgetTheme.Dark).layoutId shouldBe R.layout.widget_placeholder
    }

    @Test
    fun `a failing capture shows the placeholder instead of breaking the update`() = runBlocking<Unit> {
        val renderer = WidgetRenderer(context, profileProvider = { error("capture failed") })
        renderer.render(WidgetKind.NextUp, active, WidgetTheme.Light).layoutId shouldBe R.layout.widget_placeholder
    }

    @Test
    fun `size maps are ordered smallest first`() {
        fun Map<SizeF, *>.areas() = keys.map { it.width * it.height }
        WidgetRenderer.NEXT_UP_SIZES.areas() shouldBe WidgetRenderer.NEXT_UP_SIZES.areas().sorted()
        WidgetRenderer.TWO_CLOCKS_SIZES.areas() shouldBe WidgetRenderer.TWO_CLOCKS_SIZES.areas().sorted()
    }

    @Test
    fun `no provider lets a launcher place or resize a widget below the size its labels are fitted for`() {
        val density = context.resources.displayMetrics.density
        fun dp(xml: Int, attr: Int): Float {
            val parser = context.resources.getXml(xml)
            while (parser.next() != XmlPullParser.START_TAG) Unit
            val values = context.resources.obtainAttributes(parser, intArrayOf(attr))
            return values.getDimension(0, 0f).also { values.recycle() } / density
        }
        for (xml in listOf(R.xml.widget_next_up_info, R.xml.widget_two_clocks_info)) {
            // Below WidgetSizes.FLOOR no bucket fits and the host plays the smallest one anyway, which may clip.
            dp(xml, android.R.attr.minResizeWidth) shouldBeGreaterThanOrEqual WidgetSizes.FLOOR.width
            dp(xml, android.R.attr.minResizeHeight) shouldBeGreaterThanOrEqual WidgetSizes.FLOOR.height
            dp(xml, android.R.attr.minWidth) shouldBeGreaterThanOrEqual WidgetSizes.FLOOR.width
            dp(xml, android.R.attr.minHeight) shouldBeGreaterThanOrEqual WidgetSizes.FLOOR.height
        }
    }
}
