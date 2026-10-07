package dev.sebastiano.clockblocker.opus.core.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.sebastiano.clockblocker.opus.core.designsystem.dial.DialState
import dev.sebastiano.clockblocker.opus.core.designsystem.dial.StaticTwoSkiesDial
import dev.sebastiano.clockblocker.opus.core.designsystem.dial.toDialState
import dev.sebastiano.clockblocker.opus.core.model.AdaptationStrategy
import dev.sebastiano.clockblocker.opus.core.model.JetLagPlan
import dev.sebastiano.clockblocker.opus.core.model.PhasePoint
import dev.sebastiano.clockblocker.opus.core.model.Place
import dev.sebastiano.clockblocker.opus.core.model.ShiftDirection
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * The sky rings under Tromsø's sun (69.7° N) through the year: polar night, a short winter day, a short spring night
 * and the midnight sun. The body is 3 h behind, so the body ring shows the same sky turned.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [37], qualifiers = "w760dp-h720dp-xhdpi")
class DialPolarScreenshotTest : ScreenshotTest() {

    @Test
    fun polar() = snap("dial_polar") { Seasons() }

    @Test
    fun polarDark() = snap("dial_polar_dark", darkTheme = true) { Seasons() }
}

private val Oslo: ZoneId = ZoneId.of("Europe/Oslo")
private val Tromso = Place("TOS", "Tromsø Airport", "Tromsø", "NO", Oslo.id, 69.683, 18.919)

/** 13:00 in Tromsø on [date], with the body clock 3 h behind local time and no advice. */
private fun tromso(date: String): DialState {
    val at = LocalDateTime.parse("${date}T13:00").atZone(Oslo).toInstant()
    val body = Oslo.rules.getOffset(at).totalSeconds / 60 - 180
    val plan = JetLagPlan(
        tripId = "tromso",
        generatedAt = Instant.EPOCH,
        strategy = AdaptationStrategy.Adapt,
        direction = ShiftDirection.Advance,
        shiftHours = 3.0,
        originZoneId = "Atlantic/Azores",
        destinationZoneId = Oslo.id,
        days = emptyList(),
        phase = listOf(PhasePoint(at, body, at.minusSeconds(10L * 3600))),
        estimatedDaysToAdapt = 2.0,
        estimatedDaysWithoutPlan = 3.0,
    )
    return plan.toDialState(at, Oslo, Tromso)
}

private val SeasonDates = listOf(
    "2026-12-21" to "21 Dec · polar night",
    "2026-02-10" to "10 Feb · 6 h 40 min day",
    "2026-05-01" to "1 May · 5 h night",
    "2026-06-21" to "21 Jun · midnight sun",
)

@Composable
private fun Seasons() {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SeasonDates.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                row.forEach { (date, caption) ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        StaticTwoSkiesDial(tromso(date), Modifier.size(280.dp))
                        Text(caption, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
