package dev.sebastiano.clockblocker.opus.feature.plan

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.LazyListLayoutInfo
import androidx.compose.ui.unit.IntSize
import dev.sebastiano.clockblocker.opus.feature.plan.PlanFixtures.realPlan
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class RailDayInViewTest {

    private val now = PlanFixtures.MidAdaptation
    private val rows = buildRailRows(realPlan.railDays(now, emptyMap()), now, showEarlier = true)

    private fun item(index: Int, offset: Int, size: Int) = object : LazyListItemInfo {
        override val index = index
        override val key: Any = index
        override val offset = offset
        override val size = size
    }

    /** A 900 px viewport (reading line at 300) with [items] laid out; list index = row index + 1 (the title). */
    private fun layout(vararg items: LazyListItemInfo) = object : LazyListLayoutInfo {
        override val visibleItemsInfo = items.toList()
        override val viewportStartOffset = 0
        override val viewportEndOffset = 900
        override val totalItemsCount = rows.size + 1
        override val viewportSize = IntSize(600, 900)
        override val orientation = Orientation.Vertical
        override val reverseLayout = false
        override val beforeContentPadding = 0
        override val afterContentPadding = 0
        override val mainAxisItemSpacing = 0
    }

    private fun headerRow(day: Int) = rows.indexOfFirst { it is RailRow.Header && it.day.day.index == day }

    @Test
    fun `the day is the one whose row crosses the reading line`() {
        val day3 = headerRow(3)
        // Day 3's header at the top, its first block across the line.
        rows.dayInView(layout(item(day3 + 1, 0, 120), item(day3 + 2, 120, 240), item(day3 + 3, 360, 240)), firstRow = 1, atEnd = false) shouldBe 3
    }

    @Test
    fun `a pinned sticky header doesn't hide the row under the line`() {
        val day4 = headerRow(4)
        // Day 3's header is pinned at the top while Day 4's rows (higher indices) fill the viewport from 100 px.
        val pinned = item(headerRow(3) + 1, 0, 120)
        rows.dayInView(layout(pinned, item(day4 + 1, 100, 120), item(day4 + 2, 220, 400)), firstRow = 1, atEnd = false) shouldBe 4
    }

    @Test
    fun `at the end of the list it is the last day, however short`() {
        val day3 = headerRow(3)
        val day4 = headerRow(4)
        val shown = layout(item(day3 + 2, 0, 500), item(day4 + 1, 500, 120), item(day4 + 2, 620, 200))
        rows.dayInView(shown, firstRow = 1, atEnd = false) shouldBe 3
        rows.dayInView(shown, firstRow = 1, atEnd = true) shouldBe 4
    }

    @Test
    fun `nothing laid out, or the title under the line, is no day`() {
        rows.dayInView(layout(), firstRow = 1, atEnd = false).shouldBeNull()
        rows.dayInView(layout(item(0, 0, 900)), firstRow = 1, atEnd = false).shouldBeNull()
    }
}
