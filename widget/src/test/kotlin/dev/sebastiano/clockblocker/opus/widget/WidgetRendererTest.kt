package dev.sebastiano.clockblocker.opus.widget

import android.content.Context
import android.util.SizeF
import androidx.test.core.app.ApplicationProvider
import dev.sebastiano.clockblocker.opus.widget.draw.WidgetTheme
import dev.sebastiano.clockblocker.opus.widget.preview.DemoPlans
import dev.sebastiano.clockblocker.opus.widget.rc.NextUpLayout
import dev.sebastiano.clockblocker.opus.widget.rc.RemoteComposeSupport
import dev.sebastiano.clockblocker.opus.widget.rc.TwoClocksLayout
import dev.sebastiano.clockblocker.opus.widget.rc.widgetProfileFor
import dev.sebastiano.clockblocker.opus.widget.state.WidgetState
import dev.sebastiano.clockblocker.opus.widget.state.WidgetStateMapper
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36])
class WidgetRendererTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val now = Instant.parse("2026-10-06T09:00:00Z")
    private val active = WidgetStateMapper.map(DemoPlans.lisbonTokyo(now), now)

    @Test
    fun `remote compose is chosen when the platform reports a supported document version`() {
        WidgetRenderer(context, profileProvider = { widgetProfileFor(7) }).backend shouldBe WidgetBackend.RemoteCompose
    }

    @Test
    fun `classic remote views when the platform has no remote compose player`() {
        WidgetRenderer(context, profileProvider = { null }).backend shouldBe WidgetBackend.Legacy
    }

    @Test
    fun `forceLegacy wins over a supported platform`() {
        WidgetRenderer(context, forceLegacy = true, profileProvider = { widgetProfileFor(7) }).backend shouldBe
            WidgetBackend.Legacy
    }

    @Test
    fun `document profile follows the platform player version`() {
        widgetProfileFor(5).shouldBeNull()
        widgetProfileFor(6).shouldNotBeNull()
        widgetProfileFor(7).shouldNotBeNull()
    }

    @Test
    @Config(sdk = [29])
    fun `no remote compose below API 36`() {
        RemoteComposeSupport.profileOrNull().shouldBeNull()
        WidgetRenderer(context).backend shouldBe WidgetBackend.Legacy
    }

    @Test
    @Config(sdk = [35])
    fun `API 35 renders the classic layouts`() = runBlocking<Unit> {
        val renderer = WidgetRenderer(context)
        renderer.backend shouldBe WidgetBackend.Legacy
        // Both kinds are responsive (size-mapped RemoteViews) on API 31+.
        renderer.render(WidgetKind.TwoClocks, active, WidgetTheme.Light, now = now).shouldNotBeNull()
        renderer.render(WidgetKind.NextUp, active, WidgetTheme.NightSafe, now = now).shouldNotBeNull()
    }

    @Test
    @Config(sdk = [29])
    fun `pre-31 hosts get the single layout that fits the reported size`() = runBlocking<Unit> {
        val renderer = WidgetRenderer(context)
        suspend fun layout(kind: WidgetKind, state: WidgetState, w: Float, h: Float) =
            renderer.render(kind, state, WidgetTheme.Light, WidgetSizeDp(w, h), now).layoutId
        layout(WidgetKind.NextUp, WidgetState.NoTrip, 57f, 50f) shouldBe R.layout.widget_next_up_small_legacy
        layout(WidgetKind.NextUp, active, 300f, 50f) shouldBe R.layout.widget_next_up_legacy
        layout(WidgetKind.NextUp, active, 300f, 130f) shouldBe R.layout.widget_next_up_ribbon_legacy
        layout(WidgetKind.NextUp, active, 150f, 250f) shouldBe R.layout.widget_next_up_stack_legacy
        layout(WidgetKind.TwoClocks, active, 150f, 150f) shouldBe R.layout.widget_two_clocks_legacy
        layout(WidgetKind.TwoClocks, active, 300f, 130f) shouldBe R.layout.widget_two_clocks_wide_legacy
    }

    @Test
    fun `size picking takes the largest layout that fits`() {
        val sizes = WidgetRenderer.NEXT_UP_SIZES
        WidgetRenderer.pick(sizes, null) shouldBe NextUpLayout.Small
        WidgetRenderer.pick(sizes, WidgetSizeDp(30f, 30f)) shouldBe NextUpLayout.Small
        WidgetRenderer.pick(sizes, WidgetSizeDp(120f, 50f)) shouldBe NextUpLayout.Medium
        WidgetRenderer.pick(sizes, WidgetSizeDp(400f, 50f)) shouldBe NextUpLayout.Wide
        WidgetRenderer.pick(sizes, WidgetSizeDp(176f, 176f)) shouldBe NextUpLayout.Square
        WidgetRenderer.pick(sizes, WidgetSizeDp(176f, 260f)) shouldBe NextUpLayout.Tall
        WidgetRenderer.pick(sizes, WidgetSizeDp(360f, 172f)) shouldBe NextUpLayout.Ribbon
        WidgetRenderer.pick(sizes, WidgetSizeDp(360f, 260f)) shouldBe NextUpLayout.Tall
        val clocks = WidgetRenderer.TWO_CLOCKS_SIZES
        WidgetRenderer.pick(clocks, WidgetSizeDp(76f, 76f)) shouldBe TwoClocksLayout.Compact
        WidgetRenderer.pick(clocks, WidgetSizeDp(176f, 176f)) shouldBe TwoClocksLayout.Square
        WidgetRenderer.pick(clocks, WidgetSizeDp(280f, 120f)) shouldBe TwoClocksLayout.Wide
        WidgetRenderer.pick(clocks, WidgetSizeDp(176f, 260f)) shouldBe TwoClocksLayout.Tall
        WidgetRenderer.pick(clocks, WidgetSizeDp(360f, 260f)) shouldBe TwoClocksLayout.Large
    }

    @Test
    fun `size maps are ordered smallest first`() {
        fun Map<SizeF, *>.areas() = keys.map { it.width * it.height }
        WidgetRenderer.NEXT_UP_SIZES.areas() shouldBe WidgetRenderer.NEXT_UP_SIZES.areas().sorted()
        WidgetRenderer.TWO_CLOCKS_SIZES.areas() shouldBe WidgetRenderer.TWO_CLOCKS_SIZES.areas().sorted()
    }
}
