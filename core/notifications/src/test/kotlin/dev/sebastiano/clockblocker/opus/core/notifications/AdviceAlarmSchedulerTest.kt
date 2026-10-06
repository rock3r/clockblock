package dev.sebastiano.clockblocker.opus.core.notifications

import android.app.AlarmManager
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import dev.sebastiano.clockblocker.opus.core.model.AdviceType.AvoidLight
import dev.sebastiano.clockblocker.opus.core.model.AdviceType.Flight
import dev.sebastiano.clockblocker.opus.core.model.AdviceType.Melatonin
import dev.sebastiano.clockblocker.opus.core.model.AdviceType.SeeBrightLight
import dev.sebastiano.clockblocker.opus.core.model.AdviceType.Sleep
import dev.sebastiano.clockblocker.opus.core.model.AppSettings
import dev.sebastiano.clockblocker.opus.core.notifications.receiver.AdviceAlarmReceiver
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import dev.sebastiano.clockblocker.opus.core.model.PhasePoint
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowAlarmManager
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36])
class AdviceAlarmSchedulerTest {

    private val context: Application = ApplicationProvider.getApplicationContext()
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    // Saturday 2026-10-10, user in London (BST, UTC+1).
    private val light = advice(SeeBrightLight, "2026-10-10T08:00", "2026-10-10T10:00")
    private val avoid = advice(AvoidLight, "2026-10-10T14:00", "2026-10-10T17:00")
    private val sleep = advice(Sleep, "2026-10-10T22:00", "2026-10-11T06:00")
    private val plan = planOf(light, avoid, sleep)

    private val plans = FakePlanRepository(plan)
    private val settings = FakeSettingsRepository(AppSettings(reminderLeadMinutes = 15))
    private val logs = FakeAdviceLogRepository()
    private val clock = FakeClock(utc("2026-10-10T07:00"))
    private val capabilities = FakeCapabilities()
    private val snooze = SnoozeStore(context)
    private val factory = NotificationFactory(context, capabilities, clock)
    private val reminders = ReminderNotifier(context, factory, capabilities)
    private val widget = RecordingSurface()
    private val nowSurface = NowNotificationSurface(context, plans, settings, logs, snooze, factory, capabilities, clock, FakeTripRepository())

    private fun scheduler(vararg extra: dev.sebastiano.clockblocker.opus.core.data.PlanSurface) = AdviceAlarmScheduler(
        context, plans, settings, setOf(nowSurface, widget, *extra), reminders, snooze, capabilities, clock,
    )

    private val scheduled: List<ShadowAlarmManager.ScheduledAlarm>
        get() = shadowOf(alarmManager).scheduledAlarms.sortedBy { it.triggerAtMs }

    private val reminder: Notification? get() = shadowOf(notificationManager).getNotification(NotificationIds.REMINDER)
    private val now: Notification? get() = shadowOf(notificationManager).getNotification(NotificationIds.NOW)

    private fun ScheduledAlarm(at: String) = utc(at).toEpochMilli()

    @Before
    fun setUp() {
        snooze.clear()
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
    }

    @Test
    fun `arms the next transitions as exact, idle-allowed alarms`() = runTest {
        val result = scheduler().resync()

        result.exact shouldBe true
        scheduled.map { it.triggerAtMs } shouldContainExactly listOf(
            "2026-10-10T07:45", "2026-10-10T08:00", "2026-10-10T10:00",
            "2026-10-10T13:45", "2026-10-10T14:00", "2026-10-10T17:00",
            "2026-10-10T21:45", "2026-10-10T22:00",
        ).map(::ScheduledAlarm)
        scheduled.all { it.isAllowWhileIdle && it.type == AlarmManager.RTC_WAKEUP } shouldBe true
        scheduled.all { it.windowLengthMs == ShadowAlarmManager.WINDOW_EXACT } shouldBe true
    }

    @Test
    fun `alarm intents target the alarm receiver and carry their instant`() = runTest {
        scheduler().resync()

        val first = scheduled.first()
        val intent = shadowOf(first.operation).savedIntent
        intent.component!!.className shouldBe AdviceAlarmReceiver::class.java.name
        intent.action shouldBe NotificationIntents.alarmAction(context)
        intent.getLongExtra(NotificationIntents.EXTRA_AT, 0) shouldBe first.triggerAtMs
        shadowOf(first.operation).isImmutable shouldBe true
    }

    @Test
    fun `falls back to a 10 minute window without exact alarm permission`() = runTest {
        capabilities.exact = false
        ShadowAlarmManager.setCanScheduleExactAlarms(false)

        val result = scheduler().resync()

        result.exact shouldBe false
        scheduled shouldHaveSize AdviceAlarmScheduler.PLAN_ALARMS
        scheduled.all { it.windowLengthMs == Duration.ofMinutes(10).toMillis() } shouldBe true
    }

    @Test
    fun `rescheduling with fewer transitions cancels stale alarms`() = runTest {
        val scheduler = scheduler()
        scheduler.resync()
        scheduled shouldHaveSize 8

        plans.current.value = planOf(avoid)
        scheduler.resync()

        scheduled.map { it.triggerAtMs } shouldContainExactly
            listOf("2026-10-10T13:45", "2026-10-10T14:00", "2026-10-10T17:00").map(::ScheduledAlarm)
    }

    @Test
    fun `no plan, no alarms`() = runTest {
        val scheduler = scheduler()
        scheduler.resync()
        plans.current.value = null

        scheduler.resync().instants shouldHaveSize 0
        scheduled shouldHaveSize 0
    }

    @Test
    fun `start follows plan and settings changes and refreshes every surface`() = runTest(UnconfinedTestDispatcher()) {
        val scheduler = scheduler()
        val job = scheduler.start(backgroundScope)

        scheduled shouldHaveSize 8
        widget.refreshes shouldBe 1
        scheduler.start(backgroundScope) shouldBe job // idempotent

        plans.current.value = planOf(sleep)
        runCurrent()
        scheduled.map { it.triggerAtMs } shouldContainExactly
            listOf("2026-10-10T21:45", "2026-10-10T22:00", "2026-10-11T06:00").map(::ScheduledAlarm)
        widget.refreshes shouldBe 2

        settings.current.value = AppSettings(reminderLeadMinutes = 30)
        runCurrent()
        scheduled.first().triggerAtMs shouldBe ScheduledAlarm("2026-10-10T21:30")
        widget.refreshes shouldBe 3
    }

    @Test
    fun `turning on lock-screen privacy withdraws a reminder already on screen`() = runTest(UnconfinedTestDispatcher()) {
        val scheduler = scheduler()
        scheduler.start(backgroundScope)
        clock.instant = utc("2026-10-10T13:45")
        scheduler.onAlarm(utc("2026-10-10T13:45"))
        reminder.shouldNotBeNull()

        settings.current.value = settings.current.value.copy(hideLockScreenDetails = true)
        runCurrent()

        reminder.shouldBeNull()
    }

    @Test
    fun `privacy turned on while an alarm is handled still redacts the reminder`() = runTest {
        // The alarm reads settings first with privacy off; the user turns it on before the reminder is posted.
        val stored = settings.current
        val racing = object : dev.sebastiano.clockblocker.opus.core.data.SettingsRepository {
            var reads = 0
            override val settings: kotlinx.coroutines.flow.Flow<AppSettings> = kotlinx.coroutines.flow.flow {
                val current = stored.value
                emit(if (reads++ == 0) current else current.copy(hideLockScreenDetails = true))
            }
            override suspend fun update(transform: (AppSettings) -> AppSettings) = Unit
        }
        val scheduler = AdviceAlarmScheduler(
            context, plans, racing, setOf(widget), reminders, snooze, capabilities, clock,
        )
        clock.instant = utc("2026-10-10T13:45")

        scheduler.onAlarm(utc("2026-10-10T13:45"))

        val n = reminder.shouldNotBeNull()
        n.visibility shouldBe Notification.VISIBILITY_PRIVATE
        n.publicVersion.shouldNotBeNull()
    }

    @Test
    fun `withdrawing for privacy keeps a reminder that was already posted redacted`() = runTest {
        // An alarm racing the flip can win the lock, re-read privacy on and post redacted before the collector runs.
        clock.instant = utc("2026-10-10T13:45")
        settings.current.value = settings.current.value.copy(hideLockScreenDetails = true)
        scheduler().onAlarm(utc("2026-10-10T13:45"))
        reminder.shouldNotBeNull().publicVersion.shouldNotBeNull()

        reminders.cancelUnredacted()

        reminder.shouldNotBeNull()
    }

    @Test
    fun `turning lock-screen privacy off leaves the reminder alone`() = runTest(UnconfinedTestDispatcher()) {
        settings.current.value = settings.current.value.copy(hideLockScreenDetails = true)
        val scheduler = scheduler()
        scheduler.start(backgroundScope)
        clock.instant = utc("2026-10-10T13:45")
        scheduler.onAlarm(utc("2026-10-10T13:45"))

        settings.current.value = settings.current.value.copy(hideLockScreenDetails = false)
        runCurrent()

        reminder.shouldNotBeNull()
    }

    @Test
    fun `a refresh is armed for the next change of the body-clock header`() = runTest {
        // Body drifts from London time (0 h) to 1 h ahead over 24 h: the header leaves "in sync" at +30 min,
        // i.e. 12 h in, at 19:00 UTC, before any plan transition after 17:00 would refresh it.
        plans.current.value = plan.copy(
            phase = listOf(
                PhasePoint(utc("2026-10-10T07:00"), 60, utc("2026-10-10T07:00")),
                PhasePoint(utc("2026-10-11T07:00"), 120, utc("2026-10-11T07:00")),
            ),
        )

        val alarms = scheduler().resync().instants

        alarms shouldContain utc("2026-10-10T19:00")
    }

    @Test
    fun `alarm at a lead time posts one reminder, refreshes surfaces and re-arms`() = runTest {
        val scheduler = scheduler()
        clock.instant = utc("2026-10-10T13:45")

        scheduler.onAlarm(utc("2026-10-10T13:45"))

        val n = reminder.shouldNotBeNull()
        n.extras.getString(Notification.EXTRA_TITLE) shouldBe "Avoid light at 15:00"
        n.extras.getCharSequence(Notification.EXTRA_TEXT).toString() shouldBe "15:00–18:00"
        n.channelId shouldBe OpusChannel.Light.id
        n.actions.map { it.title.toString() } shouldContainExactly listOf("Can't do this", "Snooze 15 min")
        n.timeoutAfter shouldBe Duration.ofMinutes(45).toMillis() // until 30 min after the window starts
        widget.refreshes shouldBe 1
        scheduled.first().triggerAtMs shouldBe ScheduledAlarm("2026-10-10T14:00")
    }

    @Test
    fun `silent boundaries refresh without alerting`() = runTest {
        val scheduler = scheduler()
        clock.instant = utc("2026-10-10T14:00")

        scheduler.onAlarm(utc("2026-10-10T14:00"))

        reminder.shouldBeNull()
        now!!.extras.getString(Notification.EXTRA_TITLE) shouldBe "Avoid light"
        widget.refreshes shouldBe 1
    }

    @Test
    fun `an alarm whose transition vanished after re-planning says nothing`() = runTest {
        val scheduler = scheduler()
        plans.current.value = planOf(light, advice(AvoidLight, "2026-10-10T15:00", "2026-10-10T18:00"), sleep)
        clock.instant = utc("2026-10-10T13:45")

        scheduler.onAlarm(utc("2026-10-10T13:45"))

        reminder.shouldBeNull()
        widget.refreshes shouldBe 1
    }

    @Test
    fun `very late alarms skip reminders that are no longer true`() = runTest {
        clock.instant = utc("2026-10-10T17:30") // phone was off; Avoid light is over

        scheduler().onAlarm(utc("2026-10-10T13:45"))

        reminder.shouldBeNull()
    }

    @Test
    fun `reminders disabled - surfaces refresh, nothing alerts`() = runTest {
        settings.current.value = AppSettings(remindersEnabled = false)
        clock.instant = utc("2026-10-10T13:45")

        scheduler().onAlarm(utc("2026-10-10T13:45"))

        reminder.shouldBeNull()
        now.shouldBeNull()
        widget.refreshes shouldBe 1
    }

    @Test
    fun `wake-up alarm says what to do now`() = runTest {
        plans.current.value = planOf(sleep, advice(SeeBrightLight, "2026-10-11T06:00", "2026-10-11T08:00"))
        clock.instant = utc("2026-10-11T06:00")

        scheduler().onAlarm(utc("2026-10-11T06:00"))

        val n = reminder.shouldNotBeNull()
        n.extras.getString(Notification.EXTRA_TITLE) shouldBe "Sleep window over"
        n.extras.getCharSequence(Notification.EXTRA_TEXT).toString() shouldBe "Now: See bright light · until 09:00"
        n.channelId shouldBe OpusChannel.Sleep.id
    }

    @Test
    fun `melatonin moment alerts on the supplements channel with Done`() = runTest {
        val melatonin = advice(Melatonin, "2026-10-10T19:00", detail = "0.5 mg")
        plans.current.value = planOf(avoid, melatonin)
        clock.instant = utc("2026-10-10T19:00")

        scheduler().onAlarm(utc("2026-10-10T19:00"))

        val n = reminder.shouldNotBeNull()
        n.extras.getString(Notification.EXTRA_TITLE) shouldBe "Melatonin now"
        n.channelId shouldBe OpusChannel.SupplementsAndCaffeine.id
        n.actions.map { it.title.toString() } shouldContainExactly listOf("Done", "Snooze 15 min")
    }

    @Test
    fun `snooze hides everything, arms an alarm and brings the advice back`() = runTest {
        val scheduler = scheduler()
        clock.instant = utc("2026-10-10T14:05")
        scheduler.resync()
        now.shouldNotBeNull()

        scheduler.snooze(avoid.id)

        now.shouldBeNull()
        reminder.shouldBeNull()
        scheduled.map { it.triggerAtMs } shouldContainExactly listOf(ScheduledAlarm("2026-10-10T14:20")) +
            listOf("2026-10-10T17:00", "2026-10-10T21:45", "2026-10-10T22:00", "2026-10-11T06:00").map(::ScheduledAlarm)

        clock.instant = utc("2026-10-10T14:20")
        scheduler.onAlarm(utc("2026-10-10T14:20"))

        reminder!!.extras.getString(Notification.EXTRA_TITLE) shouldBe "Reminder: Avoid light"
        now.shouldNotBeNull()
        snooze.current().shouldBeNull()
    }

    @Test
    fun `travel day keeps a progress tick armed while the Live Update shows`() = runTest {
        plans.current.value = planOf(advice(Flight, "2026-10-10T12:00", "2026-10-10T23:00"), avoid)
        clock.instant = utc("2026-10-10T14:07:30Z")

        val result = scheduler().resync()

        result.instants.first() shouldBe utc("2026-10-10T14:22")
        now!!.channelId shouldBe OpusChannel.TravelLive.id
    }

    @Test
    fun `one failing surface does not stop the others`() = runTest {
        scheduler(FailingSurface()).refreshSurfaces()

        widget.refreshes shouldBe 1
    }

    @Test
    fun `the test reminder can go through the alarm path`() = runTest {
        val scheduler = scheduler()
        val permissions = AndroidNotificationPermissions(context, capabilities, reminders, scheduler)

        permissions.sendTestReminder(Duration.ofSeconds(10)) shouldBe true

        val alarm = scheduled.single()
        alarm.triggerAtMs shouldBe clock.instant.plusSeconds(10).toEpochMilli()
        val intent = shadowOf(alarm.operation).savedIntent
        intent.action shouldBe NotificationIntents.testAction(context)

        AdviceAlarmReceiver(scheduler, reminders, NotificationWorkScope(this)).onReceive(context, intent)
        testScheduler.advanceUntilIdle()

        shadowOf(notificationManager).getNotification(NotificationIds.TEST)!!
            .extras.getString(Notification.EXTRA_TITLE) shouldBe "Test reminder"
    }

    @Test
    fun `alarm receiver forwards fired alarms to the scheduler`() = runTest {
        val scheduler = scheduler()
        clock.instant = utc("2026-10-10T13:45")
        val intent = NotificationIntents.alarmIntent(context, utc("2026-10-10T13:45"))

        AdviceAlarmReceiver(scheduler, reminders, NotificationWorkScope(this)).onReceive(context, intent)
        testScheduler.advanceUntilIdle()

        reminder.shouldNotBeNull()
        widget.refreshes shouldBe 1
    }

    @Test
    fun `alarm receiver ignores foreign actions`() = runTest {
        AdviceAlarmReceiver(scheduler(), reminders, NotificationWorkScope(this))
            .onReceive(context, Intent("com.example.SPOOF"))
        testScheduler.advanceUntilIdle()

        widget.refreshes shouldBe 0
    }
}
