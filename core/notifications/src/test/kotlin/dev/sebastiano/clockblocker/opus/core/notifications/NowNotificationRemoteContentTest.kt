package dev.sebastiano.clockblocker.opus.core.notifications

import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.net.Uri
import android.widget.RemoteViews
import androidx.test.core.app.ApplicationProvider
import dev.sebastiano.clockblocker.opus.core.model.AdviceType.AvoidLight
import dev.sebastiano.clockblocker.opus.core.model.AdviceType.Flight
import dev.sebastiano.clockblocker.opus.core.model.AdviceType.Sleep
import dev.sebastiano.clockblocker.opus.core.model.AppSettings
import dev.sebastiano.clockblocker.opus.core.model.DeepLinks
import dev.sebastiano.clockblocker.opus.core.model.JetLagPlan
import dev.sebastiano.clockblocker.opus.core.notifications.now.NowState
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Instant

/**
 * The Now notification with Remote Compose content (#49) and its fallback to the classic views. The documents
 * themselves are `:widget`'s (goldens there); here a stand-in content marks which views were posted.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [37])
class NowNotificationRemoteContentTest {

    private val context: Application = ApplicationProvider.getApplicationContext()
    private val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    // Saturday 2026-10-10, London on BST (UTC+1).
    private val avoid = advice(AvoidLight, "2026-10-10T14:00", "2026-10-10T17:00")
    private val sleep = advice(Sleep, "2026-10-10T17:00", "2026-10-11T01:00")
    private val flight = advice(Flight, "2026-10-10T12:00", "2026-10-10T23:00", detail = "BA7")

    private val plans = FakePlanRepository(planOf(avoid, sleep))
    private val settings = FakeSettingsRepository(AppSettings(reminderLeadMinutes = 15))
    private val capabilities = FakeCapabilities()
    private val clock = FakeClock(utc("2026-10-10T15:00"))
    private val snooze = SnoozeStore(context)
    private val content = FakeContent()

    private fun TestScope.surface() = NowNotificationSurface(
        context,
        plans,
        settings,
        FakeAdviceLogRepository(),
        snooze,
        NotificationFactory(context, capabilities),
        capabilities,
        clock,
        FakeTripRepository(),
        content,
        NotificationWorkScope(backgroundScope),
    )

    private val posted: Notification? get() = shadowOf(manager).getNotification(NotificationIds.NOW)

    @Before
    fun setUp() = snooze.clear()

    @Test
    fun `with Remote Compose content, the custom views are the documents and everything else stays`() = runTest {
        surface().render() shouldBe NowRendering.RemoteOngoing

        val n = posted.shouldNotBeNull()
        n.isRemote() shouldBe true
        content.states.single().headline shouldBe avoid
        // Text for the shade's accessibility, the ticker, wearables and automation, though the documents draw it.
        n.extras.getString(Notification.EXTRA_TITLE) shouldBe "Avoid light"
        n.extras.getCharSequence(Notification.EXTRA_TEXT).toString() shouldBe "until 18:00 · 02:00 Tokyo"
        n.extras.getString(Notification.EXTRA_TEMPLATE) shouldBe Notification.DecoratedCustomViewStyle::class.java.name
        n.actions.map { it.title.toString() } shouldContainExactly listOf("Done", "Can't do this", "Snooze 15 min")
        shadowOf(n.contentIntent).savedIntent.data shouldBe Uri.parse(DeepLinks.plan("trip-1"))
        n.channelId shouldBe ClockblockChannel.Now.id
        (n.flags and Notification.FLAG_ONGOING_EVENT) shouldBe Notification.FLAG_ONGOING_EVENT
        n.smallIcon.resId shouldBe R.drawable.ic_notif_app
        n.color shouldBe nowNotificationAccent(AvoidLight)
    }

    @Test
    fun `the Remote Compose bar moves by itself, so no progress tick is armed`() = runTest {
        surface().render() shouldBe NowRendering.RemoteOngoing
        shadowOf(context.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager).scheduledAlarms.shouldBeEmpty()
    }

    @Test
    fun `no supported document version falls back to the classic views`() = runTest {
        content.result = { null }

        surface().render() shouldBe NowRendering.Ongoing
        posted.shouldNotBeNull().isClassic() shouldBe true
    }

    @Test
    fun `a render that throws falls back to the classic views`() = runTest {
        content.result = { throw IllegalStateException("capture failed") }

        surface().render() shouldBe NowRendering.Ongoing
        val n = posted.shouldNotBeNull()
        n.isClassic() shouldBe true
        n.actions.map { it.title.toString() } shouldContainExactly listOf("Done", "Can't do this", "Snooze 15 min")
    }

    @Test
    fun `a post the platform rejects falls back to the classic views`() = runTest {
        val surface = surface()
        val real = surface.post
        surface.post = { id, notification ->
            if (notification.isRemote()) throw IllegalArgumentException("DrawInstructions can't be nested")
            real(id, notification)
        }

        surface.render() shouldBe NowRendering.Ongoing
        posted.shouldNotBeNull().isClassic() shouldBe true
    }

    @Test
    fun `the travel-day Live Update stays a standard template`() = runTest {
        plans.current.value = planOf(flight, avoid, sleep)
        clock.instant = utc("2026-10-10T14:30")

        surface().render() shouldBe NowRendering.LiveUpdate
        content.states.shouldBeEmpty()
        posted.shouldNotBeNull().contentView.shouldBeNull()
    }

    @Test
    fun `the lock screen version keeps the standard template with details hidden`() = runTest {
        settings.current.value = AppSettings(hideLockScreenDetails = true)

        surface().render() shouldBe NowRendering.RemoteOngoing
        val n = posted.shouldNotBeNull()
        n.isRemote() shouldBe true
        n.visibility shouldBe Notification.VISIBILITY_PRIVATE
        val public = n.publicVersion.shouldNotBeNull()
        public.contentView.shouldBeNull()
        public.bigContentView.shouldBeNull()
        public.extras.getCharSequence(Notification.EXTRA_TEXT).toString() shouldBe "until 18:00"
        (public.actions ?: emptyArray()).toList().shouldBeEmpty()
    }

    @Test
    fun `when SystemUI drops the notification, the classic one replaces it and stays on this system`() = runTest {
        val surface = surface()
        surface.render() shouldBe NowRendering.RemoteOngoing

        // SystemUI couldn't inflate it: cancelled without a word to the app.
        manager.cancel(NotificationIds.NOW)
        advanceTimeBy(NowNotificationSurface.INFLATION_CHECK.toMillis() + 1)
        runCurrent()

        posted.shouldNotBeNull().isClassic() shouldBe true
        surface.render() shouldBe NowRendering.Ongoing
        content.states.size shouldBe 1
        // Even after the process restarts.
        surface().render() shouldBe NowRendering.Ongoing
    }

    @Test
    fun `a notification SystemUI kept, or one already replaced, is left alone`() = runTest {
        val surface = surface()
        surface.render() shouldBe NowRendering.RemoteOngoing
        advanceTimeBy(NowNotificationSurface.INFLATION_CHECK.toMillis() + 1)
        runCurrent()
        posted.shouldNotBeNull().isRemote() shouldBe true

        // Snoozed before the check: the notification is gone on purpose.
        surface.render() shouldBe NowRendering.RemoteOngoing
        snooze.snooze(clock.now(), avoid.id)
        surface.render() shouldBe NowRendering.Hidden
        advanceTimeBy(NowNotificationSurface.INFLATION_CHECK.toMillis() + 1)
        runCurrent()
        snooze.clear()
        surface.render() shouldBe NowRendering.RemoteOngoing
    }

    private fun Notification.isRemote() = contentView?.layoutId == REMOTE_COLLAPSED && bigContentView?.layoutId == REMOTE_EXPANDED

    private fun Notification.isClassic() =
        contentView?.layoutId == R.layout.notif_now_collapsed && bigContentView?.layoutId == R.layout.notif_now_expanded

    /** Stands in for `:widget`'s documents with two marker layouts. */
    private inner class FakeContent : NowNotificationContent {
        val states = mutableListOf<NowState>()
        var result: () -> NowContentViews? = {
            NowContentViews(RemoteViews(context.packageName, REMOTE_COLLAPSED), RemoteViews(context.packageName, REMOTE_EXPANDED))
        }

        override suspend fun render(state: NowState, plan: JetLagPlan, now: Instant): NowContentViews? {
            states += state
            return result()
        }
    }

    private companion object {
        val REMOTE_COLLAPSED = android.R.layout.simple_list_item_1
        val REMOTE_EXPANDED = android.R.layout.simple_list_item_2
    }
}
