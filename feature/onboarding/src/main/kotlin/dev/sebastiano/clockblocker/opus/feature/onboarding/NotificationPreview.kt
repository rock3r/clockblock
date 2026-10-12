package dev.sebastiano.clockblocker.opus.feature.onboarding

import android.content.Context
import android.content.res.Configuration
import android.widget.FrameLayout
import android.widget.RemoteViews
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import dev.sebastiano.clockblocker.opus.core.designsystem.theme.ClockblockTheme
import dev.sebastiano.clockblocker.opus.core.designsystem.time.rememberTimeFormatter
import dev.sebastiano.clockblocker.opus.core.model.AdviceType
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.math.roundToInt
import dev.sebastiano.clockblocker.opus.core.notifications.R as NotificationsR

/** Test tags for the e2e suite and screenshot tests. */
object NotificationPreviewTags {
    const val Card = "reminders_preview"

    /** On the real Remote Compose content; absent when the step draws the standard look. */
    const val Remote = "reminders_preview_remote"
}

/**
 * A sample Now notification, as it looks in the shade: the app icon on the advice's colour, the app name with the
 * body clock, the content, and the notification's own actions. The content is the real thing when the device can play
 * it ([remote]: the Remote Compose document `:widget` posts, for a sample plan), otherwise the standard notification's
 * look drawn here with the notification's own strings and glyphs. The card reports the content's width through
 * [onContentWidth] so the document is captured to fit.
 *
 * TalkBack reads it as one description: it is an example, not a control.
 */
@Composable
internal fun NotificationPreview(remote: RemoteViews?, now: Instant, onContentWidth: (Int) -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val accent = ClockblockTheme.adviceColors[AdviceType.SeeBrightLight]
    val description = stringResource(R.string.reminders_preview_description)
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = colors.surfaceContainerHighest,
        modifier = modifier
            .fillMaxWidth()
            .testTag(NotificationPreviewTags.Card)
            .clearAndSetSemantics { contentDescription = description },
    ) {
        Row(Modifier.padding(top = 14.dp, bottom = 12.dp, end = 16.dp)) {
            Box(Modifier.width(IconColumn), contentAlignment = Alignment.TopCenter) {
                Surface(shape = CircleShape, color = accent.color, modifier = Modifier.size(36.dp)) {
                    Icon(
                        painterResource(NotificationsR.drawable.ic_notif_app),
                        contentDescription = null,
                        tint = accent.onColor,
                        modifier = Modifier.padding(7.dp),
                    )
                }
            }
            BoxWithConstraints(Modifier.weight(1f)) {
                val widthDp = maxWidth.value.roundToInt()
                LaunchedEffect(widthDp) { onContentWidth(widthDp) }
                Column {
                    Text(
                        stringResource(
                            R.string.reminders_preview_header,
                            stringResource(R.string.reminders_preview_app_name),
                            stringResource(NotificationsR.string.body_behind, stringResource(R.string.reminders_preview_body_offset)),
                        ),
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp, bottom = 10.dp),
                    )
                    var failed by remember(remote) { mutableStateOf(false) }
                    if (remote != null && !failed) {
                        RemoteContent(remote, onFailure = { failed = true })
                    } else {
                        StandardContent(now)
                    }
                    Actions()
                }
            }
        }
    }
}

/**
 * The real document, played by the platform's Remote Compose player through `RemoteViews.apply`, in the app's own
 * light or dark (the player picks its palette from the context's night mode).
 */
@Composable
private fun RemoteContent(views: RemoteViews, onFailure: () -> Unit) {
    val context = LocalContext.current
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val themed = remember(context, dark) { context.withNightMode(dark) }
    AndroidView(
        factory = { FrameLayout(themed) },
        update = { frame ->
            if (frame.tag !== views) {
                frame.removeAllViews()
                try {
                    frame.addView(views.apply(themed, frame))
                    frame.tag = views
                } catch (_: Exception) {
                    onFailure()
                }
            }
        },
        modifier = Modifier.fillMaxWidth().testTag(NotificationPreviewTags.Remote),
    )
}

private fun Context.withNightMode(dark: Boolean): Context {
    val config = Configuration(resources.configuration)
    config.uiMode = (config.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
        (if (dark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO)
    return createConfigurationContext(config)
}

/**
 * The standard (non-Remote Compose) Now notification's content: glyph chip and label, the until line with the other
 * zone's time, the block's progress, and what's next. A fixed sample: See bright light in Tokyo until 18:00, with
 * Lisbon's time for that moment worked out from the zones on [now]'s date (so it follows Lisbon's summer time).
 */
@Composable
private fun StandardContent(now: Instant) {
    val colors = MaterialTheme.colorScheme
    val advice = ClockblockTheme.adviceColors
    val light = advice[AdviceType.SeeBrightLight]
    val formatter = rememberTimeFormatter()
    val until = stringResource(
        NotificationsR.string.now_join,
        stringResource(NotificationsR.string.now_until, formatter.format(SampleUntil)),
        stringResource(NotificationsR.string.now_zone_tail, formatter.format(remember(now) { sampleUntilInOtherZone(now) }), stringResource(R.string.reminders_preview_other_city)),
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        Glyph(NotificationsR.drawable.ic_notif_bright_light, light.container, light.onContainer, 32)
        Spacer(Modifier.width(10.dp))
        Column {
            Text(stringResource(NotificationsR.string.advice_label_see_bright_light), style = MaterialTheme.typography.titleSmall, color = colors.onSurface)
            Text(until, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
        }
    }
    Spacer(Modifier.size(10.dp))
    LinearProgressIndicator(
        progress = { SampleProgress },
        color = light.color,
        trackColor = light.container,
        strokeCap = StrokeCap.Round,
        gapSize = 0.dp,
        drawStopIndicator = {},
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.size(10.dp))
    val avoid = advice[AdviceType.AvoidLight]
    Row(verticalAlignment = Alignment.CenterVertically) {
        Glyph(NotificationsR.drawable.ic_notif_avoid_light, avoid.container, avoid.onContainer, 24)
        Spacer(Modifier.width(8.dp))
        Text(
            stringResource(NotificationsR.string.now_next, stringResource(NotificationsR.string.advice_label_avoid_light), formatter.format(SampleNext)),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurfaceVariant,
        )
    }
}

@Composable
private fun Glyph(icon: Int, container: Color, onContainer: Color, sizeDp: Int) {
    Surface(shape = CircleShape, color = container, modifier = Modifier.size(sizeDp.dp)) {
        Icon(painterResource(icon), contentDescription = null, tint = onContainer, modifier = Modifier.padding((sizeDp / 5).dp))
    }
}

/** The notification's own action buttons, as the shade draws them under the content. */
@Composable
private fun Actions() {
    FlowRow(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        listOf(NotificationsR.string.action_done, NotificationsR.string.action_cant_do, NotificationsR.string.action_snooze).forEach {
            Text(stringResource(it), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(vertical = 6.dp))
        }
    }
}

private val IconColumn = 64.dp
private val SampleUntil: LocalTime = LocalTime.of(18, 0)

/** The sample block runs in Tokyo; the second time is Lisbon's (`reminders_preview_other_city`). */
private val SampleZone: ZoneId = ZoneId.of("Asia/Tokyo")
private val SampleOtherZone: ZoneId = ZoneId.of("Europe/Lisbon")

/** Lisbon's time when the sample block ends (18:00 in Tokyo) on [now]'s date: 10:00 in Lisbon's summer, 09:00 in winter. */
internal fun sampleUntilInOtherZone(now: Instant): LocalTime =
    ZonedDateTime.of(now.atZone(SampleZone).toLocalDate(), SampleUntil, SampleZone).withZoneSameInstant(SampleOtherZone).toLocalTime()
private val SampleNext: LocalTime = LocalTime.of(18, 30)
private const val SampleProgress = 0.45f
