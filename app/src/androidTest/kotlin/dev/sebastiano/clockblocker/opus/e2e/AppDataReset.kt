package dev.sebastiano.clockblocker.opus.e2e

import android.app.Instrumentation
import android.content.Context
import android.os.SystemClock
import androidx.core.app.NotificationManagerCompat
import androidx.datastore.core.DataStore
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import dev.sebastiano.clockblocker.opus.AppGraph
import kotlinx.coroutines.runBlocking
import java.io.File

/**
 * Puts the app's persistent state back to a fresh install, in-process (see [ClockblockE2eTest] for why not `pm clear`).
 *
 * - Each repository's DataStore is reset to its default document through the repository singleton the app itself
 *   uses, so the reset flows to every observer (the shell sees the profile disappear, plans and widgets clear).
 *   The store field is private to `:core:data`, hence reflection; every document class has all-default
 *   constructor parameters, i.e. a no-arg constructor on the JVM, which is the store's default value.
 * - Every SharedPreferences file (snooze, plan celebration flags, widget state) is cleared through the
 *   process-wide cached instance.
 * - Notifications are cancelled.
 */
internal object AppDataReset {

    fun reset(context: Context, graph: AppGraph) = runBlocking {
        listOf(graph.profileRepository, graph.tripRepository, graph.settingsRepository, graph.adviceLogRepository)
            .forEach { repository -> repository.dataStore().resetToDefault() }
        File(context.applicationInfo.dataDir, "shared_prefs").listFiles { file -> file.extension == "xml" }?.forEach { file ->
            context.getSharedPreferences(file.nameWithoutExtension, Context.MODE_PRIVATE).edit().clear().commit()
        }
        cancelNotifications(context)
    }

    fun cancelNotifications(context: Context) = NotificationManagerCompat.from(context).cancelAll()

    @Suppress("UNCHECKED_CAST")
    private fun Any.dataStore(): DataStore<Any> {
        val field = generateSequence<Class<*>>(javaClass) { it.superclass }
            .flatMap { it.declaredFields.asSequence() }
            .firstOrNull { DataStore::class.java.isAssignableFrom(it.type) }
            ?: error("${javaClass.name} has no DataStore field: update AppDataReset")
        field.isAccessible = true
        return field.get(this) as DataStore<Any>
    }

    private suspend fun DataStore<Any>.resetToDefault() {
        updateData { current -> current.javaClass.getDeclaredConstructor().newInstance() }
    }
}

/** Finishes every live activity of the app (and waits for them to go), so each test launches into a clean task. */
internal object Activities {
    private val live = Stage.values().toList() - Stage.DESTROYED

    fun finishAll(instrumentation: Instrumentation, timeoutMillis: Long = 10_000L) {
        val deadline = SystemClock.uptimeMillis() + timeoutMillis
        while (true) {
            var remaining = 0
            instrumentation.runOnMainSync {
                val monitor = ActivityLifecycleMonitorRegistry.getInstance()
                live.flatMap { monitor.getActivitiesInStage(it) }.distinct().forEach { activity ->
                    remaining++
                    if (!activity.isFinishing) activity.finish()
                }
            }
            if (remaining == 0) return
            check(SystemClock.uptimeMillis() < deadline) { "$remaining activities still alive after ${timeoutMillis}ms" }
            SystemClock.sleep(100)
        }
    }
}
