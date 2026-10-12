package dev.sebastiano.clockblocker.opus.feature.onboarding.profile

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.toPath
import dev.sebastiano.clockblocker.opus.core.designsystem.theme.ClockblockTheme
import dev.sebastiano.clockblocker.opus.core.model.Chronotype
import dev.sebastiano.clockblocker.opus.feature.onboarding.R
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Test tags for the e2e suite. */
object ChronotypeTags {
    /** `chronotype_DefiniteMorning` … `chronotype_DefiniteEvening`. */
    fun option(chronotype: Chronotype): String = "chronotype_${chronotype.name}"
}

/**
 * Five selectable chronotype cards, Early bird → Night owl, each with a little sky showing when that person's day
 * naturally peaks. Radio-group semantics; selection changes colour only (a 100+/day-safe state change).
 *
 * @param suggested the chronotype the user's night suggests, labelled as such.
 */
@Composable
fun ChronotypePicker(
    selected: Chronotype,
    onSelect: (Chronotype) -> Unit,
    modifier: Modifier = Modifier,
    suggested: Chronotype? = null,
) {
    Column(modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(SegmentedGap)) {
        Chronotype.entries.forEachIndexed { index, chronotype ->
            ChronotypeCard(
                chronotype = chronotype,
                selected = chronotype == selected,
                suggested = chronotype == suggested,
                onClick = { onSelect(chronotype) },
                shape = segmentedShape(index, Chronotype.entries.size),
            )
        }
    }
}

@Composable
private fun ChronotypeCard(
    chronotype: Chronotype,
    selected: Boolean,
    suggested: Boolean,
    onClick: () -> Unit,
    shape: androidx.compose.ui.graphics.Shape,
) {
    val colors = MaterialTheme.colorScheme
    val motion = ClockblockTheme.motion
    val container by animateColorAsState(if (selected) colors.secondaryContainer else colors.surfaceContainer, motion.colour(), label = "chronoContainer")
    Surface(
        shape = shape,
        color = container,
        border = if (selected) BorderStroke(2.dp, colors.primary) else null,
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
            .testTag(ChronotypeTags.option(chronotype)),
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 12.dp).heightIn(min = 56.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ChronotypeSky(chronotype, Modifier.size(52.dp))
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(chronotype.label(), style = MaterialTheme.typography.titleMedium, color = colors.onSurface)
                Text(chronotype.description(), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                if (suggested) {
                    Text(
                        stringResource(R.string.chronotype_suggested),
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.primary,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Box(Modifier.size(28.dp), contentAlignment = Alignment.Center) {
                if (selected) {
                    Surface(shape = CircleShape, color = colors.primary, modifier = Modifier.size(28.dp)) {
                        Icon(
                            painterResource(R.drawable.onboarding_ic_check),
                            contentDescription = null,
                            tint = colors.onPrimary,
                            modifier = Modifier.padding(4.dp),
                        )
                    }
                } else {
                    Canvas(Modifier.size(24.dp)) {
                        drawCircle(colors.outline, size.minDimension / 2f - 1.dp.toPx(), style = Stroke(2.dp.toPx()))
                    }
                }
            }
        }
    }
}

/** Where a chronotype's day peaks: the sky's hour, the sun/moon's place along its path (0 = rising, 1 = set), and moon or sun. */
internal data class SkyPose(val hour: Float, val position: Float, val evening: Boolean)

internal fun Chronotype.pose(): SkyPose = when (this) {
    Chronotype.DefiniteMorning -> SkyPose(6.3f, 0.20f, evening = false)
    Chronotype.ModerateMorning -> SkyPose(8.5f, 0.34f, evening = false)
    Chronotype.Intermediate -> SkyPose(13f, 0.50f, evening = false)
    Chronotype.ModerateEvening -> SkyPose(19.3f, 0.70f, evening = true)
    Chronotype.DefiniteEvening -> SkyPose(23.5f, 0.82f, evening = true)
}

/**
 * A miniature sky landscape for a chronotype: the sky gradient of the hour that person's day peaks, atmospheric glow,
 * clouds or stars, a dashed trajectory path, the sun or crescent moon, and layered rolling hills.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ChronotypeSky(chronotype: Chronotype, modifier: Modifier = Modifier, cornerRadius: Dp = 14.dp) {
    val sky = ClockblockTheme.sky
    val art = ClockblockTheme.artColors
    val pose = chronotype.pose()
    val gradient = sky.gradientAt(pose.hour)
    val sunPath = remember { MaterialShapes.VerySunny.toPath().asComposePath() }
    val sunBounds = remember(sunPath) { sunPath.getBounds() }
    val starDiamond = remember { MaterialShapes.PuffyDiamond.toPath().asComposePath() }
    val starBounds = remember(starDiamond) { starDiamond.getBounds() }
    Canvas(modifier.clip(RoundedCornerShape(cornerRadius))) {
        drawChronotypeScene(
            gradient = gradient,
            tertiary = art.tertiary,
            sunPath = sunPath,
            sunBounds = sunBounds,
            starDiamond = starDiamond,
            starBounds = starBounds,
            position = pose.position,
            moonFraction = if (pose.evening) 1f else 0f,
            entrance = 1f,
            sunRotation = 0f,
            twinkle = 0.5f,
        )
    }
}

internal fun androidx.compose.ui.graphics.drawscope.DrawScope.drawChronotypeScene(
    gradient: dev.sebastiano.clockblocker.opus.core.designsystem.theme.SkyGradient,
    tertiary: Color,
    sunPath: Path,
    sunBounds: Rect,
    starDiamond: Path,
    starBounds: Rect,
    position: Float,
    moonFraction: Float,
    entrance: Float,
    sunRotation: Float,
    twinkle: Float,
) {
    val w = size.width
    val h = size.height
    val horizon = h * 0.76f
    val detailed = w >= 88.dp.toPx()
    val m = moonFraction.coerceIn(0f, 1f)
    val day = 1f - m
    val e = entrance.coerceIn(0f, 1f)

    // 1. Multi-stop sky with warm dawn/dusk horizon atmosphere.
    val dawnWeight = ((0.30f - position) / 0.12f).coerceIn(0f, 1f) * day
    val middayWeight = (1f - kotlin.math.abs(position - 0.50f) / 0.18f).coerceIn(0f, 1f) * day
    val duskWeight = (1f - kotlin.math.abs(position - 0.70f) / 0.10f).coerceIn(0f, 1f) * m
    val deepNightWeight = ((position - 0.74f) / 0.08f).coerceIn(0f, 1f) * m
    val midSky = androidx.compose.ui.graphics.lerp(gradient.top, gradient.bottom, 0.55f)
    val horizonSky = when {
        dawnWeight > 0f -> androidx.compose.ui.graphics.lerp(gradient.bottom, DawnHorizonGlow, dawnWeight * 0.45f)
        duskWeight > 0f -> androidx.compose.ui.graphics.lerp(gradient.bottom, DuskHorizonGlow, duskWeight * 0.50f)
        else -> gradient.bottom
    }
    drawRect(
        Brush.verticalGradient(
            0.0f to gradient.top,
            0.52f to midSky,
            0.82f to horizonSky,
            1.0f to gradient.bottom,
        ),
    )

    // 2. Celestial coordinates along the trajectory arc.
    val pos = position * e
    val angle = PI * (1.0 - pos)
    val rx = w * 0.37f
    val ry = horizon - h * 0.21f
    val p = Offset(w / 2f + (rx * cos(angle)).toFloat(), horizon - (ry * sin(angle)).toFloat())
    val r = w * if (detailed) 0.115f else 0.135f

    // Warm atmospheric horizon/celestial glow before hills.
    if (dawnWeight + duskWeight > 0.01f) {
        val glowColor = if (duskWeight > dawnWeight) DuskHorizonGlow else DawnHorizonGlow
        val glowAlpha = maxOf(dawnWeight, duskWeight) * 0.42f * e
        drawCircle(
            Brush.radialGradient(
                listOf(glowColor.copy(alpha = glowAlpha), glowColor.copy(alpha = 0f)),
                center = Offset(p.x, horizon),
                radius = w * 0.68f,
            ),
            radius = w * 0.68f,
            center = Offset(p.x, horizon),
        )
    }

    // 3. Stars, Evening Star & constellation (for evening/night types).
    if (m > 0f) {
        // Faint constellation lines in the upper-left sky on the large Night Owl card.
        if (detailed && deepNightWeight > 0.1f) {
            val cAlpha = 0.26f * deepNightWeight * e
            val cStroke = 1.dp.toPx()
            for ((a, b) in ConstellationEdges) {
                val (x0, y0) = Stars[a]
                val (x1, y1) = Stars[b]
                drawLine(StarColor.copy(alpha = cAlpha), Offset(x0 * w, y0 * h), Offset(x1 * w, y1 * h), strokeWidth = cStroke, cap = StrokeCap.Round)
            }
        }
        Stars.forEachIndexed { i, (sx, sy, sr) ->
            // Dusk shows the brightest early stars; deep night brings out the full starfield.
            val visibility = if (i < 4) 1f else deepNightWeight
            if (visibility > 0.01f) {
                val phase = (twinkle + i * 0.27f) % 1f
                val alpha = (0.50f + 0.50f * phase) * m * visibility * e
                val radius = sr * w * if (detailed) 1f else 1.25f
                drawCircle(StarColor.copy(alpha = alpha), radius, Offset(sx * w, sy * h))
            }
        }
        // Four-point diamond stars (Evening Star at dusk + bright night sparkles).
        DiamondStars.forEachIndexed { i, (dx, dy, ds) ->
            val visibility = if (i == 0) 1f else deepNightWeight
            if (visibility > 0.01f) {
                val pulse = 0.82f + 0.18f * sin((twinkle + i * 0.4f) * 2f * PI.toFloat())
                val starSize = ds * w * pulse * (if (detailed) 1f else 1.25f) * e
                val s = starSize / maxOf(starBounds.width, starBounds.height)
                drawCircle(
                    StarColor.copy(alpha = 0.22f * m * visibility * e),
                    radius = starSize * 0.95f,
                    center = Offset(dx * w, dy * h),
                )
                withTransform({
                    translate(dx * w - starBounds.center.x * s, dy * h - starBounds.center.y * s)
                    scale(s, s, pivot = Offset.Zero)
                }) {
                    drawPath(starDiamond, StarColor.copy(alpha = 0.92f * m * visibility * e))
                }
            }
        }
    }

    // 4. Soft daytime clouds (morning wisps and midday cumulus).
    if (day > 0.01f) {
        val cloudAlpha = (0.28f + 0.24f * middayWeight) * day * e
        drawCloudPuff(w * 0.23f, h * 0.29f, w * 0.18f, h * 0.055f, Color.White.copy(alpha = cloudAlpha))
        if (middayWeight > 0.05f || position > 0.28f) {
            drawCloudPuff(w * 0.77f, h * 0.23f, w * 0.20f, h * 0.060f, Color.White.copy(alpha = cloudAlpha * 0.9f))
        }
    }

    // 5. Trajectory arc: dashed full path + subtle brighter travelled segment up to the sun/moon.
    val arcRect = Rect(w * 0.13f, h * 0.21f, w * 0.87f, horizon * 2f - h * 0.21f)
    val dashUnit = if (detailed) 4.dp.toPx() else 2.6.dp.toPx()
    val strokeW = if (detailed) 1.5.dp.toPx() else 1.1.dp.toPx()
    val fullArc = Path().apply { arcTo(arcRect, 180f, 180f, true) }
    drawPath(
        fullArc,
        Color.White.copy(alpha = 0.42f),
        style = Stroke(strokeW, cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(dashUnit, dashUnit))),
    )
    if (pos > 0.02f) {
        val activeArc = Path().apply { arcTo(arcRect, 180f, (180f * pos).coerceIn(1f, 180f), true) }
        drawPath(
            activeArc,
            Color.White.copy(alpha = 0.75f * e),
            style = Stroke(strokeW * 1.15f, cap = StrokeCap.Round),
        )
    }

    // 6. Sun / Moon clipped above the foreground hill horizon so it rises out of the landscape.
    withTransform({ clipRect(bottom = horizon + h * 0.04f) }) {
        if (m < 1f) {
            val sunAlpha = 1f - m
            // Soft solar halo.
            drawCircle(
                Brush.radialGradient(
                    listOf(SunCoreColor.copy(alpha = 0.42f * sunAlpha * e), tertiary.copy(alpha = 0f)),
                    center = p,
                    radius = r * 2.5f,
                ),
                radius = r * 2.5f,
                center = p,
            )
            // Crepuscular dawn rays for Early Bird.
            if (dawnWeight > 0.05f && detailed) {
                for (ray in -2..2) {
                    val rayAngle = (-55f + ray * 22f) * PI.toFloat() / 180f
                    val r0 = r * 1.35f
                    val r1 = r * (2.2f + (2 - kotlin.math.abs(ray)) * 0.25f)
                    drawLine(
                        SunCoreColor.copy(alpha = 0.34f * dawnWeight * sunAlpha * e),
                        Offset(p.x + r0 * cos(rayAngle), p.y + r0 * sin(rayAngle)),
                        Offset(p.x + r1 * cos(rayAngle), p.y + r1 * sin(rayAngle)),
                        strokeWidth = 2.2.dp.toPx(),
                        cap = StrokeCap.Round,
                    )
                }
            }
            // Outer VerySunny corona + inner warm solar disc.
            val s = 2.45f * r / maxOf(sunBounds.width, sunBounds.height)
            withTransform({
                translate(p.x - sunBounds.center.x * s, p.y - sunBounds.center.y * s)
                scale(s, s, pivot = Offset.Zero)
            }) {
                withTransform({ rotate(sunRotation * 45f, pivot = sunBounds.center) }) {
                    drawPath(sunPath, tertiary.copy(alpha = sunAlpha))
                }
            }
            drawCircle(SunCoreColor.copy(alpha = 0.88f * sunAlpha), radius = r * 0.74f, center = p)
            drawCircle(Color.White.copy(alpha = 0.45f * sunAlpha), radius = r * 0.26f, center = Offset(p.x - r * 0.22f, p.y - r * 0.22f))
        }
        if (m > 0f) {
            // Soft lunar glow.
            drawCircle(
                Brush.radialGradient(
                    listOf(MoonColor.copy(alpha = 0.32f * m * e), MoonColor.copy(alpha = 0f)),
                    center = p,
                    radius = r * 2.3f,
                ),
                radius = r * 2.3f,
                center = p,
            )
            // Subtle earthshine on the dark limb (occluding the trajectory arc behind the lunar sphere).
            drawCircle(midSky.copy(alpha = m), radius = r * 0.96f, center = p)
            drawCircle(MoonColor.copy(alpha = 0.12f * m), radius = r * 0.96f, center = p)
            val disc = Path().apply { addOval(Rect(p, r)) }
            val bite = Path().apply { addOval(Rect(Offset(p.x + r * 0.52f, p.y - r * 0.42f), r * 0.84f)) }
            val crescent = Path.combine(PathOperation.Difference, disc, bite)
            drawPath(crescent, MoonColor.copy(alpha = m))
            if (detailed) {
                drawCircle(
                    tertiary.copy(alpha = 0.16f * m),
                    radius = r * 0.18f,
                    center = Offset(p.x - r * 0.36f, p.y + r * 0.14f),
                )
                drawCircle(
                    tertiary.copy(alpha = 0.13f * m),
                    radius = r * 0.11f,
                    center = Offset(p.x - r * 0.18f, p.y + r * 0.42f),
                )
            }
        }
    }

    // 7. Three-layer sculpted landscape hills at the base.
    val hillBase = androidx.compose.ui.graphics.lerp(HillDayBase, HillNightBase, m)
    val backHillColor = androidx.compose.ui.graphics.lerp(horizonSky, hillBase, 0.42f)
    val midHillColor = androidx.compose.ui.graphics.lerp(horizonSky, hillBase, 0.66f)
    val frontHillColor = androidx.compose.ui.graphics.lerp(horizonSky, hillBase, 0.86f)

    val backRidge = Path().apply {
        moveTo(0f, h * 0.77f)
        cubicTo(w * 0.20f, h * 0.69f, w * 0.42f, h * 0.80f, w * 0.65f, h * 0.73f)
        cubicTo(w * 0.82f, h * 0.68f, w * 0.93f, h * 0.74f, w, h * 0.76f)
        lineTo(w, h)
        lineTo(0f, h)
        close()
    }
    drawPath(backRidge, backHillColor)

    // Subtle rim light along the distant ridge.
    val rimCrest = Path().apply {
        moveTo(0f, h * 0.77f)
        cubicTo(w * 0.20f, h * 0.69f, w * 0.42f, h * 0.80f, w * 0.65f, h * 0.73f)
        cubicTo(w * 0.82f, h * 0.68f, w * 0.93f, h * 0.74f, w, h * 0.76f)
    }
    val rimColor = if (m > 0.5f) MoonColor else SunCoreColor
    drawPath(rimCrest, rimColor.copy(alpha = 0.28f * e), style = Stroke(if (detailed) 1.3.dp.toPx() else 0.9.dp.toPx(), cap = StrokeCap.Round))

    val midHill = Path().apply {
        moveTo(0f, h * 0.84f)
        cubicTo(w * 0.28f, h * 0.74f, w * 0.58f, h * 0.85f, w, h * 0.79f)
        lineTo(w, h)
        lineTo(0f, h)
        close()
    }
    drawPath(midHill, midHillColor)

    val frontHill = Path().apply {
        moveTo(0f, h * 0.89f)
        cubicTo(w * 0.36f, h * 0.94f, w * 0.68f, h * 0.83f, w, h * 0.90f)
        lineTo(w, h)
        lineTo(0f, h)
        close()
    }
    drawPath(frontHill, frontHillColor)

    // 8. Fine details on the reveal card: soaring morning birds or a glowing evening hillside window + water shimmer.
    if (detailed) {
        if (day > 0.2f && position < 0.42f) {
            val birdAlpha = 0.55f * day * e
            drawBird(w * 0.66f, h * 0.60f, w * 0.032f, HillDayBase.copy(alpha = birdAlpha))
            drawBird(w * 0.73f, h * 0.56f, w * 0.024f, HillDayBase.copy(alpha = birdAlpha * 0.8f))
        }
        if (m > 0.2f) {
            // A tiny warm lit window nestled on the midground hill.
            val wx = w * 0.27f
            val wy = h * 0.83f
            drawCircle(WindowGlowColor.copy(alpha = 0.32f * m * e), radius = w * 0.028f, center = Offset(wx, wy))
            drawCircle(WindowGlowColor.copy(alpha = 0.92f * m * e), radius = w * 0.011f, center = Offset(wx, wy))
        }
        // Subtle mist/water shimmer lines near the foot.
        val shimmerColor = (if (m > 0.5f) MoonColor else Color.White).copy(alpha = 0.22f * e)
        drawLine(shimmerColor, Offset(w * 0.34f, h * 0.935f), Offset(w * 0.66f, h * 0.935f), strokeWidth = 1.2.dp.toPx(), cap = StrokeCap.Round)
        drawLine(shimmerColor, Offset(w * 0.42f, h * 0.962f), Offset(w * 0.58f, h * 0.962f), strokeWidth = 1.0.dp.toPx(), cap = StrokeCap.Round)
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawCloudPuff(cx: Float, cy: Float, width: Float, height: Float, color: Color) {
    val path = Path().apply {
        val l = cx - width / 2f
        val r = cx + width / 2f
        val b = cy + height / 2f
        moveTo(l + height * 0.5f, b)
        lineTo(r - height * 0.5f, b)
        arcTo(Rect(r - height, b - height, r, b), 90f, -180f, false)
        arcTo(Rect(cx - width * 0.18f, cy - height * 0.95f, cx + width * 0.28f, b - height * 0.1f), 0f, -180f, false)
        arcTo(Rect(cx - width * 0.36f, cy - height * 0.65f, cx - width * 0.02f, b - height * 0.1f), 0f, -180f, false)
        arcTo(Rect(l, b - height, l + height, b), 270f, -180f, false)
        close()
    }
    drawPath(path, color)
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawBird(cx: Float, cy: Float, span: Float, color: Color) {
    val bird = Path().apply {
        moveTo(cx - span, cy + span * 0.15f)
        quadraticTo(cx - span * 0.45f, cy - span * 0.45f, cx, cy + span * 0.08f)
        quadraticTo(cx + span * 0.45f, cy - span * 0.45f, cx + span, cy + span * 0.15f)
    }
    drawPath(bird, color, style = Stroke(1.4.dp.toPx(), cap = StrokeCap.Round))
}

/** Star positions (fractions of the art) and radii; indices 0..3 form a delicate constellation in the upper-left sky. */
private val Stars = listOf(
    Triple(0.16f, 0.18f, 0.011f),
    Triple(0.27f, 0.12f, 0.010f),
    Triple(0.37f, 0.17f, 0.012f),
    Triple(0.47f, 0.10f, 0.009f),
    Triple(0.62f, 0.13f, 0.011f),
    Triple(0.75f, 0.09f, 0.009f),
    Triple(0.86f, 0.19f, 0.010f),
    Triple(0.22f, 0.31f, 0.008f),
    Triple(0.56f, 0.26f, 0.008f),
)

private val ConstellationEdges = listOf(0 to 1, 1 to 2, 2 to 3)

/** Four-point diamond stars: index 0 is the Evening Star (visible at dusk and night); 1..2 join in deep night. */
private val DiamondStars = listOf(
    Triple(0.24f, 0.22f, 0.052f),
    Triple(0.68f, 0.16f, 0.042f),
    Triple(0.42f, 0.30f, 0.036f),
)

private val StarColor = Color(0xFFFFF4D6)
private val MoonColor = Color(0xFFF5EDD6)
private val SunCoreColor = Color(0xFFFFF3C4)
private val DawnHorizonGlow = Color(0xFFFFB88C)
private val DuskHorizonGlow = Color(0xFFFF8E6E)
private val WindowGlowColor = Color(0xFFFFD175)
private val HillDayBase = Color(0xFF28385E)
private val HillNightBase = Color(0xFF0A0E24)

