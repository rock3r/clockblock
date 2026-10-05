package dev.sebastiano.clockblocker.opus.e2e

import android.Manifest
import android.app.Instrumentation
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import dev.sebastiano.clockblocker.opus.AppGraph
import dev.sebastiano.clockblocker.opus.MainActivity
import dev.sebastiano.clockblocker.opus.OpusApplication
import dev.sebastiano.clockblocker.opus.core.data.demo.DemoData
import dev.sebastiano.clockblocker.opus.core.model.Trip
import dev.sebastiano.clockblocker.opus.core.model.UserProfile
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule

/**
 * Base for end-to-end tests on a real device: a Compose rule for in-app assertions, a [UiDevice] for system UI
 * (permission dialogs, the notification shade, the document picker, rotation) and helpers to seed state and
 * launch [MainActivity] the way the launcher, notifications and widgets do.
 *
 * **Every test starts from cleared app data.** `pm clear` can't be used: the instrumentation runs inside the app
 * process, so clearing the package kills the test runner, and the Test Orchestrator's `clearPackageData` needs
 * extra APKs on the device. Instead, before each test, every activity is finished and [AppDataReset] puts every
 * store back to its first-install state in-process, through the same singletons the app uses (so no in-memory
 * cache goes stale). Runtime permissions are the only state that survives between tests (revoking one kills the
 * process); tests that need POST_NOTIFICATIONS grant it themselves.
 *
 * Screens are found through the shell's route tags (`route_now`, `route_trips`, … see `ShellTestTags`) and the
 * feature modules' test-tag objects.
 */
abstract class OpusE2eTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    val instrumentation: Instrumentation = InstrumentationRegistry.getInstrumentation()
    val device: UiDevice = UiDevice.getInstance(instrumentation)
    val context: Context get() = instrumentation.targetContext
    val graph: AppGraph get() = (context.applicationContext as OpusApplication).graph

    @Before
    fun startFromClearedAppData() {
        device.wakeUp()
        val keyguard = context.getSystemService(KeyguardManager::class.java)
        check(!keyguard.isKeyguardLocked) { "The device is locked: unlock it (and keep it awake) before running e2e tests" }
        Activities.finishAll(instrumentation)
        AppDataReset.reset(context, graph)
    }

    @After
    fun closeApp() {
        Activities.finishAll(instrumentation)
        AppDataReset.cancelNotifications(context)
    }

    /** Marks onboarding as done (saves [profile]), so launches land on Now/Trips. */
    fun seedOnboarded(profile: UserProfile = DemoData.profile) = runBlocking {
        graph.profileRepository.save(profile)
    }

    /** Saves [trip] as if the user had created it. */
    fun seedTrip(trip: Trip) = runBlocking { graph.tripRepository.upsert(trip) }

    /**
     * Launches [MainActivity] in a fresh task, like the launcher (no [deepLink]) or a notification/widget tap (an
     * `opusclockblock://` [deepLink], resolved through the manifest's intent filter).
     */
    fun launch(deepLink: String? = null) {
        val intent = if (deepLink == null) {
            Intent(context, MainActivity::class.java).setAction(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        } else {
            deepLinkIntent(deepLink)
        }
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
    }

    /** An `ACTION_VIEW` intent like the ones notifications and widgets send. */
    fun deepLinkIntent(uri: String): Intent =
        Intent(Intent.ACTION_VIEW, Uri.parse(uri)).setPackage(context.packageName)

    /** Waits until a node with [tag] exists (e.g. after the splash screen or a navigation). */
    @OptIn(ExperimentalTestApi::class)
    fun awaitTag(tag: String, timeoutMillis: Long = DefaultTimeoutMillis): SemanticsNodeInteraction {
        compose.waitUntilAtLeastOneExists(hasTestTag(tag), timeoutMillis)
        return compose.onNodeWithTag(tag)
    }

    /** Waits until a node matching [matcher] exists. */
    @OptIn(ExperimentalTestApi::class)
    fun await(matcher: SemanticsMatcher, timeoutMillis: Long = DefaultTimeoutMillis): SemanticsNodeInteraction {
        compose.waitUntilAtLeastOneExists(matcher, timeoutMillis)
        return compose.onAllNodes(matcher).onFirst()
    }

    /** Waits until some text containing [text] is shown. */
    fun awaitText(text: String, timeoutMillis: Long = DefaultTimeoutMillis): SemanticsNodeInteraction =
        await(hasText(text, substring = true), timeoutMillis)

    /** Waits until no node with [tag] exists any more (dialogs, sheets and screens that animate away). */
    @OptIn(ExperimentalTestApi::class)
    fun awaitGone(tag: String, timeoutMillis: Long = DefaultTimeoutMillis) {
        compose.waitUntilDoesNotExist(hasTestTag(tag), timeoutMillis)
    }

    /** Waits until any of [tags] exists and returns the first one found. */
    fun awaitAnyTag(vararg tags: String, timeoutMillis: Long = DefaultTimeoutMillis): String {
        var found: String? = null
        compose.waitUntil(timeoutMillis) {
            found = tags.firstOrNull { compose.onAllNodesWithTag(it).fetchSemanticsNodes().isNotEmpty() }
            found != null
        }
        return checkNotNull(found)
    }

    /** True when a node with [tag] is currently in the tree. */
    fun exists(tag: String): Boolean = compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()

    /** Runs [command] as the shell user (UiAutomation) and returns its output. */
    fun shell(command: String): String =
        ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(command))
            .bufferedReader()
            .use { it.readText() }

    val notificationsGranted: Boolean
        get() = context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    /** Grants POST_NOTIFICATIONS to this app only (granting, unlike revoking, doesn't restart the process). */
    fun grantNotifications() {
        if (!notificationsGranted) {
            instrumentation.uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    companion object {
        const val DefaultTimeoutMillis = 10_000L
        const val LongTimeoutMillis = 20_000L
    }
}
