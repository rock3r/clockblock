package dev.sebastiano.clockblocker.opus.widget.text

import android.content.Context
import android.graphics.Paint
import android.graphics.Typeface
import android.text.TextPaint
import dev.sebastiano.clockblocker.opus.core.designsystem.dial.spec.DialTextMeasurer
import dev.sebastiano.clockblocker.opus.core.designsystem.dial.spec.TextSpec

/**
 * How the widget dial sets the spec's text in the system font. The dial's text is drawn on a Remote Compose canvas,
 * which only knows the default typeface's four styles: a weight of 600 or more (after the Bold text setting's boost,
 * [TextFit.weightAdjustment]) is bold, slanted text (the body clock) is italic. The same [paint] measures the text at
 * capture time ([Measurer]) and draws it ([typeface]), so what fits here fits on the host.
 *
 * Text sizes are spec dp: the canvas is scaled to dp, so the paint works in dp too. Canvas text does not follow the
 * font scale; the spec builders grow it a little instead (see [textGrowth]).
 */
internal class DialType(private val weightAdjustment: Int) {

    fun typeface(spec: TextSpec): Typeface {
        val bold = spec.weight + weightAdjustment >= BOLD_FROM
        val style = when {
            bold && spec.slanted -> Typeface.BOLD_ITALIC
            bold -> Typeface.BOLD
            spec.slanted -> Typeface.ITALIC
            else -> Typeface.NORMAL
        }
        return Typeface.create(Typeface.DEFAULT, style)
    }

    /** A paint for [spec] in dp units, with its tracking. */
    fun paint(spec: TextSpec): TextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = typeface(spec)
        textSize = spec.size
        letterSpacing = spec.tracking
    }

    /** The baseline for text whose [spec] line box is centred on [y]. */
    fun centredBaseline(spec: TextSpec, y: Float): Float {
        val fm = paint(spec).fontMetrics
        return y - (fm.ascent + fm.descent) / 2f
    }

    /** Measures spec text as the canvas draws it. */
    val measurer: DialTextMeasurer = DialTextMeasurer { text, spec -> paint(spec).measureText(text) }

    companion object {
        /** Weights from here up draw bold. */
        const val BOLD_FROM = 600

        fun of(context: Context): DialType = DialType(TextFit.weightAdjustment(context))

        /**
         * How much the dial's text grows with the font scale: up to 1.15, like the old dial readouts ([TextFit.dialSp]),
         * because the rings around it don't grow. The spec builders cap it there too.
         */
        fun textGrowth(context: Context): Float = context.resources.configuration.fontScale.coerceIn(1f, MAX_GROWTH)

        private const val MAX_GROWTH = 1.15f
    }
}
