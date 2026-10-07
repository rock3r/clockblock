package dev.sebastiano.clockblocker.opus.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.android.BroadcastReceiverKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

/**
 * Base provider: every system callback re-renders through the shared [WidgetUpdater]. Providers are constructed
 * by metrox-android's `MetroAppComponentFactory` with constructor injection.
 */
abstract class ClockblockWidgetProvider(private val kind: WidgetKind, private val updater: WidgetUpdater) :
    AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        goAsync { updater.update(kind, appWidgetIds) }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle,
    ) {
        goAsync { updater.update(kind, intArrayOf(appWidgetId)) }
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            // Clock/zone/locale changes alter every time label and the dial's zone offsets.
            Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED, Intent.ACTION_LOCALE_CHANGED ->
                goAsync { updater.update(kind, updater.ids(kind)) }
            else -> super.onReceive(context, intent)
        }
    }
}

@ContributesIntoMap(AppScope::class, binding<BroadcastReceiver>())
@BroadcastReceiverKey(TwoClocksWidgetProvider::class)
@Inject
class TwoClocksWidgetProvider(updater: WidgetUpdater) : ClockblockWidgetProvider(WidgetKind.TwoClocks, updater)

@ContributesIntoMap(AppScope::class, binding<BroadcastReceiver>())
@BroadcastReceiverKey(NextUpWidgetProvider::class)
@Inject
class NextUpWidgetProvider(updater: WidgetUpdater) : ClockblockWidgetProvider(WidgetKind.NextUp, updater)

/** Runs [block] off the main thread within the broadcast's lifetime (pattern from the androidx RC widget demo). */
internal fun BroadcastReceiver.goAsync(block: suspend CoroutineScope.() -> Unit) {
    val scope = CoroutineScope(Dispatchers.Default)
    val pending = goAsync()
    scope.launch {
        try {
            try {
                coroutineScope { block() }
            } catch (e: Throwable) {
                if (e !is CancellationException) Log.e("ClockblockWidget", "Widget update failed", e)
            } finally {
                scope.cancel()
            }
        } finally {
            runCatching { pending?.finish() }
        }
    }
}
