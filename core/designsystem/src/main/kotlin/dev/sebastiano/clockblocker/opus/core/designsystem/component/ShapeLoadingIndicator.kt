package dev.sebastiano.clockblocker.opus.core.designsystem.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.unit.dp
import dev.sebastiano.clockblocker.opus.core.designsystem.R
import dev.sebastiano.clockblocker.opus.core.designsystem.illustration.sharedUnitPath
import dev.sebastiano.clockblocker.opus.core.designsystem.theme.ClockblockTheme

/** The plan-computing sequence (design.md §2.4: "a day passing" — sun, pill, star, moon-round). */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
val PlanLoadingPolygons = listOf(MaterialShapes.Sunny, MaterialShapes.Pill, MaterialShapes.PuffyDiamond, MaterialShapes.Circle)

/**
 * Indeterminate "working out your plan" indicator: an M3 Expressive [LoadingIndicator] morphing through
 * [PlanLoadingPolygons]. Under reduce motion it is a still Sunny (the morph loop is self-timed, so it stops), and
 * TalkBack announces an indeterminate progress bar labelled [contentDescription] either way.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ShapeLoadingIndicator(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    contentDescription: String = stringResource(R.string.loading_plan),
) {
    val semantics = Modifier.clearAndSetSemantics {
        this.contentDescription = contentDescription
        progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate
    }
    if (ClockblockTheme.reduceMotion) {
        val sunny = MaterialShapes.Sunny.sharedUnitPath()
        Canvas(modifier.size(48.dp).then(semantics)) {
            // LoadingIndicator draws its active shape at ~38/48 of the container.
            val s = size.minDimension * 0.79f
            translate(size.width / 2f, size.height / 2f) {
                scale(s, s, pivot = Offset.Zero) { drawPath(sunny, color) }
            }
        }
    } else {
        LoadingIndicator(modifier = modifier.then(semantics), color = color, polygons = PlanLoadingPolygons)
    }
}
