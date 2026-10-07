package dev.sebastiano.clockblocker.opus.widget.text

import android.content.Context
import android.graphics.Typeface
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import android.util.TypedValue
import kotlin.math.roundToInt

/**
 * Capture-time text measuring. Remote Compose text can wrap and ellipsize, but it cannot shrink itself on the host:
 * a long word ("Clockblocked") gets broken mid-word and a live countdown gets ellipsized ("1h 22…"). Picking the size
 * and line count up front keeps them whole. Measured with the device's default typeface at its font scale, i.e. what
 * the platform player renders `sp` text with. The layouts use it through `LabelFit`.
 */
object TextFit {
    /** Glyph and gap in front of the 1×1 countdown (see `NextUpRemote`). */
    const val SMALL_GLYPH_DP = 20
    const val SMALL_GLYPH_GAP_DP = 3

    /**
     * Width kept free in every [measure]: the player measures with its own paint flags and rounds the slot to whole
     * pixels, so a text that fits to the last fraction of a pixel here could still get an ellipsis on the host.
     */
    const val SAFETY_DP = 1f

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
