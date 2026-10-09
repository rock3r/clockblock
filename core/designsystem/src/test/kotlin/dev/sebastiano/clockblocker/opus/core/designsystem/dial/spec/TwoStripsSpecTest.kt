package dev.sebastiano.clockblocker.opus.core.designsystem.dial.spec

import dev.sebastiano.clockblocker.opus.core.designsystem.dial.DialArc
import dev.sebastiano.clockblocker.opus.core.designsystem.dial.DialGeometry
import dev.sebastiano.clockblocker.opus.core.designsystem.dial.DialPalettes
import dev.sebastiano.clockblocker.opus.core.designsystem.dial.DialState
import dev.sebastiano.clockblocker.opus.core.model.AdviceType
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.floats.plusOrMinus
import io.kotest.matchers.floats.shouldBeGreaterThan
import io.kotest.matchers.floats.shouldBeLessThan
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.element
import io.kotest.property.arbitrary.int
import io.kotest.property.checkAll
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.time.Instant

class TwoStripsSpecTest {

    private val labels = DefaultDialLabels(is24Hour = true)
    private val palette = DialPalettes.Light

    /** Tokyo, 15:20, body 7 h behind: avoiding light until 16:30, then melatonin, then sleep. */
    private val tokyo = DialState(
        instant = Instant.parse("2026-03-11T06:20:00Z"),
        displayZoneId = "Asia/Tokyo",
        localMinute = 15 * 60f + 20f,
        bodyAheadMinutes = -420f,
        arcs = persistentListOf(
            DialArc("avoid", AdviceType.AvoidLight, 13 * 60f + 30f, 180f),
            DialArc("mel", AdviceType.Melatonin, 16 * 60f + 30f, 0f),
            DialArc("sleep", AdviceType.Sleep, 23 * 60f, 480f),
        ),
        sunriseMinute = 345f,
        sunsetMinute = 1035f,
    )

    private fun spec(state: DialState = tokyo, w: Float = 240f, h: Float = 112f, labels: DialLabels = this.labels) =
        TwoStrips.spec(state, palette, labels, w, h)

    private inline fun <reified T : DialOp> DialSpec.all(part: DialPart? = null): List<T> =
        ops.filterIsInstance<T>().filter { part == null || it.part == part }

    @Test
    fun `a host's label floor sets every label on the strips, and a bar too thin for it drops its labels`() {
        for ((w, h) in listOf(250f to 51f, 250f to 84f, 328f to 170f)) {
            val s = TwoStrips.spec(tokyo, palette, labels, w, h, labelText = 10f)
            val barLabels = s.all<DialOp.Text>(DialPart.RingLabel)
            barLabels.shouldNotBeEmpty()
            // Bar labels, the advice's name, the jet lag: every word that isn't one of the two times.
            s.all<DialOp.Text>().filter { it.part != DialPart.Readout && it.part != DialPart.Needle }
                .forEach { (it.spec.size >= 10f) shouldBe true }
        }
        // 20 dp text can't sit inside a 14 dp bar: no label rather than a tiny one. Both times stay.
        val big = TwoStrips.spec(tokyo, palette, labels, 250f, 84f, labelText = 20f)
        big.all<DialOp.Text>(DialPart.RingLabel).shouldBeEmpty()
        big.all<DialOp.Text>().mapNotNull { it.live?.clock }.toSet() shouldBe setOf(LiveClock.Local, LiveClock.Body)
    }

    @Test
    fun `the jet lag gives way before the body time, at every label floor and text size`() {
        for (floor in listOf(0f, 10f, 13f, 16f, 20f)) for (growth in listOf(1f, 1.15f)) for ((w, h) in listOf(250f to 51f, 250f to 84f, 200f to 84f)) {
            val s = TwoStrips.spec(tokyo, palette, DefaultDialLabels(is24Hour = false), w, h, textGrowth = growth, labelText = floor)
            s.all<DialOp.Text>().mapNotNull { it.live?.clock }.toSet() shouldBe setOf(LiveClock.Local, LiveClock.Body)
        }
    }

    @Test
    fun `the header leaves room for the widest live reading before the body time`() {
        // Captured at 9:59 AM: the host will write 10:00 AM in the same place before the next capture.
        val twelve = DefaultDialLabels(is24Hour = false)
        val early = tokyo.copy(localMinute = 9 * 60f + 59f, arcs = persistentListOf())
        for (floor in listOf(0f, 10f, 13f)) {
            val s = TwoStrips.spec(early, palette, twelve, 250f, 84f, labelText = floor)
            val local = s.all<DialOp.Text>().single { it.live?.clock == LiveClock.Local }
            val body = s.all<DialOp.Text>().single { it.live?.clock == LiveClock.Body }
            val widest = ApproxTextMeasurer.width(twelve.fullTime(10 * 60f), local.spec)
            (body.x >= local.x + widest) shouldBe true
        }
    }

    @Test
    fun `the level comes from the box`() {
        DetailLevel.forStrip(117f, 51f) shouldBe DetailLevel.Glance
        DetailLevel.forStrip(56f, 50f) shouldBe DetailLevel.Glance
        DetailLevel.forStrip(250f, 51f) shouldBe DetailLevel.Simple
        DetailLevel.forStrip(400f, 100f) shouldBe DetailLevel.Simple
        DetailLevel.forStrip(328f, 170f) shouldBe DetailLevel.Full
        spec(w = 88f, h = 88f).level shouldBe DetailLevel.Glance
        spec(w = 328f, h = 170f).level shouldBe DetailLevel.Full
    }

    @Test
    fun `now sits about a third of the way along a 24 h window that starts on the hour`() {
        val s = spec()
        val axis = s.axis.shouldNotBeNull()
        axis.spanMinutes shouldBe DialGeometry.MinutesPerDay
        (axis.startMinute % 60f) shouldBe 0f
        val along = (axis.x(tokyo.localMinute) - axis.left) / (axis.right - axis.left)
        along shouldBeGreaterThan 0.26f
        along shouldBeLessThan 0.31f
        s.nowMinute shouldBe tokyo.localMinute
    }

    @Test
    fun `the now line is the needle, at now on the axis`() {
        val s = spec()
        val x = s.axis!!.x(tokyo.localMinute)
        val needle = s.all<DialOp.Line>(DialPart.Needle)
        needle.shouldNotBeEmpty()
        needle.forEach { it.x0 shouldBe (x plusOrMinus 0.01f); it.x1 shouldBe (x plusOrMinus 0.01f) }
    }

    @Test
    fun `the body bar is the local sky shifted by the jet lag, and identical once adapted`() {
        fun bars(state: DialState) = spec(state).all<DialOp.SkyBar>().let { it.first { b -> b.part == DialPart.LocalSky } to it.first { b -> b.part == DialPart.BodySky } }
        // Body 7 h behind: its nightfall comes 7 h (of the window's 24) later along the bar.
        val (local, body) = bars(tokyo)
        val n = local.colors.size
        val night = palette.sky.night
        fun List<Argb>.nightfall() = indices.first { i -> i > 0 && this[i] == night && this[i - 1] != night }
        val localNight = local.colors.nightfall()
        val bodyNight = body.colors.nightfall()
        (bodyNight - localNight).toFloat() shouldBe ((7f / 24f * n) plusOrMinus 2f)
        val (l2, b2) = bars(tokyo.copy(bodyAheadMinutes = 0f))
        b2.colors shouldBe l2.colors
    }

    @Test
    fun `the times are live readings, body time slanted, and in sync once adapted`() {
        for (size in listOf(88f to 88f, 117f to 51f, 250f to 51f, 240f to 112f, 328f to 170f)) {
            val s = spec(w = size.first, h = size.second)
            val live = s.all<DialOp.Text>().mapNotNull { it.live }
            live.map { it.clock }.toSet() shouldBe setOf(LiveClock.Local, LiveClock.Body)
            s.all<DialOp.Text>().filter { it.live?.clock == LiveClock.Body }.forEach { it.spec.slanted shouldBe true }
        }
        val adapted = spec(tokyo.copy(bodyAheadMinutes = 0f))
        adapted.all<DialOp.Text>().filter { it.live?.clock == LiveClock.Body }.shouldBeEmpty()
        adapted.all<DialOp.Text>().map { it.text } shouldContainAll listOf(labels.inSync())
    }

    @Test
    fun `a short narrow strip drops the body time before the local time`() {
        val roomy = spec(w = 88f, h = 88f).all<DialOp.Text>().mapNotNull { it.live?.clock }
        roomy shouldContainAll listOf(LiveClock.Local, LiveClock.Body)
        val short = spec(w = 56f, h = 30f).all<DialOp.Text>().mapNotNull { it.live?.clock }
        short shouldBe listOf(LiveClock.Local)
    }

    @Test
    fun `the advice in focus rides above the bars with its glyph, and its words on Full`() {
        val simple = spec(w = 240f, h = 112f)
        simple.all<DialOp.Glyph>().map { it.type } shouldBe listOf(AdviceType.AvoidLight)
        simple.all<DialOp.Text>(DialPart.Narration).shouldBeEmpty()
        // Never a glyph alone: its name rides beside the capsule.
        simple.all<DialOp.Text>(DialPart.Advice).map { it.text } shouldBe listOf("Avoid light")
        val full = spec(w = 328f, h = 170f)
        full.all<DialOp.Text>(DialPart.Narration).map { it.text } shouldContainAll listOf("Avoid light until 16:30", "then take melatonin")
    }

    @Test
    fun `a block the window's seam cuts draws both pieces, its glyph on the one under the now line`() {
        // 07:30 under an overnight sleep (23:30–08:00): the window starts at midnight, so the block shows at both ends.
        val night = tokyo.copy(
            localMinute = 7 * 60f + 30f,
            arcs = persistentListOf(DialArc("sleep", AdviceType.Sleep, 23 * 60f + 30f, 510f)),
        )
        val s = spec(night, w = 250f, h = 84f)
        val axis = s.axis.shouldNotBeNull()
        axis.startMinute shouldBe 0f
        val nowX = axis.x(night.localMinute)
        val fills = s.all<DialOp.Rect>(DialPart.Advice).filter { it.color == palette.adviceFill(AdviceType.Sleep) }
        fills.any { it.left < nowX && it.right > nowX } shouldBe true
        fills.any { it.right > axis.x(23 * 60f + 30f) } shouldBe true
        s.all<DialOp.Glyph>().single().cx shouldBeLessThan nowX
    }

    @Test
    fun `a glyph never shows without its name`() = runTest {
        val sizes = listOf(160f to 80f, 250f to 84f, 240f to 112f, 400f to 140f, 328f to 170f)
        checkAll(Arb.int(0, 1439), Arb.element(sizes)) { minute, (w, h) ->
            val s = spec(tokyo.copy(localMinute = minute.toFloat()), w, h)
            val names = s.all<DialOp.Text>().map { it.text }
            s.all<DialOp.Glyph>().forEach { g -> names.any { it.contains(labels.advice(g.type), ignoreCase = true) } shouldBe true }
        }
    }

    @Test
    fun `labels and times stay inside the box and off the now line, at every hour and size`() = runTest {
        val sizes = listOf(56f to 50f, 88f to 88f, 117f to 51f, 160f to 80f, 250f to 51f, 240f to 112f, 328f to 170f, 400f to 140f)
        checkAll(Arb.int(0, 1439), Arb.element(sizes), Arb.element(listOf(true, false))) { minute, (w, h), is24 ->
            val state = tokyo.copy(localMinute = minute.toFloat())
            val s = spec(state, w, h, DefaultDialLabels(is24Hour = is24))
            val nowX = s.axis!!.x(state.localMinute)
            s.all<DialOp.Text>().forEach { t ->
                val tw = ApproxTextMeasurer.width(t.text, t.spec)
                val x0 = when (t.h) { HAlign.Start -> t.x; HAlign.Center -> t.x - tw / 2f; HAlign.End -> t.x - tw }
                (x0 >= -0.5f && x0 + tw <= w + 0.5f) shouldBe true
                (t.y - t.spec.size / 2f >= -0.5f && t.y + t.spec.size / 2f <= h + 0.5f) shouldBe true
                // Bar labels step aside for the now line.
                if (t.part == DialPart.RingLabel) (x0 > nowX || x0 + tw < nowX) shouldBe true
            }
        }
    }

    @Test
    fun `the jet lag bracket names the offset on Full, between the two nights`() {
        val full = spec(w = 328f, h = 170f)
        full.all<DialOp.Text>(DialPart.Offset).map { it.text } shouldContainAll listOf(labels.offset(-420f))
        spec(tokyo.copy(bodyAheadMinutes = 0f), 328f, 170f).all<DialOp.Line>(DialPart.Offset).shouldBeEmpty()
        spec(w = 240f, h = 112f).all<DialOp.Line>(DialPart.Offset).shouldBeEmpty()
        full.level shouldNotBe DetailLevel.Simple
    }
}
