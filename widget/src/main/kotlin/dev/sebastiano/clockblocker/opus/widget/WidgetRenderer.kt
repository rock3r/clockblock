package dev.sebastiano.clockblocker.opus.widget

import android.content.Context
import android.os.Build
import android.util.Log
import android.util.SizeF
import android.widget.RemoteViews
import androidx.compose.remote.creation.profile.Profile
import dev.sebastiano.clockblocker.opus.widget.draw.WidgetPalette
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

    fun model(state: WidgetState, dark: Boolean): WidgetModel =
        WidgetModel(state, textsTransform(WidgetTexts.from(context, state)), WidgetPalette.of(dark))

    suspend fun render(
        kind: WidgetKind,
        state: WidgetState,
        dark: Boolean,
        size: WidgetSizeDp? = null,
        now: Instant = Instant.now(),
    ): RemoteViews {
        val model = model(state, dark)
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
        return when (kind) {
            WidgetKind.TwoClocks -> RemoteComposeRenderer.responsive(
                context,
                profile,
                TWO_CLOCKS_SIZES,
                click,
            ) { layout -> TwoClocksRemote(model, layout) }
            WidgetKind.NextUp -> RemoteComposeRenderer.responsive(
                context,
                profile,
                NEXT_UP_SIZES,
                click,
            ) { layout -> NextUpRemote(model, layout) }
        }
    }

    fun renderLegacy(kind: WidgetKind, model: WidgetModel, size: WidgetSizeDp?, now: Instant): RemoteViews {
        val density = context.resources.displayMetrics.density
        return when (kind) {
            WidgetKind.TwoClocks -> {
                val dialDp = size?.let { min(it.width, it.height - 24f) } ?: 150f
                val px = (dialDp * density).roundToInt()
                responsiveLegacy(TWO_CLOCKS_SIZES, size) { LegacyRemoteViews.twoClocks(context, model, px, now) }
            }
            WidgetKind.NextUp ->
                responsiveLegacy(NEXT_UP_SIZES, size) { layout ->
                    LegacyRemoteViews.nextUp(context, model, layout, density, now)
                }
        }
    }

    private fun <L> responsiveLegacy(sizes: Map<SizeF, L>, size: WidgetSizeDp?, build: (L) -> RemoteViews): RemoteViews {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Two Clocks has identical legacy layouts for both sizes: one RemoteViews is enough.
            val distinct = sizes.entries.distinctBy { (_, layout) -> layout.legacyKey() }
            if (distinct.size == 1) return build(distinct.single().value)
            return RemoteViews(sizes.mapValues { (_, layout) -> build(layout) })
        }
        return build(pick(sizes, size))
    }

    private fun Any?.legacyKey(): Any? = if (this is TwoClocksLayout) TwoClocksLayout.Square else this

    companion object {
        private const val TAG = "OpusWidget"

        val TWO_CLOCKS_SIZES: Map<SizeF, TwoClocksLayout> = linkedMapOf(
            SizeF(100f, 100f) to TwoClocksLayout.Square,
            SizeF(260f, 110f) to TwoClocksLayout.Wide,
        )

        val NEXT_UP_SIZES: Map<SizeF, NextUpLayout> = linkedMapOf(
            SizeF(40f, 40f) to NextUpLayout.Small,
            SizeF(110f, 40f) to NextUpLayout.Medium,
            SizeF(250f, 40f) to NextUpLayout.Wide,
        )

        /** Pre-API 31 selection: the largest layout that fits the reported size (smallest if unknown). */
        fun <L> pick(sizes: Map<SizeF, L>, size: WidgetSizeDp?): L {
            if (size == null) return sizes.values.first()
            return sizes.entries.lastOrNull { (s, _) -> s.width <= size.width && s.height <= size.height }?.value
                ?: sizes.values.first()
        }
    }
}
