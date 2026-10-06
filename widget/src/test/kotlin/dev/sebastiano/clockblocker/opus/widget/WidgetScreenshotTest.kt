package dev.sebastiano.clockblocker.opus.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.widget.FrameLayout
import android.widget.RemoteViews
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
import dev.sebastiano.clockblocker.opus.core.model.AdviceLog
import dev.sebastiano.clockblocker.opus.core.model.AdviceOutcome
import dev.sebastiano.clockblocker.opus.core.model.AdviceType
import dev.sebastiano.clockblocker.opus.widget.draw.CanvasOps
import dev.sebastiano.clockblocker.opus.widget.draw.GlyphKind
import dev.sebastiano.clockblocker.opus.widget.draw.Glyphs
import dev.sebastiano.clockblocker.opus.widget.draw.WidgetPalette
import dev.sebastiano.clockblocker.opus.widget.draw.WidgetTheme
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
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayInputStream
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlin.math.min
import kotlin.math.roundToInt

private const val DIR = "src/test/screenshots"

/**
 * Goldens for the widget visuals. Everything is pinned to a fixed instant (and, for Remote Compose, a fixed
 * player clock) so the host-driven hand and countdown are deterministic.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w800dp-h1800dp-xhdpi")
class WidgetScreenshotTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val zone = ZoneId.of("Asia/Tokyo")
    private val now = Instant.parse("2026-10-06T06:20:00Z") // 15:20 in Tokyo

    private fun model(
        theme: WidgetTheme,
        scenario: DemoPlans.Scenario? = DemoPlans.Scenario.AvoidLight,
        logged: AdviceOutcome? = null,
    ): WidgetModel {
        val plan = scenario?.let { DemoPlans.lisbonTokyo(now, it) }
        val logs = buildList {
            val current = (plan?.let { WidgetStateMapper.map(it, now, zone) } as? WidgetState.Active)?.current
            if (logged != null && current != null) add(AdviceLog(current.adviceId, logged))
        }
        val state = plan?.let { WidgetStateMapper.map(it, now, zone, logs) } ?: WidgetState.NoTrip
        return WidgetModel(state, WidgetTexts.from(context, state, is24Hour = true), WidgetPalette.of(theme))
    }

    // --- Classic RemoteViews fallback (API 29–35) -------------------------------------------------------------

    @Test
    fun legacyDial() {
        val models = WidgetTheme.entries.map { model(it) } + model(WidgetTheme.Light, scenario = null)
        sideBySide(models.map { LegacyRemoteViews.dialBitmap(it, 400, now) }, models.map { it.palette.surface })
            .captureRoboImage("$DIR/legacy_dial.png")
    }

    @Test
    fun legacyGlyphs() {
        val kinds = AdviceType.entries.map { GlyphKind.Advice(it) } + listOf(GlyphKind.Free, GlyphKind.Adapted, GlyphKind.NoTrip)
        val rows = WidgetTheme.entries.map { WidgetPalette.of(it) }.map { p ->
            sideBySide(kinds.map { CanvasOps.bitmap(Glyphs.build(it, p), 96) }, List(kinds.size) { p.surface })
        }
        stacked(rows).captureRoboImage("$DIR/legacy_glyphs.png")
    }

    /** The classic RemoteViews as a launcher would inflate them (API 29–35 and hosts without RC). Docs overview. */
    @Test
    @Config(sdk = [33])
    fun legacyWidgets() {
        captureRoboImage("$DIR/legacy_widgets.png") {
            Grid(
                listOf(
                    themed { legacyClocks(it, TwoClocksLayout.Square, 176, 176) },
                    listOf(legacyNextUp(model(WidgetTheme.Light), NextUpLayout.Wide, 360, 76)),
                    themed { legacyNextUp(model(it), NextUpLayout.Small, 76, 76) } +
                        legacyNextUp(model(WidgetTheme.Light, scenario = null), NextUpLayout.Medium, 176, 76),
                ),
            )
        }
    }

    @Test
    @Config(sdk = [33])
    fun legacyTwoClocksBuckets() {
        captureRoboImage("$DIR/legacy_two_clocks_buckets.png") {
            Grid(
                listOf(
                    themed { legacyClocks(it, TwoClocksLayout.Compact, 76, 76) } +
                        legacyClocks(WidgetTheme.Light, TwoClocksLayout.Compact, 76, 76, scenario = null),
                    themed { legacyClocks(it, TwoClocksLayout.Tall, 176, 260) },
                    listOf(
                        legacyClocks(WidgetTheme.Light, TwoClocksLayout.Wide, 360, 172),
                        legacyClocks(WidgetTheme.NightSafe, TwoClocksLayout.Wide, 360, 172),
                    ),
                    listOf(
                        legacyClocks(WidgetTheme.Dark, TwoClocksLayout.Large, 360, 260),
                        legacyClocks(WidgetTheme.Light, TwoClocksLayout.Large, 360, 260, logged = AdviceOutcome.Done),
                    ),
                ),
            )
        }
    }

    @Test
    @Config(sdk = [33])
    fun legacyNextUpBuckets() {
        captureRoboImage("$DIR/legacy_next_up_buckets.png") {
            Grid(
                listOf(
                    themed { legacyNextUp(model(it, DemoPlans.Scenario.FreeTime), NextUpLayout.Medium, 176, 76) },
                    listOf(
                        legacyNextUp(model(WidgetTheme.Dark), NextUpLayout.Wide, 360, 76),
                        legacyNextUp(model(WidgetTheme.NightSafe, DemoPlans.Scenario.Sleep), NextUpLayout.Wide, 360, 76),
                    ),
                    themed { legacyNextUp(model(it), NextUpLayout.Square, 176, 176) },
                    themed { legacyNextUp(model(it), NextUpLayout.Tall, 176, 260) },
                    listOf(
                        legacyNextUp(model(WidgetTheme.Light), NextUpLayout.Ribbon, 360, 172),
                        legacyNextUp(model(WidgetTheme.Dark, logged = AdviceOutcome.Done), NextUpLayout.Ribbon, 360, 172),
                    ),
                    listOf(legacyNextUp(model(WidgetTheme.NightSafe), NextUpLayout.Tall, 360, 260)),
                ),
            )
        }
    }

    /** The 4×1 row and the ribbon at 150 % font size: two lines, the other zone's time kept. */
    @Test
    @Config(sdk = [33])
    fun legacyFontScale() {
        RuntimeEnvironment.setFontScale(1.5f)
        captureRoboImage("$DIR/legacy_font_scale.png") {
            Grid(
                listOf(
                    listOf(legacyNextUp(model(WidgetTheme.Light), NextUpLayout.Wide, 360, 76)),
                    listOf(legacyNextUp(model(WidgetTheme.Dark), NextUpLayout.Ribbon, 360, 172)),
                ),
            )
        }
    }

    // --- Remote Compose documents, played back by the androidx player ------------------------------------------

    /** Docs overview: the 2×2 dial in every theme, the 4×2 layout and the empty state. */
    @Test
    fun remoteTwoClocks() {
        captureRoboImage("$DIR/remote_two_clocks.png") {
            Grid(
                listOf(
                    themed { remoteClocks(model(it), TwoClocksLayout.Square, 176, 176) } +
                        remoteClocks(model(WidgetTheme.Light, scenario = null), TwoClocksLayout.Square, 176, 176),
                    listOf(
                        remoteClocks(model(WidgetTheme.Light), TwoClocksLayout.Wide, 360, 172),
                        remoteClocks(model(WidgetTheme.Dark), TwoClocksLayout.Wide, 360, 172),
                    ),
                ),
            )
        }
    }

    @Test
    fun remoteTwoClocksBuckets() {
        captureRoboImage("$DIR/remote_two_clocks_buckets.png") {
            Grid(
                listOf(
                    themed { remoteClocks(model(it), TwoClocksLayout.Compact, 76, 76) } +
                        remoteClocks(model(WidgetTheme.Light, scenario = null), TwoClocksLayout.Compact, 76, 76),
                    themed { remoteClocks(model(it), TwoClocksLayout.Tall, 176, 260) },
                    listOf(
                        remoteClocks(model(WidgetTheme.NightSafe), TwoClocksLayout.Wide, 360, 172),
                        remoteClocks(model(WidgetTheme.Light, logged = AdviceOutcome.Done), TwoClocksLayout.Wide, 360, 172),
                    ),
                    listOf(
                        remoteClocks(model(WidgetTheme.Light), TwoClocksLayout.Large, 360, 260),
                        remoteClocks(model(WidgetTheme.NightSafe, DemoPlans.Scenario.Sleep), TwoClocksLayout.Large, 360, 260),
                    ),
                ),
            )
        }
    }

    /** Docs overview: 4×1 rows, 2×1 tiles, 1×1 tiles and the empty state. */
    @Test
    fun remoteNextUp() {
        captureRoboImage("$DIR/remote_next_up.png") {
            Grid(
                listOf(
                    listOf(
                        remoteNextUp(model(WidgetTheme.Light), NextUpLayout.Wide, 360, 76),
                        remoteNextUp(model(WidgetTheme.Dark), NextUpLayout.Wide, 360, 76),
                    ),
                    themed { remoteNextUp(model(it, DemoPlans.Scenario.FreeTime), NextUpLayout.Medium, 176, 76) },
                    listOf(DemoPlans.Scenario.Sleep, DemoPlans.Scenario.AvoidLight, DemoPlans.Scenario.Adapted).map {
                        remoteNextUp(model(WidgetTheme.Light, it), NextUpLayout.Small, 76, 76)
                    } + remoteNextUp(model(WidgetTheme.NightSafe, DemoPlans.Scenario.Sleep), NextUpLayout.Small, 76, 76) +
                        remoteNextUp(model(WidgetTheme.Light, scenario = null), NextUpLayout.Medium, 176, 76),
                ),
            )
        }
    }

    @Test
    fun remoteNextUpBuckets() {
        captureRoboImage("$DIR/remote_next_up_buckets.png") {
            Grid(
                listOf(
                    themed { remoteNextUp(model(it), NextUpLayout.Square, 176, 176) } +
                        remoteNextUp(model(WidgetTheme.Light, logged = AdviceOutcome.Skipped), NextUpLayout.Square, 176, 176),
                    themed { remoteNextUp(model(it), NextUpLayout.Tall, 176, 260) },
                    listOf(
                        remoteNextUp(model(WidgetTheme.Light), NextUpLayout.Ribbon, 360, 172),
                        remoteNextUp(model(WidgetTheme.Dark, logged = AdviceOutcome.Done), NextUpLayout.Ribbon, 360, 172),
                    ),
                    listOf(
                        remoteNextUp(model(WidgetTheme.NightSafe), NextUpLayout.Tall, 360, 260),
                        remoteNextUp(model(WidgetTheme.Light, DemoPlans.Scenario.Adapted), NextUpLayout.Tall, 360, 260),
                    ),
                ),
            )
        }
    }

    /** Long labels (as used elsewhere in the app) at the tightest sizes: they ellipsize, never clip. */
    @Test
    fun remoteLongLabels() {
        fun longModel(theme: WidgetTheme, label: String): WidgetModel {
            val m = model(theme)
            return m.copy(texts = m.texts.copy(title = label, dialTitle = label))
        }
        val labels = listOf("Nap if you're tired", "Peak fatigue: take care")
        captureRoboImage("$DIR/remote_long_labels.png") {
            Grid(
                labels.map { label ->
                    listOf(
                        remoteClocks(longModel(WidgetTheme.Light, label), TwoClocksLayout.Square, 176, 176),
                        remoteNextUp(longModel(WidgetTheme.Dark, label), NextUpLayout.Small, 76, 76),
                        remoteNextUp(longModel(WidgetTheme.Light, label), NextUpLayout.Medium, 176, 76),
                        remoteNextUp(longModel(WidgetTheme.Dark, label), NextUpLayout.Square, 176, 176),
                    )
                },
            )
        }
    }

    /** Text-heavy buckets at 150 % font size. */
    @Test
    fun remoteFontScale() {
        RuntimeEnvironment.setFontScale(1.5f)
        captureRoboImage("$DIR/remote_font_scale.png") {
            Grid(
                listOf(
                    listOf(
                        remoteClocks(model(WidgetTheme.Light), TwoClocksLayout.Square, 176, 176),
                        remoteNextUp(model(WidgetTheme.Dark), NextUpLayout.Small, 76, 76),
                        remoteNextUp(model(WidgetTheme.Light), NextUpLayout.Medium, 176, 76),
                    ),
                    listOf(
                        remoteNextUp(model(WidgetTheme.Light), NextUpLayout.Wide, 360, 76),
                        remoteNextUp(model(WidgetTheme.Dark), NextUpLayout.Ribbon, 360, 172),
                    ),
                ),
            )
        }
    }

    // --- Cells ---------------------------------------------------------------------------------------------------

    private class Cell(val theme: WidgetTheme, val widthDp: Int, val heightDp: Int, val content: @Composable () -> Unit)

    private fun themed(cell: (WidgetTheme) -> Cell): List<Cell> = WidgetTheme.entries.map(cell)

    private fun remoteClocks(model: WidgetModel, layout: TwoClocksLayout, w: Int, h: Int): Cell =
        remote(model, w, h) { TwoClocksRemote(model, layout) }

    private fun remoteNextUp(model: WidgetModel, layout: NextUpLayout, w: Int, h: Int): Cell =
        remote(model, w, h) { NextUpRemote(model, layout) }

    private fun remote(model: WidgetModel, w: Int, h: Int, content: @Composable () -> Unit): Cell {
        val d = document(w, h, content)
        return Cell(model.theme(), w, h) {
            RemoteDocumentPlayer(d.doc.document, d.widthPx, d.heightPx, Modifier.size(w.dp, h.dp))
        }
    }

    private fun legacyClocks(
        theme: WidgetTheme,
        layout: TwoClocksLayout,
        w: Int,
        h: Int,
        scenario: DemoPlans.Scenario? = DemoPlans.Scenario.AvoidLight,
        logged: AdviceOutcome? = null,
    ): Cell {
        val m = model(theme, scenario, logged)
        val density = context.resources.displayMetrics.density
        val dial = LegacyRemoteViews.dialBitmap(m, (min(w, h - 24) * density).roundToInt(), now)
        return legacy(m, w, h) { LegacyRemoteViews.twoClocks(context, m, layout, dial) }
    }

    private fun legacyNextUp(model: WidgetModel, layout: NextUpLayout, w: Int, h: Int): Cell =
        legacy(model, w, h) { LegacyRemoteViews.nextUp(context, model, layout, context.resources.displayMetrics.density, now) }

    private fun legacy(model: WidgetModel, w: Int, h: Int, build: () -> RemoteViews): Cell =
        Cell(model.theme(), w, h) {
            AndroidView(modifier = Modifier.size(w.dp, h.dp), factory = { ctx -> build().apply(ctx, FrameLayout(ctx)) })
        }

    private fun WidgetModel.theme(): WidgetTheme = WidgetTheme.entries.first { WidgetPalette.of(it) == palette }

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
    private fun Grid(rows: List<List<Cell>>) {
        Column(Modifier.background(Color(0xFF9AA0A6)).padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            rows.forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { cell ->
                        // A wallpaper-ish backdrop so the widget's own rounded background is visible.
                        val backdrop = if (cell.theme == WidgetTheme.Light) Color(0xFFDCE3EE) else Color(0xFF2B3140)
                        Box(Modifier.background(backdrop).padding(8.dp)) { cell.content() }
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
