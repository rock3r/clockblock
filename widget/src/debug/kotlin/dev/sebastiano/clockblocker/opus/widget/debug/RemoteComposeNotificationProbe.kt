package dev.sebastiano.clockblocker.opus.widget.debug

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.util.Log
import android.widget.RemoteViews
import androidx.core.content.getSystemService
import dev.sebastiano.clockblocker.opus.core.model.DeepLinks
import dev.sebastiano.clockblocker.opus.core.notifications.ClockblockChannel
import dev.sebastiano.clockblocker.opus.core.notifications.NotificationChannels
import dev.sebastiano.clockblocker.opus.core.notifications.R as NotificationsR
import dev.sebastiano.clockblocker.opus.core.notifications.nowNotificationAccent
import dev.sebastiano.clockblocker.opus.widget.WidgetRenderer
import dev.sebastiano.clockblocker.opus.widget.preview.DemoPlans
import dev.sebastiano.clockblocker.opus.widget.rc.DeepLinkIntents
import dev.sebastiano.clockblocker.opus.widget.rc.NotificationSlots
import dev.sebastiano.clockblocker.opus.widget.rc.NowNotificationDocuments
import dev.sebastiano.clockblocker.opus.widget.rc.RemoteComposeRenderer
import dev.sebastiano.clockblocker.opus.widget.rc.RemoteComposeSupport
import dev.sebastiano.clockblocker.opus.widget.state.WidgetState
import dev.sebastiano.clockblocker.opus.widget.state.WidgetStateMapper
import dev.sebastiano.clockblocker.opus.widget.state.tintType
import java.time.Instant
import kotlinx.coroutines.delay
import kotlin.coroutines.cancellation.CancellationException

/**
 * DEBUG ONLY (#49). A preview of a Remote Compose Now notification, and a check that the shade takes one
 * (`RemoteViews(DrawInstructions)` as custom content). Posts one notification per [Variant] on the Now channel, built
 * from the notification documents (`NowNotificationDocuments`: a 48 dp collapsed row and an expanded view that wraps
 * its content under the 252 dp cap, sized for this screen's shade, in colours the player picks by the system theme),
 * with the real Now notification's status bar icon and accent. Then it reports for each whether building or posting
 * threw, whether it is still active after [SETTLE_MS] (SystemUI cancels notifications it can't inflate), and whether
 * promotion stuck.
 *
 * Run it from Settings → Debug → Run the probe (debug builds), or with
 * `adb shell am start -n <applicationId>/dev.sebastiano.clockblocker.opus.widget.debug.WidgetGalleryActivity --es page notification`.
 * Results show on screen and go to logcat under [TAG]. Look at the shade too, and flip dark theme with them showing:
 * "active" only means SystemUI kept the notification, not that the document drew. The demo plan is not a real trip,
 * so Done does nothing; a tap elsewhere opens the app.
 */
class RemoteComposeNotificationProbe(private val context: Context) {

    enum class Variant(val id: Int, val description: String, val label: String) {
        /** `DecoratedCustomViewStyle`, collapsed content only. */
        DecoratedCollapsed(0x4900, "Decorated, collapsed view only", "Probe: collapsed only"),

        /** `DecoratedCustomViewStyle` with collapsed and expanded content: what the Now notification would use. */
        DecoratedBoth(0x4901, "Decorated, collapsed and expanded views", "Probe: collapsed and expanded"),

        /** No style: the system still decorates it for targetSdk ≥ 31. */
        Undecorated(0x4902, "No style (the system decorates it)", "Probe: no style"),

        /** [DecoratedBoth] asking for promotion: Live Updates forbid custom views, so this should stay unpromoted. */
        Promoted(0x4903, "Decorated, asking for promotion", "Probe: asks for promotion"),
        ;

        companion object {
            fun ofId(id: Int): Variant? = entries.firstOrNull { it.id == id }
        }
    }

    /** How a variant fared. */
    enum class Verdict { Kept, Gone, Threw, Unsupported }

    data class Result(val variant: Variant, val verdict: Verdict, val outcome: String)

    /** Runs the probe on the widgets' demo plan (Lisbon to Tokyo, avoiding light now). */
    suspend fun runOnDemoPlan(): List<Result> {
        val now = Instant.now()
        val plan = DemoPlans.lisbonTokyo(now, DemoPlans.Scenario.AvoidLight)
        return run(
            WidgetStateMapper.map(plan, now, route = DemoPlans.ROUTE, placeNames = DemoPlans.PLACE_NAMES, places = DemoPlans.PLACES),
        )
    }

    suspend fun run(state: WidgetState): List<Result> {
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
        val model = WidgetRenderer(context).themedModel(state)
        val slots = NotificationSlots.forScreenWidth(context.resources.configuration.screenWidthDp)
        // Capture can fail on a device that reports a profile (the widgets fall back to a placeholder then): report
        // it for every variant instead of failing the run.
        val documents = try {
            NowNotificationDocuments.capture(context, profile, model, slots)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "capturing the documents threw", e)
            return Variant.entries.map {
                Result(it, Verdict.Threw, "capturing the documents threw ${e.javaClass.simpleName}: ${e.message}")
            }
        }
        val expandedHeight = NowNotificationDocuments.expandedHeightDp(context, model, slots.expandedWidthDp)
        val sizes = "document v${RemoteComposeSupport.supportedVersion()}, collapsed ${slots.collapsedWidthDp}×" +
            "${NotificationSlots.COLLAPSED_HEIGHT_DP} dp, expanded ${slots.expandedWidthDp}×${expandedHeight.toInt()} dp"
        Log.i(TAG, "$sizes; collapsed ${documents.collapsed.bytes.size} B, expanded ${documents.expanded.bytes.size} B")

        // A tap on the content opens the app (the demo trip has no plan screen); Done stays inert (see the KDoc).
        val click = DeepLinkIntents.pendingIntent(context, DeepLinks.TRIPS)
        val threw = Variant.entries.associateWith { variant ->
            try {
                val collapsed = RemoteComposeRenderer.remoteViews(documents.collapsed, click)
                val expanded = RemoteComposeRenderer.remoteViews(documents.expanded, click)
                manager.notify(variant.id, build(variant, state, model.texts.title, model.texts.subtitle, collapsed, expanded))
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
                        append("still showing after $SETTLE_MS ms ($sizes)")
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

    private fun build(
        variant: Variant,
        state: WidgetState,
        title: String,
        text: String,
        collapsed: RemoteViews,
        expanded: RemoteViews,
    ): Notification {
        val builder = Notification.Builder(context, ClockblockChannel.Now.id)
            // The real Now notification's status bar icon and accent (the current advice's colour).
            .setSmallIcon(NotificationsR.drawable.ic_notif_app)
            .setColor(nowNotificationAccent(state.tintType))
            // Not drawn under a custom view, but read by accessibility services and wearables.
            .setContentTitle(title)
            .setContentText(text)
            // The header line: tells the four apart in the shade.
            .setSubText(variant.label)
            .setShowWhen(false)
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

    /** The probe's notifications in the shade now, e.g. from a run before the app's process was restarted. */
    fun activeVariants(): List<Variant> {
        val manager = context.getSystemService<NotificationManager>() ?: return emptyList()
        return manager.activeNotifications.mapNotNull { Variant.ofId(it.id) }.distinct().sortedBy { it.ordinal }
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
