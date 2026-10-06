package dev.sebastiano.clockblocker.opus.widget

import android.app.Activity
import android.app.Application
import android.app.Service
import android.content.BroadcastReceiver
import android.content.ContentProvider
import android.content.Context
import android.content.Intent
import dev.sebastiano.clockblocker.opus.core.notifications.receiver.AdviceActionReceiver
import dev.sebastiano.clockblocker.opus.core.notifications.receiver.AdviceAlarmReceiver
import dev.sebastiano.clockblocker.opus.core.notifications.receiver.ScheduleResetReceiver
import dev.zacsweers.metrox.android.MetroAppComponentProviders
import dev.zacsweers.metrox.android.MetroApplication
import kotlin.reflect.KClass

/**
 * metrox-android merges `MetroAppComponentFactory` into this module's manifest, which requires the
 * application to be a [MetroApplication]. Robolectric uses this one (see `robolectric.properties`) and
 * instantiates the manifest widget receivers through it.
 */
class TestWidgetApplication : Application(), MetroApplication {
    val updater: WidgetUpdater by lazy { WidgetUpdater(this) }

    override val appComponentProviders: MetroAppComponentProviders = object : MetroAppComponentProviders {
        override val activityProviders: Map<KClass<out Activity>, () -> Activity> = emptyMap()
        override val providerProviders: Map<KClass<out ContentProvider>, () -> ContentProvider> = emptyMap()
        override val receiverProviders: Map<KClass<out BroadcastReceiver>, () -> BroadcastReceiver> = mapOf(
            TwoClocksWidgetProvider::class to { TwoClocksWidgetProvider(updater) },
            NextUpWidgetProvider::class to { NextUpWidgetProvider(updater) },
            // :core:notifications receivers (merged in for the Done action) are registered by Robolectric too;
            // widget tests never deliver to them, so inert stand-ins are enough.
            AdviceAlarmReceiver::class to ::InertReceiver,
            AdviceActionReceiver::class to ::InertReceiver,
            ScheduleResetReceiver::class to ::InertReceiver,
        )
        override val serviceProviders: Map<KClass<out Service>, () -> Service> = emptyMap()
    }
}

private class InertReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = Unit
}
