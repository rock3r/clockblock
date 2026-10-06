package dev.sebastiano.clockblocker.opus.e2e

import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.sebastiano.clockblocker.opus.feature.trips.TripsTestTags
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The trip editor keeps what was entered across configuration changes: landscape, then a 1.5× font scale.
 * Device settings (rotation lock, user rotation, font scale) are saved before and restored after each test.
 */
@RunWith(AndroidJUnit4::class)
class EditorConfigChangesTest : ClockblockE2eTest() {
    private lateinit var saved: Map<String, String>

    @Before
    fun saveDeviceSettings() {
        saved = SystemSettings.associateWith { shell("settings get system $it").trim() }
    }

    @After
    fun restoreDeviceSettings() {
        runCatching { device.setOrientationNatural() }
        saved.forEach { (key, value) ->
            if (value == "null" || value.isEmpty()) shell("settings delete system $key") else shell("settings put system $key $value")
        }
        device.waitForIdle()
    }

    @Test
    fun editorKeepsInputAcrossRotationAndLargeFont() {
        seedOnboarded()
        launch(deepLink = "clockblock://trips/new")
        awaitTag("route_trip_editor", LongTimeoutMillis)

        pickPlace(TripsTestTags.editorFrom(0), "lis", "LIS")
        awaitTag(TripsTestTags.editorFlightNumber(0)).scrollToIfScrollable().performTextInput("TP 80")
        assertInputKept()

        device.setOrientationLandscape()
        awaitRotation(landscape = true)
        assertInputKept()

        shell("settings put system font_scale 1.5")
        compose.waitUntil(DefaultTimeoutMillis) { context.resources.configuration.fontScale > 1.4f }
        assertInputKept()

        device.setOrientationNatural()
        awaitRotation(landscape = false)
        assertInputKept()
        awaitTag(TripsTestTags.EditorSave).assertIsDisplayed()
    }

    private fun assertInputKept() {
        awaitTag("route_trip_editor", LongTimeoutMillis)
        awaitTag(TripsTestTags.editorFrom(0)).scrollToIfScrollable().assert(showsText("Lisbon") or showsText("LIS"))
        awaitTag(TripsTestTags.editorFlightNumber(0)).scrollToIfScrollable().assert(showsText("TP 80"))
    }

    private fun showsText(text: String): SemanticsMatcher =
        hasText(text, substring = true) or hasAnyDescendant(hasText(text, substring = true))

    private fun awaitRotation(landscape: Boolean) {
        compose.waitUntil(DefaultTimeoutMillis) {
            device.waitForIdle()
            (device.displayWidth > device.displayHeight) == landscape
        }
    }

    private companion object {
        val SystemSettings = listOf("accelerometer_rotation", "user_rotation", "font_scale")
    }
}
