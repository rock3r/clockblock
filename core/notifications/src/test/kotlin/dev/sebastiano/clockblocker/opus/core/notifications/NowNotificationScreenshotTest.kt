package dev.sebastiano.clockblocker.opus.core.notifications

import android.app.Notification
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.ContextThemeWrapper
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.RemoteViews
import com.github.takahirom.roborazzi.captureRoboImage
import dev.sebastiano.clockblocker.opus.core.model.AdviceLog
import dev.sebastiano.clockblocker.opus.core.model.AdviceOutcome
import dev.sebastiano.clockblocker.opus.core.model.AdviceType.AvoidCaffeine
import dev.sebastiano.clockblocker.opus.core.model.AdviceType.AvoidLight
import dev.sebastiano.clockblocker.opus.core.model.AdviceType.SeeBrightLight
import dev.sebastiano.clockblocker.opus.core.model.AdviceType.Sleep
import dev.sebastiano.clockblocker.opus.core.model.JetLagPlan
import dev.sebastiano.clockblocker.opus.core.notifications.now.NowStateCalculator
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Duration
import java.time.Instant
import kotlin.math.roundToInt

/**
 * Goldens of the Now notification as the shade lays it out: the platform's own decorated template (header with the
 * body clock, expand button, actions) wrapped around our content, collapsed above expanded, on cards the colour of
 * the shade. Light and dark, at the largest font scale we design for, in a gap, with overlapping blocks (#43), once
 * answered, and during sleep.
 *
 * Robolectric draws the small icon where newer shades show the app icon, and the card colours only approximate the
 * shade's; the layout, text and tints are the real thing.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h900dp-xxhdpi")
class NowNotificationScreenshotTest {

    // Saturday 2026-10-10, London on BST (UTC+1); Tokyo is the other zone.
    private val avoid = advice(AvoidLight, "2026-10-10T14:00", "2026-10-10T17:00")
    private val caffeine = advice(AvoidCaffeine, "2026-10-10T13:00", "2026-10-10T19:00")
    private val sleep = advice(Sleep, "2026-10-10T17:00", "2026-10-11T01:00")
    private val light = advice(SeeBrightLight, "2026-10-11T01:00", "2026-10-11T03:00")

    @Test
    fun light() = snap("now_light", planOf(avoid, sleep, light), utc("2026-10-10T15:00"))

    @Test
    fun dark() = snap("now_dark", planOf(avoid, sleep, light), utc("2026-10-10T15:00"), night = true)

    @Test
    fun fontScale() = snap("now_font_1_3", planOf(avoid, caffeine, sleep), utc("2026-10-10T15:00"), fontScale = 1.3f)

    @Test
    fun fontScaleDark() =
        snap("now_font_1_3_dark", planOf(avoid, caffeine, sleep), utc("2026-10-10T15:00"), night = true, fontScale = 1.3f)

    @Test
    fun overlap() = snap("now_overlap", planOf(avoid, caffeine, sleep), utc("2026-10-10T16:20"))

    @Test
    fun gap() = snap("now_gap", planOf(avoid, sleep), utc("2026-10-10T13:50"))

    @Test
    fun gapFontScale() = snap("now_gap_font_1_3", planOf(avoid, sleep), utc("2026-10-10T13:50"), fontScale = 1.3f)

    @Test
    fun done() = snap(
        "now_done",
        planOf(avoid, sleep),
        utc("2026-10-10T15:00"),
        logs = listOf(AdviceLog(avoid.id, AdviceOutcome.Done)),
    )

    @Test
    fun sleeping() = snap("now_sleep_dark", planOf(avoid, sleep, light), utc("2026-10-10T19:00"), night = true)

    private fun snap(
        name: String,
        plan: JetLagPlan,
        at: Instant,
        night: Boolean = false,
        fontScale: Float = 1f,
        logs: List<AdviceLog> = emptyList(),
    ) {
        if (night) RuntimeEnvironment.setQualifiers("+night")
        RuntimeEnvironment.setFontScale(fontScale)
        val application = RuntimeEnvironment.getApplication()
        // The header names the app; this module's test manifest has no label.
        application.applicationInfo.nonLocalizedLabel = APP_NAME
        val context = ContextThemeWrapper(application, android.R.style.Theme_DeviceDefault_DayNight)
        val state = NowStateCalculator.compute(plan, at, logs, Duration.ofMinutes(15))!!
        val notification: Notification = NotificationFactory(application, FakeCapabilities()).now(state, plan, at)
        val template = Notification.Builder.recoverBuilder(context, notification)

        val density = context.resources.displayMetrics.density
        fun dp(value: Int) = (value * density).roundToInt()
        val column = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(if (night) Color.BLACK else Color.parseColor("#F4F0F8"))
            setPadding(dp(8), dp(8), dp(8), 0)
        }
        listOf(template.createContentView(), template.createBigContentView()).forEach { views: RemoteViews ->
            val card = FrameLayout(context).apply {
                background = GradientDrawable().apply {
                    cornerRadius = dp(24).toFloat()
                    setColor(Color.parseColor(if (night) "#2B2930" else "#FFFFFF"))
                }
                addView(views.apply(context, this))
            }
            column.addView(
                card,
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                    .apply { bottomMargin = dp(8) },
            )
        }
        // AT_MOST, not UNSPECIFIED: the platform's text views lay out nothing without a bound.
        column.measure(
            View.MeasureSpec.makeMeasureSpec(dp(WIDTH_DP), View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(dp(MAX_HEIGHT_DP), View.MeasureSpec.AT_MOST),
        )
        column.layout(0, 0, column.measuredWidth, column.measuredHeight)
        val bitmap = Bitmap.createBitmap(column.measuredWidth, column.measuredHeight, Bitmap.Config.ARGB_8888)
        column.draw(Canvas(bitmap))
        bitmap.captureRoboImage("src/test/screenshots/$name.png")
    }

    private companion object {
        const val APP_NAME = "Clockblock"

        /** A phone shade card on a 411dp-wide screen. */
        const val WIDTH_DP = 395
        const val MAX_HEIGHT_DP = 1200
    }
}
