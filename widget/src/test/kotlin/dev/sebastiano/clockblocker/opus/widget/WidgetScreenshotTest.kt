package dev.sebastiano.clockblocker.opus.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.remote.core.SystemClock
import androidx.compose.remote.creation.compose.capture.captureSingleRemoteDocument
import androidx.compose.remote.creation.compose.capture.createCreationDisplayInfo
import androidx.compose.remote.player.compose.RemoteDocumentPlayer
import androidx.compose.remote.player.core.RemoteDocument
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.test.core.app.ApplicationProvider
import dev.sebastiano.clockblocker.opus.core.model.AdviceType
import dev.sebastiano.clockblocker.opus.widget.draw.CanvasOps
import dev.sebastiano.clockblocker.opus.widget.draw.GlyphKind
import dev.sebastiano.clockblocker.opus.widget.draw.Glyphs
import dev.sebastiano.clockblocker.opus.widget.draw.WidgetPalette
import dev.sebastiano.clockblocker.opus.widget.legacy.LegacyRemoteViews
import dev.sebastiano.clockblocker.opus.widget.preview.DemoPlans
import dev.sebastiano.clockblocker.opus.widget.rc.NextUpLayout
import dev.sebastiano.clockblocker.opus.widget.rc.NextUpRemote
import dev.sebastiano.clockblocker.opus.widget.rc.TwoClocksLayout
import dev.sebastiano.clockblocker.opus.widget.rc.TwoClocksRemote
import dev.sebastiano.clockblocker.opus.widget.rc.WidgetModel
import dev.sebastiano.clockblocker.opus.widget.rc.widgetProfileFor
import dev.sebastiano.clockblocker.opus.widget.state.WidgetState
import dev.sebastiano.clockblocker.opus.widget.state.WidgetStateMapper
import dev.sebastiano.clockblocker.opus.widget.text.WidgetTexts
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayInputStream
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlin.math.roundToInt

private const val DIR = "src/test/screenshots"

/**
 * Goldens for the widget visuals. Everything is pinned to a fixed instant (and, for Remote Compose, a fixed
 * player clock) so the host-driven hand and countdown are deterministic.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w720dp-h1200dp-xhdpi")
class WidgetScreenshotTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val zone = ZoneId.of("Asia/Tokyo")
    private val now = Instant.parse("2026-10-06T06:20:00Z") // 15:20 in Tokyo

    private fun model(dark: Boolean, scenario: DemoPlans.Scenario? = DemoPlans.Scenario.AvoidLight): WidgetModel {
        val state = scenario?.let { WidgetStateMapper.map(DemoPlans.lisbonTokyo(now, it), now, zone) }
            ?: WidgetState.NoTrip
        return WidgetModel(state, WidgetTexts.from(context, state, is24Hour = true), WidgetPalette.of(dark))
    }

    // --- Classic RemoteViews fallback (API 29–35) -------------------------------------------------------------

    @Test
    fun legacyDial() {
        val light = LegacyRemoteViews.dialBitmap(model(dark = false), 400, now)
        val dark = LegacyRemoteViews.dialBitmap(model(dark = true), 400, now)
        val empty = LegacyRemoteViews.dialBitmap(model(dark = false, scenario = null), 400, now)
        sideBySide(listOf(light, dark, empty), listOf(WidgetPalette.Light.surface, WidgetPalette.Dark.surface, WidgetPalette.Light.surface))
            .captureRoboImage("$DIR/legacy_dial.png")
    }

    @Test
    fun legacyGlyphs() {
        val kinds = AdviceType.entries.map { GlyphKind.Advice(it) } + listOf(GlyphKind.Free, GlyphKind.Adapted, GlyphKind.NoTrip)
        val rows = listOf(WidgetPalette.Light, WidgetPalette.Dark).map { p ->
            sideBySide(kinds.map { CanvasOps.bitmap(Glyphs.build(it, p), 96) }, List(kinds.size) { p.surface })
        }
        stacked(rows).captureRoboImage("$DIR/legacy_glyphs.png")
    }

    // --- Remote Compose documents, played back by the androidx player ------------------------------------------

    @Test
    fun remoteTwoClocks() {
        val cells = listOf(
            listOf(false, true).map { dark ->
                document(150, 150) { TwoClocksRemote(model(dark), TwoClocksLayout.Square) } to dark
            },
            listOf(false, true).map { dark ->
                document(300, 150) { TwoClocksRemote(model(dark), TwoClocksLayout.Wide) } to dark
            },
            listOf(false, true).map { dark ->
                document(150, 150) { TwoClocksRemote(model(dark, null), TwoClocksLayout.Square) } to dark
            },
        )
        captureRoboImage("$DIR/remote_two_clocks.png") { Grid(cells) }
    }

    @Test
    fun remoteNextUp() {
        fun row(w: Int, layout: NextUpLayout, scenario: DemoPlans.Scenario?, h: Int = 64) =
            listOf(false, true).map { dark -> document(w, h) { NextUpRemote(model(dark, scenario), layout) } to dark }
        val cells = listOf(
            row(300, NextUpLayout.Wide, DemoPlans.Scenario.AvoidLight),
            row(180, NextUpLayout.Medium, DemoPlans.Scenario.FreeTime),
            row(76, NextUpLayout.Small, DemoPlans.Scenario.Sleep, h = 76),
            row(76, NextUpLayout.Small, DemoPlans.Scenario.AvoidLight, h = 76),
            row(76, NextUpLayout.Small, DemoPlans.Scenario.Adapted, h = 76),
            row(180, NextUpLayout.Medium, null),
        )
        captureRoboImage("$DIR/remote_next_up.png") { Grid(cells) }
    }

    /** Long labels (as used elsewhere in the app) at the tightest sizes: they ellipsize, never clip. */
    @Test
    fun remoteLongLabels() {
        fun longModel(dark: Boolean, label: String): WidgetModel {
            val m = model(dark)
            return m.copy(texts = m.texts.copy(title = label, dialTitle = label))
        }
        val labels = listOf("Nap if you're tired", "Peak fatigue: take care")
        val cells = labels.flatMap { label ->
            listOf(
                listOf(false, true).flatMap { dark ->
                    listOf(
                        document(150, 150) { TwoClocksRemote(longModel(dark, label), TwoClocksLayout.Square) } to dark,
                        document(76, 76) { NextUpRemote(longModel(dark, label), NextUpLayout.Small) } to dark,
                    )
                },
                listOf(false, true).map { dark ->
                    document(180, 64) { NextUpRemote(longModel(dark, label), NextUpLayout.Medium) } to dark
                },
            )
        }
        captureRoboImage("$DIR/remote_long_labels.png") { Grid(cells) }
    }

    /** The classic RemoteViews as a launcher would inflate them (API 29–35 and hosts without RC). */
    @Test
    @Config(sdk = [33])
    fun legacyWidgets() {
        val density = context.resources.displayMetrics.density
        @Composable
        fun Cell(dark: Boolean, wDp: Int, hDp: Int, build: (WidgetModel) -> android.widget.RemoteViews) {
            Box(Modifier.background(if (dark) Color(0xFF2B3140) else Color(0xFFDCE3EE)).padding(8.dp)) {
                AndroidView(
                    modifier = Modifier.size(wDp.dp, hDp.dp),
                    factory = { ctx -> build(model(dark)).apply(ctx, FrameLayout(ctx)) },
                )
            }
        }
        captureRoboImage("$DIR/legacy_widgets.png") {
            Column(Modifier.background(Color(0xFF9AA0A6)).padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(false, true).forEach { dark ->
                        Cell(dark, 170, 190) { LegacyRemoteViews.twoClocks(context, it, (150 * density).roundToInt(), now) }
                    }
                }
                listOf(NextUpLayout.Wide to 300, NextUpLayout.Small to 76).forEach { (layout, w) ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(false, true).forEach { dark ->
                            Cell(dark, w, if (layout == NextUpLayout.Small) 76 else 64) {
                                LegacyRemoteViews.nextUp(context, it, layout, density, now)
                            }
                        }
                    }
                }
            }
        }
    }

    private class Doc(val widthDp: Int, val heightDp: Int, val widthPx: Int, val heightPx: Int, val doc: RemoteDocument)

    /** Captures [content] to a document whose player clock is pinned to [now] in Tokyo. */
    private fun document(widthDp: Int, heightDp: Int, content: @Composable () -> Unit): Doc {
        val density = context.resources.displayMetrics.density
        val w = (widthDp * density).roundToInt()
        val h = (heightDp * density).roundToInt()
        val profile = checkNotNull(widgetProfileFor(7))
        val bytes = runBlocking {
            captureSingleRemoteDocument(
                context = context,
                creationDisplayInfo = createCreationDisplayInfo(context, Size(w.toFloat(), h.toFloat())),
                profile = profile,
                content = content,
            ).bytes
        }
        @Suppress("RestrictedApiAndroidX") // test-only: pin the player clock so goldens are stable
        val document = RemoteDocument(ByteArrayInputStream(bytes), SystemClock(Clock.fixed(now, zone)))
        return Doc(widthDp, heightDp, w, h, document)
    }

    @Composable
    private fun Grid(rows: List<List<Pair<Doc, Boolean>>>) {
        Column(Modifier.background(Color(0xFF9AA0A6)).padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            rows.forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { (d, dark) ->
                        // A wallpaper-ish backdrop so the widget's own rounded background is visible.
                        Box(Modifier.background(if (dark) Color(0xFF2B3140) else Color(0xFFDCE3EE)).padding(8.dp)) {
                            RemoteDocumentPlayer(d.doc.document, d.widthPx, d.heightPx, Modifier.size(d.widthDp.dp, d.heightDp.dp))
                        }
                    }
                }
            }
        }
    }

    private fun sideBySide(bitmaps: List<Bitmap>, backgrounds: List<Int>, gap: Int = 16): Bitmap {
        val w = bitmaps.sumOf { it.width } + gap * (bitmaps.size + 1)
        val h = bitmaps.maxOf { it.height } + gap * 2
        return Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { out ->
            val c = Canvas(out)
            c.drawColor(0xFF9AA0A6.toInt())
            var x = gap
            bitmaps.forEachIndexed { i, b ->
                if (backgrounds[i] != 0) {
                    c.drawRect(x.toFloat(), gap.toFloat(), (x + b.width).toFloat(), (gap + b.height).toFloat(),
                        android.graphics.Paint().apply { color = backgrounds[i] })
                }
                c.drawBitmap(b, x.toFloat(), gap.toFloat(), null)
                x += b.width + gap
            }
        }
    }

    private fun stacked(bitmaps: List<Bitmap>): Bitmap {
        val w = bitmaps.maxOf { it.width }
        val h = bitmaps.sumOf { it.height }
        return Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { out ->
            val c = Canvas(out)
            c.drawColor(0xFF9AA0A6.toInt())
            var y = 0
            bitmaps.forEach { b ->
                c.drawBitmap(b, 0f, y.toFloat(), null)
                y += b.height
            }
        }
    }
}
