package dev.sebastiano.clockblocker.opus.core.data

/** The home-screen widgets Settings can offer to pin. */
enum class PinnableWidget { TwoClocks, NextUp }

/**
 * Asks the launcher to pin one of our widgets (`AppWidgetManager.requestPinAppWidget`). A contract so
 * `:feature:settings` needn't depend on `:widget`; bound where the widget providers are known (`:app`).
 */
interface WidgetPinning {
    /** Whether the current launcher accepts pin requests. Read it again on resume: the default launcher can change. */
    fun isSupported(): Boolean

    /** Shows the launcher's "add widget" prompt; returns `false` when the launcher refused (or can't pin). */
    fun requestPin(widget: PinnableWidget): Boolean
}
