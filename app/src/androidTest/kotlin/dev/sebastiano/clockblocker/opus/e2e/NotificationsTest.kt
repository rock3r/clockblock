package dev.sebastiano.clockblocker.opus.e2e

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import dev.sebastiano.clockblocker.opus.core.notifications.R as NotificationsR
import dev.sebastiano.clockblocker.opus.feature.onboarding.OnboardingTags
import dev.sebastiano.clockblocker.opus.feature.plan.PlanTags
import dev.sebastiano.clockblocker.opus.feature.settings.SettingsTags
import dev.sebastiano.clockblocker.opus.shell.ShellTestTags
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
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
 * granted, i.e. a fresh install — revoking it would kill the test process), then a real notification in the shade.
 * No other test class grants the permission.
 */
@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class NotificationsTest : OpusE2eTest() {

    @Test
    fun a_firstRunAllowAndFinishGrantsNotificationsThroughSystemDialog() {
        assumeFalse("POST_NOTIFICATIONS is already granted (not a fresh install): the system dialog can't be shown", notificationsGranted)
        launch()
        val choices = walkOnboardingToReminders()

        awaitTag(OnboardingTags.Finish).performClick()
        val allowButton = By.res(Pattern.compile(".*:id/permission_allow_button"))
        assertNotNull("system notification permission dialog", device.wait(Until.findObject(allowButton), LongTimeoutMillis))
        // On a cold CI emulator a tap that lands while the dialog is still settling can be dropped, leaving the
        // dialog up. Tap until it's really gone, then wait for our window before looking for Compose nodes.
        var taps = 0
        while (device.hasObject(allowButton) && taps++ < MaxAllowTaps) {
            device.waitForIdle()
            device.findObject(allowButton)?.click()
            device.wait(Until.gone(allowButton), DefaultTimeoutMillis)
        }
        assertTrue("permission dialog still showing after $taps taps", device.wait(Until.gone(allowButton), DefaultTimeoutMillis))
        assertTrue(
            "our app back in front (foreground: ${device.currentPackageName})",
            device.wait(Until.hasObject(By.pkg(context.packageName).depth(0)), LongTimeoutMillis),
        )

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
            // plan too). Expand the bundle first, as a user would, and tap the reminder itself.
            val bundle = device.findObject(
                By.res(Pattern.compile(".*:id/expandableNotificationRow"))
                    .hasDescendant(By.text(title))
                    .hasDescendant(By.res(Pattern.compile(".*:id/expand_button_number"))),
            )
            bundle?.findObject(By.res(Pattern.compile(".*:id/expand_button")).desc(Pattern.compile("(?i)expand")))?.let {
                it.click()
                device.waitForIdle()
            }

            device.wait(Until.findObject(By.text(title)), DefaultTimeoutMillis).click()

            // The tap opens the current plan (opusclockblock://plan/current → Now) in the running app.
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
}

private const val MaxAllowTaps = 3
