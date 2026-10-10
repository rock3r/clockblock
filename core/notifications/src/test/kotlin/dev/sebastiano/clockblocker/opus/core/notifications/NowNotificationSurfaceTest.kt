package dev.sebastiano.clockblocker.opus.core.notifications

import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.test.core.app.ApplicationProvider
import dev.sebastiano.clockblocker.opus.core.model.AdviceOutcome
import dev.sebastiano.clockblocker.opus.core.model.AdviceType.AvoidCaffeine
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
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldNotContain
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
import java.util.TimeZone

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [37])
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
        context, plans, settings, logs, snooze, NotificationFactory(context, capabilities), capabilities, clock, trips,
    )

    private val posted: Notification? get() = shadowOf(manager).getNotification(NotificationIds.NOW)

    @Before
    fun setUp() = snooze.clear()

    @Test
    fun `our notifications bundle under our own summary, which opens the current plan`() = runTest {
        val reminders = ReminderNotifier(context, NotificationFactory(context, capabilities), capabilities)
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
        val reminders = ReminderNotifier(context, NotificationFactory(context, capabilities), capabilities)
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
    fun `ongoing Now notification shows label and until, with the other zone as a tail`() = runTest {
        surface.render() shouldBe NowRendering.Ongoing

        val n = posted.shouldNotBeNull()
        n.extras.getString(Notification.EXTRA_TITLE) shouldBe "Avoid light"
        n.extras.getCharSequence(Notification.EXTRA_TEXT).toString() shouldBe "until 18:00 · 02:00 Tokyo"
        n.channelId shouldBe ClockblockChannel.Now.id
        n.category shouldBe Notification.CATEGORY_REMINDER
        (n.flags and Notification.FLAG_ONGOING_EVENT) shouldBe Notification.FLAG_ONGOING_EVENT
        (n.flags and Notification.FLAG_ONLY_ALERT_ONCE) shouldBe Notification.FLAG_ONLY_ALERT_ONCE
        // setSilent(true): the child of a group only alerts via its (non-existent) summary, i.e. never.
        n.groupAlertBehavior shouldBe Notification.GROUP_ALERT_SUMMARY
        n.smallIcon.resId shouldBe R.drawable.ic_notif_app
        n.visibility shouldBe Notification.VISIBILITY_PUBLIC
    }

    @Test
    fun `the Now notification is a decorated custom view with chip, until, progress and next`() = runTest {
        surface.render() shouldBe NowRendering.Ongoing

        val n = posted.shouldNotBeNull()
        n.extras.getString(Notification.EXTRA_TEMPLATE) shouldBe Notification.DecoratedCustomViewStyle::class.java.name
        val collapsed = n.contentView.shouldNotBeNull()
        collapsed.texts(context) shouldContainExactly listOf("Avoid light", "until 18:00")
        val expanded = n.bigContentView.shouldNotBeNull().texts(context)
        expanded shouldContain "until 18:00 · 02:00 Tokyo"
        expanded shouldContain "Next: Sleep at 18:00 · 02:00 Tokyo"
        // 15:00 is a third of the way through 14:00–17:00.
        collapsed.inflate(context).findViewById<android.widget.ProgressBar>(R.id.now_progress).progress shouldBe 333
    }

    /** Issue #43: "until" is the headline's own end; an overlapping block is listed on its own line. */
    @Test
    fun `an overlapping block shows as Also now with its own end, never as the headline's until`() = runTest {
        val caffeine = advice(AvoidCaffeine, "2026-10-10T13:00", "2026-10-10T19:00")
        plans.current.value = planOf(avoid, caffeine, sleep)

        surface.render()

        val n = posted.shouldNotBeNull()
        n.extras.getCharSequence(Notification.EXTRA_TEXT).toString() shouldBe "until 18:00 · 02:00 Tokyo"
        val expanded = n.bigContentView.shouldNotBeNull().texts(context)
        expanded shouldContain "Also now: Avoid caffeine until 20:00 · 04:00 Tokyo"
        expanded shouldNotContain "until 20:00"
        // It may wrap only after the dot: never before it, nor inside either time.
        n.bigContentView.inflate(context).findViewById<android.widget.TextView>(R.id.now_also).text.toString() shouldBe
            "Also now: Avoid caffeine until 20:00\u00A0· 04:00\u00A0Tokyo"
    }

    @Test
    fun `a gap says what is next, without a bar`() = runTest {
        clock.instant = utc("2026-10-10T13:50")

        surface.render() shouldBe NowRendering.Ongoing

        val n = posted.shouldNotBeNull()
        n.extras.getString(Notification.EXTRA_TITLE) shouldBe "Nothing right now"
        val views = n.contentView.shouldNotBeNull().inflate(context)
        views.findViewById<android.view.View>(R.id.now_progress).visibility shouldBe android.view.View.GONE
        n.contentView.texts(context) shouldContain "Next: Avoid light at 15:00"
        n.bigContentView.texts(context) shouldContain "Next: Avoid light at 15:00 · 23:00 Tokyo"
    }

    @Test
    fun `the bar is kept current by a non-wakeup tick that stops when the notification goes`() = runTest {
        val alarms = shadowOf(context.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager)

        surface.render() shouldBe NowRendering.Ongoing

        val tick = alarms.scheduledAlarms.single()
        tick.type shouldBe android.app.AlarmManager.RTC
        tick.triggerAtMs shouldBe clock.instant.plus(NowNotificationSurface.PROGRESS_TICK).toEpochMilli()
        shadowOf(tick.operation).savedIntent.action shouldBe NotificationIntents.progressTickAction(context)

        settings.current.value = AppSettings(remindersEnabled = false)
        surface.render() shouldBe NowRendering.Hidden
        alarms.scheduledAlarms.shouldBeEmpty()
    }

    @Test
    fun `no progress tick while the user has blocked the Now channel`() = runTest {
        val alarms = shadowOf(context.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager)
        surface.render() shouldBe NowRendering.Ongoing
        alarms.scheduledAlarms.single()

        // What a user switching the channel off in system settings leaves behind.
        val manager = context.getSystemService(android.app.NotificationManager::class.java)
        val blocked = manager.getNotificationChannel(ClockblockChannel.Now.id).apply { importance = android.app.NotificationManager.IMPORTANCE_NONE }
        manager.deleteNotificationChannel(ClockblockChannel.Now.id)
        manager.createNotificationChannel(blocked)
        surface.render()

        alarms.scheduledAlarms.shouldBeEmpty()
    }

    @Test
    fun `the travel-day Live Update has no progress tick of its own`() = runTest {
        val alarms = shadowOf(context.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager)
        surface.render() shouldBe NowRendering.Ongoing

        plans.current.value = planOf(flight, avoid, sleep)
        clock.instant = utc("2026-10-10T14:30")
        surface.render() shouldBe NowRendering.LiveUpdate

        alarms.scheduledAlarms.shouldBeEmpty()
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
        val n = NotificationFactory(context, capabilities).test()

        val content = shadowOf(n.contentIntent.shouldNotBeNull()).savedIntent
        content.data shouldBe Uri.parse(DeepLinks.CURRENT_PLAN)
        content.`package` shouldBe context.packageName
    }

    @Test
    fun `text follows the plan's zone after landing`() = runTest {
        plans.current.value = planOf(avoid, sleep, dayZone = "Asia/Tokyo")

        surface.render()

        posted!!.extras.getCharSequence(Notification.EXTRA_TEXT).toString() shouldBe "until 02:00 · 18:00 London"
    }

    @Test
    fun `logged outcome leaves a single Undo`() = runTest {
        logs.log("trip-1", avoid.id, AdviceOutcome.Done)

        surface.render()

        val n = posted.shouldNotBeNull()
        n.extras.getCharSequence(Notification.EXTRA_TEXT).toString() shouldBe "Done · until 18:00 · 02:00 Tokyo"
        n.actions.map { it.title.toString() } shouldContainExactly listOf("Undo")
        val undo = shadowOf(n.actions[0].actionIntent).savedIntent
        undo.action shouldBe NotificationIntents.adviceAction(context, AdviceAction.Undo)
        undo.getStringExtra(NotificationIntents.EXTRA_ADVICE_ID) shouldBe avoid.id
    }

    @Test
    fun `the header shows the body clock`() = runTest {
        plans.current.value = planOf(avoid, sleep, dayZone = "Asia/Tokyo")

        surface.render()

        posted.shouldNotBeNull().extras.getCharSequence(Notification.EXTRA_SUB_TEXT).toString() shouldBe "Body 8 h behind"
    }

    /**
     * Issue #36: the phone is in Rome while the plan says the traveller is still in Los Angeles (a pre-trip day, or a
     * demo trip). The plan screen and the widgets show Los Angeles time; the notifications must too.
     */
    @Test
    fun `times and the body clock follow the plan day's zone, not the device's`() = runTest {
        // 06:00Z–14:00Z is 23:00–07:00 in Los Angeles (PDT), 08:00–16:00 in Rome (CEST), 07:00–15:00 in London (BST).
        val night = advice(Sleep, "2026-10-10T06:00", "2026-10-10T14:00")
        val light = advice(SeeBrightLight, "2026-10-10T14:00", "2026-10-10T16:00")
        plans.current.value =
            planOf(night, light, origin = "America/Los_Angeles", destination = "Europe/London", dayZone = "America/Los_Angeles")
        clock.instant = utc("2026-10-10T13:15")

        withDeviceZone("Europe/Rome") { surface.render() }

        val n = posted.shouldNotBeNull()
        n.extras.getCharSequence(Notification.EXTRA_TEXT).toString() shouldBe "until 07:00 · 15:00 London"
        n.bigContentView.texts(context) shouldContain "Next: See bright light at 07:00 · 15:00 London"
        // No trajectory: the body is on home (Los Angeles) time, like the plan day.
        n.extras.getCharSequence(Notification.EXTRA_SUB_TEXT).toString() shouldBe "Body clock in sync"
    }

    @Test
    fun `reminders follow the plan day's zone, not the device's`() {
        val light = advice(SeeBrightLight, "2026-10-10T14:00", "2026-10-10T16:00")
        val plan = planOf(light, origin = "America/Los_Angeles", destination = "Europe/London", dayZone = "America/Los_Angeles")
        val spec = ReminderSpec(ReminderKind.Upcoming, light, expiresAt = light.end)

        val n = withDeviceZone("Europe/Rome") {
            NotificationFactory(context, capabilities).reminder(spec, plan, utc("2026-10-10T13:45"))
        }

        n.extras.getString(Notification.EXTRA_TITLE) shouldBe "See bright light at 07:00"
        n.extras.getCharSequence(Notification.EXTRA_TEXT).toString() shouldBe "07:00–09:00 · 15:00–17:00 London"
        n.extras.getCharSequence(Notification.EXTRA_BIG_TEXT).toString() shouldContain "15:00–17:00 London"
    }

    @Test
    fun `the travel-day Live Update follows the plan day's zone, not the device's`() = runTest {
        plans.current.value = planOf(flight, avoid, sleep, dayZone = "Europe/London")
        clock.instant = utc("2026-10-10T14:30")

        withDeviceZone("Europe/Rome") { surface.render() } shouldBe NowRendering.LiveUpdate

        val n = posted.shouldNotBeNull()
        n.shortCriticalText.toString() shouldBe "18:00"
        n.extras.getCharSequence(Notification.EXTRA_SUB_TEXT).toString() shouldBe
            "LHR → HND · Lands Sun 00:00 · Body clock in sync"
    }

    private inline fun <T> withDeviceZone(zone: String, block: () -> T): T {
        val previous = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone(zone))
        return try {
            block()
        } finally {
            TimeZone.setDefault(previous)
        }
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
        public.extras.getCharSequence(Notification.EXTRA_TEXT).toString() shouldBe "until Sun 00:00"
        public.extras.getCharSequence(Notification.EXTRA_BIG_TEXT).toString() shouldBe "until Sun 00:00\nNext: Sleep at 18:00"
        // A plain template: no custom views (they would carry the private text), the flight chip as large icon.
        public.contentView.shouldBeNull()
        public.bigContentView.shouldBeNull()
        public.getLargeIcon().shouldNotBeNull()
        (public.actions ?: emptyArray()).toList().shouldBeEmpty()
    }

    @Test
    fun `a redacted melatonin reminder hides its name, dose and pill chip`() {
        val melatonin = advice(Melatonin, "2026-10-10T20:00", detail = "0.5 mg")
        val spec = ReminderSpec(ReminderKind.Moment, melatonin, expiresAt = melatonin.start.plusSeconds(7200))

        val n = NotificationFactory(context, capabilities).reminder(spec, planOf(avoid, sleep, melatonin), utc("2026-10-10T20:00"), redact = true)

        n.visibility shouldBe Notification.VISIBILITY_PRIVATE
        n.extras.getString(Notification.EXTRA_TITLE) shouldBe "Melatonin now"
        n.smallIcon.resId shouldBe R.drawable.ic_notif_app
        val public = n.publicVersion.shouldNotBeNull()
        public.extras.getString(Notification.EXTRA_TITLE) shouldBe "Plan step now"
        public.extras.getCharSequence(Notification.EXTRA_TEXT).toString() shouldBe "Unlock to see details"
        // Done + Snooze would name melatonin on its own: the stand-in carries no actions.
        n.actions.map { it.title.toString() } shouldContainExactly listOf("Done", "Snooze 15 min")
        (public.actions ?: emptyArray()).toList().shouldBeEmpty()
    }

    @Test
    fun `channels are created with their groups and names`() = runTest {
        surface.render()

        val channels = shadowOf(manager).notificationChannels.associateBy { it.id }
        channels.keys shouldBe ClockblockChannel.entries.map { it.id }.toSet()
        channels.getValue(ClockblockChannel.Light.id).name shouldBe "Light"
        channels.getValue(ClockblockChannel.SupplementsAndCaffeine.id).name shouldBe "Supplements & caffeine"
        channels.getValue(ClockblockChannel.Now.id).sound.shouldBeNull()
        channels.getValue(ClockblockChannel.Now.id).group shouldBe ClockblockChannelGroup.Ongoing.id
        channels.getValue(ClockblockChannel.Sleep.id).group shouldBe ClockblockChannelGroup.Reminders.id
        (channels.getValue(ClockblockChannel.TravelLive.id).importance > NotificationManager.IMPORTANCE_MIN) shouldBe true
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
        n.channelId shouldBe ClockblockChannel.TravelLive.id
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
        n.channelId shouldBe ClockblockChannel.Now.id
        n.extras.getString(Notification.EXTRA_TITLE) shouldBe "In flight · BA7"
        n.smallIcon.resId shouldBe R.drawable.ic_notif_app
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
    fun `every notification shows the app mark in the status bar, whatever the advice`() = runTest {
        val factory = NotificationFactory(context, capabilities)
        val app = R.drawable.ic_notif_app
        context.getDrawable(app).shouldNotBeNull()

        // Now (ongoing) and the Live Update on travel day.
        surface.render() shouldBe NowRendering.Ongoing
        posted.shouldNotBeNull().smallIcon.resId shouldBe app
        plans.current.value = planOf(flight, avoid, sleep)
        clock.instant = utc("2026-10-10T14:30")
        surface.render() shouldBe NowRendering.LiveUpdate
        posted.shouldNotBeNull().smallIcon.resId shouldBe app

        // Reminders for every kind of advice, redacted or not, plus the test reminder and the group summary.
        val now = utc("2026-10-10T15:00")
        val melatonin = advice(Melatonin, "2026-10-10T20:00", detail = "0.5 mg")
        val plan = planOf(flight, avoid, sleep, melatonin)
        listOf(avoid, sleep, flight, melatonin).forEach { step ->
            val spec = ReminderSpec(ReminderKind.Upcoming, step, expiresAt = step.start.plusSeconds(3600))
            listOf(false, true).forEach { redact ->
                val n = factory.reminder(spec, plan, now, redact = redact)
                n.smallIcon.resId shouldBe app
                n.publicVersion?.smallIcon?.resId?.let { it shouldBe app }
            }
        }
        factory.test().smallIcon.resId shouldBe app
        factory.summary(timeoutMillis = null).smallIcon.resId shouldBe app
    }

    @Test
    fun `melatonin moments use the supplements channel`() {
        Melatonin.style.channel shouldBe ClockblockChannel.SupplementsAndCaffeine
        SeeBrightLight.style.channel shouldBe ClockblockChannel.Light
        Sleep.style.channel shouldBe ClockblockChannel.Sleep
    }
}
