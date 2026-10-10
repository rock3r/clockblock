package dev.sebastiano.clockblocker.opus.widget

import android.app.Application
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentCallbacks
import android.content.ComponentName
import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import android.util.Log
import android.util.SizeF
import android.widget.RemoteViews
import dev.sebastiano.clockblocker.opus.core.data.AdviceLogRepository
import dev.sebastiano.clockblocker.opus.core.data.PlanRepository
import dev.sebastiano.clockblocker.opus.core.data.SettingsRepository
import dev.sebastiano.clockblocker.opus.core.data.TripRepository
import dev.sebastiano.clockblocker.opus.core.data.WidgetConfigRepository
import dev.sebastiano.clockblocker.opus.core.model.AdviceLog
import dev.sebastiano.clockblocker.opus.core.model.AdviceOutcome
import dev.sebastiano.clockblocker.opus.core.model.AdviceType
import dev.sebastiano.clockblocker.opus.core.model.AppSettings
import dev.sebastiano.clockblocker.opus.core.model.JetLagPlan
import dev.sebastiano.clockblocker.opus.core.model.ThemeMode
import dev.sebastiano.clockblocker.opus.core.model.Trip
import dev.sebastiano.clockblocker.opus.core.model.WidgetConfig
import dev.sebastiano.clockblocker.opus.widget.draw.WidgetTheme
import dev.sebastiano.clockblocker.opus.widget.preview.DemoPlans
import dev.sebastiano.clockblocker.opus.widget.rc.WidgetModel
import dev.sebastiano.clockblocker.opus.widget.state.WidgetRoute
import dev.sebastiano.clockblocker.opus.widget.state.WidgetState
import dev.sebastiano.clockblocker.opus.widget.state.WidgetStateMapper
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Clock

/**
 * Renders every placed Clockblock widget from the current plan. Called by the providers (system updates, resizes),
 * by [WidgetPlanSurface] (advice boundaries, plan changes) and the debug gallery.
 *
 * [planRepository] / [settingsRepository] / [adviceLogRepository] / [tripRepository] are Metro *optional* bindings
 * (default values): until the data layer contributes them, widgets show the empty state instead of breaking the app
 * graph.
 *
 * Instances on a lock screen (`OPTION_APPWIDGET_HOST_CATEGORY` includes the keyguard bit) render the redacted state
 * while Settings › Hide details on the lock screen is on. The scheduler refreshes every surface when settings change,
 * so flipping the setting re-renders them.
 */
@SingleIn(AppScope::class)
@Inject
class WidgetUpdater(
    private val application: Application,
    private val planRepository: PlanRepository = NoPlanRepository,
    private val settingsRepository: SettingsRepository = DefaultSettingsRepository,
    private val adviceLogRepository: AdviceLogRepository = NoAdviceLogRepository,
    private val tripRepository: TripRepository = NoTripRepository,
    private val widgetConfigs: WidgetConfigRepository = NoWidgetConfigRepository,
) {
    internal var clock: Clock = Clock.systemDefaultZone()
    internal var readTimeoutMs: Long = READ_TIMEOUT_MS
    internal var rendererFactory: (Context) -> WidgetRenderer = { ctx -> WidgetRenderer(ctx) }

    /** Test hook: the model each widget id was rendered from (the Remote Compose document has no view tree to read). */
    internal var onRendered: (appWidgetId: Int, model: WidgetModel) -> Unit = { _, _ -> }

    private val mutex = Mutex()
    private val manager: AppWidgetManager get() = AppWidgetManager.getInstance(application)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var lastRenderConfig = RenderConfig.of(application.resources.configuration)

    init {
        // No widget broadcast reports these changes: a light/dark switch (the System theme and the picker previews),
        // or a font scale, Bold text or density change (the labels are fitted for all three at capture time, see
        // LabelFit, and the captured text bakes in the Bold text weight).
        application.registerComponentCallbacks(
            object : ComponentCallbacks {
                override fun onConfigurationChanged(newConfig: Configuration) {
                    if (renderConfigChanged(newConfig)) scope.launch { runCatching { updateAll() } }
                }

                @Deprecated("Deprecated in Java")
                override fun onLowMemory() = Unit
            },
        )
    }

    /** True once per change of what the rendered widgets depend on: night mode, font scale, Bold text or density. */
    internal fun renderConfigChanged(configuration: Configuration): Boolean {
        val config = RenderConfig.of(configuration)
        if (config == lastRenderConfig) return false
        lastRenderConfig = config
        return true
    }

    /** The parts of the configuration a captured widget depends on. */
    private data class RenderConfig(
        val night: Boolean,
        val fontScale: Float,
        val densityDpi: Int,
        val fontWeightAdjustment: Int,
    ) {
        val key: String get() = "${if (night) "night" else "day"}-$fontScale-$densityDpi-w$fontWeightAdjustment"

        companion object {
            fun of(configuration: Configuration) = RenderConfig(
                configuration.isNight(),
                configuration.fontScale,
                configuration.densityDpi,
                configuration.weightAdjustment(),
            )
        }
    }

    private val prefs get() = application.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /**
     * Whether the widgets were last rendered at another configuration (or never): the callback above only hears
     * changes while the process runs, and no widget broadcast reports a font scale, Bold text or density change.
     */
    private fun renderedConfigStale(): Boolean =
        prefs.getString(KEY_RENDERED_CONFIG, null) != RenderConfig.of(application.resources.configuration).key

    /** Re-render all widgets of both kinds. */
    suspend fun updateAll() {
        val config = RenderConfig.of(application.resources.configuration)
        val placed = WidgetKind.entries.associateWith { ids(it) }
        placed.forEach { (kind, ids) -> render(kind, ids) }
        prefs.edit().putString(KEY_RENDERED_CONFIG, config.key).apply()
        // A removal whose broadcast never arrived (the app was being updated, say) leaves options behind: drop them.
        widgetConfigs.retainOnly(placed.values.flatMap { it.toList() })
        publishPreviewsIfNeeded()
    }

    /**
     * Re-render the given widget ids of one kind. Also re-publishes the picker previews when their key is stale, so
     * a light/dark switch while the app was not running still reaches the picker on the next widget update. For the
     * same reason, when the configuration changed since the last full render, every widget is rendered again.
     */
    suspend fun update(kind: WidgetKind, appWidgetIds: IntArray) {
        if (renderedConfigStale()) return updateAll()
        render(kind, appWidgetIds)
        publishPreviewsIfNeeded()
    }

    private suspend fun render(kind: WidgetKind, appWidgetIds: IntArray) = mutex.withLock {
        RetiredLegacyRefresh.cancel(application)
        val plan = withTimeoutOrNull(readTimeoutMs) { planRepository.currentPlan.first() }
        val read = withTimeoutOrNull(readTimeoutMs) { settingsRepository.settings.first() }
        val settings = read ?: AppSettings()
        // Privacy fails closed: when the settings can't be read in time, lock-screen widgets stay redacted.
        val hideOnLockScreen = read?.hideLockScreenDetails ?: true
        val logs = plan?.let { withTimeoutOrNull(readTimeoutMs) { adviceLogRepository.logs(it.tripId).first() } }
        val state = state(plan, settings, keyguard = false, logs = logs)
        val redacted by lazy { WidgetStateMapper.redact(state) }
        val renderer = rendererFactory(application)
        val theme = theme(settings, state, application.resources.configuration)
        val configs = withTimeoutOrNull(readTimeoutMs) { widgetConfigs.configs.first() }.orEmpty()
        appWidgetIds.forEach { id ->
            try {
                val options = manager.getAppWidgetOptions(id)
                val shown = if (hideOnLockScreen && isKeyguard(options)) redacted else state
                val model = renderer.model(shown, theme).with(configs[id] ?: WidgetConfig())
                val views = renderer.render(kind, model)
                onRendered(id, model)
                manager.updateAppWidget(id, views)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to update $kind widget $id", e)
            }
        }
    }

    /**
     * The state to render for [plan]: with the trip's route and place names, redacted on a [keyguard] host when the
     * setting asks.
     */
    internal suspend fun state(
        plan: JetLagPlan?,
        settings: AppSettings,
        keyguard: Boolean,
        logs: List<AdviceLog>? = emptyList(),
    ): WidgetState {
        val trip = plan?.let { p -> withTimeoutOrNull(readTimeoutMs) { tripRepository.trip(p.tripId).first() } }
        val route = trip?.let { WidgetRoute(it.origin.displayCode, it.destination.displayCode) }
        val places = trip?.let(WidgetStateMapper::placeNames).orEmpty()
        val codes = trip?.let(WidgetStateMapper::placeCodes).orEmpty()
        val dialPlaces = trip?.let(WidgetStateMapper::places).orEmpty()
        val state = WidgetStateMapper.map(plan, clock.instant(), logs, route, places, codes, dialPlaces)
        return if (keyguard && settings.hideLockScreenDetails) WidgetStateMapper.redact(state) else state
    }

    /**
     * What the configuration screen previews (#52): the widget as it would show now, drawn with [config]. Without a
     * plan it shows the sample trip the picker previews use, flagged by [ConfigPreview.sample].
     */
    internal suspend fun previewModel(config: WidgetConfig): ConfigPreview {
        val plan = withTimeoutOrNull(readTimeoutMs) { planRepository.currentPlan.first() }
        val settings = withTimeoutOrNull(readTimeoutMs) { settingsRepository.settings.first() } ?: AppSettings()
        val logs = plan?.let { withTimeoutOrNull(readTimeoutMs) { adviceLogRepository.logs(it.tripId).first() } }
        val live = state(plan, settings, keyguard = false, logs = logs)
        val sample = live is WidgetState.NoTrip
        val state = if (sample) sampleState() else live
        val model = rendererFactory(application).model(state, theme(settings, state, application.resources.configuration))
        return ConfigPreview(model.with(config), sample)
    }

    /** [model] rendered as [kind] would draw it, for the configuration screen's preview. */
    internal suspend fun renderPreview(kind: WidgetKind, model: WidgetModel): RemoteViews = rendererFactory(application).render(kind, model)

    /** The size [appWidgetId] is drawn at on the home screen, in dp: the host's first reported size, else its minimum. */
    internal fun sizeDp(appWidgetId: Int): SizeF {
        val options = manager.getAppWidgetOptions(appWidgetId)
        @Suppress("DEPRECATION") // getParcelableArrayList(key, Class) needs the SizeF class token; this is equivalent.
        options?.getParcelableArrayList<SizeF>(AppWidgetManager.OPTION_APPWIDGET_SIZES)?.firstOrNull()?.let { return it }
        val width = options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH) ?: 0
        val height = options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT) ?: 0
        return if (width > 0 && height > 0) SizeF(width.toFloat(), height.toFloat()) else DefaultPreviewSize
    }

    private fun sampleState(): WidgetState {
        val now = clock.instant()
        return WidgetStateMapper.map(
            DemoPlans.lisbonTokyo(now),
            now,
            route = DemoPlans.ROUTE,
            placeNames = DemoPlans.PLACE_NAMES,
            placeCodes = DemoPlans.PLACE_CODES,
            places = DemoPlans.PLACES,
        )
    }

    /**
     * Generated widget-picker previews with a sample plan (the platform rate-limits these calls). Keyed on the app
     * version, the system night mode, the font scale, Bold text and the density, so the picker follows a light/dark
     * switch and its labels are fitted again when the fit would change.
     */
    suspend fun publishPreviewsIfNeeded(force: Boolean = false) {
        val version = application.packageManager.getPackageInfo(application.packageName, 0).longVersionCode
        val configuration = application.resources.configuration
        val night = configuration.isNight()
        val key = previewKey(
            version,
            night,
            configuration.fontScale,
            configuration.densityDpi,
            configuration.weightAdjustment(),
        )
        if (!force && prefs.getString(KEY_PREVIEW, null) == key) return
        val now = clock.instant()
        val state = WidgetStateMapper.map(
            DemoPlans.lisbonTokyo(now),
            now,
            route = DemoPlans.ROUTE,
            placeNames = DemoPlans.PLACE_NAMES,
            placeCodes = DemoPlans.PLACE_CODES,
            places = DemoPlans.PLACES,
        )
        val renderer = rendererFactory(application)
        // The picker is not the plan: always the regular palette, never night-safe.
        val theme = if (night) WidgetTheme.Dark else WidgetTheme.Light
        var ok = true
        WidgetKind.entries.forEach { kind ->
            val views = renderer.render(kind, state, theme)
            ok = ok && runCatching {
                manager.setWidgetPreview(
                    componentName(application, kind),
                    AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN or AppWidgetProviderInfo.WIDGET_CATEGORY_KEYGUARD,
                    views,
                )
            }.getOrDefault(false)
        }
        if (ok) prefs.edit().putString(KEY_PREVIEW, key).apply()
    }

    fun ids(kind: WidgetKind): IntArray = manager.getAppWidgetIds(componentName(application, kind))

    /**
     * Saves [appWidgetId]'s options (its configuration screen, #52) and redraws that widget. The scheduler doesn't
     * watch the options, so this is what makes a change show.
     */
    suspend fun configure(appWidgetId: Int, transform: (WidgetConfig) -> WidgetConfig) {
        widgetConfigs.update(appWidgetId, transform)
        kindOf(appWidgetId)?.let { update(it, intArrayOf(appWidgetId)) }
    }

    /** Which of our widgets [appWidgetId] is; null when it isn't one of ours (or no longer exists). */
    fun kindOf(appWidgetId: Int): WidgetKind? = WidgetKind.entries.firstOrNull { appWidgetId in ids(it) }

    /** The widgets were removed: forget their options. */
    suspend fun forget(appWidgetIds: IntArray) = widgetConfigs.remove(appWidgetIds.toList())

    /** A backup restore gave the widgets new ids: keep their options ([oldIds] and [newIds] pair up). */
    suspend fun restored(oldIds: IntArray, newIds: IntArray) = widgetConfigs.remap(oldIds, newIds)

    /** The host category is a bit mask, so a lock-screen host may report keyguard together with another category. */
    private fun isKeyguard(options: Bundle?): Boolean {
        val category = options?.getInt(AppWidgetManager.OPTION_APPWIDGET_HOST_CATEGORY) ?: 0
        return (category and AppWidgetProviderInfo.WIDGET_CATEGORY_KEYGUARD) != 0
    }

    companion object {
        private const val TAG = "ClockblockWidget"
        private const val PREFS = "opus_widgets"
        private const val KEY_PREVIEW = "generated_previews_key"
        private const val KEY_RENDERED_CONFIG = "rendered_config_key"
        private const val READ_TIMEOUT_MS = 3_000L

        /** A 2×2 cell on a typical phone, for a widget whose host reported no size yet. */
        private val DefaultPreviewSize = SizeF(176f, 176f)

        fun componentName(context: Context, kind: WidgetKind): ComponentName = when (kind) {
            WidgetKind.TwoClocks -> ComponentName(context, TwoClocksWidgetProvider::class.java)
            WidgetKind.NextUp -> ComponentName(context, NextUpWidgetProvider::class.java)
        }

        /**
         * Cache key of the generated picker previews: re-publish after an update, a light/dark switch, or a font
         * scale, Bold text or density change (the previews' labels are fitted for these at capture time, like placed
         * widgets).
         */
        fun previewKey(
            versionCode: Long,
            night: Boolean,
            fontScale: Float,
            densityDpi: Int,
            fontWeightAdjustment: Int,
        ): String = "$versionCode-${if (night) "night" else "day"}-$fontScale-$densityDpi-w$fontWeightAdjustment"

        /**
         * Theme for a render: the app's theme setting (System follows the device), switched to the night-safe palette
         * (true black, dim amber) while the plan says avoid light / sleep and Night-safe is on (design.md §2.3 G: our
         * own screen must not sabotage the advice).
         */
        fun theme(settings: AppSettings, state: WidgetState, configuration: Configuration): WidgetTheme {
            val nightSafe = settings.nightSafeAuto &&
                (state as? WidgetState.Active)?.current?.type in setOf(AdviceType.AvoidLight, AdviceType.Sleep)
            if (nightSafe) return WidgetTheme.NightSafe
            val dark = when (settings.themeMode) {
                ThemeMode.Dark -> true
                ThemeMode.Light -> false
                ThemeMode.System ->
                    configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
            }
            return if (dark) WidgetTheme.Dark else WidgetTheme.Light
        }

        private fun Configuration.isNight() = uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES

        /** Bold text's weight boost, 0 when off or undefined (as in `TextFit.weightAdjustment`). */
        private fun Configuration.weightAdjustment() =
            if (fontWeightAdjustment == Configuration.FONT_WEIGHT_ADJUSTMENT_UNDEFINED) 0 else fontWeightAdjustment
    }
}

/** Used until a real [AdviceLogRepository] is bound: nothing logged, the Done button stays a button. */
internal object NoAdviceLogRepository : AdviceLogRepository {
    override fun logs(tripId: String): Flow<List<AdviceLog>> = flowOf(emptyList())
    override suspend fun log(tripId: String, adviceId: String, outcome: AdviceOutcome) = Unit
    override suspend fun clear(tripId: String, adviceId: String) = Unit
}

/** Used until a real [TripRepository] is bound: no trip, so no route strip. */
internal object NoTripRepository : TripRepository {
    override val trips: Flow<List<Trip>> = flowOf(emptyList())
    override fun trip(id: String): Flow<Trip?> = flowOf(null)
    override suspend fun upsert(trip: Trip) = Unit
    override suspend fun delete(id: String) = Unit
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

/** What the configuration screen previews: [model] for the widget, and whether it shows the [sample] trip. */
internal data class ConfigPreview(val model: WidgetModel, val sample: Boolean)

/** [this] model drawn with one widget's own options. */
internal fun WidgetModel.with(config: WidgetConfig): WidgetModel = copy(bodyRing = config.bodyRing)

/** Used until a real [WidgetConfigRepository] is bound: every widget uses the defaults. */
internal object NoWidgetConfigRepository : WidgetConfigRepository {
    override val configs: Flow<Map<Int, WidgetConfig>> = flowOf(emptyMap())
    override suspend fun update(appWidgetId: Int, transform: (WidgetConfig) -> WidgetConfig) = Unit
    override suspend fun remove(appWidgetIds: Collection<Int>) = Unit
    override suspend fun remap(oldIds: IntArray, newIds: IntArray) = Unit
    override suspend fun retainOnly(appWidgetIds: Collection<Int>) = Unit
}
