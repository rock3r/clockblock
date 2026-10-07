package dev.sebastiano.clockblocker.opus.widget.text

import android.content.Context
import android.graphics.Typeface
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import android.util.TypedValue
import kotlin.math.roundToInt

/**
 * Capture-time text fitting for the tightest (1×1) layout. Remote Compose text can wrap and ellipsize, but a single
 * long word ("Clockblocked") gets broken mid-word and a live countdown gets ellipsized ("1h 22…"); picking a smaller
 * size up front keeps them whole. Measured with the device's default typeface at its font scale, i.e. what the
 * platform player renders `sp` text with.
 */
object TextFit {
    /** Content width of a 1×1 Next up cell (≈76 dp minus 4 dp side padding), the reference for [smallLabelSp]. */
    const val SMALL_CONTENT_WIDTH_DP = 68f

    /** Glyph and gap in front of the 1×1 countdown (see `NextUpRemote`). */
    const val SMALL_GLYPH_DP = 20
    const val SMALL_GLYPH_GAP_DP = 3

    /**
     * 1×1 label size: the largest in [maxSp] down to [minSp] at which the whole label wraps into [maxLines] lines;
     * else the largest down to [wordMinSp] at which every word at least fits a line (the label then ellipsizes at a
     * readable size, but "Clockblocked" never breaks mid-word, even at font scale 1.3).
     */
    fun smallLabelSp(
        context: Context,
        text: String,
        maxSp: Int = 11,
        minSp: Int = 9,
        wordMinSp: Int = 8,
        maxLines: Int = 2,
    ): Int {
        val words = text.split(' ').filter { it.isNotEmpty() }
        if (words.isEmpty()) return maxSp
        val paint = semiboldPaint()
        val available = SMALL_CONTENT_WIDTH_DP * context.resources.displayMetrics.density
        for (sp in maxSp downTo minSp) {
            paint.textSize = pxForSp(context, sp)
            if (words.all { paint.measureText(it) <= available } && greedyLines(paint, words, available) <= maxLines) return sp
        }
        return fitSp(context, words, SMALL_CONTENT_WIDTH_DP, maxSp, wordMinSp)
    }

    /** Lines needed to wrap [words] greedily (as TextView/RemoteText do) into [available] px. */
    private fun greedyLines(paint: TextPaint, words: List<String>, available: Float): Int {
        var lines = 1
        var current = ""
        for (word in words) {
            val candidate = if (current.isEmpty()) word else "$current $word"
            if (paint.measureText(candidate) <= available) {
                current = candidate
            } else {
                lines++
                current = word
            }
        }
        return lines
    }

    private fun semiboldPaint() =
        TextPaint(TextPaint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.create(Typeface.DEFAULT, 600, false) }

    private fun regularPaint() =
        TextPaint(TextPaint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.create(Typeface.DEFAULT, 400, false) }

    /**
     * [sp] in px the way captured `sp` text gets its size: through the platform's non-linear font scale curve (at
     * 1.5×, 13 sp is 20 dp, not 19.5), not `sp × fontScale`.
     */
    internal fun pxForSp(context: Context, sp: Int): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sp.toFloat(), context.resources.displayMetrics)

    /** Largest size in [maxSp] down to [minSp] at which [countdown] fits beside a [glyphDp] 1×1 glyph on one line. */
    fun smallCountdownSp(
        context: Context,
        countdown: String,
        glyphDp: Int = SMALL_GLYPH_DP,
        maxSp: Int = 12,
        minSp: Int = 8,
    ): Int = fitSp(context, listOf(countdown), SMALL_CONTENT_WIDTH_DP - glyphDp - SMALL_GLYPH_GAP_DP, maxSp, minSp)

    /**
     * Size for text drawn inside the dial: it follows the font scale only up to [cap], because the ring around it
     * does not grow (at 1.3 the clock ran into the arcs). The host still applies the full scale to `sp`, so this
     * divides the excess back out at capture time.
     */
    fun dialSp(context: Context, baseSp: Int, cap: Float = 1.15f): Int {
        val scale = context.resources.configuration.fontScale
        if (scale <= cap) return baseSp
        return (baseSp * cap / scale).roundToInt().coerceAtLeast(1)
    }

    /** Largest size in [maxSp] down to [minSp] at which each of [lines] fits [widthDp] in semibold; else [minSp]. */
    fun fitSp(context: Context, lines: List<String>, widthDp: Float, maxSp: Int, minSp: Int): Int {
        if (lines.isEmpty()) return maxSp
        val available = widthDp * context.resources.displayMetrics.density
        val paint = semiboldPaint()
        for (sp in maxSp downTo minSp) {
            paint.textSize = pxForSp(context, sp)
            if (lines.all { paint.measureText(it) <= available }) return sp
        }
        return minSp
    }

    /**
     * Width kept free in every [measure]: the player measures with its own paint flags and rounds the slot to whole
     * pixels, so a text that fits to the last fraction of a pixel here could still get an ellipsis on the host.
     */
    const val SAFETY_DP = 1f

    /**
     * [text] at [sp] in a [widthDp] slot of at most [maxLines] lines, laid out as the Remote Compose player lays out
     * `RemoteText` (`StaticLayout`, no font padding, simple line breaks, end ellipsis). It [Fitted.fits] when it
     * shows whole: no ellipsis and no word broken across lines.
     */
    fun measure(context: Context, text: String, widthDp: Float, sp: Int, maxLines: Int = 1, semibold: Boolean = false): Fitted {
        val density = context.resources.displayMetrics.density
        val paint = if (semibold) semiboldPaint() else regularPaint()
        paint.textSize = pxForSp(context, sp)
        val widthPx = ((widthDp - SAFETY_DP) * density).toInt().coerceAtLeast(1)
        val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, widthPx)
            .setIncludePad(false)
            .setMaxLines(maxLines)
            .setEllipsize(TextUtils.TruncateAt.END)
            .build()
        val wordsWhole = text.split(' ').all { paint.measureText(it) <= widthPx }
        val ellipsized = (0 until layout.lineCount).any { layout.getEllipsisCount(it) > 0 }
        val widest = (0 until layout.lineCount).maxOfOrNull { layout.getLineWidth(it) } ?: 0f
        return Fitted(
            text = text,
            sp = sp,
            lines = layout.lineCount,
            widthDp = widest / density,
            heightDp = layout.height / density,
            fits = wordsWhole && !ellipsized && layout.lineCount <= maxLines,
        )
    }

    /**
     * [text] at the largest size in [maxSp] down to [minSp] at which it [fits][Fitted.fits] [widthDp] in at most
     * [maxLines] lines; null when it never does. With [fewerLinesFirst], one line at a smaller size beats two lines
     * at a larger one (for secondary lines, which should stay compact).
     */
    fun fit(
        context: Context,
        text: String,
        widthDp: Float,
        maxSp: Int,
        minSp: Int,
        maxLines: Int = 1,
        semibold: Boolean = false,
        fewerLinesFirst: Boolean = false,
    ): Fitted? {
        val lineCounts = if (fewerLinesFirst) (1..maxLines).toList() else listOf(maxLines)
        for (lines in lineCounts) {
            for (sp in maxSp downTo minSp) {
                val measured = measure(context, text, widthDp, sp, lines, semibold)
                if (measured.fits) return measured
            }
        }
        return null
    }
}

/** A text as [TextFit] lays it out: drawn at [sp] in [lines] lines (`maxLines`), taking [heightDp]. */
data class Fitted(
    val text: String,
    val sp: Int,
    val lines: Int,
    val widthDp: Float,
    val heightDp: Float,
    /** Shown whole: no ellipsis, no word broken. */
    val fits: Boolean,
)
