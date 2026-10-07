package dev.sebastiano.clockblocker.opus.shell

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import dev.sebastiano.clockblocker.opus.TestApplication
import dev.sebastiano.clockblocker.opus.core.designsystem.theme.ClockblockTheme
import dev.sebastiano.clockblocker.opus.core.designsystem.theme.ClockblockThemeVariant
import dev.sebastiano.clockblocker.opus.core.model.SleepWindow
import dev.sebastiano.clockblocker.opus.core.model.ThemeMode
import io.kotest.matchers.shouldBe
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Clock
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

/**
 * The shell's theme wiring end to end through [ClockblockAppRoot]: Opus mode from settings (unlocked *and* enabled)
 * re-themes the live UI, and Calm motion follows body night independently of colours.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [37], application = TestApplication::class, qualifiers = "w411dp-h891dp")
class ShellThemeTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private var variant: ClockblockThemeVariant? = null
    private var calm: Boolean? = null

    /** The fake screens, with the trips screen reporting the theme it is drawn in. */
    private val probing = object : AppDestinations by FakeDestinations {
        @Composable
        override fun Trips(
            selectedTripId: String?,
            onOpenTrip: (tripId: String) -> Unit,
            onNewTrip: () -> Unit,
            onOpenSettings: () -> Unit,
            onEditTrip: (tripId: String) -> Unit,
            onCreateReturnTrip: (outboundTripId: String) -> Unit,
        ) {
            variant = ClockblockTheme.variant
            calm = ClockblockTheme.motion.isCalm
            FakeDestinations.Trips(selectedTripId, onOpenTrip, onNewTrip, onOpenSettings, onEditTrip, onCreateReturnTrip)
        }
    }

    private var uiState by mutableStateOf<ShellUiState>(ShellUiState.Loading)

    private fun launch(state: ShellUiState.Ready, clock: Clock = Clock.systemDefaultZone()) {
        uiState = state
        compose.setContent { ClockblockAppRoot(uiState = uiState, destinations = probing, clock = clock) }
        compose.waitForIdle()
    }

    private fun ready(opusUnlocked: Boolean = false, opusEnabled: Boolean = false, sleep: SleepWindow? = null) =
        ShellUiState.Ready(
            hasProfile = true,
            hasTrips = false,
            settings = TestSettings.copy(
                themeMode = ThemeMode.Light,
                opusModeUnlocked = opusUnlocked,
                opusModeEnabled = opusEnabled,
                // Calm is a motion scheme: keep motion on so it can show.
                reduceMotion = false,
            ),
            sleep = sleep,
        )

    @Test
    fun `opus mode re-themes the live app when toggled in settings`() {
        launch(ready(opusUnlocked = true, opusEnabled = true))
        variant shouldBe ClockblockThemeVariant.Opus

        uiState = ready(opusUnlocked = true, opusEnabled = false)
        compose.waitForIdle()
        variant shouldBe ClockblockThemeVariant.Standard

        uiState = ready(opusUnlocked = true, opusEnabled = true)
        compose.waitForIdle()
        variant shouldBe ClockblockThemeVariant.Opus
    }

    @Test
    fun `opus mode needs the unlock as well as the switch`() {
        launch(ready(opusUnlocked = false, opusEnabled = true))
        variant shouldBe ClockblockThemeVariant.Standard
    }

    @Test
    fun `opus mode trips screen`() {
        launch(ready(opusUnlocked = true, opusEnabled = true).let { it.copy(settings = it.settings.copy(reduceMotion = true)) })
        // Let the palette cross-fade settle before capturing.
        compose.mainClock.advanceTimeBy(2_000)
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("src/test/screenshots/shell_compact_trips_opus.png")
    }

    @Test
    fun `motion is calm during body night and expressive by day, colours untouched`() {
        val zone = ZoneId.of("Europe/Lisbon")
        val sleep = SleepWindow(LocalTime.of(23, 0), LocalTime.of(7, 0))
        launch(ready(sleep = sleep), clock = Clock.fixed(Instant.parse("2026-06-12T01:30:00Z"), zone)) // 02:30 local
        calm shouldBe true
        variant shouldBe ClockblockThemeVariant.Standard
    }

    @Test
    fun `motion is not calm by day`() {
        val zone = ZoneId.of("Europe/Lisbon")
        val sleep = SleepWindow(LocalTime.of(23, 0), LocalTime.of(7, 0))
        launch(ready(sleep = sleep), clock = Clock.fixed(Instant.parse("2026-06-12T11:00:00Z"), zone)) // 12:00 local
        calm shouldBe false
    }

    @Test
    fun `no profile, no calm`() {
        launch(ready(sleep = null), clock = Clock.fixed(Instant.parse("2026-06-12T01:30:00Z"), ZoneId.of("Europe/Lisbon")))
        calm shouldBe false
    }
}
