package dev.sebastiano.clockblocker.opus.widgets

import android.app.Application
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import dev.sebastiano.clockblocker.opus.core.data.PinnableWidget
import dev.sebastiano.clockblocker.opus.core.data.WidgetPinning
import dev.sebastiano.clockblocker.opus.widget.NextUpWidgetProvider
import dev.sebastiano.clockblocker.opus.widget.TwoClocksWidgetProvider
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject

/** [WidgetPinning] over [AppWidgetManager]: `:app` is the one module that knows both Settings and the widget providers. */
@ContributesBinding(AppScope::class)
@Inject
class AppWidgetPinning(private val application: Application) : WidgetPinning {
    private val manager: AppWidgetManager? get() = AppWidgetManager.getInstance(application)

    override fun isSupported(): Boolean = manager?.isRequestPinAppWidgetSupported == true

    override fun requestPin(widget: PinnableWidget): Boolean {
        val provider = when (widget) {
            PinnableWidget.TwoClocks -> TwoClocksWidgetProvider::class.java
            PinnableWidget.NextUp -> NextUpWidgetProvider::class.java
        }
        return try {
            manager?.requestPinAppWidget(ComponentName(application, provider), null, null) == true
        } catch (_: IllegalStateException) {
            // Thrown when the caller isn't in the foreground; the button stays for another try.
            false
        }
    }
}
