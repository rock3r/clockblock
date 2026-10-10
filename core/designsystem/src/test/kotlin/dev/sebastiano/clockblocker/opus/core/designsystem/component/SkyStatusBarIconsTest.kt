package dev.sebastiano.clockblocker.opus.core.designsystem.component

import androidx.activity.ComponentActivity
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import io.kotest.matchers.shouldBe
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Sky headers sharing the status bar across screen transitions, where the outgoing and incoming screens are composed
 * together for a while (#127). The night plan and night trips headers want light icons (`false`); a plain screen in
 * light theme keeps the theme's dark icons (`true`).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [37])
class SkyStatusBarIconsTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private enum class Screen { PlanNight, TripsNight, TripsDay, Editor }

    private val lightStatusBars: Boolean
        get() {
            val window = compose.activity.window
            return WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars
        }

    private fun startInLightTheme() {
        compose.runOnUiThread {
            val window = compose.activity.window
            WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = true
        }
    }

    @Composable
    private fun ScreenContent(screen: Screen) {
        when (screen) {
            Screen.PlanNight, Screen.TripsNight -> SkyStatusBarIcons(NightInk, ownsStatusBar = true)
            Screen.TripsDay -> SkyStatusBarIcons(DayInk, ownsStatusBar = true)
            Screen.Editor -> Unit
        }
        Box(Modifier.size(10.dp))
    }

    /**
     * Steps through a transition the way NavDisplay composes it: the outgoing screen, then both (the incoming one
     * composed after it), then only the incoming one.
     */
    private var shown by mutableStateOf(listOf<Screen>())

    private fun showTransitions() {
        startInLightTheme()
        compose.setContent { shown.forEach { screen -> key(screen) { ScreenContent(screen) } } }
    }

    private fun show(vararg screens: Screen) {
        shown = screens.toList()
        compose.waitForIdle()
    }

    @Test
    fun `night plan to night trips keeps light icons after the plan leaves`() {
        showTransitions()
        show(Screen.PlanNight)
        lightStatusBars shouldBe false

        show(Screen.PlanNight, Screen.TripsNight)
        lightStatusBars shouldBe false
        show(Screen.TripsNight)

        lightStatusBars shouldBe false
    }

    @Test
    fun `leaving the night trips header for a plain screen goes back to the theme`() {
        showTransitions()
        show(Screen.PlanNight)
        show(Screen.PlanNight, Screen.TripsNight)
        show(Screen.TripsNight)

        show(Screen.TripsNight, Screen.Editor)
        show(Screen.Editor)

        lightStatusBars shouldBe true
    }

    @Test
    fun `the incoming header wins while both are composed`() {
        showTransitions()
        show(Screen.PlanNight)

        show(Screen.PlanNight, Screen.TripsDay)
        lightStatusBars shouldBe true
        show(Screen.TripsDay)
        lightStatusBars shouldBe true

        show(Screen.TripsDay, Screen.PlanNight)
        lightStatusBars shouldBe false
        show(Screen.PlanNight)
        lightStatusBars shouldBe false
    }

    @Test
    fun `an animated switch between screens ends with the right icons`() {
        var screen by mutableStateOf(Screen.Editor)
        startInLightTheme()
        compose.setContent { AnimatedContent(targetState = screen, label = "screens") { ScreenContent(it) } }

        screen = Screen.PlanNight
        compose.waitForIdle()
        lightStatusBars shouldBe false

        screen = Screen.TripsNight
        compose.waitForIdle()
        lightStatusBars shouldBe false

        screen = Screen.Editor
        compose.waitForIdle()
        lightStatusBars shouldBe true
    }

    private companion object {
        val NightInk = Color(0xFFF4F1FF)
        val DayInk = Color(0xFF1B1B1F)
    }
}
