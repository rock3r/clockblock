package dev.sebastiano.clockblocker.opus.widget

import android.app.Application
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.util.Log
import dev.sebastiano.clockblocker.opus.core.data.PlanRepository
import dev.sebastiano.clockblocker.opus.core.data.SettingsRepository
import dev.sebastiano.clockblocker.opus.core.model.AdviceType
import dev.sebastiano.clockblocker.opus.core.model.AppSettings
import dev.sebastiano.clockblocker.opus.core.model.JetLagPlan
import dev.sebastiano.clockblocker.opus.core.model.ThemeMode
import dev.sebastiano.clockblocker.opus.widget.legacy.LegacyRefresh
import dev.sebastiano.clockblocker.opus.widget.preview.DemoPlans
import dev.sebastiano.clockblocker.opus.widget.state.WidgetState
import dev.sebastiano.clockblocker.opus.widget.state.WidgetStateMapper
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Clock
import java.time.ZoneId

/**
 * Renders every placed Opus widget from the current plan. Called by the providers (system updates, resizes),
 * by [WidgetPlanSurface] (advice boundaries, plan changes) and the debug gallery.
 *
 * [planRepository] / [settingsRepository] are Metro *optional* bindings (default values): until the data layer
 * contributes them, widgets show the empty state instead of breaking the app graph.
 */
@SingleIn(AppScope::class)
@Inject
class WidgetUpdater(
    private val application: Application,
    private val planRepository: PlanRepository = NoPlanRepository,
    private val settingsRepository: SettingsRepository = DefaultSettingsRepository,
) {
    internal var clock: Clock = Clock.systemDefaultZone()
    internal var rendererFactory: (Context, Boolean) -> WidgetRenderer = { ctx, legacy -> WidgetRenderer(ctx, legacy) }

    private val mutex = Mutex()
    private val manager: AppWidgetManager get() = AppWidgetManager.getInstance(application)

    /** Re-render all widgets of both kinds. */
    suspend fun updateAll() {
        WidgetKind.entries.forEach { kind -> update(kind, ids(kind)) }
        publishPreviewsIfNeeded()
    }

    /** Re-render the given widget ids of one kind. */
    suspend fun update(kind: WidgetKind, appWidgetIds: IntArray) = mutex.withLock {
        val plan = withTimeoutOrNull(READ_TIMEOUT_MS) { planRepository.currentPlan.first() }
        val settings = withTimeoutOrNull(READ_TIMEOUT_MS) { settingsRepository.settings.first() } ?: AppSettings()
        val state = WidgetStateMapper.map(plan, clock.instant(), ZoneId.systemDefault())
        val renderer = rendererFactory(application, false)
        val dark = isDark(settings, state, application.resources.configuration)
        appWidgetIds.forEach { id ->
            try {
                val views = renderer.render(kind, state, dark, sizeOf(id), clock.instant())
                manager.updateAppWidget(id, views)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to update $kind widget $id", e)
            }
        }
        LegacyRefresh.sync(
            application,
            enabled = renderer.backend == WidgetBackend.Legacy && ids(WidgetKind.TwoClocks).isNotEmpty(),
        )
    }

    /** Generated widget-picker previews with a sample plan (API 35+; the platform rate-limits these calls). */
    suspend fun publishPreviewsIfNeeded(force: Boolean = false) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) return
        val prefs = application.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val version = application.packageManager.getPackageInfo(application.packageName, 0).longVersionCode
        if (!force && prefs.getLong(KEY_PREVIEW_VERSION, -1) == version) return
        val now = clock.instant()
        val state = WidgetStateMapper.map(DemoPlans.lisbonTokyo(now), now, ZoneId.of("Asia/Tokyo"))
        val renderer = rendererFactory(application, false)
        val dark = application.isNight()
        var ok = true
        WidgetKind.entries.forEach { kind ->
            val views = renderer.render(kind, state, dark, null, now)
            ok = ok && runCatching {
                manager.setWidgetPreview(
                    componentName(application, kind),
                    AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN or AppWidgetProviderInfo.WIDGET_CATEGORY_KEYGUARD,
                    views,
                )
            }.getOrDefault(false)
        }
        if (ok) prefs.edit().putLong(KEY_PREVIEW_VERSION, version).apply()
    }

    fun ids(kind: WidgetKind): IntArray = manager.getAppWidgetIds(componentName(application, kind))

    private fun sizeOf(id: Int): WidgetSizeDp? {
        val options = manager.getAppWidgetOptions(id) ?: return null
        val w = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH)
        val h = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT)
        return if (w > 0 && h > 0) WidgetSizeDp(w.toFloat(), h.toFloat()) else null
    }

    companion object {
        private const val TAG = "OpusWidget"
        private const val PREFS = "opus_widgets"
        private const val KEY_PREVIEW_VERSION = "generated_previews_version"
        private const val READ_TIMEOUT_MS = 3_000L

        fun componentName(context: Context, kind: WidgetKind): ComponentName = when (kind) {
            WidgetKind.TwoClocks -> ComponentName(context, TwoClocksWidgetProvider::class.java)
            WidgetKind.NextUp -> ComponentName(context, NextUpWidgetProvider::class.java)
        }

        /**
         * Theme for a render: the app's theme setting (System follows the device), forced dark while the plan says
         * avoid light / sleep when Night-safe is on (design.md §2.3 G: our own screen must not sabotage it).
         */
        fun isDark(settings: AppSettings, state: WidgetState, configuration: Configuration): Boolean {
            val nightSafe = settings.nightSafeAuto &&
                (state as? WidgetState.Active)?.current?.type in setOf(AdviceType.AvoidLight, AdviceType.Sleep)
            return nightSafe || when (settings.themeMode) {
                ThemeMode.Dark -> true
                ThemeMode.Light -> false
                ThemeMode.System ->
                    configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
            }
        }

        private fun Context.isNight() =
            resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
    }
}

/** Used until a real [PlanRepository] is bound: no plan, widgets show "No trip — plan one". */
internal object NoPlanRepository : PlanRepository {
    override fun plan(tripId: String): Flow<JetLagPlan?> = flowOf(null)
    override val currentPlan: Flow<JetLagPlan?> = flowOf(null)
}

/** Used until a real [SettingsRepository] is bound. */
internal object DefaultSettingsRepository : SettingsRepository {
    override val settings: Flow<AppSettings> = flowOf(AppSettings())
    override suspend fun update(transform: (AppSettings) -> AppSettings) = Unit
}
