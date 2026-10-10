package dev.sebastiano.clockblocker.opus.core.model

import kotlinx.serialization.Serializable

/** What the inner (body) ring of a Two skies dial shows (design.md §2.3 A). */
@Serializable
enum class BodyRingMode {
    /** The local sky turned by the jet lag: the sky your body thinks it is under. The default everywhere. */
    Simple,

    /** The planner's biological night (≈ melatonin onset → habitual wake) as the body's night. */
    Precise,
}

/**
 * One placed widget's own options, set from its configuration screen (#52). Every option has a default, so a widget
 * nobody configured, and a file written before an option existed, both read as the defaults.
 *
 * @property bodyRing how the Two clocks dial draws the body ring.
 */
@Serializable
data class WidgetConfig(val bodyRing: BodyRingMode = BodyRingMode.Simple)
