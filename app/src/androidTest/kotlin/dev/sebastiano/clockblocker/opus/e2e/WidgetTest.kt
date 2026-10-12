package dev.sebastiano.clockblocker.opus.e2e

import android.content.ComponentName
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Rect
import androidx.compose.ui.test.assertIsDisplayed
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiObject2
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

        val (twoClocks, _, _) = openLiveWidgets()
        assertRendered(twoClocks)
        twoClocks.click()

        awaitTag("route_plan", LongTimeoutMillis).assertIsDisplayed()
        awaitTag(PlanTags.NowCard, LongTimeoutMillis).assertIsDisplayed()
        awaitText(active.trip.destination.city)
    }

    @Test
    fun nextUpWidgetWithoutTripsOpensTheTripEditor() {
        seedOnboarded()

        val (_, nextUp, _) = openLiveWidgets()
        assertRendered(nextUp)
        nextUp.click()

        awaitTag("route_trip_editor", LongTimeoutMillis).assertIsDisplayed()
    }

    @Test
    fun routeGlobeWidgetRendersTheActiveTripAndOpensItsPlan() {
        seedOnboarded()
        val active = graph.seedTripWithActiveAdvice()

        val (_, _, routeGlobe) = openLiveWidgets()
        assertRendered(routeGlobe)
        routeGlobe.click()

        awaitTag("route_plan", LongTimeoutMillis).assertIsDisplayed()
        awaitTag(PlanTags.NowCard, LongTimeoutMillis).assertIsDisplayed()
        awaitText(active.trip.destination.city)
    }

    /** Starts the gallery's live page and returns the Two Clocks, Next up and Route globe host views once all have content. */
    private fun openLiveWidgets(): Triple<UiObject2, UiObject2, UiObject2> {
        val intent = Intent()
            .setComponent(ComponentName(context.packageName, GalleryActivity))
            .putExtra("page", "live")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        context.startActivity(intent)

        val hosts = By.clazz("android.appwidget.AppWidgetHostView").hasChild(By.depth(1))
        // Until.findObjects returns as soon as ONE host matches; the other widgets can still be binding, so poll
        // until all three host views have content.
        val deadline = System.currentTimeMillis() + LongTimeoutMillis
        var found = device.findObjects(hosts)
        while (found.size < 3 && System.currentTimeMillis() < deadline) {
            Thread.sleep(250)
            found = device.findObjects(hosts)
        }
        check(found.size >= 3) {
            "Expected three live widgets, found ${found.size} (is appwidget grantbind allowed?)"
        }
        return Triple(found[0], found[1], found[2])
    }

    /** The widget left its initial placeholder layout and drew its Remote Compose document. */
    private fun assertRendered(widget: UiObject2) {
        val placeholder = By.res(context.packageName, "widget_placeholder")
        // Plain polling: the gallery is a View activity, so there's no Compose content to synchronise with.
        val deadline = System.currentTimeMillis() + LongTimeoutMillis
        var distinct = if (widget.hasObject(placeholder)) 0 else distinctColours(widget.visibleBounds)
        while (distinct <= 8 && System.currentTimeMillis() < deadline) {
            Thread.sleep(250)
            distinct = if (widget.hasObject(placeholder)) 0 else distinctColours(widget.visibleBounds)
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
