package dev.sebastiano.clockblocker.opus.feature.onboarding

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform

/**
 * A small airliner seen from the side, nose to the right, as on a runway seen edge on: fuselage with its tail fin, a
 * swept wing and its two wheels. Built in a unit box ([PlaneHeight] tall for a length of 1) whose origin sits under
 * the plane's tail on the ground (y grows down, so everything is drawn above 0).
 */
internal object RunwayPlane {
    /** How tall the plane is (tail fin tip to wheels), for a length of 1. */
    const val PlaneHeight = 0.4f

    private val body = Path().apply {
        moveTo(0.04f, -0.12f)
        lineTo(0.82f, -0.08f)
        cubicTo(0.93f, -0.08f, 1f, -0.10f, 1f, -0.13f)
        cubicTo(1f, -0.16f, 0.93f, -0.20f, 0.84f, -0.20f)
        lineTo(0.22f, -0.20f)
        lineTo(0.10f, -0.40f)
        lineTo(0.03f, -0.40f)
        lineTo(0f, -0.16f)
        close()
    }

    private val wing = Path().apply {
        moveTo(0.36f, -0.13f)
        lineTo(0.60f, -0.13f)
        lineTo(0.45f, -0.045f)
        lineTo(0.37f, -0.045f)
        close()
    }

    private val gear = Path().apply {
        moveTo(0.29f, -0.09f); lineTo(0.31f, -0.09f); lineTo(0.31f, -0.04f); lineTo(0.29f, -0.04f); close()
        moveTo(0.77f, -0.08f); lineTo(0.79f, -0.08f); lineTo(0.79f, -0.04f); lineTo(0.77f, -0.04f); close()
    }

    /**
     * Draws the plane [length] long with its wheels on [ground] (the point under its tail), in [color] at [alpha],
     * pitched up [pitchDegrees] about its main wheels and facing right, or left when [mirrored].
     */
    fun DrawScope.drawRunwayPlane(
        ground: Offset,
        length: Float,
        color: Color,
        alpha: Float = 1f,
        pitchDegrees: Float = 0f,
        mirrored: Boolean = false,
        wingColor: Color = color,
    ) {
        if (alpha <= 0f) return
        withTransform({
            translate(ground.x, ground.y)
            if (mirrored) scale(-1f, 1f, pivot = Offset.Zero)
            if (pitchDegrees != 0f) rotate(-pitchDegrees, pivot = Offset(0.3f * length, 0f))
            scale(length, length, pivot = Offset.Zero)
        }) {
            drawPath(gear, color.copy(alpha = color.alpha * alpha))
            drawCircle(color.copy(alpha = color.alpha * alpha), radius = WheelRadius, center = Offset(0.30f, -WheelRadius))
            drawCircle(color.copy(alpha = color.alpha * alpha), radius = WheelRadius, center = Offset(0.78f, -WheelRadius))
            drawPath(body, color.copy(alpha = color.alpha * alpha))
            drawPath(wing, wingColor.copy(alpha = wingColor.alpha * alpha))
        }
    }

    private const val WheelRadius = 0.025f
}
