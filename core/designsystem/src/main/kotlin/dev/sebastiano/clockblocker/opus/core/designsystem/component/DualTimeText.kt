package dev.sebastiano.clockblocker.opus.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import dev.sebastiano.clockblocker.opus.core.designsystem.R
import dev.sebastiano.clockblocker.opus.core.designsystem.theme.ClockblockTheme
import dev.sebastiano.clockblocker.opus.core.designsystem.time.cityName
import dev.sebastiano.clockblocker.opus.core.designsystem.time.rememberTimeFormatter
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * Calendar-day difference of [instant] seen in [secondary] versus [primary]: +1 when the secondary zone is
 * already on the next day, −1 when it is still on the previous one.
 */
fun dayDelta(instant: Instant, primary: ZoneId, secondary: ZoneId): Int {
    val a = instant.atZone(primary).toLocalDate()
    val b = instant.atZone(secondary).toLocalDate()
    return ChronoUnit.DAYS.between(a, b).toInt()
}

private const val NoBreakSpace = "\u00A0"

/** "+1" / "−1" (true minus sign) or null when on the same day. */
fun dayDeltaSuffix(delta: Int): String? = when {
    delta == 0 -> null
    delta > 0 -> "+$delta"
    else -> "\u2212${-delta}"
}

/**
 * Local time big, a second zone small (design.md: "times show local time plus a secondary zone"). The
 * secondary line reads `07:00 Lisbon −1` with a day-delta suffix when the zones are on different dates.
 * Follows the system 12/24-hour setting. TalkBack reads one merged phrase: "14:00, 07:00 in Lisbon".
 *
 * Use [inline] for list rows (`14:00 · 07:00 Lisbon`), stacked otherwise. Inline, the secondary part moves to
 * its own line as a whole when it doesn't fit (large font), and its day suffix never wraps away from the city.
 * [prefix] ("until") stays on the primary time's line and leads the spoken phrase.
 */
@Composable
fun DualTimeText(
    instant: Instant,
    zone: ZoneId,
    secondaryZone: ZoneId?,
    modifier: Modifier = Modifier,
    style: TextStyle = ClockblockTheme.textStyles.timeTitle,
    secondaryStyle: TextStyle = ClockblockTheme.textStyles.timeLabel,
    secondaryLabel: String? = secondaryZone?.cityName(),
    color: Color = LocalContentColor.current,
    secondaryColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    inline: Boolean = false,
    prefix: String? = null,
    prefixStyle: TextStyle = MaterialTheme.typography.titleMedium,
) {
    val formatter = rememberTimeFormatter()
    val primary = formatter.formatFull(instant.atZone(zone).toLocalTime())
    val secondary = secondaryZone?.let { formatter.formatFull(instant.atZone(it).toLocalTime()) }
    val suffix = secondaryZone?.let { dayDeltaSuffix(dayDelta(instant, zone, it)) }
    val secondaryText = secondary?.let {
        listOfNotNull(it, listOfNotNull(secondaryLabel, suffix).joinToString(NoBreakSpace).ifEmpty { null }).joinToString(" ")
    }
    val description = if (secondary != null) {
        stringResource(
            R.string.dual_time_description,
            primary,
            listOfNotNull(secondary, suffix?.let { dayWord(it) }).joinToString(" "),
            secondaryLabel ?: secondaryZone.id,
        )
    } else {
        primary
    }
    val spoken = listOfNotNull(prefix, description).joinToString(" ")
    val semantics = Modifier.clearAndSetSemantics { contentDescription = spoken }
    if (inline) {
        FlowRow(modifier.then(semantics), itemVerticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.alignByBaseline(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (prefix != null) Text(prefix, style = prefixStyle, color = color, modifier = Modifier.alignByBaseline())
                Text(primary, style = style, color = color, modifier = Modifier.alignByBaseline())
            }
            if (secondaryText != null) Text(secondaryText, style = secondaryStyle, color = secondaryColor, modifier = Modifier.alignByBaseline())
        }
    } else {
        Column(modifier.then(semantics)) {
            Text(primary, style = style, color = color)
            if (secondaryText != null) Text(secondaryText, style = secondaryStyle, color = secondaryColor)
        }
    }
}

@Composable
private fun dayWord(suffix: String): String =
    stringResource(if (suffix.startsWith("+")) R.string.day_delta_next else R.string.day_delta_previous)
