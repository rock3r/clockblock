package dev.sebastiano.clockblocker.opus.widget.config

import android.app.Activity
import android.app.Application
import android.appwidget.AppWidgetManager
import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import dev.sebastiano.clockblocker.opus.widget.WidgetKind
import dev.sebastiano.clockblocker.opus.widget.WidgetUpdater
import io.kotest.matchers.shouldBe
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The launcher's entry point: it is exported, so it only opens for one of our own widgets. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [37])
class WidgetConfigActivityTest {
    private val app: Application = ApplicationProvider.getApplicationContext()

    private fun intent(appWidgetId: Int?) = Intent(app, WidgetConfigActivity::class.java).apply {
        if (appWidgetId != null) putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
    }

    @Test
    fun `refuses an id that isn't one of our widgets`() {
        ActivityScenario.launchActivityForResult<WidgetConfigActivity>(intent(42)).use { scenario ->
            scenario.result.resultCode shouldBe Activity.RESULT_CANCELED
        }
    }

    @Test
    fun `refuses a launch without a widget id`() {
        ActivityScenario.launchActivityForResult<WidgetConfigActivity>(intent(null)).use { scenario ->
            scenario.result.resultCode shouldBe Activity.RESULT_CANCELED
        }
    }

    @Test
    fun `opens for one of our widgets, and leaving keeps the widget`() {
        val manager = AppWidgetManager.getInstance(app)
        shadowOf(manager).setAllowedToBindAppWidgets(true)
        manager.bindAppWidgetIdIfAllowed(7, WidgetUpdater.componentName(app, WidgetKind.TwoClocks))

        ActivityScenario.launchActivityForResult<WidgetConfigActivity>(intent(7)).use { scenario ->
            scenario.onActivity { it.isFinishing shouldBe false }
            scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
            scenario.result.resultCode shouldBe Activity.RESULT_OK
            scenario.result.resultData.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1) shouldBe 7
        }
    }
}
