package dev.sebastiano.clockblocker.opus.core.notifications

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.core.content.ContextCompat
import androidx.core.graphics.createBitmap
import dev.sebastiano.clockblocker.opus.core.model.AdviceType
import kotlin.math.roundToInt

/**
 * The advice glyph on its colour chip as a bitmap, for the large icon of standard-template notifications (reminders,
 * the travel-day Live Update, and the lock-screen version of Now), so they carry the same visual
 * cue as the custom Now view. A bitmap can't follow the shade's theme by itself, so it takes the app's current
 * light/dark state at build time; every notification is rebuilt at the next plan boundary anyway.
 */
internal object AdviceChip {
    private const val SIZE_DP = 48f
    private const val GLYPH_FRACTION = 0.55f

    /** [type]'s chip; `null` (a gap, or advice that mustn't be named) is the neutral clock. */
    fun bitmap(context: Context, type: AdviceType?): Bitmap {
        val dark = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        val colors = NotificationPalette.of(type, dark)
        val size = (SIZE_DP * context.resources.displayMetrics.density).roundToInt()
        val bitmap = createBitmap(size, size)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colors.container }
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)
        val glyph = ContextCompat.getDrawable(context, type?.style?.icon ?: R.drawable.ic_notif_clock)?.mutate()
        if (glyph != null) {
            val inset = ((size * (1 - GLYPH_FRACTION)) / 2).roundToInt()
            glyph.setBounds(inset, inset, size - inset, size - inset)
            glyph.setTint(colors.onContainer)
            glyph.draw(canvas)
        }
        return bitmap
    }
}
