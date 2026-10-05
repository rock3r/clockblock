package dev.sebastiano.clockblocker.opus.core.designsystem.component

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.WavyProgressIndicatorDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.sebastiano.clockblocker.opus.core.designsystem.R
import dev.sebastiano.clockblocker.opus.core.designsystem.theme.OpusTheme
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * How wavy the adaptation line is: the remaining misalignment as a fraction of the initial one (0 = flat,
 * 1 = full amplitude). A trip with no initial jet lag is flat.
 */
fun misalignmentFraction(currentHours: Float, initialHours: Float): Float {
    val initial = abs(initialHours)
    if (initial < 1e-3f) return 0f
    return (abs(currentHours) / initial).coerceIn(0f, 1f)
}

/**
 * "Wavy = jet-lagged, flat = adapted" (design.md §2.4 D). A [LinearWavyProgressIndicator] whose fill is the
 * adaptation [progress] (0–1) and whose wave amplitude is [misalignment] (0–1, see [misalignmentFraction]):
 * the line literally calms down as you adapt.
 *
 * The amplitude carries the meaning, so the wave is still by default: frequent surfaces (plan, trip cards) must
 * not run a travelling loop the whole time they are on screen (T-009). [travel] opts a rare surface into the M3
 * wave travel; reduce motion always stops it. TalkBack reads "60% adapted, about 2 h to go" ([remaining] is a
 * preformatted duration).
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun WavyAdaptationIndicator(
    progress: Float,
    misalignment: Float,
    modifier: Modifier = Modifier,
    remaining: String? = null,
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.secondaryContainer,
    wavelength: Dp = WavyProgressIndicatorDefaults.LinearDeterminateWavelength,
    travel: Boolean = false,
) {
    val p = progress.coerceIn(0f, 1f)
    val amplitude = misalignment.coerceIn(0f, 1f)
    val percent = (p * 100).roundToInt()
    val description = if (remaining != null) {
        stringResource(R.string.adaptation_description_remaining, percent, remaining)
    } else {
        stringResource(R.string.adaptation_description, percent)
    }
    LinearWavyProgressIndicator(
        progress = { p },
        modifier = modifier.clearAndSetSemantics {
            contentDescription = description
            progressBarRangeInfo = ProgressBarRangeInfo(p, 0f..1f)
        },
        color = color,
        trackColor = trackColor,
        amplitude = { amplitude },
        wavelength = wavelength,
        waveSpeed = if (travel && !OpusTheme.reduceMotion) wavelength else 0.dp,
    )
}
