package dev.sebastiano.clockblocker.opus.core.notifications

import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.test.core.app.ApplicationProvider
import dev.sebastiano.clockblocker.opus.core.model.AdviceOutcome
import dev.sebastiano.clockblocker.opus.core.model.AdviceType.AvoidLight
import dev.sebastiano.clockblocker.opus.core.model.AdviceType.Flight
import dev.sebastiano.clockblocker.opus.core.model.AdviceType.Melatonin
import dev.sebastiano.clockblocker.opus.core.model.AdviceType.SeeBrightLight
import dev.sebastiano.clockblocker.opus.core.model.AdviceType.Sleep
import dev.sebastiano.clockblocker.opus.core.model.AppSettings
import dev.sebastiano.clockblocker.opus.core.model.DeepLinks
import dev.sebastiano.clockblocker.opus.core.model.FlightLeg
import dev.sebastiano.clockblocker.opus.core.model.Place
import dev.sebastiano.clockblocker.opus.core.model.Trip
import dev.sebastiano.clockblocker.opus.core.notifications.schedule.ReminderKind
import dev.sebastiano.clockblocker.opus.core.notifications.schedule.ReminderSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.longs.shouldBeGreaterThan
import io.kotest.matchers.longs.shouldBeLessThanOrEqual
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36])
class NowNotificationSurfaceTest {

    private val context: Application = ApplicationProvider.getApplicationContext()
    private val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    // Saturday 2026-10-10, London on BST (UTC+1).
    private val avoid = advice(AvoidLight, "2026-10-10T14:00", "2026-10-10T17:00")
    private val sleep = advice(Sleep, "2026-10-10T17:00", "2026-10-11T01:00")
    private val flight = advice(Flight, "2026-10-10T12:00", "2026-10-10T23:00", detail = "BA7")

    private val plans = FakePlanRepository(planOf(avoid, sleep))
    private val settings = FakeSettingsRepository(AppSettings(reminderLeadMinutes = 15))
    private val logs = FakeAdviceLogRepository()
    private val clock = FakeClock(utc("2026-10-10T15:00"))
    private val capabilities = FakeCapabilities()
    private val snooze = SnoozeStore(context)
    private val trips = FakeTripRepository(
        Trip(
            id = "trip-1",
            title = "Tokyo",
            legs = listOf(
                FlightLeg(
                    id = "leg-1",
                    origin = Place("LHR", "Heathrow", "London", "GB", "Europe/London", 51.47, -0.45),
                    destination = Place("HND", "Haneda", "Tokyo", "JP", "Asia/Tokyo", 35.55, 139.78),
                    departureLocal = LocalDateTime.parse("2026-10-10T13:00"),
                    arrivalLocal = LocalDateTime.parse("2026-10-11T08:00"),
                    flightNumber = "BA7",
                ),
            ),
            createdAt = Instant.EPOCH,
        ),
    )

    private val surface = NowNotificationSurface(
        context, plans, settings, logs, snooze, NotificationFactory(context, capabilities, clock), capabilities, clock, trips,
    )

    private val posted: Notification? get() = shadowOf(manager).getNotification(NotificationIds.NOW)

    @Before
    fun setUp() = snooze.clear()

    @Test
    fun `our notifications bundle under our own summary, which opens the current plan`() = runTest {
        val reminders = ReminderNotifier(context, NotificationFactory(context, capabilities, clock), capabilities)
        surface.render() shouldBe NowRendering.Ongoing
        // One notification: no summary (a lone summary would show as an empty notification).
        shadowOf(manager).getNotification(NotificationIds.SUMMARY).shouldBeNull()

        reminders.postTest() shouldBe true
        val summary = shadowOf(manager).getNotification(NotificationIds.SUMMARY).shouldNotBeNull()
        (summary.flags and Notification.FLAG_GROUP_SUMMARY) shouldBe Notification.FLAG_GROUP_SUMMARY
        summary.group shouldBe NotificationGroup.KEY
        posted.shouldNotBeNull().group shouldBe NotificationGroup.KEY
        shadowOf(manager).getNotification(NotificationIds.TEST).group shouldBe NotificationGroup.KEY
        shadowOf(summary.contentIntent).savedIntent.data shouldBe Uri.parse(DeepLinks.CURRENT_PLAN)

        // Back to one child: the summary goes with the group.
        plans.current.value = null
        surface.render() shouldBe NowRendering.Hidden
        shadowOf(manager).getNotification(NotificationIds.SUMMARY).shouldBeNull()
    }

    @Test
    fun `with the ongoing Now and an expiring reminder, the summary expires with the reminder`() = runTest {
        val reminders = ReminderNotifier(context, NotificationFactory(context, capabilities, clock), capabilities)
        surface.render() shouldBe NowRendering.Ongoing
        reminders.postTest() shouldBe true

        val test = shadowOf(manager).getNotification(NotificationIds.TEST).shouldNotBeNull()
        val summary = shadowOf(manager).getNotification(NotificationIds.SUMMARY).shouldNotBeNull()
        // The group falls below two children when the test reminder times out, even though Now never expires.
        summary.timeoutAfter shouldBeGreaterThan 0L
        summary.timeoutAfter shouldBeLessThanOrEqual test.timeoutAfter
    }

    @Test
    fun `summary lifetime is the second-longest child lifetime`() {
        // null = never expires (ongoing).
        NotificationGroup.summaryLifetime(listOf(null, 600_000L)) shouldBe 600_000L
        NotificationGroup.summaryLifetime(listOf(300_000L, 600_000L)) shouldBe 300_000L
        NotificationGroup.summaryLifetime(listOf(600_000L, null, 300_000L)) shouldBe 600_000L
        NotificationGroup.summaryLifetime(listOf(null, null)) shouldBe null
    }

    @Test
    fun `ongoing Now notification shows label, until and then in the current zone`() = runTest {
        surface.render() shouldBe NowRendering.Ongoing

        val n = posted.shouldNotBeNull()
        n.extras.getString(Notification.EXTRA_TITLE) shouldBe "Avoid light"
        n.extras.getCharSequence(Notification.EXTRA_TEXT).toString() shouldBe "until 18:00 · then Sleep 18:00–02:00"
        n.extras.getCharSequence(Notification.EXTRA_BIG_TEXT).toString() shouldContain
            "Tokyo: until 02:00 · then Sleep 02:00–10:00"
        n.channelId shouldBe OpusChannel.Now.id
        n.category shouldBe Notification.CATEGORY_REMINDER
        (n.flags and Notification.FLAG_ONGOING_EVENT) shouldBe Notification.FLAG_ONGOING_EVENT
        (n.flags and Notification.FLAG_ONLY_ALERT_ONCE) shouldBe Notification.FLAG_ONLY_ALERT_ONCE
        // setSilent(true): the child of a group only alerts via its (non-existent) summary, i.e. never.
        n.groupAlertBehavior shouldBe Notification.GROUP_ALERT_SUMMARY
        n.smallIcon.resId shouldBe R.drawable.ic_notif_avoid_light
        n.visibility shouldBe Notification.VISIBILITY_PUBLIC
    }

    @Test
    fun `actions are Done, Can't do this and Snooze, and the content opens the plan deep link`() = runTest {
        surface.render()

        val n = posted.shouldNotBeNull()
        n.actions.map { it.title.toString() } shouldContainExactly listOf("Done", "Can't do this", "Snooze 15 min")
        val content = shadowOf(n.contentIntent).savedIntent
        content.data shouldBe Uri.parse(DeepLinks.plan("trip-1"))
        content.`package` shouldBe context.packageName
        val done = shadowOf(n.actions[0].actionIntent).savedIntent
        done.action shouldBe NotificationIntents.adviceAction(context, AdviceAction.Done)
        done.getStringExtra(NotificationIntents.EXTRA_ADVICE_ID) shouldBe avoid.id
        done.getStringExtra(NotificationIntents.EXTRA_TRIP_ID) shouldBe "trip-1"
    }

    @Test
    fun `test reminder opens the current plan when tapped`() {
        val n = NotificationFactory(context, capabilities, clock).test()

        val content = shadowOf(n.contentIntent.shouldNotBeNull()).savedIntent
        content.data shouldBe Uri.parse(DeepLinks.CURRENT_PLAN)
        content.`package` shouldBe context.packageName
    }

    @Test
    fun `text follows the user's zone after landing`() = runTest {
        clock.zoneId = ZoneId.of("Asia/Tokyo")

        surface.render()

        posted!!.extras.getCharSequence(Notification.EXTRA_TEXT).toString() shouldBe "until 02:00 · then Sleep 02:00–10:00"
    }

    @Test
    fun `logged outcome leaves a single Undo`() = runTest {
        logs.log("trip-1", avoid.id, AdviceOutcome.Done)

        surface.render()

        val n = posted.shouldNotBeNull()
        n.extras.getCharSequence(Notification.EXTRA_TEXT).toString() shouldBe "Done · until 18:00 · then Sleep 18:00–02:00"
        n.actions.map { it.title.toString() } shouldContainExactly listOf("Undo")
        val undo = shadowOf(n.actions[0].actionIntent).savedIntent
        undo.action shouldBe NotificationIntents.adviceAction(context, AdviceAction.Undo)
        undo.getStringExtra(NotificationIntents.EXTRA_ADVICE_ID) shouldBe avoid.id
    }

    @Test
    fun `the header shows the body clock`() = runTest {
        clock.zoneId = ZoneId.of("Asia/Tokyo")

        surface.render()

        posted.shouldNotBeNull().extras.getCharSequence(Notification.EXTRA_SUB_TEXT).toString() shouldBe "Body 8 h behind"
    }

    @Test
    fun `details stay visible on the lock screen by default`() = runTest {
        surface.render()

        val n = posted.shouldNotBeNull()
        n.visibility shouldBe Notification.VISIBILITY_PUBLIC
        n.publicVersion.shouldBeNull()
    }

    @Test
    fun `hiding lock screen details posts a private notification with a redacted public version`() = runTest {
        settings.current.value = AppSettings(hideLockScreenDetails = true)
        plans.current.value = planOf(flight, sleep)
        clock.instant = utc("2026-10-10T13:00")

        surface.render() shouldBe NowRendering.Ongoing

        val n = posted.shouldNotBeNull()
        n.visibility shouldBe Notification.VISIBILITY_PRIVATE
        n.extras.getString(Notification.EXTRA_TITLE) shouldBe "In flight · BA7"
        val public = n.publicVersion.shouldNotBeNull()
        public.extras.getString(Notification.EXTRA_TITLE) shouldBe "In flight"
        public.extras.getCharSequence(Notification.EXTRA_TEXT).toString() shouldBe "until 18:00 · then Sleep 18:00–02:00"
        public.extras.getCharSequence(Notification.EXTRA_BIG_TEXT).toString() shouldNotContain "Tokyo"
    }

    @Test
    fun `a redacted melatonin reminder hides its name, dose and pill icon`() {
        val melatonin = advice(Melatonin, "2026-10-10T20:00", detail = "0.5 mg")
        val spec = ReminderSpec(ReminderKind.Moment, melatonin, expiresAt = melatonin.start.plusSeconds(7200))

        val n = NotificationFactory(context, capabilities, clock).reminder(spec, planOf(avoid, sleep, melatonin), utc("2026-10-10T20:00"), redact = true)

        n.visibility shouldBe Notification.VISIBILITY_PRIVATE
        n.extras.getString(Notification.EXTRA_TITLE) shouldBe "Melatonin now"
        n.smallIcon.resId shouldBe R.drawable.ic_notif_clock
        val public = n.publicVersion.shouldNotBeNull()
        public.extras.getString(Notification.EXTRA_TITLE) shouldBe "Plan step now"
        public.extras.getCharSequence(Notification.EXTRA_TEXT).toString() shouldBe "Unlock to see details"
    }

    @Test
    fun `channels are created with their groups and names`() = runTest {
        surface.render()

        val channels = shadowOf(manager).notificationChannels.associateBy { it.id }
        channels.keys shouldBe OpusChannel.entries.map { it.id }.toSet()
        channels.getValue(OpusChannel.Light.id).name shouldBe "Light"
        channels.getValue(OpusChannel.SupplementsAndCaffeine.id).name shouldBe "Supplements & caffeine"
        channels.getValue(OpusChannel.Now.id).sound.shouldBeNull()
        channels.getValue(OpusChannel.Now.id).group shouldBe OpusChannelGroup.Ongoing.id
        channels.getValue(OpusChannel.Sleep.id).group shouldBe OpusChannelGroup.Reminders.id
        (channels.getValue(OpusChannel.TravelLive.id).importance > NotificationManager.IMPORTANCE_MIN) shouldBe true
    }

    @Test
    fun `hidden when reminders are off, notifications blocked, no plan, plan not started or snoozed`() = runTest {
        surface.render() shouldBe NowRendering.Ongoing

        settings.current.value = AppSettings(remindersEnabled = false)
        surface.render() shouldBe NowRendering.Hidden
        posted.shouldBeNull()

        settings.current.value = AppSettings()
        capabilities.notifications = false
        surface.render() shouldBe NowRendering.Hidden

        capabilities.notifications = true
        plans.current.value = null
        surface.render() shouldBe NowRendering.Hidden

        plans.current.value = planOf(avoid, sleep)
        clock.instant = utc("2026-10-09T12:00")
        surface.render() shouldBe NowRendering.Hidden

        clock.instant = utc("2026-10-10T15:00")
        snooze.snooze(clock.instant, avoid.id)
        surface.render() shouldBe NowRendering.Hidden
        clock.instant = utc("2026-10-10T15:16")
        surface.render() shouldBe NowRendering.Ongoing
    }

    @Test
    fun `travel day becomes a promoted Live Update with segments, points and an end-time chip`() = runTest {
        plans.current.value = planOf(flight, avoid, sleep)
        clock.instant = utc("2026-10-10T14:30")

        surface.render() shouldBe NowRendering.LiveUpdate

        val n = posted.shouldNotBeNull()
        n.channelId shouldBe OpusChannel.TravelLive.id
        NotificationCompat.isRequestPromotedOngoing(n) shouldBe true
        n.extras.getString(Notification.EXTRA_TITLE) shouldBe "Avoid light"
        val style = Notification.Builder.recoverBuilder(context, n).style as Notification.ProgressStyle
        // 09-12 airport, 12-14 on board, 14-17 avoid light, 17-01 sleep (beats the flight marker, runs past landing).
        style.progressSegments.map { it.length } shouldContainExactly listOf(180, 120, 180, 480)
        style.progressSegments[2].color shouldBe AvoidLight.style.color
        style.progressPoints.map { it.position } shouldContainExactly listOf(3 * 60, 14 * 60)
        style.progress shouldBe 5 * 60 + 30
        n.shortCriticalText.toString() shouldBe "18:00"
        n.actions.map { it.title.toString() } shouldContainExactly listOf("Done", "Can't do this", "Snooze 15 min")
        // Route from the trip, phase as an absolute time (the notification only re-renders at plan boundaries).
        n.extras.getCharSequence(Notification.EXTRA_SUB_TEXT).toString() shouldBe
            "LHR → HND · Lands Sun 00:00 · Body clock in sync"
    }

    @Test
    fun `a redacted live update keeps its progress but drops the route`() = runTest {
        settings.current.value = AppSettings(hideLockScreenDetails = true)
        plans.current.value = planOf(flight, avoid, sleep)
        clock.instant = utc("2026-10-10T14:30")

        surface.render() shouldBe NowRendering.LiveUpdate

        val n = posted.shouldNotBeNull()
        n.visibility shouldBe Notification.VISIBILITY_PRIVATE
        val public = n.publicVersion.shouldNotBeNull()
        public.extras.getCharSequence(Notification.EXTRA_SUB_TEXT).toString() shouldBe "Lands Sun 00:00 · Body clock in sync"
        (Notification.Builder.recoverBuilder(context, public).style is Notification.ProgressStyle) shouldBe true
    }

    @Test
    fun `live update counts down in the last hour`() = runTest {
        plans.current.value = planOf(flight, avoid, sleep)
        clock.instant = utc("2026-10-10T16:30")

        surface.render()

        val n = posted.shouldNotBeNull()
        n.shortCriticalText.shouldBeNull()
        n.extras.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER) shouldBe true
        n.extras.getBoolean(Notification.EXTRA_CHRONOMETER_COUNT_DOWN) shouldBe true
        n.`when` shouldBe utc("2026-10-10T17:00").toEpochMilli()
    }

    @Test
    fun `without promotion permission the progress notification is posted unpromoted`() = runTest {
        capabilities.promoted = false
        plans.current.value = planOf(flight, avoid, sleep)
        clock.instant = utc("2026-10-10T14:30")

        surface.render() shouldBe NowRendering.LiveUpdate

        val n = posted.shouldNotBeNull()
        NotificationCompat.isRequestPromotedOngoing(n) shouldBe false
        (Notification.Builder.recoverBuilder(context, n).style is Notification.ProgressStyle) shouldBe true
    }

    @Test
    fun `on board with only the flight marker active, the standard Now notification is used`() = runTest {
        plans.current.value = planOf(flight, avoid, sleep)
        clock.instant = utc("2026-10-10T12:30")

        surface.render() shouldBe NowRendering.Ongoing

        val n = posted.shouldNotBeNull()
        n.channelId shouldBe OpusChannel.Now.id
        n.extras.getString(Notification.EXTRA_TITLE) shouldBe "In flight · BA7"
        n.smallIcon.resId shouldBe R.drawable.ic_notif_flight
        (n.actions ?: emptyArray()).toList().shouldBeEmpty()
    }

    @Test
    fun `every advice type has its own monochrome icon resource`() {
        dev.sebastiano.clockblocker.opus.core.model.AdviceType.entries.forEach { type ->
            context.getDrawable(type.style.icon).shouldNotBeNull()
        }
        context.getDrawable(R.drawable.ic_notif_clock).shouldNotBeNull()
    }

    @Test
    fun `melatonin moments use the supplements channel`() {
        Melatonin.style.channel shouldBe OpusChannel.SupplementsAndCaffeine
        SeeBrightLight.style.channel shouldBe OpusChannel.Light
        Sleep.style.channel shouldBe OpusChannel.Sleep
    }
}
