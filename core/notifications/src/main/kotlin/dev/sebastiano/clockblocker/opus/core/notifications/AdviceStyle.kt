package dev.sebastiano.clockblocker.opus.core.notifications

import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes
import dev.sebastiano.clockblocker.opus.core.model.AdviceType

/** Monochrome small icon (AOD-legible), accent colour and channel for each kind of advice. */
internal data class AdviceStyle(
    @param:DrawableRes val icon: Int,
    /** Semantic advice seed colour from `docs/design.md` §2.4; used for progress segments and the accent. */
    @param:ColorInt val color: Int,
    val channel: OpusChannel,
)

internal val AdviceType.style: AdviceStyle
    get() = when (this) {
        AdviceType.SeeBrightLight -> AdviceStyle(R.drawable.ic_notif_bright_light, 0xFFFFB000.toInt(), OpusChannel.Light)
        AdviceType.SeeLight -> AdviceStyle(R.drawable.ic_notif_light, 0xFFFFD67A.toInt(), OpusChannel.Light)
        AdviceType.AvoidLight -> AdviceStyle(R.drawable.ic_notif_avoid_light, 0xFF3B2F5C.toInt(), OpusChannel.Light)
        AdviceType.Sleep -> AdviceStyle(R.drawable.ic_notif_sleep, 0xFF1E2A78.toInt(), OpusChannel.Sleep)
        AdviceType.Nap -> AdviceStyle(R.drawable.ic_notif_nap, 0xFF7C8CFF.toInt(), OpusChannel.Sleep)
        AdviceType.OptionalNap -> AdviceStyle(R.drawable.ic_notif_nap, 0xFFB0BAFF.toInt(), OpusChannel.Sleep)
        AdviceType.PeakFatigue -> AdviceStyle(R.drawable.ic_notif_peak_fatigue, 0xFFFF5A4E.toInt(), OpusChannel.Sleep)
        AdviceType.Melatonin -> AdviceStyle(
            R.drawable.ic_notif_melatonin,
            0xFFB69DF8.toInt(),
            OpusChannel.SupplementsAndCaffeine,
        )
        AdviceType.Caffeine -> AdviceStyle(
            R.drawable.ic_notif_caffeine,
            0xFFB5652B.toInt(),
            OpusChannel.SupplementsAndCaffeine,
        )
        AdviceType.AvoidCaffeine -> AdviceStyle(
            R.drawable.ic_notif_avoid_caffeine,
            0xFF8A6A55.toInt(),
            OpusChannel.SupplementsAndCaffeine,
        )
        // Flights never remind; the travel-day Live Update chooses its own channel.
        AdviceType.Flight -> AdviceStyle(R.drawable.ic_notif_flight, 0xFF00A3A3.toInt(), OpusChannel.Now)
    }

/** Neutral segment colour for stretches of the travel day without advice. */
@ColorInt
internal const val NEUTRAL_SEGMENT_COLOR: Int = 0xFF9E9E9E.toInt()

/** Twilight Indigo seed, the app accent for neutral notifications. */
@ColorInt
internal const val BRAND_COLOR: Int = 0xFF4F46E5.toInt()
