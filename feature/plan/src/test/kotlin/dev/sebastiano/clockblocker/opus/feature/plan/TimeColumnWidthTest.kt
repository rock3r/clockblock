package dev.sebastiano.clockblocker.opus.feature.plan

import androidx.compose.ui.unit.dp
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/** The rail's time column with localised AM/PM markers (#110). */
class TimeColumnWidthTest {

    @Test
    fun `a marker narrower than the time leaves the column at the time's width`() {
        // "PM" under "10:58".
        timeColumnWidth(digits = 48.dp, widestMarker = 16.dp) shouldBe 60.dp
    }

    @Test
    fun `a marker wider than the time widens the column to fit it`() {
        // Tamil's "பிற்பகல்", say.
        timeColumnWidth(digits = 48.dp, widestMarker = 58.dp) shouldBe 70.dp
    }

    @Test
    fun `a phrase-sized marker widens the column only so far`() {
        // Kölsch's "Uhr vörmiddaachs": cut short on one line rather than taking the row's width.
        timeColumnWidth(digits = 48.dp, widestMarker = 150.dp) shouldBe 84.dp
    }
}
