package dev.sebastiano.clockblocker.opus.feature.settings

import dev.sebastiano.clockblocker.opus.core.data.PinnableWidget
import dev.sebastiano.clockblocker.opus.core.data.WidgetPinning

/** Scriptable [WidgetPinning]: records pin requests. */
class FakeWidgetPinning(var supported: Boolean = true, var accepts: Boolean = true) : WidgetPinning {
    val requested = mutableListOf<PinnableWidget>()

    override fun isSupported(): Boolean = supported

    override fun requestPin(widget: PinnableWidget): Boolean {
        requested += widget
        return supported && accepts
    }
}
