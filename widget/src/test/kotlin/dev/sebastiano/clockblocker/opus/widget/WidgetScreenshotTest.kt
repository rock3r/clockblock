package dev.sebastiano.clockblocker.opus.widget

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.remote.core.SystemClock
import androidx.compose.remote.creation.compose.layout.RemoteRow
import androidx.compose.remote.creation.compose.modifier.RemoteModifier
import androidx.compose.remote.creation.compose.modifier.background
import androidx.compose.remote.creation.compose.modifier.fillMaxSize
import androidx.compose.remote.creation.compose.state.rc
import androidx.compose.remote.creation.compose.capture.captureSingleRemoteDocument
import androidx.compose.remote.creation.compose.capture.createCreationDisplayInfo
import androidx.compose.remote.player.compose.RemoteDocumentPlayer
import androidx.compose.remote.player.core.RemoteDocument
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import dev.sebastiano.clockblocker.opus.core.model.AdviceLog
import dev.sebastiano.clockblocker.opus.core.model.AdviceOutcome
import dev.sebastiano.clockblocker.opus.core.model.AdviceType
import dev.sebastiano.clockblocker.opus.widget.draw.GlyphKind
import dev.sebastiano.clockblocker.opus.widget.draw.WidgetPalette
import dev.sebastiano.clockblocker.opus.widget.draw.WidgetTheme
import dev.sebastiano.clockblocker.opus.widget.preview.DemoPlans
import dev.sebastiano.clockblocker.opus.widget.rc.Glyph
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
import kotlin.math.roundToInt

private const val DIR = "src/test/screenshots"

/** Four 2×2 cells per row only fit the 800 dp canvas unsqueezed with 6 dp gaps (8 dp squeezes the last by 8 dp). */
private val SQUARES_GAP = 6.dp

/**
 * Goldens for the widget visuals. Everything is pinned to a fixed instant (and, for Remote Compose, a fixed
 * player clock) so the host-driven hand and countdown are deterministic.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [37], qualifiers = "w800dp-h1800dp-xhdpi")
class WidgetScreenshotTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val zone = ZoneId.of("Asia/Tokyo")
    private val now = Instant.parse("2026-10-06T06:20:00Z") // 15:20 in Tokyo

    private fun model(
        theme: WidgetTheme,
        scenario: DemoPlans.Scenario? = DemoPlans.Scenario.AvoidLight,
        logged: AdviceOutcome? = null,
        redacted: Boolean = false,
    ): WidgetModel {
        val plan = scenario?.let { DemoPlans.lisbonTokyo(now, it) }
        val logs = buildList {
            val current = (plan?.let { WidgetStateMapper.map(it, now) } as? WidgetState.Active)?.current
            if (logged != null && current != null) add(AdviceLog(current.adviceId, logged))
        }
        val full = plan?.let { WidgetStateMapper.map(it, now, logs, route = DemoPlans.ROUTE, placeNames = DemoPlans.PLACE_NAMES) } ?: WidgetState.NoTrip
        val state = if (redacted) WidgetStateMapper.redact(full) else full
        return WidgetModel(state, WidgetTexts.from(context, state, is24Hour = true), WidgetPalette.of(theme))
    }

    // --- Remote Compose documents, played back by the androidx player ------------------------------------------

    /** Every glyph on the widget surface, in every theme. */
    @Test
    fun remoteGlyphs() {
        val kinds = AdviceType.entries.map { GlyphKind.Advice(it) } +
            listOf(GlyphKind.Free, GlyphKind.Adapted, GlyphKind.NoTrip, GlyphKind.PlanStep)
        val size = 40
        captureRoboImage("$DIR/remote_glyphs.png") {
            Grid(
                WidgetTheme.entries.map { theme ->
                    val palette = WidgetPalette.of(theme)
                    val doc = document(kinds.size * size, size) {
                        RemoteRow(RemoteModifier.fillMaxSize().background(Color(palette.surface).rc)) {
                            kinds.forEach { Glyph(it, palette, size) }
                        }
                    }
                    listOf(
                        Cell(theme, doc.widthDp, doc.heightDp) {
                            RemoteDocumentPlayer(doc.doc.document, doc.widthPx, doc.heightPx, Modifier.size(doc.widthDp.dp, doc.heightDp.dp))
                        },
                    )
                },
            )
        }
    }

    /**
     * Lock-screen instances with "Hide details on the lock screen" on: no places or route, melatonin as a neutral
     * "Plan step" (it is up next here). Light and dark.
     */
    @Test
    fun remoteKeyguard() {
        captureRoboImage("$DIR/remote_keyguard.png") {
            Grid(
                listOf(WidgetTheme.Light, WidgetTheme.Dark).map { theme ->
                    listOf(
                        remoteClocks(model(theme, redacted = true), TwoClocksLayout.Large, 360, 260),
                        remoteNextUp(model(theme, redacted = true), NextUpLayout.Tall, 176, 260),
                    )
                },
            )
        }
    }

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
                gap = SQUARES_GAP,
            )
        }
    }

    /**
     * Every label a widget shows (each advice type, "Plan step", free time, adapted, no trip) in the tightest Next up
     * buckets, 1×1 and 2×1. Nothing may be cut off: see WidgetLabelFitTest for the measured guarantee.
     */
    @Test
    fun remoteLabelsNextUp() {
        captureRoboImage("$DIR/remote_labels_next_up.png") { LabelGrid(labelModels(is24 = true), small = true) }
    }

    /** Every label in the 2×2 buckets: the Next up stack and the Two Clocks dial caption. */
    @Test
    fun remoteLabelsSquare() {
        captureRoboImage("$DIR/remote_labels_square.png") { LabelGrid(labelModels(is24 = true), small = false) }
    }

    /** [remoteLabelsNextUp] at 130 % font size, with 12-hour times and a long place name: the worst case. */
    @Test
    fun remoteLabelsNextUpLargeText() {
        RuntimeEnvironment.setFontScale(1.3f)
        captureRoboImage("$DIR/remote_labels_next_up_130.png") {
            LabelGrid(labelModels(is24 = false, place = "San Francisco"), small = true)
        }
    }

    /** [remoteLabelsSquare] at 130 % font size, with 12-hour times and a long place name. */
    @Test
    fun remoteLabelsSquareLargeText() {
        RuntimeEnvironment.setFontScale(1.3f)
        captureRoboImage("$DIR/remote_labels_square_130.png") {
            LabelGrid(labelModels(is24 = false, place = "San Francisco"), small = false)
        }
    }

    /**
     * One real model per label: the demo plan with its current block switched to each advice type, the redacted
     * melatonin ("Plan step"), free time, adapted and no trip. Light and dark alternate.
     */
    private fun labelModels(is24: Boolean, place: String? = null): List<WidgetModel> {
        val names = place?.let { DemoPlans.PLACE_NAMES + ("Europe/Lisbon" to it) } ?: DemoPlans.PLACE_NAMES
        fun state(scenario: DemoPlans.Scenario?): WidgetState = scenario?.let {
            WidgetStateMapper.map(DemoPlans.lisbonTokyo(now, it), now, route = DemoPlans.ROUTE, placeNames = names)
        } ?: WidgetState.NoTrip
        val active = state(DemoPlans.Scenario.AvoidLight) as WidgetState.Active
        val current = checkNotNull(active.current)
        val states = AdviceType.entries.map { active.copy(current = current.copy(type = it)) } +
            WidgetStateMapper.redact(active.copy(current = current.copy(type = AdviceType.Melatonin))) +
            listOf(state(DemoPlans.Scenario.FreeTime), state(DemoPlans.Scenario.Adapted), state(null))
        return states.mapIndexed { i, state ->
            val theme = if (i % 2 == 0) WidgetTheme.Light else WidgetTheme.Dark
            WidgetModel(state, WidgetTexts.from(context, state, is24), WidgetPalette.of(theme))
        }
    }

    /** Two labels per row. [small]: the 1×1 and 2×1 Next up of each. Else its 2×2 Next up and Two Clocks. */
    @Composable
    private fun LabelGrid(models: List<WidgetModel>, small: Boolean) {
        val cells = models.map { m ->
            if (small) {
                listOf(remoteNextUp(m, NextUpLayout.Small, 76, 76), remoteNextUp(m, NextUpLayout.Medium, 176, 76))
            } else {
                listOf(remoteNextUp(m, NextUpLayout.Square, 176, 176), remoteClocks(m, TwoClocksLayout.Square, 176, 176))
            }
        }
        Grid(cells.chunked(2).map { it.flatten() }, gap = SQUARES_GAP)
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

    /** [gap] spaces the cells and pads each backdrop; rows must fit the 800 dp canvas or the last cell is squeezed. */
    @Composable
    private fun Grid(rows: List<List<Cell>>, gap: Dp = 8.dp) {
        Column(Modifier.background(Color(0xFF9AA0A6)).padding(gap), verticalArrangement = Arrangement.spacedBy(gap)) {
            rows.forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                    row.forEach { cell ->
                        // A wallpaper-ish backdrop so the widget's own rounded background is visible.
                        val backdrop = if (cell.theme == WidgetTheme.Light) Color(0xFFDCE3EE) else Color(0xFF2B3140)
                        Box(Modifier.background(backdrop).padding(gap)) { cell.content() }
                    }
                }
            }
        }
    }
}
