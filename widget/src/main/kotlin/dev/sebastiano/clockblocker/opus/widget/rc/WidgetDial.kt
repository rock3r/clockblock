package dev.sebastiano.clockblocker.opus.widget.rc

import android.content.Context
import androidx.compose.remote.creation.compose.layout.RemoteCanvas
import androidx.compose.remote.creation.compose.layout.RemoteComposable
import androidx.compose.remote.creation.compose.modifier.RemoteModifier
import androidx.compose.remote.creation.compose.modifier.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import dev.sebastiano.clockblocker.opus.core.designsystem.dial.DialPalettes
import dev.sebastiano.clockblocker.opus.core.designsystem.dial.ResourceDialLabels
import dev.sebastiano.clockblocker.opus.core.designsystem.dial.spec.Argb
import dev.sebastiano.clockblocker.opus.core.designsystem.dial.spec.BodyRingMode
import dev.sebastiano.clockblocker.opus.core.designsystem.dial.spec.DialLabels
import dev.sebastiano.clockblocker.opus.core.designsystem.dial.spec.DialPalette
import dev.sebastiano.clockblocker.opus.core.designsystem.dial.spec.DialSpec
import dev.sebastiano.clockblocker.opus.core.designsystem.dial.spec.TwoSkies
import dev.sebastiano.clockblocker.opus.core.designsystem.dial.spec.TwoStrips
import dev.sebastiano.clockblocker.opus.core.designsystem.time.TimeFormatter
import dev.sebastiano.clockblocker.opus.widget.draw.WidgetPalette
import dev.sebastiano.clockblocker.opus.widget.draw.WidgetTheme
import dev.sebastiano.clockblocker.opus.widget.state.WidgetState
import dev.sebastiano.clockblocker.opus.widget.text.DialType

/** Which of the shared dial designs a widget region draws (issue #46). */
enum class DialDesign {
    /** The Two skies dial: Square, Tall and Large. */
    TwoSkies,

    /** The Two strips: the 1×1, the 2×1 / 4×1 rows and the Wide card, where a round dial gets too small to read. */
    TwoStrips,
}

/**
 * The widgets' dial: the app's shared spec ([TwoSkies], [TwoStrips]) laid out at capture time for the region's size
 * at the bucket's minimum, in the system font, then replayed by [drawDialSpec]. Its level (Glance, Simple, Full)
 * comes from that size in dp, the same rule the app uses.
 */
internal object WidgetDial {

    /** The dial palette for a widget theme: the app's, with the face matched to the widget's surface. */
    fun palette(p: WidgetPalette): DialPalette = palettes.getValue(p.theme).let { base ->
        // Remembered per theme: the spec caches its skies by palette identity.
        faced.getOrPut(p.theme to p.surface) { base.copy(face = Argb(p.surface)) }
    }

    private val palettes: Map<WidgetTheme, DialPalette> by lazy {
        mapOf(WidgetTheme.Light to DialPalettes.Light, WidgetTheme.Dark to DialPalettes.Dark, WidgetTheme.NightSafe to DialPalettes.NightSafe)
    }
    private val faced = java.util.concurrent.ConcurrentHashMap<Pair<WidgetTheme, Int>, DialPalette>()

    /** The app's dial labels, in the widget's 12/24-hour setting and the device locale. */
    fun labels(context: Context, is24Hour: Boolean): DialLabels {
        val locale = context.resources.configuration.locales[0] ?: java.util.Locale.getDefault()
        return ResourceDialLabels(context.resources, TimeFormatter(is24Hour, locale))
    }

    /**
     * [design] for [state] laid out in a [widthDp] × [heightDp] region (its size at the bucket's minimum).
     *
     * @param bodyRing the body ring's mode: Simple for now; #52 makes Precise a per-widget option.
     */
    fun spec(
        context: Context,
        state: WidgetState.Active,
        palette: WidgetPalette,
        is24Hour: Boolean,
        design: DialDesign,
        widthDp: Float,
        heightDp: Float,
        bodyRing: BodyRingMode = BodyRingMode.Simple,
    ): DialSpec {
        val type = DialType.of(context)
        val labels = labels(context, is24Hour)
        val growth = DialType.textGrowth(context)
        return when (design) {
            DialDesign.TwoSkies -> TwoSkies.spec(
                state.dial, palette(palette), labels, widthDp, heightDp, mode = bodyRing, measurer = type.measurer, textGrowth = growth,
                namePlace = !state.redacted,
            )
            DialDesign.TwoStrips -> TwoStrips.spec(
                state.dial, palette(palette), labels, widthDp, heightDp, mode = bodyRing, measurer = type.measurer, textGrowth = growth,
                placeOptions = state.placeNameOptions(state.displayZoneId).let { names ->
                    // The trip's stop (or the zone's city) and its shorter forms; redacted widgets name no place.
                    if (state.redacted) emptyList() else names
                },
            )
        }
    }

    /** What the host's clock drives on the dial of [state]. */
    fun clocks(context: Context, state: WidgetState.Active, is24Hour: Boolean): LiveClocks {
        val labels = labels(context, is24Hour)
        return LiveClocks(
            localOffsetMinutes = state.displayOffsetMinutes,
            bodyOffsetMinutes = state.bodyOffsetMinutes,
            is24Hour = is24Hour,
            am = labels.marker(MORNING_MINUTE) ?: "AM",
            pm = labels.marker(EVENING_MINUTE) ?: "PM",
        )
    }

    private const val MORNING_MINUTE = 60f
    private const val EVENING_MINUTE = 13 * 60f
}

/**
 * The dial of [model] in a canvas filling its parent, laid out for [widthDp] × [heightDp] (the region at the
 * bucket's minimum) and scaled to the real size on the host.
 */
@RemoteComposable
@Composable
internal fun WidgetDialCanvas(model: WidgetModel, design: DialDesign, widthDp: Float, heightDp: Float) {
    val state = model.state as? WidgetState.Active ?: return
    val context = LocalContext.current
    val spec = WidgetDial.spec(context, state, model.palette, model.texts.is24Hour, design, widthDp, heightDp, model.bodyRing)
    val clocks = WidgetDial.clocks(context, state, model.texts.is24Hour)
    val type = DialType.of(context)
    RemoteCanvas(modifier = RemoteModifier.fillMaxSize()) { drawDialSpec(spec, type, clocks) }
}
