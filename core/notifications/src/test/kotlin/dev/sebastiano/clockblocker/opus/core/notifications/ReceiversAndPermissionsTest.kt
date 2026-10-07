package dev.sebastiano.clockblocker.opus.core.notifications

import android.app.AlarmManager
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import dev.sebastiano.clockblocker.opus.core.model.AdviceLog
import dev.sebastiano.clockblocker.opus.core.model.AdviceOutcome
import dev.sebastiano.clockblocker.opus.core.model.AdviceType.AvoidLight
import dev.sebastiano.clockblocker.opus.core.model.AdviceType.Sleep
import dev.sebastiano.clockblocker.opus.core.model.AppSettings
import dev.sebastiano.clockblocker.opus.core.notifications.receiver.AdviceActionReceiver
import dev.sebastiano.clockblocker.opus.core.notifications.receiver.ScheduleResetReceiver
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowAlarmManager

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [37])
class ReceiversAndPermissionsTest {

    private val context: Application = ApplicationProvider.getApplicationContext()
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    private val avoid = advice(AvoidLight, "2026-10-10T14:00", "2026-10-10T17:00")
    private val sleep = advice(Sleep, "2026-10-10T17:00", "2026-10-11T01:00")

    private val plans = FakePlanRepository(planOf(avoid, sleep))
    private val settings = FakeSettingsRepository(AppSettings())
    private val logs = FakeAdviceLogRepository()
    private val clock = FakeClock(utc("2026-10-10T15:00"))
    private val capabilities = FakeCapabilities()
    private val snooze = SnoozeStore(context)
    private val factory = NotificationFactory(context, capabilities)
    private val reminders = ReminderNotifier(context, factory, capabilities)
    private val nowSurface = NowNotificationSurface(context, plans, settings, logs, snooze, factory, capabilities, clock, FakeTripRepository())
    private val scheduler = AdviceAlarmScheduler(
        context, plans, settings, setOf(nowSurface), reminders, snooze, capabilities, clock,
    )
    private val permissions = AndroidNotificationPermissions(context, capabilities, reminders, scheduler)

    private val now: Notification? get() = shadowOf(notificationManager).getNotification(NotificationIds.NOW)

    @Before
    fun setUp() {
        snooze.clear()
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
    }

    private fun TestScope.actionReceiver() = AdviceActionReceiver(logs, scheduler, reminders, NotificationWorkScope(this))

    private fun TestScope.press(action: AdviceAction, adviceId: String = avoid.id) {
        actionReceiver().onReceive(context, NotificationIntents.actionIntent(context, action, "trip-1", adviceId))
        testScheduler.advanceUntilIdle()
    }

    @Test
    fun `Done logs the outcome and the Now notification re-renders from the same plan`() = runTest {
        nowSurface.render()

        press(AdviceAction.Done)

        logs.logged.value["trip-1"] shouldBe listOf(AdviceLog(avoid.id, AdviceOutcome.Done))
        now!!.extras.getCharSequence(Notification.EXTRA_TEXT).toString() shouldBe
            "Done · until 18:00 · then Sleep 18:00–02:00"
    }

    @Test
    fun `Can't do this logs CantDo`() = runTest {
        press(AdviceAction.CantDo)

        logs.logged.value["trip-1"] shouldBe listOf(AdviceLog(avoid.id, AdviceOutcome.CantDo))
        now!!.extras.getCharSequence(Notification.EXTRA_TEXT).toString() shouldBe
            "Noted, skip it · until 18:00 · then Sleep 18:00–02:00"
    }

    @Test
    fun `Undo forgets the logged outcome and brings the actions back`() = runTest {
        val widget = RecordingSurface()
        val scheduler = AdviceAlarmScheduler(
            context, plans, settings, setOf(nowSurface, widget), reminders, snooze, capabilities, clock,
        )
        fun pressWith(action: AdviceAction) {
            AdviceActionReceiver(logs, scheduler, reminders, NotificationWorkScope(this))
                .onReceive(context, NotificationIntents.actionIntent(context, action, "trip-1", avoid.id))
            testScheduler.advanceUntilIdle()
        }
        pressWith(AdviceAction.Done)
        now!!.actions.map { it.title.toString() } shouldContainExactly listOf("Undo")
        val refreshesAfterDone = widget.refreshes

        pressWith(AdviceAction.Undo)

        logs.logged.value["trip-1"].orEmpty().shouldBeEmpty()
        now!!.extras.getCharSequence(Notification.EXTRA_TEXT).toString() shouldBe "until 18:00 · then Sleep 18:00–02:00"
        now!!.actions.map { it.title.toString() } shouldContainExactly listOf("Done", "Can't do this", "Snooze 15 min")
        // Every surface (widgets too) re-renders from the reverted log.
        widget.refreshes shouldBe refreshesAfterDone + 1
    }

    @Test
    fun `Snooze hides the Now notification and arms the snooze alarm`() = runTest {
        nowSurface.render()

        press(AdviceAction.Snooze)

        now.shouldBeNull()
        snooze.current()!!.adviceId shouldBe avoid.id
        shadowOf(alarmManager).scheduledAlarms.map { it.triggerAtMs }.min() shouldBe
            utc("2026-10-10T15:15").toEpochMilli()
    }

    @Test
    fun `action receiver ignores intents without ids or with foreign actions`() = runTest {
        actionReceiver().onReceive(context, Intent(NotificationIntents.adviceAction(context, AdviceAction.Done)))
        actionReceiver().onReceive(context, Intent("com.example.DONE").putExtra(NotificationIntents.EXTRA_TRIP_ID, "trip-1"))
        testScheduler.advanceUntilIdle()

        logs.logged.value shouldBe emptyMap()
    }

    @Test
    fun `system events re-arm alarms and refresh surfaces`() = runTest {
        ScheduleResetReceiver.HANDLED_ACTIONS shouldBe setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_TIMEZONE_CHANGED,
            "android.intent.action.TIME_SET",
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_LOCALE_CHANGED,
            "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED",
        )
        val receiver = ScheduleResetReceiver(scheduler, NotificationWorkScope(this))

        receiver.onReceive(context, Intent(Intent.ACTION_TIMEZONE_CHANGED))
        testScheduler.advanceUntilIdle()

        val lead = java.time.Duration.ofMinutes(AppSettings().reminderLeadMinutes.toLong())
        shadowOf(alarmManager).scheduledAlarms.map { it.triggerAtMs }.sorted() shouldBe listOf(
            utc("2026-10-10T17:00").minus(lead), // Sleep coming up
            utc("2026-10-10T17:00"), // Avoid light ends, sleep starts
            utc("2026-10-11T01:00"), // Wake up
        ).map { it.toEpochMilli() }
        now.shouldNotBeNull()
    }

    @Test
    fun `reset receiver ignores unrelated broadcasts`() = runTest {
        ScheduleResetReceiver(scheduler, NotificationWorkScope(this)).onReceive(context, Intent("com.example.NOPE"))
        testScheduler.advanceUntilIdle()

        shadowOf(alarmManager).scheduledAlarms shouldHaveSize 0
        now.shouldBeNull()
    }

    @Test
    fun `permission state mirrors the platform`() {
        capabilities.exact = false
        capabilities.promoted = false

        permissions.state() shouldBe NotificationPermissionState(
            notificationsGranted = true,
            exactAlarmsAllowed = false,
            promotedAllowed = false,
            batteryOptimizationIgnored = false,
        )
        permissions.state().isReliable shouldBe false
        capabilities.exact = true
        permissions.state().isReliable shouldBe true
    }

    @Test
    fun `settings intents point at this app's screens`() {
        permissions.runtimePermission shouldBe android.Manifest.permission.POST_NOTIFICATIONS
        permissions.notificationSettingsIntent().let {
            it.action shouldBe Settings.ACTION_APP_NOTIFICATION_SETTINGS
            it.getStringExtra(Settings.EXTRA_APP_PACKAGE) shouldBe context.packageName
        }
        permissions.exactAlarmSettingsIntent().let {
            it.action shouldBe Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM
            it.data.toString() shouldBe "package:${context.packageName}"
        }
        permissions.promotedSettingsIntent().action shouldBe Settings.ACTION_APP_NOTIFICATION_PROMOTION_SETTINGS
        permissions.batteryOptimizationSettingsIntent().action shouldBe Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS
        listOf(
            permissions.notificationSettingsIntent(),
            permissions.exactAlarmSettingsIntent(),
            permissions.promotedSettingsIntent(),
        ).all { it.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0 } shouldBe true
    }

    @Test
    fun `test reminder posts immediately, or reports blocked notifications`() {
        permissions.sendTestReminder() shouldBe true
        val n = shadowOf(notificationManager).getNotification(NotificationIds.TEST).shouldNotBeNull()
        n.extras.getString(Notification.EXTRA_TITLE) shouldBe "Test reminder"
        n.extras.getCharSequence(Notification.EXTRA_TEXT).toString() shouldBe "If you can read this, reminders work."
        n.channelId shouldBe ClockblockChannel.Light.id

        capabilities.notifications = false
        permissions.sendTestReminder() shouldBe false
    }

    @Test
    fun `android capabilities read the exact alarm permission`() {
        val android = AndroidPlatformCapabilities(context)

        ShadowAlarmManager.setCanScheduleExactAlarms(false)
        android.canScheduleExactAlarms() shouldBe false
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
        android.canScheduleExactAlarms() shouldBe true
        android.areNotificationsEnabled() shouldBe true
    }

    @Test
    fun `channels are named from resources and their ids are stable`() {
        NotificationChannels.ensureCreated(context)

        val expected = listOf("advice_light", "advice_sleep", "advice_supplements_caffeine", "travel_live", "now")
        ClockblockChannel.entries.map { it.id } shouldContainExactly expected
        shadowOf(notificationManager).notificationChannels.map { it.id } shouldContainExactlyInAnyOrder expected
        notificationManager.getNotificationChannelGroup(ClockblockChannelGroup.Reminders.id).name shouldBe "Reminders"
    }
}
