package dev.sebastiano.clockblocker.opus.widget.rc

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.sebastiano.clockblocker.opus.core.model.AdviceType
import dev.sebastiano.clockblocker.opus.widget.R
import dev.sebastiano.clockblocker.opus.widget.preview.DemoPlans
import dev.sebastiano.clockblocker.opus.widget.state.PlaceNames
import dev.sebastiano.clockblocker.opus.widget.state.WidgetState
import dev.sebastiano.clockblocker.opus.widget.state.WidgetStateMapper
import dev.sebastiano.clockblocker.opus.widget.text.DoneText
import dev.sebastiano.clockblocker.opus.widget.text.TextFit
import dev.sebastiano.clockblocker.opus.widget.text.UpcomingText
import dev.sebastiano.clockblocker.opus.widget.text.WidgetTextFactory
import dev.sebastiano.clockblocker.opus.widget.text.WidgetTexts
import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldStartWith
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.Instant

/**
 * The no-clip guarantee: every label a widget can show, in every size bucket, at font scale 1.0 and 1.3, fits its
 * slot whole (no ellipsis, no word broken) at or above its floor size, measured with the renderer's own metrics
 * ([LabelFit] / [TextFit], the player's `StaticLayout` setup). The label and the other zone's time always stay; the
 * "then …" tail, the countdown, the glyph and "Up next" entries are what give way.
 *
 * Worst cases on purpose: 12-hour times ("11:30 PM"), a long place name, a Done button and a live countdown.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [37], qualifiers = "xxhdpi")
class WidgetLabelFitTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val now = Instant.parse("2026-10-06T06:20:00Z")

    private fun str(id: Int, vararg args: Any) = context.getString(id, *args)

    private fun base(is24: Boolean, scenario: DemoPlans.Scenario?): WidgetTexts {
        val state = scenario?.let {
            WidgetStateMapper.map(DemoPlans.lisbonTokyo(now, it), now, route = DemoPlans.ROUTE, placeNames = DemoPlans.PLACE_NAMES)
        } ?: WidgetState.NoTrip
        return WidgetTexts.from(context, state, is24)
    }

    private class Case(val name: String, val texts: WidgetTexts)

    /** A trip's place in the other zone: its city and airport code. */
    private data class TestPlace(val city: String, val code: String) {
        override fun toString() = city
    }

    /**
     * The places to fit: two common ones, then the longest cities in the bundled airport data (`places.tsv`), which
     * only fit through their shorter forms ([PlaceNames.options]).
     */
    private val places: List<TestPlace> by lazy {
        val longest = File("../core/data/src/main/assets/places.tsv").readLines()
            .filterNot { it.startsWith("#") || it.isBlank() }
            .map { it.split('\t') }
            .map { TestPlace(city = it[2], code = it[0]) }
            .sortedByDescending { it.city.length }
            .take(LONGEST_PLACES)
        listOf(LISBON, SAN_FRANCISCO) + longest
    }

    /** Every advice label, "Plan step", and the states, with the longest times and the given place. */
    private fun cases(is24: Boolean, place: TestPlace): List<Case> {
        val time = if (is24) "23:30" else "11:30 PM"
        val until = str(R.string.widget_until, time)
        val names = PlaceNames.options(place.city, place.code)
        val forms = names.map { str(R.string.widget_secondary_time, time, it) }
        val secondary = forms.first()
        val secondaryShort = forms.drop(1)
        val untilCompact = str(R.string.widget_until_compact, until, str(R.string.widget_secondary_compact, time, names.last()))
        val longest = WidgetTextFactory(context, is24).label(AdviceType.SeeBrightLight)
        val active = base(is24, DemoPlans.Scenario.AvoidLight)
        val done = active.done ?: DoneText("trip", "advice", null, str(R.string.widget_done), "Mark as done")
        fun advice(label: String, type: AdviceType) = active.copy(
            title = label,
            dialTitle = label,
            subtitleLines = listOf(until, str(R.string.widget_then, label)),
            subtitle = listOf(until, str(R.string.widget_then, label)).joinToString(" · "),
            secondary = secondary,
            secondaryShort = secondaryShort,
            untilCompact = untilCompact,
            dialDetail = until,
            done = done,
            upcoming = List(3) { UpcomingText(type, label, time, secondary, secondaryShort = secondaryShort) },
        )
        val factory = WidgetTextFactory(context, is24)
        val labels = AdviceType.entries.map { Case(factory.label(it), advice(factory.label(it), it)) } +
            Case(str(R.string.widget_advice_redacted), advice(str(R.string.widget_advice_redacted), AdviceType.Melatonin))
        val free = base(is24, DemoPlans.Scenario.FreeTime).let { t ->
            t.copy(
                subtitleLines = listOf(until, str(R.string.widget_then, longest)),
                subtitle = listOf(until, str(R.string.widget_then, longest)).joinToString(" · "),
                secondary = secondary,
                secondaryShort = secondaryShort,
                untilCompact = untilCompact,
                dialDetail = until,
                upcoming = List(3) { UpcomingText(AdviceType.SeeBrightLight, longest, time, secondary, secondaryShort = secondaryShort) },
            )
        }
        val adapted = base(is24, DemoPlans.Scenario.Adapted).let { t ->
            val line = str(R.string.widget_adapted_subtitle, place.city)
            t.copy(subtitleLines = listOf(line), subtitle = line, dialDetail = line)
        }
        return labels + listOf(
            Case("Free time", free),
            Case("Clockblocked", adapted),
            Case("No trip", base(is24, scenario = null)),
        )
    }

    /** Android's smallest text setting, the default, and the largest scale the widgets promise to fit. */
    private val scales = listOf(0.85f, 1f, 1.3f)

    /** The widest text the 1×1 countdown shows: what [NextUpRemote] sizes it for. */
    private val smallCountdown = HostText.countdownWidest(23 * 60 + 59, compact = true)

    private fun nextUp(texts: WidgetTexts, bucket: Bucket<NextUpLayout>) =
        LabelFit.nextUp(context, texts, bucket.layout, bucket.fitAt, smallCountdown.takeIf { texts.countdownEnd != null })

    private fun twoClocks(texts: WidgetTexts, bucket: Bucket<TwoClocksLayout>) =
        LabelFit.twoClocks(context, texts, bucket.layout, bucket.fitAt)

    /** Densities to measure at: text snaps to whole pixels, so a label that just fits at one may not at another. */
    private val densities = listOf("mdpi", "xhdpi", "420dpi", "xxhdpi", "xxxhdpi")

    /** Runs [block] at every density in [densities] and every font scale in [scales]. */
    private fun everyDisplay(block: (where: String) -> Unit) {
        for (density in densities) {
            RuntimeEnvironment.setQualifiers("+$density")
            for (scale in scales) {
                RuntimeEnvironment.setFontScale(scale)
                block("@${scale}x $density")
            }
        }
    }

    @Test
    fun `every label fits every bucket at its minimum size, at every font scale up to 1_3`() {
        val failures = mutableListOf<String>()
        everyDisplay { display ->
            for (is24 in listOf(true, false)) {
                for (place in places) {
                    for (case in cases(is24, place)) {
                        val where = "${case.name} $display ${if (is24) "24h" else "12h"} $place"
                        WidgetSizes.NEXT_UP.forEach { bucket ->
                            failures += check(nextUp(case.texts, bucket), case.texts, "$where Next up ${bucket.layout} ${bucket.min}")
                        }
                        WidgetSizes.TWO_CLOCKS.forEach { bucket ->
                            failures += check(twoClocks(case.texts, bucket), case.texts, "$where Two Clocks ${bucket.layout} ${bucket.min}")
                        }
                    }
                }
            }
        }
        withClue(failures.joinToString("\n")) { failures.shouldBeEmpty() }
    }

    @Test
    fun `up next rows give way before the now block loses its until line or the other zone's time`() {
        val failures = sortedSetOf<String>()
        everyDisplay { display ->
            for (place in places) for (case in cases(is24 = false, place)) {
                fun verify(fit: WidgetFit, where: String) {
                    val now = fit.now ?: return
                    if (!now.whole && (fit.upNext.rows.isNotEmpty() || fit.upNext.capsules)) {
                        failures += "${case.name} $display $place $where: ${fit.upNext.rows.size} up next rows, now ${now.describe()}"
                    }
                }
                WidgetSizes.NEXT_UP.forEach { verify(nextUp(case.texts, it), "Next up ${it.layout} ${it.min}") }
                WidgetSizes.TWO_CLOCKS.forEach { verify(twoClocks(case.texts, it), "Two Clocks ${it.layout} ${it.min}") }
            }
        }
        withClue(failures.joinToString("\n")) { failures.shouldBeEmpty() }
    }

    @Test
    fun `the other zone's time drops only at the tightest minimums at the largest font scale`() {
        val failures = sortedSetOf<String>()
        everyDisplay { display ->
            if (display.startsWith("@1.3x")) return@everyDisplay
            for (place in places) for (case in cases(is24 = false, place)) {
                fun verify(fit: WidgetFit, where: String) {
                    if (fit.now?.secondaryDropped == true) failures += "${case.name} $display $place $where"
                }
                WidgetSizes.NEXT_UP.forEach { verify(nextUp(case.texts, it), "Next up ${it.layout} ${it.min}") }
                WidgetSizes.TWO_CLOCKS.forEach { verify(twoClocks(case.texts, it), "Two Clocks ${it.layout} ${it.min}") }
            }
        }
        withClue(failures.joinToString("\n")) { failures.shouldBeEmpty() }
    }

    @Test
    fun `every Done label fits every bucket at its minimum size`() {
        val failures = mutableListOf<String>()
        val texts = cases(is24 = false, place = SAN_FRANCISCO).first { it.name == "See bright light" }.texts
        val labels = listOf(R.string.widget_done, R.string.widget_done_logged, R.string.widget_skipped).map { str(it) }
        everyDisplay { display ->
            for (label in labels) {
                val withLabel = texts.copy(done = texts.done!!.copy(label = label))
                val where = "\"$label\" $display"
                WidgetSizes.NEXT_UP.forEach { failures += check(nextUp(withLabel, it), withLabel, "$where Next up ${it.layout} ${it.min}") }
                WidgetSizes.TWO_CLOCKS.forEach { failures += check(twoClocks(withLabel, it), withLabel, "$where Two Clocks ${it.layout} ${it.min}") }
            }
        }
        withClue(failures.joinToString("\n")) { failures.shouldBeEmpty() }
    }

    @Test
    fun `every launcher cell gets a bucket no larger than itself`() {
        fun CellDp.within(cell: CellDp) = width <= cell.width && height <= cell.height
        val failures = WidgetSizes.docsCells().flatMap { (name, cell) ->
            buildList {
                if (!WidgetSizes.FLOOR.within(cell)) add("$name ($cell) is below the floor ${WidgetSizes.FLOOR}")
                WidgetSizes.pick(WidgetSizes.NEXT_UP, cell).let { if (!it.min.within(cell)) add("$name ($cell): Next up ${it.layout} needs ${it.min}") }
                WidgetSizes.pick(WidgetSizes.TWO_CLOCKS, cell).let { if (!it.min.within(cell)) add("$name ($cell): Two Clocks ${it.layout} needs ${it.min}") }
            }
        }
        withClue(failures.joinToString("\n")) { failures.shouldBeEmpty() }
    }

    @Test
    fun `launcher cells get the layout they were designed for`() {
        val cells = WidgetSizes.docsCells().toMap()
        fun nextUp(name: String) = WidgetSizes.pick(WidgetSizes.NEXT_UP, cells.getValue(name)).layout
        fun twoClocks(name: String) = WidgetSizes.pick(WidgetSizes.TWO_CLOCKS, cells.getValue(name)).layout
        mapOf(
            "1×1 portrait" to NextUpLayout.Small,
            "1×1 landscape" to NextUpLayout.Small,
            "2×1 portrait" to NextUpLayout.Medium,
            "4×1 portrait" to NextUpLayout.Wide,
            "2×1 landscape" to NextUpLayout.Wide,
            "4×1 landscape" to NextUpLayout.Wide,
            "2×2 portrait" to NextUpLayout.Square,
            "4×2 portrait" to NextUpLayout.Ribbon,
            "2×2 landscape" to NextUpLayout.Ribbon,
            "2×3 portrait" to NextUpLayout.Tall,
            "4×3 portrait" to NextUpLayout.Tall,
            "4×4 landscape" to NextUpLayout.Tall,
        ).forEach { (name, layout) -> withClue("Next up $name") { nextUp(name) shouldBe layout } }
        mapOf(
            "1×1 portrait" to TwoClocksLayout.Compact,
            "1×1 landscape" to TwoClocksLayout.Compact,
            "2×2 portrait" to TwoClocksLayout.Square,
            "2×3 portrait" to TwoClocksLayout.Tall,
            "2×2 landscape" to TwoClocksLayout.Wide,
            "3×2 landscape" to TwoClocksLayout.Wide,
            "4×3 portrait" to TwoClocksLayout.Large,
            "4×4 landscape" to TwoClocksLayout.Large,
        ).forEach { (name, layout) -> withClue("Two Clocks $name") { twoClocks(name) shouldBe layout } }
    }

    @Test
    fun `pick takes the closest bucket that fits, else the smallest`() {
        val buckets = listOf(Bucket("a", CellDp(50f, 50f)), Bucket("b", CellDp(100f, 50f)), Bucket("c", CellDp(100f, 100f)))
        WidgetSizes.pick(buckets, CellDp(120f, 60f)).layout shouldBe "b"
        WidgetSizes.pick(buckets, CellDp(60f, 200f)).layout shouldBe "a"
        WidgetSizes.pick(buckets, CellDp(100f, 100f)).layout shouldBe "c"
        WidgetSizes.pick(buckets, CellDp(30f, 30f)).layout shouldBe "a"
        // The host's rounding slack: a layout fits a widget up to just under 1 dp smaller than it.
        WidgetSizes.pick(buckets, CellDp(99.5f, 99.5f)).layout shouldBe "c"
        WidgetSizes.pick(buckets, CellDp(99f, 99f)).layout shouldBe "a"
        Bucket("c", CellDp(100f, 100f)).fitAt shouldBe CellDp(99f, 99f)
    }

    /** The bucket a launcher cell from [WidgetSizes.docsCells] gets, e.g. "2×3 portrait". */
    private fun <L> at(buckets: List<Bucket<L>>, cell: String) =
        WidgetSizes.pick(buckets, WidgetSizes.docsCells().toMap().getValue(cell))

    @Test
    fun `common cells keep the full label size and room for up next at the default font scale`() {
        val texts = base(is24 = true, DemoPlans.Scenario.AvoidLight)
        nextUp(texts, at(WidgetSizes.NEXT_UP, "2×2 portrait")).now!!.title.sp shouldBe LabelFit.TITLE_SP
        nextUp(texts, at(WidgetSizes.NEXT_UP, "2×3 portrait")).upNext.rows.size shouldBe UP_NEXT_ROWS
        // "05:00 See some light" never fits a third of a 4×2: the ribbon shows the next two whole instead.
        nextUp(texts, at(WidgetSizes.NEXT_UP, "4×2 portrait")).upNext.rows.size shouldBe 2
        twoClocks(texts, at(WidgetSizes.TWO_CLOCKS, "4×3 portrait")).upNext.rows.size shouldBe UP_NEXT_ROWS
    }

    @Test
    fun `then goes first`() {
        val texts = cases(is24 = false, place = SAN_FRANCISCO).first { it.name == "See bright light" }.texts
        val medium = nextUp(texts, at(WidgetSizes.NEXT_UP, "2×1 portrait")).now!!
        medium.details.map { it.text } shouldBe listOf(texts.subtitleLines.first())
        medium.secondary?.text shouldBe texts.secondary
        medium.title.text shouldBe texts.title
    }

    @Test
    fun `a short row keeps the label and the local until line, and the other zone's time joins it while it fits`() {
        val texts = cases(is24 = false, place = SAN_FRANCISCO).first { it.name == "See bright light" }.texts
        val until = texts.subtitleLines.first()
        val wide = WidgetSizes.smallest(WidgetSizes.NEXT_UP, NextUpLayout.Wide)
        nextUp(texts, wide).now!!.let { row ->
            row.fits shouldBe true
            row.title.text shouldBe texts.title
            row.secondaryDropped shouldBe false
            (row.details.single().text == texts.untilCompact || row.secondary != null) shouldBe true
            row.details.single().text shouldStartWith until
        }
        // At 1.3× the tightest row keeps "See bright light / until 11:30 PM": local time beats the other zone's.
        RuntimeEnvironment.setFontScale(1.3f)
        nextUp(texts, wide).now!!.let { row ->
            row.fits shouldBe true
            row.title.text shouldBe texts.title
            row.details.single().text shouldStartWith until
        }
    }

    @Test
    fun `the header strip keeps the route when the place is too long to fit`() {
        RuntimeEnvironment.setFontScale(1.3f)
        val texts = cases(is24 = false, place = SAN_FRANCISCO).first { it.name == "See bright light" }.texts
        val large = WidgetSizes.smallest(WidgetSizes.TWO_CLOCKS, TwoClocksLayout.Large)
        twoClocks(texts, large).let { fit ->
            fit.headerStrip?.text shouldBe texts.header
            fit.headerRoute shouldBe true
        }
        // No header fits even across the whole strip: the fixed-width route still shows alone.
        val long = texts.copy(header = "Llanfairpwllgwyngyllgogerychwyrndrobwllllantysiliogogogoch · Day 2")
        twoClocks(long, large).let { fit ->
            fit.headerStrip shouldBe null
            fit.headerRoute shouldBe true
        }
    }

    @Test
    fun `the 1x1 tile shows the label whole, with the glyph when there is room`() {
        val texts = cases(is24 = true, place = LISBON).first { it.name == "Sleep" }.texts
        val small = nextUp(texts, WidgetSizes.smallest(WidgetSizes.NEXT_UP, NextUpLayout.Small)).small!!
        small.fits shouldBe true
        small.glyph shouldBe true
        small.label.text shouldBe texts.title
        val long = cases(is24 = true, place = LISBON).first { it.name == "Clockblocked" }.texts
        val tile = nextUp(long, WidgetSizes.smallest(WidgetSizes.NEXT_UP, NextUpLayout.Small)).small!!
        tile.label.fits shouldBe true
        tile.label.lines shouldBe 1
    }

    private fun check(fit: WidgetFit, texts: WidgetTexts, where: String): List<String> = buildList {
        fit.small?.let { small ->
            if (!small.fits || !small.label.fits) add("$where: 1×1 label \"${texts.title}\" does not fit (${small.label.sp} sp × ${small.label.lines})")
            if (small.label.text != texts.title) add("$where: 1×1 label replaced by \"${small.label.text}\"")
            val drawnDp = TextFit.pxForSp(context, small.label.sp) / context.resources.displayMetrics.density
            if (drawnDp < LabelFit.SMALL_LABEL_MIN_DP - 0.01f) add("$where: 1×1 label drawn at $drawnDp dp")
        }
        fit.now?.let { now ->
            if (!now.fits) add("$where: no layout fits (${now.describe()})")
            if (now.title.text != texts.title) add("$where: label replaced by \"${now.title.text}\"")
            now.secondary?.takeIf { it.sp < LabelFit.SECONDARY_LAST_RESORT_SP }?.let { add("$where: other zone's time at ${it.sp} sp") }
            if (now.title.sp < LabelFit.TITLE_LAST_RESORT_SP) add("$where: label at ${now.title.sp} sp, below the floor")
            if (!now.title.fits) add("$where: label \"${texts.title}\" clipped at ${now.title.sp} sp")
            now.details.filterNot { it.fits }.forEach { add("$where: \"${it.text}\" clipped") }
            // Local time first: the until line is never dropped. (The adapted line, which names no time, may go.)
            val until = texts.subtitleLines.firstOrNull() ?: texts.subtitle
            if (texts.secondary != null && now.details.none { it.text.startsWith(until) }) add("$where: the until line \"$until\" was dropped")
            now.details.filter { it.sp < LabelFit.DETAIL_LAST_RESORT_SP }.forEach { add("$where: \"${it.text}\" at ${it.sp} sp") }
            // The other zone's time: on its own line, joined to the until line, or (tightest minimums) flagged dropped.
            val joined = now.details.any { it.text == texts.untilCompact }
            if (texts.secondary != null && now.secondary == null && !joined && !now.secondaryDropped) {
                add("$where: the other zone's time went missing")
            }
            now.secondary?.takeIf { it.text !in texts.secondaryOptions }?.let { add("$where: other zone's time replaced by \"${it.text}\"") }
            now.secondary?.takeUnless { it.fits }?.let { add("$where: \"${it.text}\" clipped") }
            now.header?.takeUnless { it.fits }?.let { add("$where: header \"${it.text}\" clipped") }
        }
        fit.caption?.let { caption ->
            val title = caption.title
            if (title == null) add("$where: the caption label was dropped") else if (!title.fits) add("$where: caption \"${title.text}\" clipped")
            caption.detail?.takeUnless { it.fits }?.let { add("$where: caption \"${it.text}\" clipped") }
        }
        fit.upNext.rows.forEach { row ->
            if (!row.line.fits || !row.line.text.contains(row.item.label)) add("$where: up next \"${row.line.text}\" clipped")
            row.secondary?.takeUnless { it.fits }?.let { add("$where: up next \"${it.text}\" clipped") }
            row.secondary?.takeIf { it.text !in row.item.secondaryOptions }?.let { add("$where: up next \"${it.text}\" replaced") }
        }
        fit.upNext.bar?.takeUnless { it.fits }?.let { add("$where: adaptation \"${it.text}\" clipped") }
        fit.done?.takeUnless { it.fits }?.let { add("$where: Done \"${it.text}\" clipped at ${it.sp} sp") }
        fit.headerStrip?.takeUnless { it.fits }?.let { add("$where: header \"${it.text}\" clipped") }
    }

    @Test
    fun `a long place shortens before anything clips, and shows in full where it fits`() {
        val cpc = TestPlace("Chapelco/San Martin de los Andes", "CPC")
        val ysq = TestPlace("Qian Gorlos Mongol Autonomous County", "YSQ")
        val medium = WidgetSizes.smallest(WidgetSizes.NEXT_UP, NextUpLayout.Medium)
        val tallerMedium = WidgetSizes.NEXT_UP.last { it.layout == NextUpLayout.Medium }
        val largest = WidgetSizes.NEXT_UP.maxBy { it.min.width * it.min.height }
        /** The other zone's time as shown: on its own line, or joined to the until line; null when it had to go. */
        fun shown(place: TestPlace, bucket: Bucket<NextUpLayout>): String? {
            val texts = cases(is24 = false, place = place).first { it.name == "See bright light" }.texts
            // The texts keep the full name; WidgetTextsTest checks that screen readers get it.
            texts.secondary shouldBe str(R.string.widget_secondary_time, "11:30 PM", place.city)
            val now = nextUp(texts, bucket).now!!
            now.fits shouldBe true
            // Whatever happens to the other zone's time, the local until line stays.
            now.details.first().text shouldStartWith texts.subtitleLines.first()
            return now.secondary?.text ?: now.details.firstOrNull { it.text == texts.untilCompact }?.text
        }

        RuntimeEnvironment.setFontScale(1f)
        shown(cpc, largest) shouldBe str(R.string.widget_secondary_time, "11:30 PM", cpc.city)
        shown(cpc, medium) shouldBe str(R.string.widget_secondary_time, "11:30 PM", "Chapelco")
        RuntimeEnvironment.setFontScale(1.3f)
        shown(cpc, tallerMedium) shouldBe str(R.string.widget_secondary_time, "11:30 PM", "CPC")
        shown(ysq, tallerMedium) shouldBe str(R.string.widget_secondary_time, "11:30 PM", "YSQ")
        // The tightest minimum at 1.3×: "See bright light / until 11:30 PM", the other zone's time gone.
        shown(cpc, medium) shouldBe null
    }

    private companion object {
        val LISBON = TestPlace("Lisbon", "LIS")
        val SAN_FRANCISCO = TestPlace("San Francisco", "SFO")

        /** How many of the longest bundled cities the matrix fits. */
        const val LONGEST_PLACES = 4
    }

    private fun NowFit.describe() = "title ${title.sp} sp × ${title.lines}, details ${details.map { "${it.text}@${it.sp}" }}, " +
        "secondary ${secondary?.let { "${it.text}@${it.sp}" }}, glyph $glyph, countdown $countdown, height $heightDp"
}
