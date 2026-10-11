package dev.sebastiano.clockblocker.opus.e2e

import android.app.Notification
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.performClick
import androidx.core.app.NotificationManagerCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import dev.sebastiano.clockblocker.opus.core.model.AdviceOutcome
import dev.sebastiano.clockblocker.opus.core.notifications.NotificationIds
import dev.sebastiano.clockblocker.opus.core.notifications.NowNotificationSurface
import dev.sebastiano.clockblocker.opus.core.notifications.NowRendering
import dev.sebastiano.clockblocker.opus.core.notifications.R as NotificationsR
import dev.sebastiano.clockblocker.opus.feature.onboarding.NotificationPreviewTags
import dev.sebastiano.clockblocker.opus.feature.onboarding.OnboardingTags
import dev.sebastiano.clockblocker.opus.feature.onboarding.RemindersTags
import dev.sebastiano.clockblocker.opus.feature.plan.PlanTags
import dev.sebastiano.clockblocker.opus.feature.settings.SettingsTags
import dev.sebastiano.clockblocker.opus.shell.ShellTestTags
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeFalse
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import java.util.regex.Pattern

/**
 * Notifications end to end, in order: first the system permission dialog (needs POST_NOTIFICATIONS not yet
 * granted, i.e. a fresh install — revoking it would kill the test process), then real notifications in the shade.
 * No other test class grants the permission.
 */
@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class NotificationsTest : ClockblockE2eTest() {

    @Test
    fun a_firstRunAllowFromTheCardGrantsNotificationsThroughSystemDialog() {
        assumeFalse("POST_NOTIFICATIONS is already granted (not a fresh install): the system dialog can't be shown", notificationsGranted)
        launch()
        val choices = walkOnboardingToReminders()

        // The step's only Allow is on the card, under the notification preview; the bottom bar just has Maybe later.
        awaitTag(NotificationPreviewTags.Card).assertExists()
        awaitTag(OnboardingTags.MaybeLater).assertIsDisplayed()
        assertFalse("no Finish before notifications are handled", exists(OnboardingTags.Finish))
        awaitTag(RemindersTags.AllowNotifications).scrollToIfScrollable().performClick()
        val allowButton = By.res(Pattern.compile(".*:id/permission_allow_button"))
        assertNotNull("system notification permission dialog", device.wait(Until.findObject(allowButton), LongTimeoutMillis))
        // On a cold CI emulator a tap that lands while the dialog is still settling can be dropped, leaving the
        // dialog up. Tap until it's really gone, then wait for our window before looking for Compose nodes.
        var taps = 0
        while (device.hasObject(allowButton) && taps++ < MaxAllowTaps) {
            device.waitForIdle()
            device.clickWhenFound(allowButton, DefaultTimeoutMillis)
            device.wait(Until.gone(allowButton), DefaultTimeoutMillis)
        }
        assertTrue("permission dialog still showing after $taps taps", device.wait(Until.gone(allowButton), DefaultTimeoutMillis))
        assertTrue(
            "our app back in front (foreground: ${device.currentPackageName})",
            device.wait(Until.hasObject(By.pkg(context.packageName).depth(0)), LongTimeoutMillis),
        )

        // Back on the step: notifications allowed, and Exact timing offered only where it can still be granted.
        awaitTag(RemindersTags.NotificationsAllowed, LongTimeoutMillis)
        val exactOffered = exists(RemindersTags.ExactAlarmRow)
        assertEquals("exact timing offered exactly when it isn't allowed yet", !exactAlarmsAllowed, exactOffered)
        awaitTag(OnboardingTags.Finish).performClick()

        awaitTag("route_trips", LongTimeoutMillis).assertIsDisplayed()
        assertTrue(notificationsGranted)
        choices.assertSaved(runBlocking { graph.profileRepository.profile.filterNotNull().first() })
    }

    @Test
    fun b_testReminderAppearsInTheShadeAndOpensThePlan() {
        grantNotifications()
        seedOnboarded()
        val active = graph.seedTripWithActiveAdvice()
        launch()
        awaitTag(ShellTestTags.NavSettings, LongTimeoutMillis).performClick()
        awaitTag(SettingsTags.TestReminder).scrollToIfScrollable().performClick()

        val title = context.getString(NotificationsR.string.reminder_test_title)
        val text = context.getString(NotificationsR.string.reminder_test_text)
        try {
            device.openNotification()
            val notification = device.wait(Until.findObject(By.text(title)), LongTimeoutMillis)
            assertNotNull("'$title' in the notification shade", notification)
            assertNotNull(device.wait(Until.findObject(By.text(text)), DefaultTimeoutMillis))

            // With the Now notification also showing, both sit in our group under a summary (which opens the current
            // plan too). Expand the bundle first, as a user would, and tap the reminder itself. The shade rebuilds
            // its rows as the group expands, so both taps re-find their target instead of holding on to a stale one.
            val bundle = By.res(Pattern.compile(".*:id/expandableNotificationRow"))
                .hasDescendant(By.text(title))
                .hasDescendant(By.res(Pattern.compile(".*:id/expand_button_number")))
            val expand = By.res(Pattern.compile(".*:id/expand_button")).desc(Pattern.compile("(?i)expand"))
            if (device.hasObject(bundle)) {
                device.clickWhenFound(BundleExpandTimeoutMillis) { findObject(bundle)?.findObject(expand) }
                device.waitForIdle()
            }

            assertTrue("tapped '$title' in the notification shade", device.clickWhenFound(By.text(title), DefaultTimeoutMillis))

            // The tap opens the current plan (clockblock://plan/current → Now) in the running app.
            assertNotNull(device.wait(Until.hasObject(By.pkg(context.packageName).depth(0)), LongTimeoutMillis))
            awaitTag("route_now", LongTimeoutMillis).assertIsDisplayed()
            awaitTag(PlanTags.NowCard, LongTimeoutMillis).assertIsDisplayed()
            awaitText(active.trip.destination.city)
            awaitTag(ShellTestTags.NavNow)
        } finally {
            AppDataReset.cancelNotifications(context)
            if (device.hasObject(By.pkg("com.android.systemui").res("com.android.systemui:id/notification_stack_scroller"))) {
                device.pressBack()
            }
        }
    }

    /**
     * The Now notification with Remote Compose content (#49) in the real shade: SystemUI keeps it (it would cancel one
     * it can't inflate, and the surface would fall back to the classic views), and the system Done action under it
     * logs the outcome and turns into Undo.
     */
    @Test
    fun c_nowNotificationKeepsItsRemoteComposeContentAndDoneWorks() {
        grantNotifications()
        seedOnboarded()
        val active = graph.seedTripWithActiveAdvice()
        launch()
        awaitTag(ShellTestTags.NavNow, LongTimeoutMillis)

        // Alone in the shade (an upcoming reminder would bundle it under our summary); render() syncs the group.
        NotificationManagerCompat.from(context).cancel(NotificationIds.REMINDER)
        val surface = graph.nowNotificationSurface
        assertEquals(NowRendering.RemoteOngoing, runBlocking { surface.render() })
        // SystemUI inflates it after notify returns; by now the surface's own check has run too.
        Thread.sleep(NowNotificationSurface.INFLATION_CHECK.toMillis() + InflationSlackMillis)
        val now = NotificationManagerCompat.from(context).activeNotifications.firstOrNull { it.id == NotificationIds.NOW }
        assertNotNull("the Now notification still in the shade", now)
        assertEquals(NowRendering.RemoteOngoing, runBlocking { surface.render() })

        val title = now!!.notification.extras.getString(Notification.EXTRA_TITLE).orEmpty()
        val done = context.getString(NotificationsR.string.action_done)
        try {
            device.openNotification()
            // The document is spoken as "<label>. until …": that finds our row. It is silent, so it may be collapsed.
            val row = By.res(Pattern.compile(".*:id/expandableNotificationRow"))
                .hasDescendant(By.desc(Pattern.compile(Pattern.quote(title) + "\\. .*")))
            assertTrue("'$title' in the notification shade", device.wait(Until.hasObject(row), LongTimeoutMillis))
            val action = By.res(Pattern.compile(".*:id/action0")).text(done)
            if (device.findObject(row)?.hasObject(action) != true) {
                device.clickWhenFound(DefaultTimeoutMillis) { findObject(row)?.findObject(By.res(Pattern.compile(".*:id/expand_button"))) }
                device.waitForIdle()
            }
            assertTrue("tapped Done on the Now notification", device.clickWhenFound(DefaultTimeoutMillis) { findObject(row)?.findObject(action) })

            val logged = runBlocking {
                withTimeoutOrNull(LongTimeoutMillis) {
                    graph.adviceLogRepository.logs(active.trip.id).first { logs -> logs.any { it.outcome == AdviceOutcome.Done } }
                }
            }
            assertNotNull("Done logged from the notification", logged)
            val undo = By.res(Pattern.compile(".*:id/action0")).text(context.getString(NotificationsR.string.action_undo))
            assertNotNull("Undo replaces the actions", device.wait(Until.findObject(undo), LongTimeoutMillis))
        } finally {
            AppDataReset.cancelNotifications(context)
            if (device.hasObject(By.pkg("com.android.systemui").res("com.android.systemui:id/notification_stack_scroller"))) {
                device.pressBack()
            }
        }
    }
}

private const val MaxAllowTaps = 3

/** How long past the surface's inflation check to look again. */
private const val InflationSlackMillis = 2_000L

/** The bundle may already be expanded (no "expand" button): don't spend long looking for one. */
private const val BundleExpandTimeoutMillis = 3_000L
