package dev.sebastiano.clockblocker.opus.widget.rc

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.sebastiano.clockblocker.opus.core.designsystem.dial.spec.DetailLevel
import dev.sebastiano.clockblocker.opus.core.designsystem.dial.spec.DialOp
import dev.sebastiano.clockblocker.opus.core.designsystem.dial.spec.DialPart
import dev.sebastiano.clockblocker.opus.core.designsystem.dial.spec.DialSpec
import dev.sebastiano.clockblocker.opus.core.designsystem.dial.spec.HAlign
import dev.sebastiano.clockblocker.opus.core.designsystem.dial.spec.LiveClock
import dev.sebastiano.clockblocker.opus.core.designsystem.dial.spec.VAlign
import dev.sebastiano.clockblocker.opus.widget.draw.WidgetPalette
import dev.sebastiano.clockblocker.opus.widget.draw.WidgetTheme
import dev.sebastiano.clockblocker.opus.widget.preview.DemoPlans
import dev.sebastiano.clockblocker.opus.widget.state.WidgetState
import dev.sebastiano.clockblocker.opus.widget.state.WidgetStateMapper
import dev.sebastiano.clockblocker.opus.widget.text.DialType
import dev.sebastiano.clockblocker.opus.widget.text.WidgetTexts
import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Duration
import java.time.Instant

/**
 * The widget dial ([WidgetDial]): each Two Clocks bucket draws the design it was made for, laid out for its dial
 * region at the bucket's minimum ([LabelFit.dialBox]), and its text stays whole, inside the region and clear of the
 * other text at every density and font scale up to 1.3, in 12- and 24-hour time, measured in the system font the
 * canvas draws with ([DialType]).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [37], qualifiers = "xxhdpi")
class WidgetDialFitTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val now = Instant.parse("2026-10-06T06:20:00Z")
    private val palette = WidgetPalette.of(WidgetTheme.Light)

    private fun state(scenario: DemoPlans.Scenario, at: Instant = now): WidgetState.Active = WidgetStateMapper.map(
        DemoPlans.lisbonTokyo(now, scenario),
        at,
        route = DemoPlans.ROUTE,
        placeNames = DemoPlans.PLACE_NAMES,
        places = DemoPlans.PLACES,
    ) as WidgetState.Active

    private fun design(layout: TwoClocksLayout) = when (layout) {
        TwoClocksLayout.Compact, TwoClocksLayout.Strip, TwoClocksLayout.Wide -> DialDesign.TwoStrips
        TwoClocksLayout.Square, TwoClocksLayout.Tall, TwoClocksLayout.Large -> DialDesign.TwoSkies
    }

    private fun spec(state: WidgetState.Active, bucket: Bucket<TwoClocksLayout>, is24: Boolean): DialSpec {
        val texts = WidgetTexts.from(context, state, is24)
        val box = LabelFit.dialBox(texts, bucket.layout, bucket.fitAt, LabelFit.twoClocks(context, texts, bucket.layout, bucket.fitAt))
        return WidgetDial.spec(context, state, palette, is24, design(bucket.layout), box.width, box.height)
    }

    @Test
    fun `the strips draw the small and wide buckets, the two skies the rest`() {
        val cells = WidgetSizes.docsCells().toMap()
        fun layout(name: String) = WidgetSizes.pick(WidgetSizes.TWO_CLOCKS, cells.getValue(name)).layout
        mapOf(
            "1×1 portrait" to DialDesign.TwoStrips,
            "2×1 portrait" to DialDesign.TwoStrips,
            "2×1 landscape" to DialDesign.TwoStrips,
            "4×1 portrait" to DialDesign.TwoStrips,
            "4×1 landscape" to DialDesign.TwoStrips,
            "2×2 landscape" to DialDesign.TwoStrips,
            "2×2 portrait" to DialDesign.TwoSkies,
            "2×3 portrait" to DialDesign.TwoSkies,
            "4×3 portrait" to DialDesign.TwoSkies,
        ).forEach { (name, expected) -> withClue(name) { design(layout(name)) shouldBe expected } }
        withClue("2×1 portrait") { layout("2×1 portrait") shouldBe TwoClocksLayout.Strip }
        withClue("4×1 portrait") { layout("4×1 portrait") shouldBe TwoClocksLayout.Strip }
    }

    @Test
    fun `the level follows the dial region, not the bucket`() {
        val state = state(DemoPlans.Scenario.AvoidLight)
        val levels = WidgetSizes.TWO_CLOCKS.associate { "${it.layout} ${it.min}" to spec(state, it, is24 = true).level }
        withClue(levels.toString()) {
            // A wide row has room for the bar labels; every narrower region is a glance.
            levels.getValue("Strip 250×51 dp") shouldBe DetailLevel.Simple
            levels.getValue("Strip 250×84 dp") shouldBe DetailLevel.Simple
            levels.getValue("Compact 57×51 dp") shouldBe DetailLevel.Glance
            levels.getValue("Strip 117×84 dp") shouldBe DetailLevel.Glance
        }
    }

    @Test
    fun `the needle and both readings show in every bucket`() {
        val state = state(DemoPlans.Scenario.AvoidLight)
        WidgetSizes.TWO_CLOCKS.forEach { bucket ->
            withClue("${bucket.layout} ${bucket.min}") {
                val spec = spec(state, bucket, is24 = false)
                spec.ops.filter { it.part == DialPart.Needle }.shouldNotBeEmpty()
                val live = spec.ops.filterIsInstance<DialOp.Text>().mapNotNull { it.live?.clock }.toSet()
                live.size shouldBe 2
            }
        }
    }

    @Test
    fun `a redacted widget names no place on its dial`() {
        val full = state(DemoPlans.Scenario.AvoidLight)
        val redacted = WidgetStateMapper.redact(full) as WidgetState.Active
        val names = listOf("Tokyo", "Lisbon", "HND", "LIS")
        WidgetSizes.TWO_CLOCKS.forEach { bucket ->
            val texts = spec(redacted, bucket, is24 = true).ops.mapNotNull {
                when (it) {
                    is DialOp.Text -> it.text
                    is DialOp.CurvedText -> it.text
                    else -> null
                }
            }
            withClue("${bucket.layout} ${bucket.min}: $texts") { texts.none { t -> names.any { t.contains(it) } } shouldBe true }
        }
    }

    @Test
    fun `dial text stays whole, readable, inside its region and clear of other text, at every display`() {
        val failures = sortedSetOf<String>()
        val scenarios = DemoPlans.Scenario.entries
        // Through the day: times of every width, and the advice in focus changing.
        val hours = listOf(0L, 5L, 11L, 17L)
        for (density in DENSITIES) {
            RuntimeEnvironment.setQualifiers("+$density")
            for (scale in SCALES) {
                RuntimeEnvironment.setFontScale(scale)
                for (scenario in scenarios) for (hour in hours) for (is24 in listOf(true, false)) {
                    val state = state(scenario, now.plus(Duration.ofHours(hour)))
                    for (redact in listOf(false, true)) {
                        val s = if (redact) WidgetStateMapper.redact(state) as WidgetState.Active else state
                        WidgetSizes.TWO_CLOCKS.forEach { bucket ->
                            val where = "$scenario +${hour}h ${if (is24) "24h" else "12h"}${if (redact) " redacted" else ""} " +
                                "@${scale}x $density ${bucket.layout} ${bucket.min}"
                            failures += problems(spec(s, bucket, is24), design(bucket.layout), is24, s.dial.isAligned).map { "$where: $it" }
                        }
                    }
                }
            }
        }
        withClue(failures.take(40).joinToString("\n")) { failures.shouldBeEmpty() }
    }

    @Test
    fun `12-hour dial text fits with other locales' AM PM markers`() {
        val failures = sortedSetOf<String>()
        // British "am"/"pm", Spanish "a. m."/"p. m." (with a no-break space), Korean "오전"/"오후" before noon and after.
        for (locale in listOf("en-rGB", "es-rES", "ko-rKR")) {
            RuntimeEnvironment.setQualifiers("$locale-xhdpi")
            for (scale in SCALES) {
                RuntimeEnvironment.setFontScale(scale)
                for (scenario in DemoPlans.Scenario.entries) for (hour in listOf(0L, 5L, 11L, 17L)) {
                    val state = state(scenario, now.plus(Duration.ofHours(hour)))
                    WidgetSizes.TWO_CLOCKS.forEach { bucket ->
                        val where = "$locale $scenario +${hour}h @${scale}x ${bucket.layout} ${bucket.min}"
                        failures += problems(spec(state, bucket, is24 = false), design(bucket.layout), false, state.dial.isAligned)
                            .map { "$where: $it" }
                    }
                }
            }
        }
        withClue(failures.take(40).joinToString("\n")) { failures.shouldBeEmpty() }
    }

    private class Box(val left: Float, val top: Float, val right: Float, val bottom: Float) {
        fun overlaps(o: Box) = left < o.right - SLACK && o.left < right - SLACK && top < o.bottom - SLACK && o.top < bottom - SLACK
        override fun toString() = "[%.1f, %.1f – %.1f, %.1f]".format(left, top, right, bottom)
    }

    /** The ink box of [t] as the canvas draws it: its measured width, and a line of its size round its anchor. */
    private fun box(t: DialOp.Text, type: DialType): Box {
        val w = type.measurer.width(t.text, t.spec)
        val x0 = when (t.h) {
            HAlign.Start -> t.x
            HAlign.Center -> t.x - w / 2f
            HAlign.End -> t.x - w
        }
        val size = t.spec.size
        val cy = when (t.v) {
            VAlign.Top -> t.y + size / 2f
            VAlign.Center -> t.y
            VAlign.Baseline -> t.y - size * BASELINE_TO_MIDDLE
            VAlign.Bottom -> t.y - size / 2f
        }
        return Box(x0, cy - size / 2f, x0 + w, cy + size / 2f)
    }

    private fun problems(spec: DialSpec, design: DialDesign, is24: Boolean, aligned: Boolean): List<String> = buildList {
        val type = DialType.of(context)
        val texts = spec.ops.filterIsInstance<DialOp.Text>().filter { it.text.isNotBlank() }
        val boxes = texts.map { it to box(it, type) }
        boxes.forEach { (t, b) ->
            if (b.left < -SLACK || b.top < -SLACK || b.right > spec.width + SLACK || b.bottom > spec.height + SLACK) {
                add("\"${t.text}\" $b leaves the ${spec.width}×${spec.height} region")
            }
            if (t.spec.size < WidgetDial.MIN_TEXT_DP - 0.01f) add("\"${t.text}\" at ${t.spec.size} dp")
        }
        boxes.forEachIndexed { i, (a, ab) ->
            boxes.drop(i + 1).forEach { (b, bb) -> if (ab.overlaps(bb)) add("\"${a.text}\" $ab overlaps \"${b.text}\" $bb") }
        }
        // Never a glyph alone: on the strips the glyph sits beside (or under) its name.
        if (design == DialDesign.TwoStrips) {
            spec.ops.filterIsInstance<DialOp.Glyph>().forEach { g ->
                val name = WidgetDial.labels(context, is24).advice(g.type)
                val named = texts.any { it.text.contains(name, ignoreCase = true) }
                if (!named) add("the ${g.type} glyph shows without its name")
            }
        }
        // Labels (every word that isn't a time) are readable: at least 10 sp at the font scale, or not there at all.
        val floor = WidgetDial.labelTextDp(context) - 0.01f
        texts.filter { it.part != DialPart.Readout && it.part != DialPart.Needle }.forEach { t ->
            if (t.spec.size < floor) add("label \"${t.text}\" at ${t.spec.size} dp, under the ${floor + 0.01f} dp floor")
        }
        val curved = spec.ops.filterIsInstance<DialOp.CurvedText>()
        curved.forEach { t -> if (t.spec.size < floor) add("label \"${t.text}\" at ${t.spec.size} dp, under the ${floor + 0.01f} dp floor") }
        // The readouts too: the local time's AM/PM and the body time (or "in sync") are never under the floor.
        texts.filter { it.part == DialPart.Readout }.forEach { t ->
            if (t.spec.size < floor) add("readout \"${t.text}\" at ${t.spec.size} dp, under the ${floor + 0.01f} dp floor")
        }
        // The body time always shows beside the local time, so the two skies stay told apart ("in sync" may go).
        val clocks = texts.mapNotNull { it.live?.clock }.toSet()
        if (LiveClock.Local !in clocks) add("no local time")
        if (!aligned && LiveClock.Body !in clocks) add("no body time")
        // …and sit inside their ring or bar.
        val rings = spec.ops.filterIsInstance<DialOp.SweepRing>()
        curved.filter { it.part == DialPart.RingLabel }.forEach { t ->
            val ring = rings.firstOrNull { kotlin.math.abs(it.r - t.r) < 0.01f }
            if (ring == null || t.spec.size * CAP_HEIGHT > ring.width) add("ring label \"${t.text}\" at ${t.spec.size} dp is too big for its ring")
        }
        val bars = spec.ops.filterIsInstance<DialOp.SkyBar>()
        texts.filter { it.part == DialPart.RingLabel }.forEach { t ->
            val bar = bars.firstOrNull { t.y > it.top && t.y < it.bottom }
            if (bar == null || t.spec.size * CAP_HEIGHT > bar.bottom - bar.top) add("bar label \"${t.text}\" at ${t.spec.size} dp is too big for its bar")
        }
    }

    private companion object {
        val DENSITIES = listOf("mdpi", "xhdpi", "xxhdpi")
        val SCALES = listOf(1f, 1.3f)

        /** Text may graze its region by this much (anti-aliasing, the measurer's rounding). */
        const val SLACK = 0.5f

        /** From the baseline up to the middle of a line of text, as a share of its size. */
        const val BASELINE_TO_MIDDLE = 0.35f

        /** Capital height per dp of text size in the system font. */
        const val CAP_HEIGHT = 0.71f
    }
}
