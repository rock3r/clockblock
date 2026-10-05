package dev.sebastiano.clockblocker.opus.widget.draw

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import androidx.core.graphics.createBitmap

/** Replays a [DrawOp] list onto an android.graphics.Canvas (fallback widgets, previews, tests). */
object CanvasOps {

    fun draw(canvas: Canvas, ops: List<DrawOp>, cx: Float, cy: Float, unit: Float) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val rect = RectF()
        ops.forEach { op ->
            paint.reset()
            paint.isAntiAlias = true
            paint.color = op.color
            when (op) {
                is DrawOp.Circle -> {
                    paint.stroke(op.stroke * unit)
                    canvas.drawCircle(cx + op.cx * unit, cy + op.cy * unit, op.r * unit, paint)
                }
                is DrawOp.Arc -> {
                    if (op.fill) paint.style = Paint.Style.FILL else paint.stroke(op.stroke * unit)
                    paint.strokeCap = if (op.roundCap) Paint.Cap.ROUND else Paint.Cap.BUTT
                    rect.set(
                        cx + (op.cx - op.r) * unit,
                        cy + (op.cy - op.r) * unit,
                        cx + (op.cx + op.r) * unit,
                        cy + (op.cy + op.r) * unit,
                    )
                    canvas.drawArc(rect, op.startDeg, op.sweepDeg, op.fill, paint)
                }
                is DrawOp.Line -> {
                    paint.stroke(op.stroke * unit)
                    paint.strokeCap = if (op.roundCap) Paint.Cap.ROUND else Paint.Cap.BUTT
                    canvas.drawLine(cx + op.x0 * unit, cy + op.y0 * unit, cx + op.x1 * unit, cy + op.y1 * unit, paint)
                }
                is DrawOp.RoundRect -> {
                    paint.stroke(op.stroke * unit)
                    rect.set(cx + op.left * unit, cy + op.top * unit, cx + op.right * unit, cy + op.bottom * unit)
                    canvas.drawRoundRect(rect, op.radius * unit, op.radius * unit, paint)
                }
            }
        }
    }

    /** Renders [ops] (unit radius 1) into a square bitmap of [sizePx], with [paddingPx] margin. */
    fun bitmap(ops: List<DrawOp>, sizePx: Int, paddingPx: Float = 0f): Bitmap =
        createBitmap(sizePx, sizePx).also { bmp ->
            val half = sizePx / 2f
            draw(Canvas(bmp), ops, half, half, half - paddingPx)
        }

    private fun Paint.stroke(width: Float) {
        if (width > 0f) {
            style = Paint.Style.STROKE
            strokeWidth = width
        } else {
            style = Paint.Style.FILL
        }
    }
}
