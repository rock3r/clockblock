package dev.sebastiano.clockblocker.opus.widget.rc

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.sebastiano.clockblocker.opus.core.model.AdviceType
import dev.sebastiano.clockblocker.opus.widget.R
import dev.sebastiano.clockblocker.opus.widget.preview.DemoPlans
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
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
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

    /** Every advice label, "Plan step", and the states, with the longest times and the given place. */
    private fun cases(is24: Boolean, place: String): List<Case> {
        val time = if (is24) "23:30" else "11:30 PM"
        val until = str(R.string.widget_until, time)
        val secondary = str(R.string.widget_secondary_time, time, place)
        val longest = WidgetTextFactory(context, is24).label(AdviceType.SeeBrightLight)
        val active = base(is24, DemoPlans.Scenario.AvoidLight)
        val done = active.done ?: DoneText("trip", "advice", null, str(R.string.widget_done), "Mark as done")
        fun advice(label: String, type: AdviceType) = active.copy(
            title = label,
            dialTitle = label,
            subtitleLines = listOf(until, str(R.string.widget_then, label)),
            subtitle = listOf(until, str(R.string.widget_then, label)).joinToString(" · "),
            secondary = secondary,
            dialDetail = until,
            done = done,
            upcoming = List(3) { UpcomingText(type, label, time, secondary) },
        )
        val factory = WidgetTextFactory(context, is24)
        val labels = AdviceType.entries.map { Case(factory.label(it), advice(factory.label(it), it)) } +
            Case(str(R.string.widget_advice_redacted), advice(str(R.string.widget_advice_redacted), AdviceType.Melatonin))
        val free = base(is24, DemoPlans.Scenario.FreeTime).let { t ->
            t.copy(
                subtitleLines = listOf(until, str(R.string.widget_then, longest)),
                subtitle = listOf(until, str(R.string.widget_then, longest)).joinToString(" · "),
                secondary = secondary,
                dialDetail = until,
                upcoming = List(3) { UpcomingText(AdviceType.SeeBrightLight, longest, time, secondary) },
            )
        }
        val adapted = base(is24, DemoPlans.Scenario.Adapted).let { t ->
            val line = str(R.string.widget_adapted_subtitle, place)
            t.copy(subtitleLines = listOf(line), subtitle = line, dialDetail = line)
        }
        return labels + listOf(
            Case("Free time", free),
            Case("Clockblocked", adapted),
            Case("No trip", base(is24, scenario = null)),
        )
    }

    @Test
    fun `every label fits every bucket at font scale 1 and 1_3`() {
        val failures = mutableListOf<String>()
        for (scale in listOf(1f, 1.3f)) {
            RuntimeEnvironment.setFontScale(scale)
            for (is24 in listOf(true, false)) {
                for (place in listOf("Lisbon", "San Francisco")) {
                    for (case in cases(is24, place)) {
                        val where = "${case.name} @${scale}x ${if (is24) "24h" else "12h"} $place"
                        NextUpLayout.entries.forEach { layout ->
                            failures += check(LabelFit.nextUp(context, case.texts, layout), case.texts, "$where Next up $layout")
                        }
                        TwoClocksLayout.entries.forEach { layout ->
                            failures += check(LabelFit.twoClocks(context, case.texts, layout), case.texts, "$where Two Clocks $layout")
                        }
                    }
                }
            }
        }
        withClue(failures.joinToString("\n")) { failures.shouldBeEmpty() }
    }

    @Test
    fun `the label slot keeps its full size and room for up next at the default font scale`() {
        val texts = base(is24 = true, DemoPlans.Scenario.AvoidLight)
        LabelFit.nextUp(context, texts, NextUpLayout.Square).now!!.title.sp shouldBe LabelFit.TITLE_SP
        LabelFit.nextUp(context, texts, NextUpLayout.Tall).upNext.rows.size shouldBe UP_NEXT_ROWS
        // "05:00 See some light" never fits a third of a 4×2: the ribbon shows the next two whole instead.
        LabelFit.nextUp(context, texts, NextUpLayout.Ribbon).upNext.rows.size shouldBe 2
        LabelFit.twoClocks(context, texts, TwoClocksLayout.Large).upNext.rows.size shouldBe UP_NEXT_ROWS
    }

    @Test
    fun `then goes first`() {
        val texts = cases(is24 = false, place = "San Francisco").first { it.name == "See bright light" }.texts
        val medium = LabelFit.nextUp(context, texts, NextUpLayout.Medium).now!!
        medium.details.map { it.text } shouldBe listOf(texts.subtitleLines.first())
        medium.secondary?.text shouldBe texts.secondary
        medium.title.text shouldBe texts.title
    }

    private fun check(fit: WidgetFit, texts: WidgetTexts, where: String): List<String> = buildList {
        fit.smallLabel?.let { sp ->
            val measured = TextFit.measure(context, texts.title, TextFit.SMALL_CONTENT_WIDTH_DP, sp, maxLines = 2, semibold = true)
            if (!measured.fits) add("$where: 1×1 label \"${texts.title}\" does not fit at $sp sp")
        }
        fit.now?.let { now ->
            if (!now.fits) add("$where: no layout fits (${now.describe()})")
            if (now.title.text != texts.title) add("$where: label replaced by \"${now.title.text}\"")
            now.secondary?.takeIf { it.sp < LabelFit.SECONDARY_LAST_RESORT_SP }?.let { add("$where: other zone's time at ${it.sp} sp") }
            if (now.title.sp < LabelFit.TITLE_MIN_SP) add("$where: label at ${now.title.sp} sp, below the floor")
            if (!now.title.fits) add("$where: label \"${texts.title}\" clipped at ${now.title.sp} sp")
            if (now.details.firstOrNull()?.text?.startsWith(texts.subtitleLines.first()) != true) {
                add("$where: the \"until\" line is missing (${now.describe()})")
            }
            now.details.filterNot { it.fits }.forEach { add("$where: \"${it.text}\" clipped") }
            if (texts.secondary != null && now.secondary == null) add("$where: the other zone's time was dropped")
            now.secondary?.takeUnless { it.fits }?.let { add("$where: \"${it.text}\" clipped") }
        }
        fit.caption?.let { caption ->
            if (!caption.title.fits) add("$where: caption \"${caption.title.text}\" clipped")
            caption.detail?.takeUnless { it.fits }?.let { add("$where: caption \"${it.text}\" clipped") }
        }
        fit.upNext.rows.forEach { row ->
            if (!row.line.fits || !row.line.text.contains(row.item.label)) add("$where: up next \"${row.line.text}\" clipped")
            row.secondary?.takeUnless { it.fits }?.let { add("$where: up next \"${it.text}\" clipped") }
        }
    }

    private fun NowFit.describe() = "title ${title.sp} sp × ${title.lines}, details ${details.map { "${it.text}@${it.sp}" }}, " +
        "secondary ${secondary?.let { "${it.text}@${it.sp}" }}, glyph $glyph, countdown $countdown, height $heightDp"
}
