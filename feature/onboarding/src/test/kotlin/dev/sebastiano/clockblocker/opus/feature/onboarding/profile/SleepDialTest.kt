package dev.sebastiano.clockblocker.opus.feature.onboarding.profile

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.sebastiano.clockblocker.opus.core.designsystem.dial.DialGeometry
import dev.sebastiano.clockblocker.opus.core.designsystem.theme.ClockblockTheme
import dev.sebastiano.clockblocker.opus.core.model.SleepWindow
import dev.sebastiano.clockblocker.opus.core.testing.captureRoboImageInvalidated
import dev.sebastiano.clockblocker.opus.feature.onboarding.ClockblockScreenshotTest
import io.kotest.matchers.floats.shouldBeLessThan
import io.kotest.matchers.ints.shouldBeLessThanOrEqual
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
        compose.onNodeWithTag(SleepDialTags.Duration).assert(hasText("8h 15m"))
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

    /** A short, wide space (a landscape phone): the dial with the toggle and the pills in a column beside it. */
    @Test
    fun two_schedules_short_side_by_side() = snap("sleep_dial_two_short") {
        SleepDial(work, daysOff, {}, {}, Modifier.padding(16.dp), maxHeight = 260.dp)
    }

    @Test
    fun two_schedules_short_side_by_side_large_font() = snap("sleep_dial_two_short_fontscale_1_5", fontScale = 1.5f) {
        SleepDial(work, daysOff, {}, {}, Modifier.padding(16.dp), maxHeight = 300.dp)
    }

    @Test
    fun two_schedules_largest_font() = snap("sleep_dial_two_fontscale_2", fontScale = 2f) {
        SleepDial(work, daysOff, {}, {}, Modifier.padding(16.dp))
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
    fun the_toggle_is_two_radio_buttons_and_switches_what_the_pills_and_handles_edit() {
        var editedWork by mutableStateOf(work)
        var editedDaysOff by mutableStateOf<SleepWindow?>(daysOff)
        setContent { SleepDial(editedWork, editedDaysOff, { editedWork = it }, { editedDaysOff = it }) }
        compose.onNodeWithTag(SleepDialTags.WorkDays)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))
            .assertIsOn()
        compose.onNodeWithTag(SleepDialTags.DaysOff).assertIsOff().performClick()
        compose.onNodeWithTag(SleepDialTags.DaysOff).assertIsOn()
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
        compose.onNodeWithTag(SleepDialTags.DaysOff).assertIsOn()
    }

    @Test
    fun toggle_labels_are_whole_at_the_default_size() = assertToggleReadable(fontScale = 1f)

    @Test
    fun toggle_labels_are_whole_at_a_large_font() = assertToggleReadable(fontScale = 1.5f)

    @Test
    fun toggle_labels_are_whole_at_the_largest_font() = assertToggleReadable(fontScale = 2f)

    /** A landscape phone: the dial shrinks to the height, and the toggle must still read in full. */
    @Test
    fun toggle_labels_are_whole_on_a_short_dial() = assertToggleReadable(fontScale = 1f, maxHeight = 260.dp)

    @Test
    fun toggle_labels_are_whole_on_a_short_dial_at_a_large_font() = assertToggleReadable(fontScale = 1.5f, maxHeight = 300.dp)

    private fun assertToggleReadable(fontScale: Float, maxHeight: Dp = Dp.Infinity) {
        setContent(fontScale = fontScale) { SleepDial(work, daysOff, {}, {}, maxHeight = maxHeight) }
        listOf(SleepDialTags.WorkDays to "Work days", SleepDialTags.DaysOff to "Days off").forEach { (tag, label) ->
            val tab = compose.onNodeWithTag(tag).getUnclippedBoundsInRoot()
            ((tab.right - tab.left) >= 48.dp) shouldBe true
            ((tab.bottom - tab.top) >= 48.dp) shouldBe true
            val text = compose.onNodeWithText(label, useUnmergedTree = true)
            val layouts = mutableListOf<TextLayoutResult>()
            text.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            val layout = layouts.single()
            // Every line fits the width its tab gives it, nothing is cut off at the end, and no line is cut off below.
            // (Not hasVisualOverflow: centred text lays its paragraph out at the full max width and reports that.)
            (0 until layout.lineCount).forEach { line ->
                (layout.getLineRight(line) - layout.getLineLeft(line) <= layout.layoutInput.constraints.maxWidth + 0.5f) shouldBe true
            }
            layout.getLineEnd(layout.lineCount - 1, visibleEnd = true) shouldBe label.length
            // Lines break only between words ("Wor" / "k" is a fail).
            (0 until layout.lineCount - 1).forEach { line -> label[layout.getLineEnd(line) - 1] shouldBe ' ' }
            layout.didOverflowHeight shouldBe false
            layout.layoutInput.text.text shouldBe label
            // Never shrunk past legibility: at least 85 % of the label style.
            (layout.layoutInput.style.fontSize.value >= 14f * 0.85f) shouldBe true
            // And the text sits inside its tab.
            val bounds = text.getUnclippedBoundsInRoot()
            (bounds.left >= tab.left - 0.5.dp && bounds.right <= tab.right + 0.5.dp) shouldBe true
        }
        if (maxHeight != Dp.Infinity) {
            val top = minOf(
                compose.onNodeWithTag(SleepDialTags.Dial).getUnclippedBoundsInRoot().top,
                compose.onNodeWithTag(SleepDialTags.WorkDays).getUnclippedBoundsInRoot().top,
            )
            val bottom = maxOf(
                compose.onNodeWithTag(SleepDialTags.Dial).getUnclippedBoundsInRoot().bottom,
                compose.onNodeWithTag(SleepDialTags.WakePill).getUnclippedBoundsInRoot().bottom,
            )
            (bottom - top <= maxHeight + 0.5.dp) shouldBe true
        }
    }

    @Test
    fun talkback_names_the_schedule_the_dial_edits() {
        setContent { SleepDial(work, daysOff, {}, {}) }
        compose.onNodeWithTag(SleepDialTags.Bedtime).assert(hasContentDescription("Work days: Bedtime, 23:00"))
        compose.onNodeWithTag(SleepDialTags.Dial).assert(hasContentDescription("Work days", substring = true))
        compose.onNodeWithTag(SleepDialTags.BedtimePill).fetchSemanticsNode().config[SemanticsActions.OnClick].label shouldBe
            "set work-day bedtime"
        compose.onNodeWithTag(SleepDialTags.DaysOff).performClick()
        compose.onNodeWithTag(SleepDialTags.Bedtime).assert(hasContentDescription("Days off: Bedtime, 00:30"))
        compose.onNodeWithTag(SleepDialTags.Wake).assert(hasContentDescription("Days off: Wake-up, 09:15"))
        compose.onNodeWithTag(SleepDialTags.Dial).assert(hasContentDescription("Days off", substring = true))
        compose.onNodeWithTag(SleepDialTags.WakePill).fetchSemanticsNode().config[SemanticsActions.OnClick].label shouldBe
            "set days-off wake-up time"
    }

    @Test
    fun a_single_schedule_dial_has_no_toggle() {
        setContent { SleepDial(SleepWindow.Default, {}) }
        compose.onNodeWithTag(SleepDialTags.WorkDays).assertDoesNotExist()
        compose.onNodeWithText("of sleep").assertIsDisplayed()
    }

    /** The pills' times are morphing text: they carry the time as their content description. */
    private fun hasTime(time: String) = hasAnyDescendant(hasContentDescription(time)) or hasContentDescription(time)

    // --- Centre readout ------------------------------------------------------------------------------------------

    @Test
    fun the_duration_uses_the_narrow_unit_form() {
        setContent { SleepDial(SleepWindow(LocalTime.of(22, 0), LocalTime.of(10, 25)), {}) }
        compose.onNodeWithTag(SleepDialTags.Duration).assert(hasText("12h 25m"))
    }

    /** TalkBack hears the spaced form used everywhere else ("12 h 25 m"), not the narrow one drawn on the face. */
    @Test
    fun talkback_hears_the_spaced_duration() {
        setContent { SleepDial(SleepWindow(LocalTime.of(22, 0), LocalTime.of(10, 25)), {}) }
        compose.onNodeWithTag(SleepDialTags.Duration).assert(hasContentDescription("12 h 25 m"))
    }

    private val longest = SleepWindow(LocalTime.of(0, 0), LocalTime.of(23, 55))
    private val tenOhFive = SleepWindow(LocalTime.of(22, 0), LocalTime.of(8, 5))
    private val twelveTwentyFive = SleepWindow(LocalTime.of(22, 0), LocalTime.of(10, 25))

    @Test fun readout_clear_longest_night() = assertReadoutClear(longest, 1f)

    @Test fun readout_clear_longest_night_large_font() = assertReadoutClear(longest, 1.5f)

    @Test fun readout_clear_ten_oh_five_large_font() = assertReadoutClear(tenOhFive, 1.5f)

    @Test fun readout_clear_longest_night_12_hour_large_font() = assertReadoutClear(longest, 1.5f, is24Hour = false)

    /** The owner's case: "12 h 25 m" ran into the 18 numeral. */
    @Test fun readout_clear_twelve_twenty_five_large_font() = assertReadoutClear(twelveTwentyFive, 1.5f)

    @Test fun readout_clear_twelve_twenty_five_on_a_narrow_window_large_font() = assertReadoutClear(twelveTwentyFive, 1.5f, width = 320.dp)

    /** The smallest ring the layout allows: a short, narrow space (too narrow for side by side) gets 140 dp. */
    @Test fun readout_clear_on_the_smallest_dial() = assertReadoutClear(longest, 1f, maxHeight = 200.dp, width = 320.dp, ring = MinRing)

    @Test fun readout_clear_on_the_smallest_dial_large_font() =
        assertReadoutClear(longest, 1.5f, maxHeight = 200.dp, width = 320.dp, ring = MinRing)

    /** A narrow window: the choice beside the dial leaves the ring about 200 dp. */
    @Test fun readout_clear_beside_on_a_narrow_window() = assertReadoutClear(longest, 1f, width = 320.dp)

    @Test fun readout_clear_beside_on_a_narrow_window_large_font() = assertReadoutClear(longest, 1.5f, width = 320.dp)

    @Test
    fun readout_smallest_dial_large_font() = snap("sleep_dial_readout_smallest_fontscale_1_5", fontScale = 1.5f) {
        SleepDial(longest, daysOff, {}, {}, Modifier.padding(16.dp), maxHeight = 200.dp)
    }

    @Test
    fun readout_longest_night_beside() = snap("sleep_dial_readout_longest") {
        SleepDial(longest, daysOff, {}, {}, Modifier.padding(16.dp))
    }

    @Test
    fun readout_ten_oh_five_large_font() = snap("sleep_dial_readout_10h05_fontscale_1_5", fontScale = 1.5f) {
        SleepDial(tenOhFive, daysOff, {}, {}, Modifier.padding(16.dp))
    }

    @Test
    fun fit_readout_memoizes_caption_steps_instead_of_remeasuring_per_outer_step() {
        val measured = mutableListOf<Pair<String, Float>>()
        setContent(fontScale = 1.5f) {
            val measurer = rememberTextMeasurer(cacheSize = 0)
            val density = LocalDensity.current
            val textStyles = ClockblockTheme.textStyles
            val captionBase = androidx.compose.material3.MaterialTheme.typography.labelLarge
            val sidePx = with(density) { 200.dp.toPx() }
            val labels = numeralRects(sidePx, measurer, textStyles.timeLabel, is24Hour = true, density = density)
            with(density) {
                fitReadout(
                    measurer = measurer,
                    duration = textStyles.timeHeadline.copy(fontSize = 30.sp, lineHeight = 36.sp),
                    caption = captionBase,
                    template = "88h 88m",
                    captions = listOf("of sleep", "your body's day"),
                    sidePx = sidePx,
                    numerals = labels,
                    measureLine = { text, style ->
                        measured += text to style.fontSize.value
                        measurer.measure(text, style, maxLines = 1, softWrap = false).size
                    },
                )
            }
        }
        // Every (text, fontSize) pair is measured at most once (vs 423 measurements unmemoized).
        measured.distinct().size shouldBe measured.size
        measured.size shouldBeLessThanOrEqual 60
    }

    /**
     * The duration and its caption, as laid out, keep clear of the four hour numerals and stay inside the hour ticks.
     * The numerals' boxes come from the face's spec, not from the code that sizes the readout: "00", "06", "12" and
     * "18" (or "12a", "6a", "12p", "6p"), 10 u tall, centred 81 u from the centre (1 u = side / 320), midnight at the
     * bottom, 06 on the left, noon at the top and 18 on the right; the ticks' inner end is 92 u out. [ring], when given,
     * is the ring's expected diameter.
     */
    private fun assertReadoutClear(
        window: SleepWindow,
        fontScale: Float,
        is24Hour: Boolean = true,
        maxHeight: Dp = Dp.Infinity,
        width: Dp = Dp.Unspecified,
        ring: Dp? = null,
    ) {
        var measurer: TextMeasurer? = null
        var style: TextStyle? = null
        var density: Density? = null
        setContent(fontScale = fontScale, is24Hour = is24Hour) {
            measurer = rememberTextMeasurer()
            style = ClockblockTheme.textStyles.timeLabel
            density = LocalDensity.current
            val m = if (width != Dp.Unspecified) Modifier.width(width) else Modifier
            SleepDial(window, daysOff, {}, {}, m, maxHeight = maxHeight)
        }
        val d = density!!
        val dial = compose.onNodeWithTag(SleepDialTags.Dial).getUnclippedBoundsInRoot()
        if (ring != null) ((dial.right - dial.left) - ring).value.absoluteValue shouldBeLessThan 1f
        val duration = compose.onNodeWithTag(SleepDialTags.Duration, useUnmergedTree = true).getUnclippedBoundsInRoot()
        val caption = compose.onNodeWithTag(SleepDialTags.Caption, useUnmergedTree = true).getUnclippedBoundsInRoot()
        with(d) {
            val side = (dial.right - dial.left).toPx()
            val cx = ((dial.left + dial.right) / 2).toPx()
            val cy = ((dial.top + dial.bottom) / 2).toPx()
            val u = side / 320f
            val numeralStyle = style!!.copy(fontSize = (10f * u).toSp())
            val labels = if (is24Hour) listOf("00", "06", "12", "18") else listOf("12a", "6a", "12p", "6p")
            val centres = listOf(Offset(0f, 81f * u), Offset(-81f * u, 0f), Offset(0f, -81f * u), Offset(81f * u, 0f))
            val numerals = labels.zip(centres) { text, c ->
                val size = measurer!!.measure(text, numeralStyle).size
                Rect(c.x - size.width / 2f, c.y - size.height / 2f, c.x + size.width / 2f, c.y + size.height / 2f)
            }
            listOf(duration, caption).forEach { b ->
                val r = Rect(b.left.toPx() - cx, b.top.toPx() - cy, b.right.toPx() - cx, b.bottom.toPx() - cy)
                // Clear of every numeral, with a little room to spare.
                numerals.forEach { n -> n.overlaps(r.inflate(2f * u)) shouldBe false }
                listOf(r.topLeft, r.topRight, r.bottomLeft, r.bottomRight).forEach { (it.getDistance() <= 92f * u) shouldBe true }
            }
        }
    }

    private companion object {
        /** MinFitDialSize: the smallest ring a short space gets. */
        val MinRing = 140.dp
    }
}
