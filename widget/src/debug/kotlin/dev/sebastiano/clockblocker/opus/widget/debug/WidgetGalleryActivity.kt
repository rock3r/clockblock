package dev.sebastiano.clockblocker.opus.widget.debug

import android.app.Activity
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.SizeF
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import dev.sebastiano.clockblocker.opus.core.model.AdviceType
import dev.sebastiano.clockblocker.opus.widget.WidgetKind
import dev.sebastiano.clockblocker.opus.widget.WidgetRenderer
import dev.sebastiano.clockblocker.opus.widget.WidgetUpdater
import dev.sebastiano.clockblocker.opus.widget.draw.GlyphKind
import dev.sebastiano.clockblocker.opus.widget.draw.WidgetTheme
import dev.sebastiano.clockblocker.opus.widget.preview.DemoPlans
import dev.sebastiano.clockblocker.opus.widget.rc.RemoteComposeSupport
import dev.sebastiano.clockblocker.opus.widget.state.WidgetState
import dev.sebastiano.clockblocker.opus.widget.state.WidgetStateMapper
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.time.Instant

/**
 * DEBUG ONLY. Hosts the Clockblock widgets in-process so they can be checked on an emulator without a launcher:
 *
 * - demo frames: [WidgetRenderer] output (Remote Compose, or the placeholder) applied to plain
 *   [AppWidgetHostView]s at a typical cell size per responsive bucket, in every [WidgetTheme], fed with [DemoPlans];
 * - live: real provider instances bound through [AppWidgetHost] (needs
 *   `adb shell appwidget grantbind --package <applicationId>`), updated by the real provider/updater path.
 *
 * Extras: `scenario` (a [DemoPlans.Scenario], NoTrip, or any [AdviceType] name: the demo's current block becomes
 * that type), `label` (replaces the advice label, to try long ones), `page` (twoclocks|nextup|live|all, or
 * notification for [RemoteComposeNotificationProbe]).
 *
 * Settings → Debug (debug builds) opens these pages without adb.
 *
 * `adb shell am start -n <applicationId>/dev.sebastiano.clockblocker.opus.widget.debug.WidgetGalleryActivity --es page nextup`
 */
class WidgetGalleryActivity : Activity() {
    private val scope = MainScope()
    private var host: AppWidgetHost? = null

    private val dp get() = resources.displayMetrics.density

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val scenario = intent.getStringExtra("scenario") ?: "AvoidLight"
        val page = intent.getStringExtra("page") ?: "all"
        val labelOverride = intent.getStringExtra("label")

        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(px(12), px(36), px(12), px(36))
        }
        val scroll = ScrollView(this).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(0xFF2A2E6E.toInt(), 0xFF6B4E9B.toInt(), 0xFFF49D6E.toInt()),
            )
            addView(column)
        }
        setContentView(scroll)

        val renderer = WidgetRenderer(
            this,
            textsTransform = { texts ->
                if (labelOverride == null || texts.glyph !is GlyphKind.Advice) texts
                else texts.copy(title = labelOverride, dialTitle = labelOverride)
            },
        )
        val scale = resources.configuration.fontScale
        column.addView(label("$scenario${labelOverride?.let { " \"$it\"" }.orEmpty()} · player v${runCatching { RemoteComposeSupport.supportedVersion() }.getOrNull()} · font ×$scale"))

        val now = Instant.now()
        val state = when (scenario) {
            "NoTrip" -> WidgetState.NoTrip
            in DemoPlans.Scenario.entries.map { it.name } -> {
                val plan = DemoPlans.lisbonTokyo(now, DemoPlans.Scenario.valueOf(scenario))
                WidgetStateMapper.map(plan, now)
            }
            else -> {
                // Any AdviceType: the demo's current block (AvoidLight, now ± 1.5 h) becomes that type.
                val type = AdviceType.valueOf(scenario)
                val plan = DemoPlans.lisbonTokyo(now, DemoPlans.Scenario.AvoidLight).let { plan ->
                    plan.copy(
                        days = plan.days.map { day ->
                            day.copy(
                                advice = day.advice.map {
                                    when {
                                        it.type == AdviceType.AvoidLight -> it.copy(type = type)
                                        it.type == type -> it.copy(type = AdviceType.AvoidLight)
                                        else -> it
                                    }
                                },
                            )
                        },
                    )
                }
                WidgetStateMapper.map(plan, now)
            }
        }

        scope.launch {
            if (page == "notification") {
                column.addView(label("Remote Compose notification probe (#49): posting…"))
                RemoteComposeNotificationProbe(this@WidgetGalleryActivity).run(state, WidgetTheme.Light).forEach {
                    column.addView(label("${it.variant}: ${it.verdict}: ${it.outcome}"))
                }
                return@launch
            }
            for ((kind, buckets) in listOf(WidgetKind.TwoClocks to TWO_CLOCKS_CELLS, WidgetKind.NextUp to NEXT_UP_CELLS)) {
                val pageName = if (kind == WidgetKind.TwoClocks) "twoclocks" else "nextup"
                if (page != "all" && page != pageName) continue
                for ((name, size) in buckets) {
                    column.addView(label("${kind.name} $name"))
                    val (w, h) = size
                    if (w > 200) {
                        WidgetTheme.entries.forEach { column.addView(frame(renderer, kind, state, it, w, h)) }
                    } else {
                        column.addView(row(*WidgetTheme.entries.map { frame(renderer, kind, state, it, w, h) }.toTypedArray()))
                    }
                }
                column.addView(label("${kind.name} empty state"))
                column.addView(frame(renderer, kind, WidgetState.NoTrip, WidgetTheme.Light, 176, 176))
            }
            if (page == "all" || page == "live") addLiveWidgets(column)
        }
    }

    private suspend fun frame(
        renderer: WidgetRenderer,
        kind: WidgetKind,
        state: WidgetState,
        theme: WidgetTheme,
        widthDp: Int,
        heightDp: Int,
    ): View {
        val view = AppWidgetHostView(this)
        view.layoutParams = LinearLayout.LayoutParams(px(widthDp), px(heightDp)).apply { setMargins(px(6), px(6), px(6), px(6)) }
        view.updateAppWidgetSize(Bundle(), listOf(SizeF(widthDp.toFloat(), heightDp.toFloat())))
        val views = renderer.render(kind, state, theme)
        view.updateAppWidget(views)
        return view
    }

    private fun row(vararg views: View) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        views.forEach { addView(it) }
    }

    private fun label(text: String) = TextView(this).apply {
        this.text = text
        setTextColor(Color.WHITE)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        setPadding(px(6), px(10), px(6), px(2))
    }

    /** Real providers bound through AppWidgetHost: exercises Metro receiver injection + the update path. */
    private fun addLiveWidgets(column: LinearLayout) {
        val manager = AppWidgetManager.getInstance(this)
        val host = AppWidgetHost(this, HOST_ID).also { this.host = it }
        host.deleteHost() // drop ids from previous runs
        host.startListening()
        column.addView(label("Live providers (AppWidgetHost)"))
        listOf(WidgetKind.TwoClocks to (176 to 176), WidgetKind.NextUp to (360 to 76)).forEach { (kind, size) ->
            val component = WidgetUpdater.componentName(this, kind)
            val id = host.allocateAppWidgetId()
            val options = Bundle().apply {
                putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, size.first)
                putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, size.first)
                putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, size.second)
                putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, size.second)
            }
            if (!manager.bindAppWidgetIdIfAllowed(id, component, options)) {
                column.addView(label("Bind not allowed: adb shell appwidget grantbind --package $packageName"))
                return
            }
            val info = manager.getAppWidgetInfo(id)
            val view = host.createView(this, id, info)
            view.layoutParams = ViewGroup.MarginLayoutParams(px(size.first), px(size.second))
                .let { LinearLayout.LayoutParams(it).apply { setMargins(px(6), px(6), px(6), px(6)) } }
            column.addView(view)
        }
    }

    override fun onDestroy() {
        scope.cancel()
        host?.stopListening()
        super.onDestroy()
    }

    private fun px(dp: Int) = (dp * this.dp).toInt()

    private companion object {
        const val HOST_ID = 0x0C10C

        /** Typical launcher cell sizes (dp) that land in each responsive bucket. */
        val TWO_CLOCKS_CELLS = listOf(
            "1×1" to (76 to 76),
            "2×2" to (176 to 176),
            "4×2" to (360 to 172),
            "2×3" to (176 to 260),
            "4×3" to (360 to 260),
        )
        val NEXT_UP_CELLS = listOf(
            "1×1" to (76 to 76),
            "2×1" to (176 to 76),
            "4×1" to (360 to 76),
            "2×2" to (176 to 176),
            "2×3" to (176 to 260),
            "4×2" to (360 to 172),
            "4×3" to (360 to 260),
        )
    }
}
