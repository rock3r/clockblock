package dev.sebastiano.clockblocker.opus.widget

import android.content.Context
import android.os.Build
import android.util.Log
import android.util.SizeF
import android.widget.RemoteViews
import androidx.compose.remote.creation.profile.Profile
import dev.sebastiano.clockblocker.opus.core.notifications.NotificationIntents
import dev.sebastiano.clockblocker.opus.widget.draw.WidgetPalette
import dev.sebastiano.clockblocker.opus.widget.draw.WidgetTheme
import dev.sebastiano.clockblocker.opus.widget.legacy.LegacyRemoteViews
import dev.sebastiano.clockblocker.opus.widget.rc.DeepLinkIntents
import dev.sebastiano.clockblocker.opus.widget.rc.NextUpLayout
import dev.sebastiano.clockblocker.opus.widget.rc.NextUpRemote
import dev.sebastiano.clockblocker.opus.widget.rc.RemoteComposeRenderer
import dev.sebastiano.clockblocker.opus.widget.rc.RemoteComposeSupport
import dev.sebastiano.clockblocker.opus.widget.rc.TwoClocksLayout
import dev.sebastiano.clockblocker.opus.widget.rc.TwoClocksRemote
import dev.sebastiano.clockblocker.opus.widget.rc.WidgetModel
import dev.sebastiano.clockblocker.opus.widget.state.WidgetState
import dev.sebastiano.clockblocker.opus.widget.text.WidgetTexts
import java.time.Instant
import kotlin.math.min
import kotlin.math.roundToInt

enum class WidgetKind { TwoClocks, NextUp }

enum class WidgetBackend { RemoteCompose, Legacy }

/** Widget size in dp as reported by the host (`AppWidgetManager.OPTION_APPWIDGET_*`), if known. */
data class WidgetSizeDp(val width: Float, val height: Float)

/**
 * Turns a [WidgetState] into `RemoteViews`, choosing the backend:
 * Remote Compose documents on API 36+ hosts that report a supported document version, classic RemoteViews
 * otherwise (or when capture fails, or when [forceLegacy] is set, e.g. for OEM hosts that render blank).
 */
class WidgetRenderer(
    private val context: Context,
    private val forceLegacy: Boolean = false,
    private val profileProvider: () -> Profile? = { RemoteComposeSupport.profileOrNull() },
    /** Debug hook (widget gallery): rewrites the resolved texts, e.g. to try extra-long labels. */
    private val textsTransform: (WidgetTexts) -> WidgetTexts = { it },
) {
    val backend: WidgetBackend
        get() = if (!forceLegacy && profileProvider() != null) WidgetBackend.RemoteCompose else WidgetBackend.Legacy

    fun model(state: WidgetState, theme: WidgetTheme): WidgetModel =
        WidgetModel(state, textsTransform(WidgetTexts.from(context, state)), WidgetPalette.of(theme))

    suspend fun render(
        kind: WidgetKind,
        state: WidgetState,
        theme: WidgetTheme,
        size: WidgetSizeDp? = null,
        now: Instant = Instant.now(),
    ): RemoteViews {
        val model = model(state, theme)
        val profile = profileProvider().takeUnless { forceLegacy }
        if (profile != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            try {
                return renderRemoteCompose(kind, model, profile)
            } catch (e: Exception) {
                Log.w(TAG, "Remote Compose capture failed, falling back to classic RemoteViews", e)
            }
        }
        return renderLegacy(kind, model, size, now)
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
            ) { layout -> TwoClocksRemote(model, layout) }
            WidgetKind.NextUp -> RemoteComposeRenderer.responsive(
                context,
                profile,
                NEXT_UP_SIZES,
                click,
                done,
            ) { layout -> NextUpRemote(model, layout) }
        }
    }

    fun renderLegacy(kind: WidgetKind, model: WidgetModel, size: WidgetSizeDp?, now: Instant): RemoteViews {
        val density = context.resources.displayMetrics.density
        return when (kind) {
            WidgetKind.TwoClocks -> {
                // One bitmap for every responsive layout (RemoteViews dedupes identical bitmap instances), sized for
                // the largest dial the reported size can show.
                val dialDp = size?.let { min(it.width, it.height - 24f) } ?: 150f
                val dial = LegacyRemoteViews.dialBitmap(model, (dialDp * density).roundToInt(), now)
                responsiveLegacy(TWO_CLOCKS_SIZES, size) { layout -> LegacyRemoteViews.twoClocks(context, model, layout, dial) }
            }
            WidgetKind.NextUp ->
                responsiveLegacy(NEXT_UP_SIZES, size) { layout ->
                    LegacyRemoteViews.nextUp(context, model, layout, density, now)
                }
        }
    }

    private fun <L> responsiveLegacy(sizes: Map<SizeF, L>, size: WidgetSizeDp?, build: (L) -> RemoteViews): RemoteViews {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val built = mutableMapOf<L, RemoteViews>()
            return RemoteViews(sizes.mapValues { (_, layout) -> built.getOrPut(layout) { build(layout) } })
        }
        return build(pick(sizes, size))
    }

    companion object {
        private const val TAG = "OpusWidget"

        /**
         * Responsive buckets, smallest area first. The host shows the largest that fits (API 31+); [pick] does the same
         * on older hosts. Thresholds sit below typical launcher cell sizes: 1×1 (Compact), 2×2, 2×3, 4×2, 4×3.
         */
        val TWO_CLOCKS_SIZES: Map<SizeF, TwoClocksLayout> = linkedMapOf(
            SizeF(90f, 90f) to TwoClocksLayout.Compact,
            SizeF(120f, 120f) to TwoClocksLayout.Square,
            SizeF(240f, 110f) to TwoClocksLayout.Wide,
            SizeF(120f, 230f) to TwoClocksLayout.Tall,
            SizeF(240f, 220f) to TwoClocksLayout.Large,
        )

        /** 1×1, 2×1, 4×1, 2×2, 2×3, 4×2 (ribbon) and 4×3 (the 2×3 layout, wider). */
        val NEXT_UP_SIZES: Map<SizeF, NextUpLayout> = linkedMapOf(
            SizeF(40f, 40f) to NextUpLayout.Small,
            SizeF(110f, 40f) to NextUpLayout.Medium,
            SizeF(240f, 40f) to NextUpLayout.Wide,
            SizeF(110f, 110f) to NextUpLayout.Square,
            SizeF(110f, 200f) to NextUpLayout.Tall,
            SizeF(240f, 110f) to NextUpLayout.Ribbon,
            SizeF(240f, 200f) to NextUpLayout.Tall,
        )

        /** Pre-API 31 selection: the largest layout that fits the reported size (smallest if unknown). */
        fun <L> pick(sizes: Map<SizeF, L>, size: WidgetSizeDp?): L {
            if (size == null) return sizes.values.first()
            return sizes.entries
                .filter { (s, _) -> s.width <= size.width && s.height <= size.height }
                .maxByOrNull { (s, _) -> s.width * s.height }?.value
                ?: sizes.values.first()
        }
    }
}
