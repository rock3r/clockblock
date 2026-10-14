package dev.sebastiano.clockblocker.opus.core.designsystem.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.sebastiano.clockblocker.opus.core.designsystem.R
import dev.sebastiano.clockblocker.opus.core.designsystem.theme.ClockblockTheme
import dev.sebastiano.clockblocker.opus.core.designsystem.theme.toHourFloat
import dev.sebastiano.clockblocker.opus.core.designsystem.time.rememberTimeFormatter
import dev.sebastiano.clockblocker.opus.core.model.AdviceType
import dev.sebastiano.clockblocker.opus.core.model.Place
import dev.sebastiano.clockblocker.opus.core.model.ZoneLabels
import java.time.Instant
import java.time.LocalTime

/**
 * One airport (or city) in a search result list: the same row in the trip editor, onboarding and Settings › Home.
 *
 * Code chip | "🇬🇧 London" over "London Heathrow Airport · United Kingdom" | day/night dot, local time, GMT offset.
 * The country sits on the second line (which may wrap to three lines) so the city never truncates. The row is one
 * TalkBack node with a single description. It draws no container: callers put it in their list or card and pass
 * a [modifier] with their clip and test tag.
 */
@Composable
fun PlaceResultRow(
    place: Place,
    now: Instant,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
) {
    val localTime = now.atZone(place.zone).toLocalTime()
    val time = rememberTimeFormatter().formatFull(localTime)
    val offset = ZoneLabels.offset(place.zone, now)
    val subtitle = placeResultSubtitle(place.name, place.city, place.countryCode)
    val parts = listOf(place.displayCode, place.city, place.name.takeUnless { it == place.city }.orEmpty(), countryName(place.countryCode))
    val description = stringResource(R.string.place_result_description, parts.filter { it.isNotBlank() }.joinToString(", "), time, offset)
    // Narrow for the font size (a phone at 1.5×): the time moves under the text so the city keeps the width.
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clickable(onClick = onClick)
            .clearAndSetSemantics {
                contentDescription = description
                role = Role.Button
                onClick { onClick(); true }
            }
            .padding(contentPadding),
        contentAlignment = Alignment.CenterStart,
    ) {
        val stacked = maxWidth < StackBelowWidth * LocalDensity.current.fontScale
        Row(verticalAlignment = Alignment.CenterVertically) {
            CodeChip(place.displayCode, Modifier.widthIn(min = 48.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    placeResultTitle(place.city.ifBlank { place.name }, place.countryCode),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subtitle.isNotBlank()) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (stacked) {
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        DayNightDot(localTime)
                        Spacer(Modifier.width(6.dp))
                        Text(time, style = ClockblockTheme.textStyles.timeLabel, color = MaterialTheme.colorScheme.onSurface)
                        Text(
                            " · $offset",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            if (!stacked) {
                Spacer(Modifier.width(8.dp))
                Column(horizontalAlignment = Alignment.End) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        DayNightDot(localTime)
                        Spacer(Modifier.width(6.dp))
                        Text(time, style = ClockblockTheme.textStyles.timeLabel, color = MaterialTheme.colorScheme.onSurface)
                    }
                    Text(offset, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

/** Below this width (scaled by the font size) the time column moves under the text. */
private val StackBelowWidth = 260.dp

/** The IATA code in a small tonal box, in the dot-matrix label face (so codes line up in result lists). */
@Composable
fun CodeChip(code: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    ) {
        Box(Modifier.padding(horizontal = 8.dp, vertical = 6.dp), contentAlignment = Alignment.Center) {
            IataCode(code, style = ClockblockTheme.textStyles.iataLabel, contentDescription = null, animateChanges = false)
        }
    }
}

/**
 * A tiny sky at [time]: the sky colour of that hour with a sun or a crescent moon. Decorative (the time next to it
 * carries the meaning).
 */
@Composable
private fun DayNightDot(time: LocalTime) {
    val hour = time.toHourFloat()
    val sky = ClockblockTheme.sky.gradientAt(hour)
    val isSun = celestialPosition(hour).isSun
    val sun = ClockblockTheme.adviceColors[AdviceType.SeeBrightLight].color
    Canvas(Modifier.size(18.dp).clearAndSetSemantics {}) {
        val r = size.minDimension / 2f
        drawCircle(sky.verticalBrush(0f, size.height), r)
        val c = center
        if (isSun) {
            drawCircle(sun, r * 0.42f, c)
        } else {
            val moon = Path().apply {
                addOval(Rect(c, r * 0.48f))
                op(this, Path().apply { addOval(Rect(c + Offset(r * 0.26f, -r * 0.2f), r * 0.42f)) }, PathOperation.Difference)
            }
            drawPath(moon, MoonColor)
        }
    }
}

private val MoonColor = Color(0xFFF4F1FF)
