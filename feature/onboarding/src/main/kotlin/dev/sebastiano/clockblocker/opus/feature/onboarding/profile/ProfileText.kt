package dev.sebastiano.clockblocker.opus.feature.onboarding.profile

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.sebastiano.clockblocker.opus.core.model.Chronotype
import dev.sebastiano.clockblocker.opus.core.model.Intensity
import dev.sebastiano.clockblocker.opus.feature.onboarding.R

/** Short name of a chronotype ("Early bird" … "Night owl"). */
@Composable
fun Chronotype.label(): String = stringResource(
    when (this) {
        Chronotype.DefiniteMorning -> R.string.chronotype_definite_morning
        Chronotype.ModerateMorning -> R.string.chronotype_moderate_morning
        Chronotype.Intermediate -> R.string.chronotype_intermediate
        Chronotype.ModerateEvening -> R.string.chronotype_moderate_evening
        Chronotype.DefiniteEvening -> R.string.chronotype_definite_evening
    },
)

/** One-line description of a chronotype. */
@Composable
fun Chronotype.description(): String = stringResource(
    when (this) {
        Chronotype.DefiniteMorning -> R.string.chronotype_definite_morning_description
        Chronotype.ModerateMorning -> R.string.chronotype_moderate_morning_description
        Chronotype.Intermediate -> R.string.chronotype_intermediate_description
        Chronotype.ModerateEvening -> R.string.chronotype_moderate_evening_description
        Chronotype.DefiniteEvening -> R.string.chronotype_definite_evening_description
    },
)

/** "Gentle" / "Balanced" / "Max". */
@Composable
fun Intensity.label(): String = stringResource(
    when (this) {
        Intensity.Gentle -> R.string.effort_gentle
        Intensity.Balanced -> R.string.effort_balanced
        Intensity.Max -> R.string.effort_max
    },
)

/** One-line explanation of an effort level. */
@Composable
fun Intensity.description(): String = stringResource(
    when (this) {
        Intensity.Gentle -> R.string.effort_gentle_description
        Intensity.Balanced -> R.string.effort_balanced_description
        Intensity.Max -> R.string.effort_max_description
    },
)

/**
 * Shape for item [index] of [count] in a segmented group (M3 Expressive grouped list): large outer corners on the
 * group's ends, small inner corners between items, so a group reads as one object with separate rows.
 */
fun segmentedShape(index: Int, count: Int, outer: Dp = 24.dp, inner: Dp = 6.dp): Shape {
    val top = if (index == 0) outer else inner
    val bottom = if (index == count - 1) outer else inner
    return RoundedCornerShape(topStart = top, topEnd = top, bottomStart = bottom, bottomEnd = bottom)
}

/** Gap between items of a segmented group. */
val SegmentedGap: Dp = 3.dp
