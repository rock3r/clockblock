package dev.sebastiano.clockblocker.opus.feature.onboarding.profile

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import dev.sebastiano.clockblocker.opus.core.testing.captureRoboImageInvalidated
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import dev.sebastiano.clockblocker.opus.core.designsystem.dial.DialGeometry
import dev.sebastiano.clockblocker.opus.core.model.SleepWindow
import dev.sebastiano.clockblocker.opus.feature.onboarding.ClockblockScreenshotTest
import io.kotest.matchers.shouldBe
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalTime
import kotlin.math.absoluteValue
import kotlin.math.cos
import kotlin.math.sin

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [37], qualifiers = "w400dp-h800dp-xxhdpi")
class SleepDialTest : ClockblockScreenshotTest() {

    @Test
    fun dial_default() = snap("sleep_dial_default") {
        SleepDial(SleepWindow.Default, {}, Modifier.padding(16.dp))
    }

    @Test
    fun dial_dark_late_short() = snap("sleep_dial_dark_short", darkTheme = true) {
        SleepDial(SleepWindow(LocalTime.of(2, 30), LocalTime.of(6, 45)), {}, Modifier.padding(16.dp))
    }

    @Test
    fun dial_fontscale() = snap("sleep_dial_fontscale_1_5", fontScale = 1.5f) {
        SleepDial(SleepWindow(LocalTime.of(22, 15), LocalTime.of(6, 30)), {}, Modifier.padding(16.dp))
    }

    @Test
    fun talkback_adjusts_handles_by_15_minutes() {
        var window by mutableStateOf(SleepWindow.Default)
        setContent { SleepDial(window, { window = it }) }
        compose.onNodeWithTag(SleepDialTags.Wake)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "07:00"))
        compose.onNodeWithTag(SleepDialTags.Wake).performSemanticsAction(SemanticsActions.SetProgress) { it(7 * 60f + 15f) }
        window shouldBe SleepWindow(LocalTime.of(23, 0), LocalTime.of(7, 15))
        val actions = compose.onNodeWithTag(SleepDialTags.Bedtime).fetchSemanticsNode().config[SemanticsActions.CustomActions]
        compose.runOnIdle { actions.first { it.label == "15 minutes earlier" }.action() }
        window shouldBe SleepWindow(LocalTime.of(22, 45), LocalTime.of(7, 15))
    }

    @Test
    fun keyboard_arrows_move_the_focused_handle() {
        var window by mutableStateOf(SleepWindow.Default)
        setContent { SleepDial(window, { window = it }) }
        compose.onNodeWithTag(SleepDialTags.Bedtime).requestFocus()
        compose.onNodeWithTag(SleepDialTags.Bedtime).performKeyInput { pressKey(Key.DirectionLeft) }
        window shouldBe SleepWindow(LocalTime.of(22, 45), LocalTime.of(7, 0))
    }

    @Test
    fun pills_follow_the_window_and_say_what_a_tap_does() {
        var window by mutableStateOf(SleepWindow.Default)
        setContent { SleepDial(window, { window = it }) }
        compose.onNodeWithTag(SleepDialTags.BedtimePill).assert(hasText("Bedtime")).assert(hasTime("23:00"))
        compose.onNodeWithTag(SleepDialTags.WakePill).assert(hasText("Wake")).assert(hasTime("07:00"))
        compose.onNodeWithTag(SleepDialTags.BedtimePill).fetchSemanticsNode().config[SemanticsActions.OnClick].label shouldBe "set bedtime"
        compose.onNodeWithTag(SleepDialTags.WakePill).assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
        // A TalkBack nudge on a handle shows up in its pill.
        compose.onNodeWithTag(SleepDialTags.Wake).performSemanticsAction(SemanticsActions.SetProgress) { it(7 * 60f + 15f) }
        compose.onNodeWithTag(SleepDialTags.WakePill).assert(hasTime("07:15"))
        compose.onNodeWithTag(SleepDialTags.Duration).assert(hasText("8 h 15 m"))
    }

    @Test
    fun tapping_a_pill_opens_the_time_picker_and_cancel_keeps_the_window() {
        var window by mutableStateOf(SleepWindow.Default)
        setContent { SleepDial(window, { window = it }) }
        compose.onNodeWithTag(SleepDialTags.WakePill).performClick()
        compose.onNodeWithText("Wake-up time").assertIsDisplayed()
        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithTag(SleepDialTags.PickerConfirm).assertDoesNotExist()
        window shouldBe SleepWindow.Default
    }

    @Test
    fun confirming_the_picker_sets_that_end_of_the_window() {
        var window by mutableStateOf(SleepWindow(LocalTime.of(22, 30), LocalTime.of(6, 45)))
        setContent { SleepDial(window, { window = it }) }
        compose.onNodeWithTag(SleepDialTags.BedtimePill).performClick()
        compose.onNodeWithTag(SleepDialTags.PickerConfirm).performClick()
        compose.onNodeWithTag(SleepDialTags.PickerConfirm).assertDoesNotExist()
        // The picker opened on the current bedtime, so confirming keeps it and leaves wake alone.
        window shouldBe SleepWindow(LocalTime.of(22, 30), LocalTime.of(6, 45))
    }

    @Test
    fun the_ring_shrinks_so_ring_and_pills_fit_max_height() = assertFits(fontScale = 1f)

    @Test
    fun the_ring_shrinks_so_ring_and_pills_fit_max_height_at_a_large_font() = assertFits(fontScale = 1.5f)

    private fun assertFits(fontScale: Float) {
        val maxHeight = 380.dp
        setContent(fontScale = fontScale) { SleepDial(SleepWindow.Default, {}, maxHeight = maxHeight) }
        val dial = compose.onNodeWithTag(SleepDialTags.Dial).getUnclippedBoundsInRoot()
        val pill = compose.onNodeWithTag(SleepDialTags.WakePill).getUnclippedBoundsInRoot()
        (pill.bottom - dial.top <= maxHeight + 0.5.dp) shouldBe true
        (dial.right - dial.left < 360.dp) shouldBe true
        (((dial.right - dial.left) - (dial.bottom - dial.top)).value.absoluteValue < 0.01f) shouldBe true
    }

    @Test
    fun dial_12_hour_large_font() = snap("sleep_dial_12h_fontscale_1_5", fontScale = 1.5f, is24Hour = false) {
        // The widest times ("10:55 PM") at a large font: the AM/PM pills must fit without clipping.
        SleepDial(SleepWindow(LocalTime.of(22, 55), LocalTime.of(10, 55)), {}, Modifier.padding(16.dp))
    }

    @Test
    fun the_egg_hides_as_soon_as_the_gate_closes() {
        var enabled by mutableStateOf(true)
        setContent { SleepDial(SleepWindow.Default, {}, easterEggEnabled = enabled) }
        val line = hasText("24.2 hours", substring = true)
        compose.onNodeWithTag(SleepDialTags.Dial).performTouchInput {
            val c = center
            val r = 122f * minOf(width, height) / 320f
            fun at(minute: Float): Offset {
                val a = Math.toRadians(DialGeometry.angleForMinute(minute).toDouble())
                return Offset(c.x + r * cos(a).toFloat(), c.y + r * sin(a).toFloat())
            }
            // Push the wake handle (07:00) round past the longest window until the egg fires.
            down(at(7 * 60f))
            var minute = 7 * 60f
            while (minute < 7 * 60f + 18 * 60f) {
                minute += 20f
                moveTo(at(minute))
            }
        }
        compose.onNode(line).assertExists()
        compose.mainClock.autoAdvance = false
        enabled = false
        Snapshot.sendApplyNotifications()
        // Far less than the egg's 4.5 s hold: the gate closing hides it straight away.
        compose.mainClock.advanceTimeBy(1_000)
        compose.onNode(line).assertDoesNotExist()
        compose.onNodeWithTag(SleepDialTags.Dial).performTouchInput { up() }
    }

    // --- Two schedules -----------------------------------------------------------------------------------------

    private val work = SleepWindow(LocalTime.of(23, 0), LocalTime.of(7, 0))
    private val daysOff = SleepWindow(LocalTime.of(0, 30), LocalTime.of(9, 15))

    @Test
    fun two_schedules_linked() = snap("sleep_dial_two_linked") {
        SleepDial(work, null, {}, {}, Modifier.padding(16.dp))
    }

    @Test
    fun two_schedules_work_days() = snap("sleep_dial_two_work_days") {
        SleepDial(work, daysOff, {}, {}, Modifier.padding(16.dp))
    }

    @Test
    fun two_schedules_days_off() {
        setContent { SleepDial(work, daysOff, {}, {}, Modifier.padding(16.dp)) }
        compose.onNodeWithTag(SleepDialTags.DaysOff).performClick()
        capture("sleep_dial_two_days_off")
    }

    @Test
    fun two_schedules_dark() {
        setContent(darkTheme = true) { SleepDial(work, daysOff, {}, {}, Modifier.padding(16.dp)) }
        compose.onNodeWithTag(SleepDialTags.DaysOff).performClick()
        capture("sleep_dial_two_days_off_dark")
    }

    /** The owner's layout check: the toggle in the centre and the handles against the outer ring at font scale 1.3. */
    @Test
    @Config(qualifiers = "w360dp-h740dp-xxhdpi")
    fun two_schedules_compact_large_font() = snap("sleep_dial_two_compact_fontscale_1_3", fontScale = 1.3f) {
        SleepDial(work, daysOff, {}, {}, Modifier.padding(16.dp))
    }

    @Test
    fun two_schedules_12_hour_large_font() = snap("sleep_dial_two_12h_fontscale_1_5", fontScale = 1.5f, is24Hour = false) {
        SleepDial(SleepWindow(LocalTime.of(22, 55), LocalTime.of(10, 55)), daysOff, {}, {}, Modifier.padding(16.dp))
    }

    /** One frame of the swap with motion on: the rings mid-way through trading radius and thickness. */
    @Test
    fun two_schedules_swap_mid() {
        setContent(reduceMotion = false) { SleepDial(work, daysOff, {}, {}, Modifier.padding(16.dp)) }
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag(SleepDialTags.DaysOff).performClick()
        compose.mainClock.advanceTimeBy(120)
        compose.captureRoboImageInvalidated("src/test/screenshots/sleep_dial_two_swap_mid.png")
    }

    @Test
    fun the_toggle_is_two_tabs_and_switches_what_the_pills_and_handles_edit() {
        var editedWork by mutableStateOf(work)
        var editedDaysOff by mutableStateOf<SleepWindow?>(daysOff)
        setContent { SleepDial(editedWork, editedDaysOff, { editedWork = it }, { editedDaysOff = it }) }
        compose.onNodeWithTag(SleepDialTags.WorkDays)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab))
            .assertIsSelected()
        compose.onNodeWithTag(SleepDialTags.DaysOff).assertIsNotSelected().performClick()
        compose.onNodeWithTag(SleepDialTags.DaysOff).assertIsSelected()
        compose.onNodeWithTag(SleepDialTags.BedtimePill).assert(hasTime("00:30"))
        compose.onNodeWithTag(SleepDialTags.WakePill).assert(hasTime("09:15"))
        compose.onNodeWithTag(SleepDialTags.Wake).performSemanticsAction(SemanticsActions.SetProgress) { it(9 * 60f + 30f) }
        editedDaysOff shouldBe SleepWindow(LocalTime.of(0, 30), LocalTime.of(9, 30))
        editedWork shouldBe work
    }

    @Test
    fun linked_days_off_show_work_days_until_a_handle_moves_them() {
        var editedWork by mutableStateOf(work)
        var editedDaysOff by mutableStateOf<SleepWindow?>(null)
        setContent { SleepDial(editedWork, editedDaysOff, { editedWork = it }, { editedDaysOff = it }) }
        compose.onNodeWithTag(SleepDialTags.DaysOff).performClick()
        compose.onNodeWithTag(SleepDialTags.BedtimePill).assert(hasTime("23:00"))
        editedDaysOff shouldBe null
        val actions = compose.onNodeWithTag(SleepDialTags.Bedtime).fetchSemanticsNode().config[SemanticsActions.CustomActions]
        compose.runOnIdle { actions.first { it.label == "15 minutes later" }.action() }
        editedDaysOff shouldBe SleepWindow(LocalTime.of(23, 15), LocalTime.of(7, 0))
        editedWork shouldBe work
    }

    @Test
    fun tapping_the_outer_ring_switches_schedule() {
        setContent { SleepDial(work, daysOff, {}, {}) }
        compose.onNodeWithTag(SleepDialTags.Dial).performTouchInput {
            val r = 151f * minOf(width, height) / 320f
            val a = Math.toRadians(DialGeometry.angleForMinute(15 * 60f).toDouble())
            click(Offset(center.x + r * cos(a).toFloat(), center.y + r * sin(a).toFloat()))
        }
        compose.onNodeWithTag(SleepDialTags.DaysOff).assertIsSelected()
    }

    @Test
    fun a_single_schedule_dial_has_no_toggle() {
        setContent { SleepDial(SleepWindow.Default, {}) }
        compose.onNodeWithTag(SleepDialTags.WorkDays).assertDoesNotExist()
        compose.onNodeWithText("of sleep").assertIsDisplayed()
    }

    /** The pills' times are morphing text: they carry the time as their content description. */
    private fun hasTime(time: String) = hasAnyDescendant(hasContentDescription(time)) or hasContentDescription(time)
}
