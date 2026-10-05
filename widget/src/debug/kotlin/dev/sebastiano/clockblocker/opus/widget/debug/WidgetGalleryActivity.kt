package dev.sebastiano.clockblocker.opus.widget.debug

import android.app.Activity
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
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
import dev.sebastiano.clockblocker.opus.widget.WidgetSizeDp
import dev.sebastiano.clockblocker.opus.widget.WidgetUpdater
import dev.sebastiano.clockblocker.opus.widget.draw.GlyphKind
import dev.sebastiano.clockblocker.opus.widget.preview.DemoPlans
import dev.sebastiano.clockblocker.opus.widget.state.WidgetState
import dev.sebastiano.clockblocker.opus.widget.state.WidgetStateMapper
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId

/**
 * DEBUG ONLY. Hosts the Opus widgets in-process so they can be checked on an emulator without a launcher:
 *
 * - demo frames: [WidgetRenderer] output (Remote Compose on API 36+, else classic) applied to plain
 *   [AppWidgetHostView]s at typical cell sizes, light and dark side by side, fed with [DemoPlans];
 * - live: real provider instances bound through [AppWidgetHost] (needs
 *   `adb shell appwidget grantbind --package <applicationId>`), updated by the real provider/updater path.
 *
 * Extras: `scenario` (a [DemoPlans.Scenario], NoTrip, or any [AdviceType] name: the demo's current block becomes
 * that type), `label` (replaces the advice label, to try long ones), `legacy` (bool),
 * `page` (twoclocks|nextup|live|all).
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
        val legacy = intent.getBooleanExtra("legacy", false)
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
            forceLegacy = legacy,
            textsTransform = { texts ->
                if (labelOverride == null || texts.glyph !is GlyphKind.Advice) texts
                else texts.copy(title = labelOverride, dialTitle = labelOverride)
            },
        )
        val scale = resources.configuration.fontScale
        column.addView(label("$scenario${labelOverride?.let { " \"$it\"" }.orEmpty()} · ${renderer.backend} · API ${Build.VERSION.SDK_INT} · font ×$scale"))

        val now = Instant.now()
        val state = when (scenario) {
            "NoTrip" -> WidgetState.NoTrip
            in DemoPlans.Scenario.entries.map { it.name } -> {
                val plan = DemoPlans.lisbonTokyo(now, DemoPlans.Scenario.valueOf(scenario))
                WidgetStateMapper.map(plan, now, ZoneId.of("Asia/Tokyo"))
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
                WidgetStateMapper.map(plan, now, ZoneId.of("Asia/Tokyo"))
            }
        }

        scope.launch {
            if (page == "all" || page == "twoclocks") {
                column.addView(label("Two Clocks 2×2"))
                column.addView(pair { dark -> frame(renderer, WidgetKind.TwoClocks, state, dark, 176, 176) })
                column.addView(label("Two Clocks 4×2"))
                for (dark in listOf(false, true)) {
                    column.addView(frame(renderer, WidgetKind.TwoClocks, state, dark, 360, 172))
                }
            }
            if (page == "all" || page == "nextup") {
                column.addView(label("Next up 4×1"))
                for (dark in listOf(false, true)) column.addView(frame(renderer, WidgetKind.NextUp, state, dark, 360, 76))
                column.addView(label("Next up 2×1"))
                column.addView(pair { dark -> frame(renderer, WidgetKind.NextUp, state, dark, 176, 76) })
                column.addView(label("Next up 1×1 · empty state 2×1"))
                column.addView(
                    row(
                        frame(renderer, WidgetKind.NextUp, state, false, 76, 76),
                        frame(renderer, WidgetKind.NextUp, state, true, 76, 76),
                        frame(renderer, WidgetKind.NextUp, WidgetState.NoTrip, false, 176, 76),
                    ),
                )
            }
            if (page == "all" || page == "live") addLiveWidgets(column)
        }
    }

    private suspend fun frame(
        renderer: WidgetRenderer,
        kind: WidgetKind,
        state: WidgetState,
        dark: Boolean,
        widthDp: Int,
        heightDp: Int,
    ): View {
        val view = AppWidgetHostView(this)
        view.layoutParams = LinearLayout.LayoutParams(px(widthDp), px(heightDp)).apply { setMargins(px(6), px(6), px(6), px(6)) }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            view.updateAppWidgetSize(Bundle(), listOf(SizeF(widthDp.toFloat(), heightDp.toFloat())))
        }
        val views = renderer.render(kind, state, dark, WidgetSizeDp(widthDp.toFloat(), heightDp.toFloat()))
        view.updateAppWidget(views)
        return view
    }

    private suspend fun pair(make: suspend (Boolean) -> View): View = row(make(false), make(true))

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
    }
}
