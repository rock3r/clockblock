package dev.sebastiano.clockblocker.opus.feature.plan

import android.content.Context
import android.graphics.Bitmap
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.unit.Density
import androidx.test.core.app.ApplicationProvider
import dev.sebastiano.clockblocker.opus.core.designsystem.theme.ClockblockTheme
import dev.sebastiano.clockblocker.opus.core.testing.invalidateAllViews
import dev.sebastiano.clockblocker.opus.feature.plan.PlanFixtures.ready
import io.kotest.assertions.withClue
import io.kotest.matchers.ints.shouldBeGreaterThanOrEqual
import io.kotest.matchers.shouldBe
import kotlin.math.abs
import kotlin.math.roundToInt
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.RobolectricTestRunner

/**
 * The line joining the rail's capsules runs unbroken from one capsule to the next (owner, device QA: it stopped a few
 * pixels short after each step), through the Now marker, on the Now card and at a large font. Reads the pixels down
 * the capsules' axis between every two neighbouring capsules of a day and compares each with the strip beside it at
 * the same height: wherever they match, the line has a gap.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [37], qualifiers = "w400dp-h1600dp-xhdpi")
class RailConnectorTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `the connector is continuous between capsules in light theme`() = assertContinuous(darkTheme = false, fontScale = 1f).let {}

    @Test
    fun `the connector is continuous between capsules in dark theme`() = assertContinuous(darkTheme = true, fontScale = 1f).let {}

    @Test
    fun `the connector is continuous between capsules at a large font`() = assertContinuous(darkTheme = false, fontScale = 1.5f).let {}

    @Test
    fun `the connector runs through the now marker between blocks`() {
        // 12:00 BST on day 2 of the fake plan: after the morning's bright light, before the evening's avoid light, so
        // the rail shows the Now marker row between two blocks.
        val gaps = assertContinuous(darkTheme = false, fontScale = 1f, plan = PlanFixtures.fakePlan, at = java.time.Instant.parse("2026-06-17T11:00:00Z"), scrollTo = null)
        withClue("a Now marker sat between two checked capsules") { gaps.throughMarker shouldBe true }
    }

    private class Checked(val pairs: Int, val throughMarker: Boolean)

    private fun assertContinuous(
        darkTheme: Boolean,
        fontScale: Float,
        plan: dev.sebastiano.clockblocker.opus.core.model.JetLagPlan = PlanFixtures.realPlan,
        at: java.time.Instant = PlanFixtures.MidAdaptation,
        scrollTo: Int? = 9,
    ): Checked {
        Settings.System.putString(ApplicationProvider.getApplicationContext<Context>().contentResolver, Settings.System.TIME_12_24, "24")
        var timeColumnPx = 0f
        var density = 1f
        var nowEdge = 0
        compose.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(base.density, fontScale)) {
                ClockblockTheme(darkTheme = darkTheme, dynamicColor = false, reduceMotion = true) {
                    val d = LocalDensity.current
                    timeColumnPx = with(d) { rememberTimeColumnWidth().toPx() }
                    density = d.density
                    nowEdge = MaterialTheme.colorScheme.primary.toArgb()
                    Box(Modifier.background(MaterialTheme.colorScheme.surface)) { PlanContent(ready(at, plan = plan), PlanActions(onBack = {})) }
                }
            }
        }
        if (scrollTo != null) {
            compose.onNodeWithTag(PlanTags.Rail).performScrollToIndex(scrollTo)
        } else {
            // To the Now marker, then back a little so the block above it shows too.
            compose.onNodeWithTag(PlanTags.Rail).performScrollToNode(hasTestTag(PlanTags.NowMarker))
            compose.onNodeWithTag(PlanTags.Rail).performTouchInput { swipeDown(startY = centerY, endY = centerY + 600f, durationMillis = 1_000) }
        }
        compose.waitForIdle()
        compose.invalidateAllViews()
        val image = compose.onRoot().captureToImage().asAndroidBitmap().copy(Bitmap.Config.ARGB_8888, false)

        fun bounds(regex: Regex): List<Rect> = compose.onAllNodes(
            SemanticsMatcher("tag ~ $regex") { node -> node.config.getOrNull(SemanticsProperties.TestTag)?.matches(regex) == true },
            useUnmergedTree = true,
        ).fetchSemanticsNodes().map { it.boundsInRoot }

        val blocks = bounds(Regex("plan_block_.*"))
        val rowLeft = blocks.minOf { it.left }
        // Rows only (nested chips carry the same tag, further right), top to bottom.
        val rows = blocks.filter { abs(it.left - rowLeft) < 1f }.sortedBy { it.top }
        val markers = bounds(Regex.fromLiteral(PlanTags.NowMarker))
        val headers = bounds(Regex("plan_day_\\d+"))
        val gap = RowGap.value * density
        // The row's node is its clickable card; the time column starts after the row's inner padding.
        val railLeft = rowLeft + RowInnerPadding.value * density + timeColumnPx
        val x = (railLeft + RailColumnWidth.value * density / 2f).roundToInt()
        val besideX = (railLeft + 2f * density).roundToInt()
        // Clear of the floating toolbar at the bottom.
        val bottomLimit = image.height - 140f * density

        var pairs = 0
        var throughMarker = false
        rows.zipWithNext().forEach { (a, b) ->
            val between = markers.firstOrNull { abs(it.top - a.bottom) < 1.5f && abs(it.bottom - b.top) < 1.5f }
            if (abs(b.top - a.bottom) >= 1.5f && between == null) return@forEach // a day header (or zone switch) between
            val top = a.bottom - gap
            val bottom = b.top + gap
            if (top < 0f || bottom > bottomLimit) return@forEach
            if (headers.any { it.bottom > top && it.top < bottom }) return@forEach // under a pinned day header
            pairs++
            if (between != null) throughMarker = true
            // A pixel inside each capsule's edge either side, so the check also catches a line stopping short of it.
            for (y in (top.roundToInt() - 1)..(bottom.roundToInt())) {
                val line = image.getPixel(x, y)
                val strip = image.getPixel(besideX, y)
                // The Now card's outline runs across the rail over the line (by design): no strip to compare with.
                if (channelDelta(strip, nowEdge) < MinDelta && channelDelta(line, nowEdge) < MinDelta) continue
                withClue("connector gap at y=$y between rows at ${a.top}..${a.bottom} and ${b.top}..${b.bottom} (dark=$darkTheme, font=$fontScale)") {
                    channelDelta(line, strip) shouldBeGreaterThanOrEqual MinDelta
                }
            }
        }
        withClue("neighbouring capsules checked") { pairs shouldBeGreaterThanOrEqual 3 }
        return Checked(pairs, throughMarker)
    }

    private fun channelDelta(a: Int, b: Int): Int = maxOf(
        abs((a shr 16 and 0xFF) - (b shr 16 and 0xFF)),
        abs((a shr 8 and 0xFF) - (b shr 8 and 0xFF)),
        abs((a and 0xFF) - (b and 0xFF)),
    )

    private companion object {
        /**
         * A gap shows the strip itself (within a step or two of rounding); the line (outlineVariant) differs from the
         * strip beside it by well over this, even over the strip's darkest night tone in light theme (about 15).
         */
        const val MinDelta = 8
    }
}
