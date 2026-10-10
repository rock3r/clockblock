package dev.sebastiano.clockblocker.opus.widget.debug

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.util.Log
import android.widget.RemoteViews
import androidx.core.content.getSystemService
import dev.sebastiano.clockblocker.opus.core.notifications.ClockblockChannel
import dev.sebastiano.clockblocker.opus.core.notifications.NotificationChannels
import dev.sebastiano.clockblocker.opus.core.notifications.R as NotificationsR
import dev.sebastiano.clockblocker.opus.widget.WidgetRenderer
import dev.sebastiano.clockblocker.opus.widget.draw.WidgetTheme
import dev.sebastiano.clockblocker.opus.widget.preview.DemoPlans
import dev.sebastiano.clockblocker.opus.widget.rc.NextUpLayout
import dev.sebastiano.clockblocker.opus.widget.rc.NextUpRemote
import dev.sebastiano.clockblocker.opus.widget.rc.RemoteComposeRenderer
import dev.sebastiano.clockblocker.opus.widget.rc.RemoteComposeSupport
import dev.sebastiano.clockblocker.opus.widget.rc.TwoClocksLayout
import dev.sebastiano.clockblocker.opus.widget.rc.TwoClocksRemote
import dev.sebastiano.clockblocker.opus.widget.rc.WidgetSizes
import dev.sebastiano.clockblocker.opus.widget.state.WidgetState
import dev.sebastiano.clockblocker.opus.widget.state.WidgetStateMapper
import java.time.Instant
import kotlinx.coroutines.delay
import kotlin.coroutines.cancellation.CancellationException

/**
 * DEBUG ONLY (#49). Can a Remote Compose document (`RemoteViews(DrawInstructions)`) be a notification's custom
 * content? Posts one notification per [Variant] on the Now channel, each built from the widgets' own documents
 * (Next up Wide collapsed, Two Clocks Wide expanded), then reports for each whether building or posting threw,
 * whether it is still active after [SETTLE_MS] (SystemUI cancels notifications it can't inflate), and whether
 * promotion stuck.
 *
 * Run it from Settings → Debug → Run the probe (debug builds), or with
 * `adb shell am start -n <applicationId>/dev.sebastiano.clockblocker.opus.widget.debug.WidgetGalleryActivity --es page notification`.
 * Results show on screen and go to logcat under [TAG]. Look at the shade too: "active" only means SystemUI kept
 * the notification, not that the document drew.
 */
class RemoteComposeNotificationProbe(private val context: Context) {

    enum class Variant(val id: Int, val description: String) {
        /** `DecoratedCustomViewStyle`, collapsed content only. */
        DecoratedCollapsed(0x4900, "Decorated, collapsed view only"),

        /** `DecoratedCustomViewStyle` with collapsed and expanded content: what the Now notification would use. */
        DecoratedBoth(0x4901, "Decorated, collapsed and expanded views"),

        /** No style: the system still decorates it for targetSdk ≥ 31. */
        Undecorated(0x4902, "No style (the system decorates it)"),

        /** [DecoratedBoth] asking for promotion: Live Updates forbid custom views, so this should stay unpromoted. */
        Promoted(0x4903, "Decorated, asking for promotion"),
    }

    /** How a variant fared. */
    enum class Verdict { Kept, Gone, Threw, Unsupported }

    data class Result(val variant: Variant, val verdict: Verdict, val outcome: String)

    /** Runs the probe on the widgets' demo plan (Lisbon to Tokyo, avoiding light now), in the light theme. */
    suspend fun runOnDemoPlan(): List<Result> {
        val now = Instant.now()
        return run(WidgetStateMapper.map(DemoPlans.lisbonTokyo(now, DemoPlans.Scenario.AvoidLight), now), WidgetTheme.Light)
    }

    suspend fun run(state: WidgetState, theme: WidgetTheme): List<Result> {
        NotificationChannels.ensureCreated(context)
        val manager = context.getSystemService<NotificationManager>()
            ?: return Variant.entries.map { Result(it, Verdict.Unsupported, "no NotificationManager") }
        if (!manager.areNotificationsEnabled()) {
            return Variant.entries.map { Result(it, Verdict.Unsupported, "notifications are off for this app") }
        }
        // A blocked channel or channel group would swallow every post and read as "dropped by SystemUI".
        val channelOff = manager.getNotificationChannel(ClockblockChannel.Now.id)?.importance == NotificationManager.IMPORTANCE_NONE
        val groupOff = manager.getNotificationChannelGroup(ClockblockChannel.Now.group.id)?.isBlocked == true
        if (channelOff || groupOff) {
            return Variant.entries.map { Result(it, Verdict.Unsupported, "the Now notification channel is turned off") }
        }
        val profile = RemoteComposeSupport.profileOrNull()
            ?: return Variant.entries.map { Result(it, Verdict.Unsupported, "no Remote Compose profile on this device") }
        val renderer = WidgetRenderer(context)
        val model = renderer.model(state, theme)
        val collapsedBucket = WidgetSizes.NEXT_UP.first { it.layout == NextUpLayout.Wide }
        val expandedBucket = WidgetSizes.TWO_CLOCKS.first { it.layout == TwoClocksLayout.Wide }
        // Capture can fail on a device that reports a profile (the widgets fall back to a placeholder then): report
        // it for every variant instead of failing the run.
        val (collapsedDoc, expandedDoc) = try {
            RemoteComposeRenderer.capture(context, profile) {
                NextUpRemote(model, collapsedBucket.layout, collapsedBucket.fitAt)
            } to RemoteComposeRenderer.capture(context, profile) {
                TwoClocksRemote(model, expandedBucket.layout, expandedBucket.fitAt)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "capturing the documents threw", e)
            return Variant.entries.map {
                Result(it, Verdict.Threw, "capturing the documents threw ${e.javaClass.simpleName}: ${e.message}")
            }
        }
        Log.i(TAG, "documents: collapsed ${collapsedDoc.bytes.size} B, expanded ${expandedDoc.bytes.size} B")

        val threw = Variant.entries.associateWith { variant ->
            try {
                val collapsed = RemoteComposeRenderer.remoteViews(collapsedDoc)
                val expanded = RemoteComposeRenderer.remoteViews(expandedDoc)
                manager.notify(variant.id, build(variant, collapsed, expanded))
                null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "$variant threw", e)
                "threw ${e.javaClass.simpleName}: ${e.message}"
            }
        }
        delay(SETTLE_MS)
        val active = manager.activeNotifications.associateBy { it.id }
        return Variant.entries.map { variant ->
            val error = threw[variant]
            val notification = active[variant.id]?.notification
            when {
                error != null -> Result(variant, Verdict.Threw, error)
                notification != null -> Result(
                    variant,
                    Verdict.Kept,
                    buildString {
                        append("still showing after $SETTLE_MS ms")
                        if (variant == Variant.Promoted) {
                            append(", promoted=${notification.flags and Notification.FLAG_PROMOTED_ONGOING != 0}")
                            append(", promotable=${notification.hasPromotableCharacteristics()}")
                        }
                    },
                )
                else -> Result(variant, Verdict.Gone, "posted, then gone after $SETTLE_MS ms (SystemUI inflation error? see logcat)")
            }.also { Log.i(TAG, "${it.variant}: ${it.verdict}: ${it.outcome}") }
        }
    }

    private fun build(variant: Variant, collapsed: RemoteViews, expanded: RemoteViews): Notification {
        val builder = Notification.Builder(context, ClockblockChannel.Now.id)
            .setSmallIcon(NotificationsR.drawable.ic_notif_clock)
            .setContentTitle("RC probe: ${variant.name}")
            .setContentText("Remote Compose custom content")
            .setOnlyAlertOnce(true)
            .setCustomContentView(collapsed)
        when (variant) {
            Variant.DecoratedCollapsed -> builder.setStyle(Notification.DecoratedCustomViewStyle())
            Variant.DecoratedBoth -> builder.setStyle(Notification.DecoratedCustomViewStyle()).setCustomBigContentView(expanded)
            Variant.Undecorated -> builder.setCustomBigContentView(expanded)
            Variant.Promoted -> builder.setStyle(Notification.DecoratedCustomViewStyle())
                .setCustomBigContentView(expanded)
                .setOngoing(true)
                .setRequestPromotedOngoing(true)
        }
        return builder.build()
    }

    /** Removes the probe's notifications. */
    fun cancelAll() {
        val manager = context.getSystemService<NotificationManager>() ?: return
        Variant.entries.forEach { manager.cancel(it.id) }
    }

    companion object {
        const val TAG = "RcNotifProbe"
        const val SETTLE_MS = 3_000L
    }
}
