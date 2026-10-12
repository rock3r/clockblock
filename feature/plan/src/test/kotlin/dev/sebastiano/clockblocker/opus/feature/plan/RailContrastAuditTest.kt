package dev.sebastiano.clockblocker.opus.feature.plan

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import dev.sebastiano.clockblocker.opus.core.designsystem.theme.AdviceColors
import dev.sebastiano.clockblocker.opus.core.designsystem.theme.ClockblockTheme
import dev.sebastiano.clockblocker.opus.core.designsystem.theme.ColorMath
import dev.sebastiano.clockblocker.opus.core.designsystem.theme.SkyPalette
import dev.sebastiano.clockblocker.opus.core.model.AdviceType
import dev.sebastiano.clockblocker.opus.core.testing.SeedStyle
import dev.sebastiano.clockblocker.opus.core.testing.WallpaperSeeds
import dev.sebastiano.clockblocker.opus.core.testing.seedColorScheme
import io.kotest.assertions.assertSoftly
import io.kotest.assertions.withClue
import io.kotest.matchers.floats.shouldBeGreaterThanOrEqual
import io.kotest.matchers.floats.shouldBeLessThan
import java.io.File
import java.util.Locale
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * WCAG contrast of what the rail actually draws ([RailColors], [capsuleColors]), computed for the static theme and
 * for wallpaper palettes (dynamic colour), light and dark: 3:1 for the capsules' edges against everything they sit on
 * (the page, the Now card, the body-clock strip over either), 4.5:1 for text. Past capsules are deliberately quieter
 * (2:1; their label, at full text contrast, carries the meaning).
 *
 * Also writes the numbers as a Markdown table to `build/reports/rail-contrast.md`, with what the rail drew before
 * (pale containers on the grey Now row and chips, no edge) for comparison.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [37])
class RailContrastAuditTest {
    @get:Rule
    val compose = createComposeRule()

    private class Palette(val name: String, val scheme: ColorScheme, val advice: AdviceColors, val sky: SkyPalette)

    /** The static theme as the app builds it, light and dark. */
    private fun staticPalettes(): List<Palette> {
        val out = mutableListOf<Palette>()
        compose.setContent {
            listOf(false, true).forEach { dark ->
                ClockblockTheme(darkTheme = dark, dynamicColor = false) {
                    out += Palette(if (dark) "static dark" else "static light", MaterialTheme.colorScheme, ClockblockTheme.adviceColors, ClockblockTheme.sky)
                }
            }
        }
        compose.waitForIdle()
        return out.distinctBy { it.name }
    }

    /** Wallpaper palettes, built the way [ClockblockTheme] does for dynamic colour (advice harmonised, skies blended). */
    private fun dynamicPalettes(): List<Palette> = buildList {
        val styles = listOf(SeedStyle.TonalSpot) + SeedStyle.Expressive
        WallpaperSeeds.all.forEach { (seedName, seed) ->
            styles.forEach { style ->
                listOf(false, true).forEach { dark ->
                    val scheme = seedColorScheme(seed, dark, style)
                    val advice = (if (dark) AdviceColors.Dark else AdviceColors.Light).harmonizedWith(scheme.primary)
                    val sky = SkyPalette.Default.blendedToward(scheme.primary, 0.15f).let { if (dark) it.forDarkTheme() else it }
                    val styleName = if (style == SeedStyle.TonalSpot) "" else " expressive"
                    add(Palette("$seedName$styleName ${if (dark) "dark" else "light"}", scheme, advice, sky))
                }
            }
        }
    }

    /** The body-clock strip behind the capsules over [background], at every half hour of body time. */
    private fun strip(sky: SkyPalette, background: Color): List<Color> =
        (0 until 48).map { ColorMath.over(sky.gradientAt(it / 2f).mid.copy(alpha = SkyBandAlpha), background) }

    private class Row(val palette: String, val type: AdviceType, val before: Float, val page: Float, val card: Float, val strip: Float, val now: Float, val past: Float)

    @Test
    fun `capsules and rail text keep their contrast on every palette`() {
        val palettes = staticPalettes() + dynamicPalettes()
        val rows = mutableListOf<Row>()
        val text = mutableListOf<List<String>>()
        assertSoftly {
            palettes.forEach { p ->
                val rail = RailColors.of(p.scheme, p.advice, p.sky)
                val backgrounds = listOf(rail.page, rail.emphasisedRow)
                val strips = backgrounds.flatMap { strip(p.sky, it) }
                AdviceType.entries.forEach { type ->
                    val role = p.advice[type]
                    fun worst(state: CapsuleState, on: List<Color>) =
                        on.minOf { bg -> ColorMath.contrast(rail.capsule(type, state).edge, bg) }
                    val page = worst(CapsuleState.Upcoming, listOf(rail.page))
                    val card = worst(CapsuleState.Upcoming, listOf(rail.emphasisedRow))
                    val stripWorst = worst(CapsuleState.Upcoming, strips)
                    val now = worst(CapsuleState.Now, backgrounds + strips)
                    val past = worst(CapsuleState.Past, backgrounds + strips)
                    // Before: the pale container straight on the grey Now row (surfaceContainerHigh), no edge.
                    val before = ColorMath.contrast(role.container, p.scheme.surfaceContainerHigh)
                    rows += Row(p.name, type, before, page, card, stripWorst, now, past)
                    withClue("${p.name} $type edge on the page") { page shouldBeGreaterThanOrEqual 3f }
                    withClue("${p.name} $type edge on the Now card") { card shouldBeGreaterThanOrEqual 3f }
                    withClue("${p.name} $type edge on the body-clock strip") { stripWorst shouldBeGreaterThanOrEqual 3f }
                    withClue("${p.name} $type Now capsule edge") { now shouldBeGreaterThanOrEqual 3f }
                    withClue("${p.name} $type past capsule edge") { past shouldBeGreaterThanOrEqual 2f }
                    // Pushing the tone for contrast keeps the hue: the edge still says which advice it is.
                    val source = if (role.hasOutline) role.outline else role.color
                    if (ColorMath.chroma(source) > 0.04f) {
                        val drift = kotlin.math.abs(ColorMath.hueDelta(ColorMath.hue(source), ColorMath.hue(rail.edge(type))))
                        withClue("${p.name} $type edge hue drift") { drift shouldBeLessThan 12f }
                    }
                }
                val textPairs = listOf(
                    "text" to rail.text,
                    "quiet text" to rail.quietText,
                    "Now time" to rail.nowTime,
                )
                val textNumbers = textPairs.map { (name, color) ->
                    val worst = backgrounds.minOf { ColorMath.contrast(color, it) }
                    withClue("${p.name} $name") { worst shouldBeGreaterThanOrEqual 4.5f }
                    worst
                }
                val nowCard = minOf(ColorMath.contrast(rail.nowEdge, rail.page), ColorMath.contrast(rail.nowEdge, rail.emphasisedRow))
                withClue("${p.name} Now card outline") { nowCard shouldBeGreaterThanOrEqual 3f }
                val highlight = minOf(ColorMath.contrast(rail.highlightEdge, rail.page), ColorMath.contrast(rail.highlightEdge, rail.emphasisedRow))
                withClue("${p.name} highlight outline") { highlight shouldBeGreaterThanOrEqual 3f }
                text += listOf(p.name) + (textNumbers + nowCard + highlight).map { it.fmt() }
            }
        }
        writeReport(rows, text)
    }

    private fun Float.fmt() = String.format(Locale.ROOT, "%.1f", this)

    private fun writeReport(rows: List<Row>, text: List<List<String>>) {
        val out = File("build/reports/rail-contrast.md")
        out.parentFile.mkdirs()
        out.writeText(
            buildString {
                appendLine("| Palette | Advice | Before: pill on grey row | Edge on page | Edge on Now card | Edge on strip (worst) | Now pill (worst) | Past pill (worst) |")
                appendLine("|---|---|---|---|---|---|---|---|")
                rows.forEach { r ->
                    appendLine("| ${r.palette} | ${r.type} | ${r.before.fmt()} | ${r.page.fmt()} | ${r.card.fmt()} | ${r.strip.fmt()} | ${r.now.fmt()} | ${r.past.fmt()} |")
                }
                appendLine()
                appendLine("| Palette | Text | Quiet text | Now time | Now card outline | Highlight outline |")
                appendLine("|---|---|---|---|---|---|")
                text.forEach { appendLine("| " + it.joinToString(" | ") + " |") }
            },
        )
    }
}
