package dev.sebastiano.clockblocker.opus.core.designsystem.component

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.layout.LastBaseline
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.Constraints
import dev.sebastiano.clockblocker.opus.core.designsystem.R
import dev.sebastiano.clockblocker.opus.core.designsystem.theme.DotMatrixStyle
import dev.sebastiano.clockblocker.opus.core.designsystem.theme.LocalReduceMotion
import dev.sebastiano.clockblocker.opus.core.designsystem.theme.ClockblockTheme
import kotlin.math.ceil

/**
 * A 5 × 7 LED dot-matrix face for data: airport codes, short figures (design.md §2.2 "dot-matrix language for
 * data"; our own glyphs, not a copy of any brand's font). Drawn on a Canvas, so there is no font file to bundle,
 * and widgets can draw the same glyphs from [glyph] with their own draw ops.
 *
 * Covers `A–Z`, `0–9`, `·` (the resting dot), `+`, `-`, `−`, `:`, `?` and space. Lowercase reads as uppercase;
 * anything else falls back to `?`.
 */
object DotMatrixFont {
    const val Columns = 5
    const val Rows = 7

    /** The middle dot shown in every cell of an empty slot (`· · ·`). */
    const val RestingDot = '\u00B7'

    private val glyphs: Map<Char, IntArray> = buildMap {
        fun def(c: Char, vararg rows: String) {
            require(rows.size == Rows && rows.all { it.length == Columns }) { "Bad glyph $c" }
            put(c, IntArray(Rows) { r -> rows[r].fold(0) { acc, ch -> (acc shl 1) or if (ch == '#') 1 else 0 } })
        }
        def('A', ".###.", "#...#", "#...#", "#####", "#...#", "#...#", "#...#")
        def('B', "####.", "#...#", "#...#", "####.", "#...#", "#...#", "####.")
        def('C', ".###.", "#...#", "#....", "#....", "#....", "#...#", ".###.")
        def('D', "####.", "#...#", "#...#", "#...#", "#...#", "#...#", "####.")
        def('E', "#####", "#....", "#....", "####.", "#....", "#....", "#####")
        def('F', "#####", "#....", "#....", "####.", "#....", "#....", "#....")
        def('G', ".###.", "#...#", "#....", "#.###", "#...#", "#...#", ".####")
        def('H', "#...#", "#...#", "#...#", "#####", "#...#", "#...#", "#...#")
        def('I', ".###.", "..#..", "..#..", "..#..", "..#..", "..#..", ".###.")
        def('J', "..###", "...#.", "...#.", "...#.", "...#.", "#..#.", ".##..")
        def('K', "#...#", "#..#.", "#.#..", "##...", "#.#..", "#..#.", "#...#")
        def('L', "#....", "#....", "#....", "#....", "#....", "#....", "#####")
        def('M', "#...#", "##.##", "#.#.#", "#.#.#", "#...#", "#...#", "#...#")
        def('N', "#...#", "#...#", "##..#", "#.#.#", "#..##", "#...#", "#...#")
        def('O', ".###.", "#...#", "#...#", "#...#", "#...#", "#...#", ".###.")
        def('P', "####.", "#...#", "#...#", "####.", "#....", "#....", "#....")
        def('Q', ".###.", "#...#", "#...#", "#...#", "#.#.#", "#..#.", ".##.#")
        def('R', "####.", "#...#", "#...#", "####.", "#.#..", "#..#.", "#...#")
        def('S', ".####", "#....", "#....", ".###.", "....#", "....#", "####.")
        def('T', "#####", "..#..", "..#..", "..#..", "..#..", "..#..", "..#..")
        def('U', "#...#", "#...#", "#...#", "#...#", "#...#", "#...#", ".###.")
        def('V', "#...#", "#...#", "#...#", "#...#", "#...#", ".#.#.", "..#..")
        def('W', "#...#", "#...#", "#...#", "#.#.#", "#.#.#", "#.#.#", ".#.#.")
        def('X', "#...#", "#...#", ".#.#.", "..#..", ".#.#.", "#...#", "#...#")
        def('Y', "#...#", "#...#", ".#.#.", "..#..", "..#..", "..#..", "..#..")
        def('Z', "#####", "....#", "...#.", "..#..", ".#...", "#....", "#####")
        def('0', ".###.", "#...#", "#..##", "#.#.#", "##..#", "#...#", ".###.")
        def('1', "..#..", ".##..", "..#..", "..#..", "..#..", "..#..", ".###.")
        def('2', ".###.", "#...#", "....#", "...#.", "..#..", ".#...", "#####")
        def('3', "#####", "...#.", "..#..", "...#.", "....#", "#...#", ".###.")
        def('4', "...#.", "..##.", ".#.#.", "#..#.", "#####", "...#.", "...#.")
        def('5', "#####", "#....", "####.", "....#", "....#", "#...#", ".###.")
        def('6', "..##.", ".#...", "#....", "####.", "#...#", "#...#", ".###.")
        def('7', "#####", "....#", "...#.", "..#..", ".#...", ".#...", ".#...")
        def('8', ".###.", "#...#", "#...#", ".###.", "#...#", "#...#", ".###.")
        def('9', ".###.", "#...#", "#...#", ".####", "....#", "...#.", ".##..")
        def(RestingDot, ".....", ".....", ".....", "..#..", ".....", ".....", ".....")
        def('+', ".....", "..#..", "..#..", "#####", "..#..", "..#..", ".....")
        def('-', ".....", ".....", ".....", "#####", ".....", ".....", ".....")
        def('\u2212', ".....", ".....", ".....", "#####", ".....", ".....", ".....")
        def(':', ".....", "..#..", ".....", ".....", ".....", "..#..", ".....")
        def('?', ".###.", "#...#", "....#", "...#.", "..#..", ".....", "..#..")
        def(' ', ".....", ".....", ".....", ".....", ".....", ".....", ".....")
    }

    /** Every character with its own glyph. */
    val supported: Set<Char> get() = glyphs.keys

    /** The lit dots of [char] as [Rows] row bitmasks; bit `Columns − 1` is the leftmost column. */
    fun glyph(char: Char): IntArray = (glyphs[char.uppercaseChar()] ?: glyphs.getValue('?')).copyOf()

    /** True when the dot at ([row], [column]) of [char] is lit. */
    fun isLit(char: Char, row: Int, column: Int): Boolean {
        val bits = (glyphs[char.uppercaseChar()] ?: glyphs.getValue('?'))[row]
        return (bits shr (Columns - 1 - column)) and 1 == 1
    }
}

/**
 * The characters a dot-matrix slot shows: [text] uppercased and padded with spaces to [minLength] cells (so a
 * slot never changes width), or one resting dot per cell when [text] is null or blank.
 */
fun dotMatrixCells(text: String?, minLength: Int): String {
    val trimmed = text?.trim().orEmpty()
    if (trimmed.isEmpty()) return DotMatrixFont.RestingDot.toString().repeat(maxOf(minLength, 1))
    return trimmed.uppercase().padEnd(minLength, ' ')
}

/**
 * How far the dot in [column] of cell [index] (of [cellCount]) has turned to its new state when the whole
 * reveal is at [progress] (0–1): cells start left to right, staggered across the first half, and within a cell
 * the columns light left to right.
 */
fun dotRevealFraction(index: Int, column: Int, progress: Float, cellCount: Int): Float {
    val stagger = if (cellCount > 1) 0.5f / (cellCount - 1) else 0f
    val window = if (cellCount > 1) 0.5f else 1f
    val local = ((progress - index * stagger) / window).coerceIn(0f, 1f)
    return (local * (DotMatrixFont.Columns + 1) - column).coerceIn(0f, 1f)
}

/** "SFO" → "S F O", so TalkBack spells a code out instead of guessing a word. */
fun spellOut(code: String): String = code.trim().uppercase().toCharArray().joinToString(" ")

private class Holder(var value: String)

private data class DotTransition(val from: String, val to: String)

/**
 * Draws [text] in the dot-matrix face ([DotMatrixFont]). Null or blank text shows the empty state: one resting
 * dot per cell (`· · ·`). When the text changes, the dots turn to the new characters left to right, cell by
 * cell (`ClockblockMotion.colour()`, ~180 ms); under reduce motion (or [animateChanges] = false) it snaps. The first
 * composition shows the text at rest (no entrance).
 *
 * Exposes first/last baselines at the bottom of the dot grid, so it aligns with [androidx.compose.material3.Text]
 * via `Modifier.alignByBaseline()`.
 *
 * @param minLength cells to reserve, so a slot keeps its width when empty or shorter.
 * @param contentDescription what TalkBack reads; null makes it decorative (when a parent already speaks it).
 */
@Composable
fun DotMatrixText(
    text: String?,
    modifier: Modifier = Modifier,
    style: DotMatrixStyle = ClockblockTheme.textStyles.iataLabel,
    color: Color = LocalContentColor.current,
    minLength: Int = 0,
    contentDescription: String? = text,
    animateChanges: Boolean = true,
) {
    val cells = dotMatrixCells(text, minLength)
    val reduce = LocalReduceMotion.current
    val motion = ClockblockTheme.motion
    val previous = remember { Holder(cells) }
    val transition = remember(cells) {
        val n = maxOf(previous.value.length, cells.length)
        DotTransition(previous.value.padEnd(n, ' '), cells.padEnd(n, ' '))
    }
    SideEffect { previous.value = cells }
    val play = animateChanges && !reduce && transition.from != transition.to
    val reveal = remember(transition) { Animatable(if (play) 0f else 1f) }
    LaunchedEffect(transition, play) {
        if (play) reveal.animateTo(1f, motion.colour()) else reveal.snapTo(1f)
    }

    val pitch = with(LocalDensity.current) { style.glyphHeight.toPx() } / DotMatrixFont.Rows
    val n = transition.to.length
    val widthPx = ceil(dotMatrixWidth(n, style, pitch)).toInt()
    val heightPx = ceil(DotMatrixFont.Rows * pitch).toInt()
    val semantics = if (contentDescription != null) {
        Modifier.clearAndSetSemantics { this.contentDescription = contentDescription }
    } else {
        Modifier.clearAndSetSemantics { }
    }
    Spacer(
        modifier
            .then(semantics)
            .layout { measurable, constraints ->
                val w = widthPx.coerceIn(constraints.minWidth, constraints.maxWidth)
                val h = heightPx.coerceIn(constraints.minHeight, constraints.maxHeight)
                val placeable = measurable.measure(Constraints.fixed(w, h))
                layout(w, h, mapOf(FirstBaseline to h, LastBaseline to h)) { placeable.place(0, 0) }
            }
            .drawBehind {
                // The reveal progress is read here, in the draw phase only.
                drawDotMatrix(transition.from, transition.to, reveal.value, pitch, style, color)
            },
    )
}

/**
 * An airport code in the dot-matrix face: three cells, `· · ·` while the slot is empty, a left-to-right reveal
 * when a place is picked. TalkBack spells the code out ("S F O") unless you pass a richer
 * [contentDescription] such as "San Francisco, S F O"; an empty slot reads "Not set".
 */
@Composable
fun IataCode(
    code: String?,
    modifier: Modifier = Modifier,
    style: DotMatrixStyle = ClockblockTheme.textStyles.iataDisplay,
    color: Color = LocalContentColor.current,
    contentDescription: String? = if (code.isNullOrBlank()) stringResource(R.string.iata_not_set) else spellOut(code),
    animateChanges: Boolean = true,
) {
    DotMatrixText(
        text = code,
        modifier = modifier,
        style = style,
        color = color,
        minLength = IataLength,
        contentDescription = contentDescription,
        animateChanges = animateChanges,
    )
}

/** IATA airport codes are three characters. */
const val IataLength = 3

/** Width of [cells] dot-matrix cells in [style], in px for the given dot [pitch]. */
internal fun dotMatrixWidth(cells: Int, style: DotMatrixStyle, pitch: Float): Float =
    (cells * DotMatrixFont.Columns + (cells - 1).coerceAtLeast(0) * style.cellGap) * pitch

private fun DrawScope.drawDotMatrix(from: String, to: String, progress: Float, pitch: Float, style: DotMatrixStyle, color: Color) {
    val radius = pitch * style.dotFill / 2f
    val n = to.length
    val unlit = color.copy(alpha = color.alpha * style.unlitAlpha)
    for (i in 0 until n) {
        val x0 = i * (DotMatrixFont.Columns + style.cellGap) * pitch
        val a = from[i]
        val b = to[i]
        for (r in 0 until DotMatrixFont.Rows) {
            for (c in 0 until DotMatrixFont.Columns) {
                val centre = Offset(x0 + (c + 0.5f) * pitch, (r + 0.5f) * pitch)
                if (style.showUnlit) drawCircle(unlit, radius, centre)
                val litA = if (DotMatrixFont.isLit(a, r, c)) 1f else 0f
                val litB = if (DotMatrixFont.isLit(b, r, c)) 1f else 0f
                val lit = if (litA == litB) litB else litA + (litB - litA) * dotRevealFraction(i, c, progress, n)
                if (lit > 0.001f) drawCircle(color, radius, centre, alpha = lit.coerceIn(0f, 1f))
            }
        }
    }
}
