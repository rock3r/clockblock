package dev.sebastiano.clockblocker.opus.core.designsystem.advice

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.res.stringResource
import dev.sebastiano.clockblocker.opus.core.designsystem.R
import dev.sebastiano.clockblocker.opus.core.model.AdviceType

/** String resource of the advice label ("See bright light"). Use from non-Compose code (notifications, widgets). */
@get:StringRes
val AdviceType.labelRes: Int
    get() = when (this) {
        AdviceType.SeeBrightLight -> R.string.advice_label_see_bright_light
        AdviceType.SeeLight -> R.string.advice_label_see_light
        AdviceType.AvoidLight -> R.string.advice_label_avoid_light
        AdviceType.Sleep -> R.string.advice_label_sleep
        AdviceType.Nap -> R.string.advice_label_nap
        AdviceType.OptionalNap -> R.string.advice_label_optional_nap
        AdviceType.Melatonin -> R.string.advice_label_melatonin
        AdviceType.Caffeine -> R.string.advice_label_caffeine
        AdviceType.AvoidCaffeine -> R.string.advice_label_avoid_caffeine
        AdviceType.PeakFatigue -> R.string.advice_label_peak_fatigue
        AdviceType.Flight -> R.string.advice_label_flight
    }

/** String resource of the one-line instruction ("Outside is best. A window seat counts."). */
@get:StringRes
val AdviceType.shortInstructionRes: Int
    get() = when (this) {
        AdviceType.SeeBrightLight -> R.string.advice_instruction_see_bright_light
        AdviceType.SeeLight -> R.string.advice_instruction_see_light
        AdviceType.AvoidLight -> R.string.advice_instruction_avoid_light
        AdviceType.Sleep -> R.string.advice_instruction_sleep
        AdviceType.Nap -> R.string.advice_instruction_nap
        AdviceType.OptionalNap -> R.string.advice_instruction_optional_nap
        AdviceType.Melatonin -> R.string.advice_instruction_melatonin
        AdviceType.Caffeine -> R.string.advice_instruction_caffeine
        AdviceType.AvoidCaffeine -> R.string.advice_instruction_avoid_caffeine
        AdviceType.PeakFatigue -> R.string.advice_instruction_peak_fatigue
        AdviceType.Flight -> R.string.advice_instruction_flight
    }

/** The advice label. Every card shows this next to its glyph. */
@Composable
@ReadOnlyComposable
fun AdviceType.label(): String = stringResource(labelRes)

/** One precise, kind sentence telling the user what to do. */
@Composable
@ReadOnlyComposable
fun AdviceType.shortInstruction(): String = stringResource(shortInstructionRes)
