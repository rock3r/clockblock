package dev.sebastiano.clockblocker.opus.widget.text

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.sebastiano.clockblocker.opus.core.model.AdviceLog
import dev.sebastiano.clockblocker.opus.core.model.AdviceOutcome
import dev.sebastiano.clockblocker.opus.core.model.AdviceType
import dev.sebastiano.clockblocker.opus.core.model.DeepLinks
import dev.sebastiano.clockblocker.opus.widget.draw.GlyphKind
import dev.sebastiano.clockblocker.opus.widget.preview.DemoPlans
import dev.sebastiano.clockblocker.opus.widget.state.WidgetState
import dev.sebastiano.clockblocker.opus.widget.state.WidgetStateMapper
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldStartWith
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import kotlin.math.roundToInt

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class WidgetTextsTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val now = Instant.parse("2026-10-06T09:00:00Z")

    private fun texts(scenario: DemoPlans.Scenario, is24: Boolean = true) = WidgetTexts.from(
        context,
        WidgetStateMapper.map(DemoPlans.lisbonTokyo(now, scenario), now, ZoneId.of("Asia/Tokyo")),
        is24,
    )

    @Test
    fun `empty state invites to plan a trip and deep links to the new trip flow`() {
        val t = WidgetTexts.from(context, WidgetState.NoTrip, true)
        t.glyph shouldBe GlyphKind.NoTrip
        t.title shouldBe "No trip"
        t.deepLink shouldBe DeepLinks.NEW_TRIP
        t.countdownEnd.shouldBeNull()
        t.misalignment.shouldBeNull()
        t.dialTitle shouldContain "plan one"
        t.dialDetail.shouldBeNull()
    }

    @Test
    fun `active advice reads label, until and then`() {
        val t = texts(DemoPlans.Scenario.AvoidLight)
        t.glyph shouldBe GlyphKind.Advice(AdviceType.AvoidLight)
        t.title shouldBe "Avoid light"
        t.subtitle shouldStartWith "until "
        t.subtitle shouldContain " · then "
        t.subtitleLines.size shouldBe 2
        t.subtitleLines[1] shouldStartWith "then "
        t.deepLink shouldBe DeepLinks.plan("demo-lisbon-tokyo")
    }

    @Test
    fun `square dial caption splits label and time over two short lines`() {
        val t = texts(DemoPlans.Scenario.AvoidLight)
        t.dialTitle shouldBe "Avoid light"
        t.dialDetail.shouldNotBeNull() shouldStartWith "until "
    }

    @Test
    fun `jet lag label and description describe the body relative to local time`() {
        // Lisbon → Tokyo: flying east leaves the body behind local time.
        val t = texts(DemoPlans.Scenario.AvoidLight)
        t.misalignment.shouldNotBeNull() shouldStartWith "\u2212"
        t.contentDescription shouldContain "behind local time"
    }

    @Test
    fun `free time caption keeps the time even behind a long next label`() {
        val t = texts(DemoPlans.Scenario.FreeTime)
        t.dialTitle shouldBe "Free time"
        t.dialDetail.shouldNotBeNull() shouldStartWith "until "
    }

    @Test
    fun `end time is repeated in the secondary zone`() {
        texts(DemoPlans.Scenario.AvoidLight).secondary.shouldNotBeNull() shouldContain "Lisbon"
    }

    @Test
    fun `countdown targets the end of the current block within 24 h`() {
        val end = texts(DemoPlans.Scenario.AvoidLight).countdownEnd.shouldNotBeNull()
        (Duration.between(now, end) in Duration.ZERO..Duration.ofHours(24)) shouldBe true
    }

    @Test
    fun `free time points at the next block`() {
        val t = texts(DemoPlans.Scenario.FreeTime)
        t.glyph shouldBe GlyphKind.Free
        t.countdownEnd.shouldBeNull()
        t.subtitle shouldContain "then"
    }

    @Test
    fun `adapted state when the plan is over`() {
        texts(DemoPlans.Scenario.Adapted).glyph shouldBe GlyphKind.Adapted
    }

    @Test
    fun `12 hour clock respected`() {
        texts(DemoPlans.Scenario.AvoidLight, is24 = false).subtitle.uppercase().let {
            (it.contains("AM") || it.contains("PM")) shouldBe true
        }
    }

    @Test
    fun `content description speaks both clocks`() {
        texts(DemoPlans.Scenario.AvoidLight).contentDescription shouldContain "Avoid light"
    }

    @Test
    fun `header names the place and the plan day`() {
        texts(DemoPlans.Scenario.AvoidLight).header shouldBe "Tokyo · Day 2"
        WidgetTexts.from(context, WidgetState.NoTrip, true).header.shouldBeNull()
    }

    @Test
    fun `up next rows carry a label and a start time`() {
        val rows = texts(DemoPlans.Scenario.AvoidLight).upcoming
        rows.map { it.type } shouldBe listOf(AdviceType.Melatonin, AdviceType.Sleep, AdviceType.SeeLight)
        rows.first().label shouldBe "Melatonin"
        rows.first().time.length shouldBe 5 // "HH:mm"
    }

    @Test
    fun `adaptation reads as a percentage`() {
        val t = texts(DemoPlans.Scenario.AvoidLight)
        val fraction = t.adaptation.shouldNotBeNull()
        t.adaptationLabel shouldBe "${(fraction * 100).roundToInt()}% adapted"
    }

    @Test
    fun `done is offered for the current block and reads its label`() {
        val done = texts(DemoPlans.Scenario.AvoidLight).done.shouldNotBeNull()
        done.logged.shouldBeNull()
        done.label shouldBe "Done"
        done.contentDescription shouldBe "Mark Avoid light as done"
        done.tripId shouldBe DemoPlans.TRIP_ID
    }

    @Test
    fun `logged outcome replaces the button with a chip`() {
        val plan = DemoPlans.lisbonTokyo(now, DemoPlans.Scenario.AvoidLight)
        val current = (WidgetStateMapper.map(plan, now, ZoneId.of("Asia/Tokyo")) as WidgetState.Active).current!!
        fun with(outcome: AdviceOutcome) = WidgetTexts.from(
            context,
            WidgetStateMapper.map(plan, now, ZoneId.of("Asia/Tokyo"), listOf(AdviceLog(current.adviceId, outcome))),
            true,
        ).done.shouldNotBeNull()

        with(AdviceOutcome.Done).let {
            it.logged shouldBe AdviceOutcome.Done
            it.label shouldBe "✓ Done"
            it.contentDescription shouldBe "Avoid light: done"
        }
        with(AdviceOutcome.Skipped).label shouldBe "Skipped"
        with(AdviceOutcome.CantDo).label shouldBe "Skipped"
    }

    @Test
    fun `no done without something to do`() {
        texts(DemoPlans.Scenario.FreeTime).done.shouldBeNull()
        texts(DemoPlans.Scenario.Adapted).done.shouldBeNull()
        WidgetTexts.from(context, WidgetState.NoTrip, true).done.shouldBeNull()
    }
}
