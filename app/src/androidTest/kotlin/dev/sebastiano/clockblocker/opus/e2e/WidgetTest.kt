package dev.sebastiano.clockblocker.opus.e2e

import android.content.ComponentName
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Rect
import androidx.compose.ui.test.assertIsDisplayed
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import dev.sebastiano.clockblocker.opus.feature.plan.PlanTags
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Home-screen widgets, hosted through a real [android.appwidget.AppWidgetHost] by the debug-only
 * `WidgetGalleryActivity` (page `live`): the real providers are bound, render through the real update path, and a
 * tap goes through the widget's PendingIntent. No launcher is involved, so the test doesn't depend on (or change)
 * the user's home screen. Binding needs `appwidget grantbind`, which is granted for this app only and revoked
 * afterwards.
 */
@RunWith(AndroidJUnit4::class)
class WidgetTest : ClockblockE2eTest() {

    @Before
    fun allowBinding() {
        shell("appwidget grantbind --package ${context.packageName}")
    }

    @After
    fun revokeBinding() {
        shell("appwidget revokebind --package ${context.packageName}")
    }

    @Test
    fun twoClocksWidgetRendersTheActiveTripAndOpensItsPlan() {
        seedOnboarded()
        val active = graph.seedTripWithActiveAdvice()

        val (twoClocks, _) = openLiveWidgets()
        assertRendered(twoClocks)
        twoClocks.click()

        awaitTag("route_plan", LongTimeoutMillis).assertIsDisplayed()
        awaitTag(PlanTags.NowCard, LongTimeoutMillis).assertIsDisplayed()
        awaitText(active.trip.destination.city)
    }

    @Test
    fun nextUpWidgetWithoutTripsOpensTheTripEditor() {
        seedOnboarded()

        val (_, nextUp) = openLiveWidgets()
        assertRendered(nextUp)
        nextUp.click()

        awaitTag("route_trip_editor", LongTimeoutMillis).assertIsDisplayed()
    }

    /** Starts the gallery's live page and returns the Two Clocks and Next up host views once both have content. */
    private fun openLiveWidgets(): Pair<UiObject2, UiObject2> {
        val intent = Intent()
            .setComponent(ComponentName(context.packageName, GalleryActivity))
            .putExtra("page", "live")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        context.startActivity(intent)

        val hosts = By.clazz("android.appwidget.AppWidgetHostView").hasChild(By.depth(1))
        val found = device.wait(Until.findObjects(hosts), LongTimeoutMillis)
        check(found != null && found.size >= 2) {
            "Expected two live widgets, found ${found?.size ?: 0} (is appwidget grantbind allowed?)"
        }
        return found[0] to found[1]
    }

    /** The widget drew something: its pixels aren't a single flat colour. */
    private fun assertRendered(widget: UiObject2) {
        // Plain polling: the gallery is a View activity, so there's no Compose content to synchronise with.
        val deadline = System.currentTimeMillis() + LongTimeoutMillis
        var distinct = distinctColours(widget.visibleBounds)
        while (distinct <= 8 && System.currentTimeMillis() < deadline) {
            Thread.sleep(250)
            distinct = distinctColours(widget.visibleBounds)
        }
        assertTrue("widget at ${widget.visibleBounds} looks blank ($distinct colours)", distinct > 8)
    }

    private fun distinctColours(bounds: Rect): Int {
        val screen: Bitmap = instrumentation.uiAutomation.takeScreenshot() ?: return 0
        val colours = HashSet<Int>()
        val step = 8
        for (y in bounds.top until bounds.bottom.coerceAtMost(screen.height) step step) {
            for (x in bounds.left until bounds.right.coerceAtMost(screen.width) step step) {
                colours += screen.getPixel(x, y)
            }
        }
        screen.recycle()
        return colours.size
    }

    private companion object {
        const val GalleryActivity = "dev.sebastiano.clockblocker.opus.widget.debug.WidgetGalleryActivity"
    }
}
