package dev.sebastiano.clockblocker.opus.core.designsystem.dial.spec

import dev.sebastiano.clockblocker.opus.core.designsystem.dial.DialArc
import dev.sebastiano.clockblocker.opus.core.designsystem.dial.DialGeometry
import dev.sebastiano.clockblocker.opus.core.designsystem.dial.DialPalettes
import dev.sebastiano.clockblocker.opus.core.designsystem.dial.DialState
import dev.sebastiano.clockblocker.opus.core.model.AdviceType
import dev.sebastiano.clockblocker.opus.core.model.BodyRingMode
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.floats.plusOrMinus
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.element
import io.kotest.property.arbitrary.numericFloat
import io.kotest.property.checkAll
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.math.atan2

class TwoSkiesSpecTest {

    private val labels = DefaultDialLabels(is24Hour = true)
    private val palette = DialPalettes.Light

    /** Tokyo, 15:20, body 7 h behind: avoiding light until 16:30, then melatonin, then sleep. */
    private val tokyo = DialState(
        instant = Instant.parse("2026-03-11T06:20:00Z"),
        displayZoneId = "Asia/Tokyo",
        localMinute = 15 * 60f + 20f,
        bodyAheadMinutes = -420f,
        cbtMinBodyMinute = 270f,
        arcs = persistentListOf(
            DialArc("avoid", AdviceType.AvoidLight, 13 * 60f + 30f, 180f),
            DialArc("mel", AdviceType.Melatonin, 16 * 60f + 30f, 0f),
            DialArc("sleep", AdviceType.Sleep, 23 * 60f, 480f),
        ),
        sunriseMinute = 345f,
        sunsetMinute = 1035f,
    )

    private fun spec(
        state: DialState = tokyo,
        side: Float = 328f,
        scrub: Float = 0f,
        ahead: Float = state.bodyAheadMinutes,
        mode: BodyRingMode = BodyRingMode.Simple,
        w: Float = side,
        h: Float = side,
    ) = TwoSkies.spec(state, palette, labels, w, h, scrubMinutes = scrub, bodyAheadMinutes = ahead, mode = mode)

    @Test
    fun `the level comes from the smaller side`() {
        spec(side = 328f).level shouldBe DetailLevel.Full
        spec(side = 180f).level shouldBe DetailLevel.Simple
        spec(side = 96f).level shouldBe DetailLevel.Glance
        spec(w = 400f, h = 200f).level shouldBe DetailLevel.Simple
    }

    @Test
    fun `simple body ring is the local sky turned by the jet lag`() {
        // Body 7 h behind: the body's night starts 7 h after the local one.
        val night = BodySky.nightInLocal(tokyo, BodyRingMode.Simple)
        night.start shouldBe ((1035f + 420f).mod(1440f) plusOrMinus 0.01f)
        night.end shouldBe ((345f + 420f).mod(1440f) plusOrMinus 0.01f)
    }

    @Test
    fun `precise body ring is the planner's biological night in local time`() {
        val night = BodySky.nightInLocal(tokyo, BodyRingMode.Precise)
        night.start shouldBe (tokyo.localMinuteForBody(tokyo.biologicalNightStartBodyMinute) plusOrMinus 0.01f)
        night.end shouldBe (tokyo.localMinuteForBody(tokyo.biologicalNightEndBodyMinute) plusOrMinus 0.01f)
    }

    @Test
    fun `once adapted the two skies are identical`() {
        val adapted = tokyo.copy(bodyAheadMinutes = 0f)
        val rings = spec(adapted).ops.filterIsInstance<DialOp.SweepRing>()
        val local = rings.single { it.part == DialPart.LocalSky }
        val body = rings.single { it.part == DialPart.BodySky }
        (0 until 360 step 5).forEach { deg -> body.colorAt(deg.toFloat()) shouldBe local.colorAt(deg.toFloat()) }
    }

    @Test
    fun `the body sky turns by the jet lag`() = runTest {
        checkAll(Arb.numericFloat(-719f, 719f)) { ahead ->
            val body = spec(ahead = ahead).ops.filterIsInstance<DialOp.SweepRing>().single { it.part == DialPart.BodySky }
            body.rotationDeg shouldBe (-ahead / 4f plusOrMinus 0.01f)
        }
    }

    @Test
    fun `the needle points at the displayed minute`() = runTest {
        checkAll(Arb.numericFloat(0f, 1439f), Arb.numericFloat(-480f, 959f), Arb.numericFloat(60f, 600f)) { now, scrub, side ->
            val state = tokyo.copy(localMinute = now)
            val s = spec(state, side = side, scrub = scrub)
            val needle = s.ops.filterIsInstance<DialOp.Line>().last { it.part == DialPart.Needle }
            val deg = Math.toDegrees(atan2((needle.y1 - needle.y0).toDouble(), (needle.x1 - needle.x0).toDouble())).toFloat().mod(360f)
            val expected = DialGeometry.angleForMinute(now + scrub)
            DialGeometry.angleDelta(expected, deg) shouldBe (0f plusOrMinus 0.05f)
        }
    }

    @Test
    fun `every mark stays inside the box`() = runTest {
        checkAll(
            Arb.numericFloat(0f, 1439f),
            Arb.numericFloat(-719f, 719f),
            Arb.numericFloat(48f, 640f),
            Arb.numericFloat(1f, 1.6f),
            Arb.element(BodyRingMode.entries),
        ) { now, ahead, side, aspect, mode ->
            val w = side * aspect
            val s = spec(tokyo.copy(localMinute = now, bodyAheadMinutes = ahead), w = w, h = side, mode = mode)
            s.ops.forEach { op -> extent(op).forEach { (x, y) -> inside(x, y, w, side) shouldBe true } }
        }
    }

    @Test
    fun `the hub sits inside the body ring`() {
        DetailLevel.entries.zip(listOf(96f, 180f, 328f)).forEach { (_, side) ->
            val s = spec(side = side)
            val body = s.ops.filterIsInstance<DialOp.SweepRing>().single { it.part == DialPart.BodySky }
            (s.hubRadius <= body.r - body.width / 2f) shouldBe true
            TwoSkies.hubRadius(side) shouldBe (s.hubRadius plusOrMinus 0.001f)
        }
    }

    @Test
    fun `the advice in focus is the block under the hand and the next one after it`() {
        val focus = tokyo.focusAt(tokyo.localMinute)
        focus.current?.adviceId shouldBe "avoid"
        focus.next?.adviceId shouldBe "mel"
    }

    @Test
    fun `with nothing under the hand the next block is in focus`() {
        val focus = tokyo.focusAt(18 * 60f)
        focus.current.shouldBeNull()
        focus.next?.adviceId shouldBe "sleep"
    }

    @Test
    fun `overlapping blocks favour the higher-priority type`() {
        val state = tokyo.copy(
            arcs = persistentListOf(
                DialArc("sleep", AdviceType.Sleep, 14 * 60f, 120f),
                DialArc("avoid", AdviceType.AvoidLight, 13 * 60f, 240f),
            ),
        )
        state.focusAt(15 * 60f).current?.adviceId shouldBe "avoid"
    }

    @Test
    fun `the full dial narrates the advice on the rim and labels both skies`() {
        val texts = spec().ops.mapNotNull {
            when (it) {
                is DialOp.CurvedText -> it.text
                is DialOp.Text -> it.text
                else -> null
            }
        }
        texts shouldContain "Avoid light until 16:30"
        texts shouldContain "then take melatonin"
        texts shouldContain "TOKYO NIGHT"
        texts shouldContain "YOUR BODY\u2019S NIGHT"
        texts shouldContain "15:20"
        texts shouldContain "08:20 body"
        texts shouldContain "7 h behind"
    }

    @Test
    fun `a dial that names no place keeps the body's labels and drops the place's`() {
        for (side in listOf(328f, 180f)) {
            val texts = TwoSkies.spec(tokyo, palette, labels, side, side, namePlace = false).ops.mapNotNull {
                when (it) {
                    is DialOp.CurvedText -> it.text
                    is DialOp.Text -> it.text
                    else -> null
                }
            }
            texts.none { it.contains("TOKYO", ignoreCase = true) } shouldBe true
            texts.any { it.contains("BODY") } shouldBe true
        }
    }

    @Test
    fun `a host's text floor keeps the AM PM marker readable, the digits giving way`() {
        val twelve = DefaultDialLabels(is24Hour = false)
        for (side in listOf(60f, 72f, 88f, 100f)) {
            val texts = TwoSkies.spec(tokyo, palette, twelve, side, side, minText = 7f).ops.filterIsInstance<DialOp.Text>()
            texts.forEach { (it.spec.size >= 7f) shouldBe true }
            // Still one group: digits and marker side by side within the glance's width.
            val marker = texts.single { it.text == twelve.marker(tokyo.localMinute) }
            val digits = texts.single { it.text == twelve.time(tokyo.localMinute) }
            (marker.x >= digits.x + ApproxTextMeasurer.width(digits.text, digits.spec) - 0.01f) shouldBe true
        }
        // Without a floor the app's own sizes stay as they were.
        val app = TwoSkies.spec(tokyo, palette, twelve, 72f, 72f).ops.filterIsInstance<DialOp.Text>()
        app.single { it.text == twelve.marker(tokyo.localMinute) }.spec.size shouldBe (6.5f * 72f / 88f plusOrMinus 0.5f)
    }

    @Test
    fun `a host's label floor sets the ring labels, and a ring too thin for them drops them`() {
        fun ringLabels(side: Float, floor: Float) =
            TwoSkies.spec(tokyo, palette, labels, side, side, labelText = floor).ops.filterIsInstance<DialOp.CurvedText>()
                .filter { it.part == DialPart.RingLabel }
        for (side in listOf(160f, 328f)) {
            val shown = ringLabels(side, 10f)
            (shown.isNotEmpty()) shouldBe true
            shown.forEach { (it.spec.size >= 10f) shouldBe true }
        }
        // 13 dp text (10 sp at 1.3×) can't sit inside the 9 dp rings of a 110 dp dial: no label rather than a tiny one.
        ringLabels(110f, 13f).shouldBeEmpty()
        // Without a floor the app keeps its own sizes.
        ringLabels(160f, 0f).forEach { it.spec.size shouldBe (7.6f plusOrMinus 0.01f) }
    }

    @Test
    fun `a host's label floor sets the centre readouts, dropping in sync, then AM PM, never the body time`() {
        val twelve = DefaultDialLabels(is24Hour = false)
        for (side in listOf(68f, 88f, 120f, 160f)) for (floor in listOf(10f, 13f)) {
            val texts = TwoSkies.spec(tokyo, palette, twelve, side, side, minText = 7f, labelText = floor)
                .ops.filterIsInstance<DialOp.Text>().filter { it.part == DialPart.Readout }
            texts.forEach { (it.spec.size >= floor - 0.01f) shouldBe true }
            // The body time stays, with or without its AM/PM.
            texts.any { it.live?.clock == LiveClock.Body && it.text.startsWith(twelve.time(tokyo.bodyMinute)) } shouldBe true
        }
        // In a 68 dp glance at 13 dp, "in sync" can't fit under the local time: it goes rather than shrinks.
        val adapted = tokyo.copy(bodyAheadMinutes = 0f)
        TwoSkies.spec(adapted, palette, twelve, 68f, 68f, minText = 7f, labelText = 13f).ops.filterIsInstance<DialOp.Text>()
            .none { it.text == twelve.inSync() } shouldBe true
    }

    @Test
    fun `a host that rewrites the time keeps room before the AM PM marker for the widest reading`() {
        val twelve = DefaultDialLabels(is24Hour = false)
        for (side in listOf(88f, 160f, 328f)) {
            val texts = TwoSkies.spec(tokyo, palette, twelve, side, side, liveReadouts = true).ops.filterIsInstance<DialOp.Text>()
            val digits = texts.single { it.text == twelve.time(tokyo.localMinute) }
            val marker = texts.single { it.text == twelve.marker(tokyo.localMinute) }
            // The digits end at the marker, so a longer reading grows away from it…
            digits.h shouldBe HAlign.End
            (marker.x >= digits.x) shouldBe true
            // …and there's room for "10:00" before it, inside the group's width.
            val widest = ApproxTextMeasurer.width(twelve.time(10 * 60f), digits.spec)
            (digits.x - widest >= side / 2f - (marker.x + ApproxTextMeasurer.width(marker.text, marker.spec) - side / 2f) - 0.5f) shouldBe true
        }
    }

    @Test
    fun `the simple dial's two ring labels never share a sector, so they can't read as one phrase`() = runTest {
        checkAll(
            Arb.numericFloat(0f, 1439f),
            Arb.element(0f, 0f, -60f, 45f, -420f, 300f),
            Arb.element(110f, 140f, 160f, 200f, 249f),
            Arb.element(0f, 10f, 13f),
        ) { now, ahead, side, floor ->
            val shown = TwoSkies.spec(tokyo.copy(localMinute = now, bodyAheadMinutes = ahead), palette, labels, side, side, labelText = floor)
                .ops.filterIsInstance<DialOp.CurvedText>().filter { it.part == DialPart.RingLabel }
            val spans = shown.map { label ->
                val half = Math.toDegrees((ApproxTextMeasurer.width(label.text, label.spec) / label.r).toDouble()).toFloat() / 2f
                label.centerDeg to half
            }
            spans.forEachIndexed { i, (a, ha) ->
                spans.drop(i + 1).forEach { (b, hb) -> (kotlin.math.abs(DialGeometry.angleDelta(a, b)) >= ha + hb) shouldBe true }
            }
        }
        // Adapted: the skies match, so the body's label would sit right under the place's. It goes to the other half.
        val adapted = TwoSkies.spec(tokyo.copy(bodyAheadMinutes = 0f), palette, labels, 160f, 160f, labelText = 10f)
            .ops.filterIsInstance<DialOp.CurvedText>().filter { it.part == DialPart.RingLabel }
        val place = adapted.single { it.text == "TOKYO" }
        adapted.forEach { if (it !== place) (kotlin.math.abs(DialGeometry.angleDelta(place.centerDeg, it.centerDeg)) > 90f) shouldBe true }
    }

    @Test
    fun `upcoming advice is narrated with its start when nothing is on`() {
        val texts = spec(scrub = 18 * 60f - tokyo.localMinute).ops.filterIsInstance<DialOp.CurvedText>().map { it.text }
        texts shouldContain "Sleep at 23:00"
    }

    @Test
    fun `a block running past the window is narrated with its real end`() {
        val long = tokyo.copy(arcs = persistentListOf(DialArc("f", AdviceType.AvoidLight, 14 * 60f, 16 * 60f + 40f, narratedEndMinute = 9 * 60f)))
        spec(long).ops.filterIsInstance<DialOp.CurvedText>().map { it.text } shouldContain "Avoid light until 09:00"
    }

    @Test
    fun `advice that starts before the block in focus ends is narrated with its start, not as then`() {
        // Noon on a flight that lands at 08:10 tomorrow; sleep at 16:00 comes first, so "then sleep" would be wrong.
        val flight = tokyo.copy(
            localMinute = 12 * 60f,
            arcs = persistentListOf(
                DialArc("flight", AdviceType.Flight, 10 * 60f, 22 * 60f + 10f),
                DialArc("sleep", AdviceType.Sleep, 16 * 60f, 480f),
            ),
        )
        val texts = spec(flight).ops.filterIsInstance<DialOp.CurvedText>().map { it.text }
        texts.filter { it.startsWith("then") }.shouldBeEmpty()
        texts shouldContain "Sleep at 16:00"
    }

    @Test
    fun `advice inside a block of 24 h or more is narrated with its start, not as then`() {
        // Noon, an hour into a 24 h 30 min flight (11:00 today to 11:30 tomorrow); sleep starts at 18:00 on board.
        // On the face the flight "runs" 30 min (it wraps), so only real time can tell sleep comes first.
        val takeOff = Instant.parse("2026-03-11T02:00:00Z") // 11:00 Tokyo
        val long = tokyo.copy(
            localMinute = 12 * 60f,
            arcs = persistentListOf(
                DialArc(
                    "flight", AdviceType.Flight, 11 * 60f, 1440f, narratedEndMinute = 11 * 60f + 30f,
                    startInstant = takeOff, endInstant = takeOff.plusSeconds((24 * 60 + 30) * 60L),
                ),
                DialArc(
                    "sleep", AdviceType.Sleep, 18 * 60f, 480f,
                    startInstant = takeOff.plusSeconds(7 * 3600L), endInstant = takeOff.plusSeconds(15 * 3600L),
                ),
            ),
        )
        val texts = spec(long).ops.filterIsInstance<DialOp.CurvedText>().map { it.text }
        texts.filter { it.startsWith("then") }.shouldBeEmpty()
        texts shouldContain "Sleep at 18:00"
    }

    @Test
    fun `advice before the block's end across a fall-back change is narrated with its start, not as then`() {
        // New York, 1 Nov 2026: clocks fall back at 02:00 EDT to 01:00 EST. A block from 00:30 EDT to 01:30 EST runs
        // 2 h; sleep at 01:45 EDT starts inside it, though 01:45 on the face is after the block's 01:30 end.
        val start = Instant.parse("2026-11-01T04:30:00Z") // 00:30 EDT
        val state = tokyo.copy(
            instant = start.plusSeconds(15 * 60L),
            displayZoneId = "America/New_York",
            localMinute = 45f,
            arcs = persistentListOf(
                DialArc(
                    "avoid", AdviceType.AvoidLight, 30f, 120f, narratedEndMinute = 90f,
                    startInstant = start, endInstant = Instant.parse("2026-11-01T06:30:00Z"), // 01:30 EST
                ),
                DialArc(
                    "sleep", AdviceType.Sleep, 105f, 480f,
                    startInstant = Instant.parse("2026-11-01T05:45:00Z"), endInstant = Instant.parse("2026-11-01T13:45:00Z"), // 01:45 EDT
                ),
            ),
        )
        val texts = spec(state).ops.filterIsInstance<DialOp.CurvedText>().map { it.text }
        texts.filter { it.startsWith("then") }.shouldBeEmpty()
        texts shouldContain "Sleep at 01:45"
    }

    @Test
    fun `a moment is in focus when it is due`() {
        val focus = tokyo.focusAt(16 * 60f + 30f)
        focus.current?.adviceId shouldBe "mel"
        focus.next?.adviceId shouldBe "sleep"
        val texts = spec(scrub = 16 * 60f + 30f - tokyo.localMinute).ops.filterIsInstance<DialOp.CurvedText>().map { it.text }
        texts shouldContain "Take melatonin at 16:30"
        texts shouldContain "then sleep"
    }

    @Test
    fun `a block about to start stays next until it starts`() {
        // 16:29:45: the 16:30 melatonin moment is due, but a block starting at 16:30 must not vanish meanwhile.
        val state = tokyo.copy(arcs = persistentListOf(DialArc("light", AdviceType.SeeBrightLight, 16 * 60f + 30f, 60f)))
        state.focusAt(16 * 60f + 29.75f).next?.adviceId shouldBe "light"
    }

    @Test
    fun `sky rings are sampled once, not on every frame`() {
        val a = spec(scrub = 10f).ops.filterIsInstance<DialOp.SweepRing>()
        val b = spec(scrub = 200f, ahead = -300f).ops.filterIsInstance<DialOp.SweepRing>()
        a.zip(b).forEach { (x, y) -> (x.colors === y.colors) shouldBe true }
    }

    @Test
    fun `scrubbing to the end of the window never brings past advice back as next`() {
        // 05:20 tomorrow, 14 h ahead of now: the 13:30 avoid light block was 16 h earlier, not 8 h later.
        val focus = tokyo.focusAt(5 * 60f + 20f)
        focus.current?.adviceId shouldBe "sleep"
        focus.next.shouldBeNull()
    }

    @Test
    fun `ring labels step aside from the needle`() {
        // 11:30 is the middle of Tokyo's day: the needle would run straight through "TOKYO DAY".
        val noon = tokyo.copy(localMinute = 690f)
        val label = spec(noon).ops.filterIsInstance<DialOp.CurvedText>().single { it.text == "TOKYO DAY" }
        val half = Math.toDegrees((ApproxTextMeasurer.width(label.text, label.spec) / label.r).toDouble()).toFloat() / 2f
        val gap = kotlin.math.abs(DialGeometry.angleDelta(label.centerDeg, DialGeometry.angleForMinute(690f)))
        (gap > half) shouldBe true
    }

    @Test
    fun `the narration reads in order on the lower half too`() {
        // 23:40, avoiding light until 01:00, then sleep: the rim text sits on the lower half, read right to left
        // round the dial, so "then sleep" must come after (counter-clockwise of) the narration.
        val night = tokyo.copy(
            localMinute = 23 * 60f + 40f,
            arcs = persistentListOf(
                DialArc("avoid", AdviceType.AvoidLight, 22 * 60f, 180f),
                DialArc("sleep", AdviceType.Sleep, 60f, 420f),
            ),
        )
        val rim = spec(night).ops.filterIsInstance<DialOp.CurvedText>().filter { it.part == DialPart.Narration }
        val say = rim.single { it.text == "Avoid light until 01:00" }
        val then = rim.single { it.text == "then sleep" }
        say.readsInward shouldBe true
        then.readsInward shouldBe true
        (DialGeometry.angleDelta(say.centerDeg, then.centerDeg) < 0f) shouldBe true
    }

    @Test
    fun `body time is slanted and local time upright`() {
        val ops = spec().ops.filterIsInstance<DialOp.Text>()
        ops.single { it.text == "15:20" }.spec.slanted shouldBe false
        ops.single { it.text == "08:20 body" }.spec.slanted shouldBe true
    }

    @Test
    fun `glance keeps only the two skies, the needle and the readouts`() {
        val s = spec(side = 96f)
        s.ops.filter { it.part == DialPart.Advice || it.part == DialPart.Narration || it.part == DialPart.RingLabel }.shouldBeEmpty()
        s.ops.filterIsInstance<DialOp.Text>().map { it.text } shouldContain "08:20"
        spec(tokyo.copy(bodyAheadMinutes = 0f), side = 96f).ops.filterIsInstance<DialOp.Text>().map { it.text } shouldContain "in sync"
    }

    @Test
    fun `scrubbing marks where now is`() {
        spec().ops.filter { it.part == DialPart.NowMark }.shouldBeEmpty()
        spec(scrub = 120f).ops.filter { it.part == DialPart.NowMark } shouldNotBe emptyList<DialOp>()
    }

    @Test
    fun `twelve-hour clocks use their own numerals and times`() {
        val s = TwoSkies.spec(tokyo, palette, DefaultDialLabels(is24Hour = false), 328f, 328f)
        val texts = s.ops.filterIsInstance<DialOp.Text>().map { it.text }
        texts shouldContain "3:20"
        texts shouldContain "PM"
        texts shouldContain "12p"
        s.ops.filterIsInstance<DialOp.CurvedText>().map { it.text } shouldContain "Avoid light until 4:30 PM"
    }

    @Test
    fun `twelve-hour clocks keep AM and PM at every size`() {
        listOf(180f, 96f).forEach { side ->
            val s = TwoSkies.spec(tokyo, palette, DefaultDialLabels(is24Hour = false), side, side)
            val texts = s.ops.filterIsInstance<DialOp.Text>().filter { it.part == DialPart.Readout }.map { it.text }
            texts shouldContain "3:20"
            texts shouldContain "PM"
            texts shouldContain "8:20 AM" // the body clock too: no pill or numerals to tell morning from evening
        }
    }

    @Test
    fun `large text never runs the local time into the side numerals`() = runTest {
        checkAll(Arb.numericFloat(0f, 1439f), Arb.element(true, false)) { minute, is24 ->
            val l = DefaultDialLabels(is24Hour = is24)
            val s = TwoSkies.spec(tokyo.copy(localMinute = minute), palette, l, 328f, 328f, textGrowth = 1.15f)
            val texts = s.ops.filterIsInstance<DialOp.Text>()
            val left = texts.single { it.part == DialPart.Numeral && it.text == l.numeral(6) }.span().second
            val right = texts.single { it.part == DialPart.Numeral && it.text == l.numeral(18) }.span().first
            val time = texts.filter { it.part == DialPart.Readout && (it.text == l.time(minute) || it.text == l.marker(minute)) }
            (time.minOf { it.span().first } > left) shouldBe true
            (time.maxOf { it.span().second } < right) shouldBe true
        }
    }

    private fun DialOp.Text.span(): Pair<Float, Float> {
        val w = ApproxTextMeasurer.width(text, spec)
        return when (h) {
            HAlign.Start -> x to x + w
            HAlign.Center -> x - w / 2f to x + w / 2f
            HAlign.End -> x - w to x
        }
    }

    private fun inside(x: Float, y: Float, w: Float, h: Float): Boolean =
        x >= -0.01f && y >= -0.01f && x <= w + 0.01f && y <= h + 0.01f

    /** Extreme points of an op (circles and arcs as their outer bounding box, text by its approximate box). */
    private fun extent(op: DialOp): List<Pair<Float, Float>> = when (op) {
        is DialOp.Circle -> box(op.cx, op.cy, op.r + op.stroke / 2f)
        is DialOp.Arc -> box(op.cx, op.cy, op.r + op.width / 2f)
        is DialOp.SweepRing -> box(op.cx, op.cy, op.r + op.width / 2f)
        is DialOp.Line -> listOf(op.x0 to op.y0, op.x1 to op.y1)
        is DialOp.Rect -> listOf(op.left to op.top, op.right to op.bottom)
        is DialOp.SkyBar -> listOf(op.left to op.top, op.right to op.bottom)
        is DialOp.Glyph -> box(op.cx, op.cy, maxOf(op.discRadius, op.size / 2f))
        is DialOp.Text -> {
            val half = ApproxTextMeasurer.width(op.text, op.spec) / 2f
            listOf(op.x - half to op.y - op.spec.size / 2f, op.x + half to op.y + op.spec.size / 2f)
        }
        is DialOp.CurvedText -> box(op.cx, op.cy, op.r + op.spec.size * 0.6f)
    }

    private fun box(cx: Float, cy: Float, r: Float) = listOf(cx - r to cy - r, cx + r to cy + r)
}
