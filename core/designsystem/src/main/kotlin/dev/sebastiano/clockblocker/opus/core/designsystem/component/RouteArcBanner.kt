package dev.sebastiano.clockblocker.opus.core.designsystem.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import dev.sebastiano.clockblocker.opus.core.designsystem.R
import dev.sebastiano.clockblocker.opus.core.designsystem.illustration.sharedUnitPath
import dev.sebastiano.clockblocker.opus.core.designsystem.theme.DotMatrixStyle
import dev.sebastiano.clockblocker.opus.core.designsystem.theme.LocalReduceMotion
import dev.sebastiano.clockblocker.opus.core.designsystem.theme.OpusTheme
import java.time.Duration
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.min

/** Colours of a [RouteArcBanner]. [background] is the surface it sits on (destination core, plane halo). */
@Immutable
data class RouteArcColors(
    val route: Color,
    val progress: Color,
    val origin: Color,
    val destination: Color,
    val plane: Color,
    val background: Color,
    val code: Color,
    val emptyCode: Color,
    val caption: Color,
)

object RouteArcDefaults {
    /** Height of the arc area; grows with the code size so large text never squashes the arc. */
    val ArcHeight: Dp = 56.dp

    /** The flattest arc a set route draws (a short hop), as a fraction of the available height. */
    const val MinApex = 0.35f

    private const val LongHaulHours = 14f
    private const val LongHaulKm = 12_000.0

    /** Arc height for a flight of [duration]: short hops stay low, ~14 h long-hauls use the full height. */
    fun apexForDuration(duration: Duration): Float {
        val hours = duration.toMinutes() / 60f
        if (hours <= 0f) return MinApex
        return (MinApex + (1f - MinApex) * hours / LongHaulHours).coerceIn(MinApex, 1f)
    }

    /** Arc height for a great-circle distance in km (12,000 km and up use the full height). */
    fun apexForDistance(km: Double): Float {
        if (km <= 0.0) return MinApex
        return (MinApex + (1f - MinApex) * (km / LongHaulKm).toFloat()).coerceIn(MinApex, 1f)
    }

    /**
     * Pass [background] = the container colour when the banner sits on a card, so the destination ring's core
     * and the plane's halo cut out cleanly.
     */
    @Composable
    fun colors(
        background: Color = MaterialTheme.colorScheme.surface,
        route: Color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
        progress: Color = MaterialTheme.colorScheme.primary,
        origin: Color = MaterialTheme.colorScheme.primary,
        destination: Color = MaterialTheme.colorScheme.tertiary,
        plane: Color = MaterialTheme.colorScheme.tertiary,
        code: Color = MaterialTheme.colorScheme.onSurface,
        emptyCode: Color = MaterialTheme.colorScheme.onSurfaceVariant,
        caption: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    ): RouteArcColors = RouteArcColors(route, progress, origin, destination, plane, background, code, emptyCode, caption)
}

/** Control-point y of a quadratic arc between two points on [baseY] whose peak rises [apex] px above it. */
internal fun routeControlY(baseY: Float, apex: Float): Float = baseY - 2f * apex

/** Distance along a route of [length] where the plane sits at [progress], kept [inset] clear of both ends. */
internal fun planeDistance(progress: Float, length: Float, inset: Float): Float {
    val i = min(inset, length / 2f)
    return i + (length - 2f * i) * progress.coerceIn(0f, 1f)
}

/**
 * **From ──✈── To**: two dot-matrix airport codes bridged by a great-circle arc (design.md §2.3 E, flattened
 * into a banner for cards and editor headers).
 *
 * - The whole route is a muted dashed arc; the flown part `[0, progress]` is overdrawn solid.
 * - Origin dot, destination ring, and a `MaterialShapes.Arrow` plane oriented along the arc's tangent
 *   (`PathMeasure` position and tangent, read in the draw phase).
 * - Only [origin] set: a flat dotted horizon with the plane resting at the origin and an empty `· · ·`
 *   destination slot. When [destination] arrives, its code reveals, the arc springs up to [apex]
 *   (`OpusMotion.containerSpatial()`) and the plane glides to [progress] (`OpusMotion.dataSpatial()`: the plane's
 *   position is data, so it never bounces). Reduce motion snaps both; the still picture carries the same meaning.
 * - Mirrors in right-to-left layouts (origin on the start side).
 *
 * TalkBack reads one phrase, e.g. "From San Francisco, S F O to Amsterdam, A M S"; pass [contentDescription] to
 * add flight progress when the plane is live (the default doesn't announce it, since editors use it as a preview).
 *
 * @param progress plane position along the route, 0 = departure, 1 = arrival.
 * @param apex arc height (0–1 of the available height) once a destination is set; see
 *   [RouteArcDefaults.apexForDuration] / [RouteArcDefaults.apexForDistance].
 * @param originCaption / [destinationCaption] optional city names under the codes.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun RouteArcBanner(
    origin: String?,
    destination: String?,
    modifier: Modifier = Modifier,
    progress: Float = 0f,
    apex: Float = 1f,
    originCaption: String? = null,
    destinationCaption: String? = null,
    codeStyle: DotMatrixStyle = OpusTheme.textStyles.iataDisplay,
    arcHeight: Dp = RouteArcDefaults.ArcHeight,
    colors: RouteArcColors = RouteArcDefaults.colors(),
    contentDescription: String = routeArcDescription(origin, destination, originCaption, destinationCaption),
) {
    val hasOrigin = !origin.isNullOrBlank()
    val hasDestination = !destination.isNullOrBlank()
    val motion = OpusTheme.motion
    val reduce = LocalReduceMotion.current

    // Starts at rest (no entrance); later changes animate. Both values are read only in the draw phase.
    val apexTarget = if (hasDestination) apex.coerceIn(0f, 1f) else 0f
    val planeTarget = if (hasDestination) progress.coerceIn(0f, 1f) else 0f
    val apexValue = remember { Animatable(apexTarget) }
    val planeValue = remember { Animatable(planeTarget) }
    LaunchedEffect(apexTarget, reduce) {
        if (reduce) apexValue.snapTo(apexTarget) else apexValue.animateTo(apexTarget, motion.containerSpatial())
    }
    LaunchedEffect(planeTarget, reduce) {
        if (reduce) planeValue.snapTo(planeTarget) else planeValue.animateTo(planeTarget, motion.dataSpatial())
    }
    val originCode by animateColorAsState(if (hasOrigin) colors.code else colors.emptyCode, motion.colour(), label = "originCode")
    val destinationCode by animateColorAsState(if (hasDestination) colors.code else colors.emptyCode, motion.colour(), label = "destCode")

    val codeHeight = with(LocalDensity.current) { codeStyle.glyphHeight.toDp() }
    val canvasHeight = max(arcHeight, codeHeight + 20.dp)
    val route = remember { Path() }
    val flown = remember { Path() }
    val measure = remember { PathMeasure() }

    Column(modifier.clearAndSetSemantics { this.contentDescription = contentDescription }) {
        Row(verticalAlignment = Alignment.Bottom) {
            IataCode(origin, style = codeStyle, color = originCode, contentDescription = null)
            Canvas(Modifier.weight(1f).height(canvasHeight)) {
                val mirror = layoutDirection == LayoutDirection.Rtl
                scale(if (mirror) -1f else 1f, 1f) {
                    drawRoute(
                        RouteFrame(codeHeight.toPx(), apexValue.value, planeValue.value, hasOrigin, hasDestination),
                        colors, route, flown, measure,
                    )
                }
            }
            IataCode(destination, style = codeStyle, color = destinationCode, contentDescription = null)
        }
        if (originCaption != null || destinationCaption != null) {
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                val captionStyle = MaterialTheme.typography.labelMedium
                Text(
                    originCaption.orEmpty(), Modifier.weight(1f), style = captionStyle, color = colors.caption,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Text(
                    destinationCaption.orEmpty(), Modifier.weight(1f), style = captionStyle, color = colors.caption,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.End,
                )
            }
        }
    }
}

/** One frame's inputs (animated values already read, in the draw phase). */
private class RouteFrame(
    val codeHeight: Float,
    val apex: Float,
    val plane: Float,
    val hasOrigin: Boolean,
    val hasDestination: Boolean,
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private fun DrawScope.drawRoute(f: RouteFrame, colors: RouteArcColors, route: Path, flown: Path, measure: PathMeasure) {
    val gap = 10.dp.toPx()
    val stroke = 2.dp.toPx()
    val planeSize = 16.dp.toPx()
    val baseY = size.height - f.codeHeight / 2f
    val x0 = gap
    val x1 = size.width - gap
    if (x1 <= x0) return
    val maxApex = (baseY - planeSize * 0.75f - stroke).coerceAtLeast(0f)
    // The spring may overshoot upwards (the "apex spring"); never let the arc dip below the horizon.
    val apexPx = (f.apex * maxApex).coerceAtLeast(0f)
    // 0 = flat dotted horizon, 1 = dashed arc: the dash grows as the arc lifts.
    val lift = (f.apex / RouteArcDefaults.MinApex).coerceIn(0f, 1f)

    route.reset()
    route.moveTo(x0, baseY)
    route.quadraticTo((x0 + x1) / 2f, routeControlY(baseY, apexPx), x1, baseY)
    measure.setPath(route, false)
    val length = measure.length

    val dash = 0.01f + (5.dp.toPx() - 0.01f) * lift
    drawPath(
        route,
        colors.route,
        style = Stroke(stroke, cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash, 5.dp.toPx()))),
    )

    val at = planeDistance(f.plane, length, inset = planeSize * 0.9f)
    if (f.hasDestination && f.hasOrigin) {
        flown.reset()
        measure.getSegment(0f, at, flown, true)
        drawPath(flown, colors.progress, style = Stroke(stroke * 1.25f, cap = StrokeCap.Round))
    }

    drawCircle(if (f.hasOrigin) colors.origin else colors.route, 4.5.dp.toPx(), Offset(x0, baseY))
    if (f.hasDestination) {
        drawCircle(colors.destination, 6.dp.toPx(), Offset(x1, baseY))
        drawCircle(colors.background, 2.5.dp.toPx(), Offset(x1, baseY))
    } else {
        drawCircle(colors.route, 5.dp.toPx(), Offset(x1, baseY), style = Stroke(1.5.dp.toPx()))
    }

    // The plane: MaterialShapes.Arrow points up; turn it onto the tangent, with a halo cut from the background.
    val pos = measure.getPosition(at)
    val tan = measure.getTangent(at)
    val heading = atan2(tan.y, tan.x) * 180f / PI.toFloat() + 90f
    val arrow = MaterialShapes.Arrow.sharedUnitPath()
    val planeColor = if (f.hasOrigin) colors.plane else colors.route
    for ((k, color) in listOf(1.35f to colors.background, 1f to planeColor)) {
        withTransform({
            translate(pos.x, pos.y)
            rotate(heading, pivot = Offset.Zero)
            scale(planeSize * k, planeSize * k, pivot = Offset.Zero)
        }) { drawPath(arrow, color) }
    }
}

/** The banner's spoken phrase: "From San Francisco, S F O to Amsterdam, A M S", or which end is missing. */
@Composable
fun routeArcDescription(origin: String?, destination: String?, originCaption: String?, destinationCaption: String?): String {
    fun place(code: String?, caption: String?): String? = when {
        !code.isNullOrBlank() && caption != null -> "$caption, ${spellOut(code)}"
        !code.isNullOrBlank() -> spellOut(code)
        else -> caption
    }
    val from = place(origin, originCaption)
    val to = place(destination, destinationCaption)
    return when {
        from != null && to != null -> stringResource(R.string.route_description, from, to)
        from != null -> stringResource(R.string.route_description_origin_only, from)
        to != null -> stringResource(R.string.route_description_destination_only, to)
        else -> stringResource(R.string.route_description_empty)
    }
}
