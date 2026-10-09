package dev.sebastiano.clockblocker.opus.widget

import android.content.Context
import android.util.Log
import android.util.SizeF
import android.widget.RemoteViews
import androidx.compose.remote.creation.profile.Profile
import dev.sebastiano.clockblocker.opus.core.notifications.NotificationIntents
import dev.sebastiano.clockblocker.opus.widget.draw.WidgetPalette
import dev.sebastiano.clockblocker.opus.widget.draw.WidgetTheme
import dev.sebastiano.clockblocker.opus.widget.rc.Bucket
import dev.sebastiano.clockblocker.opus.widget.rc.DeepLinkIntents
import dev.sebastiano.clockblocker.opus.widget.rc.NextUpLayout
import dev.sebastiano.clockblocker.opus.widget.rc.NextUpRemote
import dev.sebastiano.clockblocker.opus.widget.rc.RemoteComposeRenderer
import dev.sebastiano.clockblocker.opus.widget.rc.RemoteComposeSupport
import dev.sebastiano.clockblocker.opus.widget.rc.TwoClocksLayout
import dev.sebastiano.clockblocker.opus.widget.rc.TwoClocksRemote
import dev.sebastiano.clockblocker.opus.widget.rc.WidgetModel
import dev.sebastiano.clockblocker.opus.widget.rc.WidgetSizes
import dev.sebastiano.clockblocker.opus.widget.state.WidgetState
import dev.sebastiano.clockblocker.opus.widget.text.WidgetTexts
import kotlin.coroutines.cancellation.CancellationException

enum class WidgetKind { TwoClocks, NextUp }

/**
 * Turns a [WidgetState] into `RemoteViews` that play a Remote Compose document (`RemoteViews.DrawInstructions`).
 * Remote Compose is the only renderer: when the host reports no supported document version, or a capture fails,
 * the widget shows the [placeholder] instead.
 */
class WidgetRenderer(
    private val context: Context,
    private val profileProvider: () -> Profile? = { RemoteComposeSupport.profileOrNull() },
    /** Debug hook (widget gallery): rewrites the resolved texts, e.g. to try extra-long labels. */
    private val textsTransform: (WidgetTexts) -> WidgetTexts = { it },
) {
    fun model(state: WidgetState, theme: WidgetTheme): WidgetModel =
        WidgetModel(state, textsTransform(WidgetTexts.from(context, state)), WidgetPalette.of(theme))

    suspend fun render(kind: WidgetKind, state: WidgetState, theme: WidgetTheme): RemoteViews =
        render(kind, model(state, theme))

    suspend fun render(kind: WidgetKind, model: WidgetModel): RemoteViews {
        try {
            val profile = profileProvider()
            if (profile != null) return renderRemoteCompose(kind, model, profile)
            Log.w(TAG, "The widget host reports no supported Remote Compose version, showing the placeholder")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Remote Compose capture failed, showing the placeholder", e)
        }
        return placeholder(model.texts.deepLink)
    }

    /**
     * The degraded widget: one tappable "Open Clockblock" view that opens [deepLink]. A capture failure leaves no
     * document to wrap in `RemoteViews.DrawInstructions`, so this is the one classic `RemoteViews` layout left
     * (it is also the providers' `initialLayout`, shown until the first update).
     */
    fun placeholder(deepLink: String): RemoteViews =
        RemoteViews(context.packageName, R.layout.widget_placeholder).apply {
            setOnClickPendingIntent(R.id.widget_placeholder, DeepLinkIntents.pendingIntent(context, deepLink))
        }

    private suspend fun renderRemoteCompose(kind: WidgetKind, model: WidgetModel, profile: Profile): RemoteViews {
        val click = DeepLinkIntents.pendingIntent(context, model.texts.deepLink)
        val done = model.texts.done?.takeIf { it.logged == null }?.let {
            NotificationIntents.widgetDone(context, it.tripId, it.adviceId)
        }
        return when (kind) {
            WidgetKind.TwoClocks -> RemoteComposeRenderer.responsive(
                context,
                profile,
                TWO_CLOCKS_SIZES,
                click,
                done,
            ) { bucket -> TwoClocksRemote(model, bucket.layout, bucket.fitAt) }
            WidgetKind.NextUp -> RemoteComposeRenderer.responsive(
                context,
                profile,
                NEXT_UP_SIZES,
                click,
                done,
            ) { bucket -> NextUpRemote(model, bucket.layout, bucket.fitAt) }
        }
    }

    companion object {
        private const val TAG = "ClockblockWidget"

        /** Two Clocks' responsive entries ([WidgetSizes.TWO_CLOCKS]): each bucket's minimum maps to that bucket. */
        val TWO_CLOCKS_SIZES: Map<SizeF, Bucket<TwoClocksLayout>> = sizes(WidgetSizes.TWO_CLOCKS)

        /** Next up's responsive entries ([WidgetSizes.NEXT_UP]). */
        val NEXT_UP_SIZES: Map<SizeF, Bucket<NextUpLayout>> = sizes(WidgetSizes.NEXT_UP)

        private fun <L> sizes(buckets: List<Bucket<L>>): Map<SizeF, Bucket<L>> =
            buckets.associateByTo(linkedMapOf()) { SizeF(it.min.width, it.min.height) }
    }
}
