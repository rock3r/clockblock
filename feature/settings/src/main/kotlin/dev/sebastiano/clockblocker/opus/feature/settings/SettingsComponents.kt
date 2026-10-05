package dev.sebastiano.clockblocker.opus.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.sebastiano.clockblocker.opus.feature.onboarding.profile.SegmentedGap

/** A section title above a group of rows. */
@Composable
internal fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 28.dp, bottom = 10.dp)
            .semantics { heading() },
    )
}

/** Rows stacked with the expressive segmented gap; give each row `segmentedShape(index, count)`. */
@Composable
internal fun SettingsGroup(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(SegmentedGap), content = content)
}

/**
 * A tappable row: title, optional supporting text, optional trailing content (a chevron by default when
 * [onClick] is set) and optional [below] content under the text. At least 64 dp tall; the whole row is the target.
 */
@Composable
internal fun SettingsRow(
    title: String,
    shape: Shape,
    tag: String,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    container: Color = MaterialTheme.colorScheme.surfaceContainer,
    trailing: (@Composable RowScope.() -> Unit)? = if (onClick != null) ({ Chevron() }) else null,
    below: (@Composable () -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    Surface(shape = shape, color = container, modifier = modifier.fillMaxWidth()) {
        Row(
            Modifier
                .clip(shape)
                .then(if (onClick != null) Modifier.clickable(enabled = enabled, onClick = onClick) else Modifier)
                .heightIn(min = 64.dp)
                .padding(horizontal = 20.dp, vertical = 14.dp)
                .testTag(tag)
                .graphicsLayer { alpha = if (enabled) 1f else DisabledAlpha },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = colors.onSurface)
                if (supporting != null) {
                    Text(supporting, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                }
                if (below != null) {
                    Spacer(Modifier.height(8.dp))
                    below()
                }
            }
            if (trailing != null) {
                Spacer(Modifier.width(16.dp))
                trailing()
            }
        }
    }
}

@Composable
private fun Chevron() {
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    Icon(
        painterResource(R.drawable.settings_ic_chevron_right),
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.size(24.dp).graphicsLayer { scaleX = if (rtl) -1f else 1f },
    )
}

/**
 * A permission status row: "Allowed" with a check when granted, otherwise a tonal fix button under the text (so
 * long descriptions and large fonts never squeeze it). The label always carries the state; colour is only a
 * second cue.
 */
@Composable
internal fun PermissionRow(
    title: String,
    description: String,
    granted: Boolean,
    actionLabel: String,
    onAction: () -> Unit,
    shape: Shape,
    tag: String,
) {
    val colors = MaterialTheme.colorScheme
    SettingsRow(
        title = title,
        supporting = description,
        shape = shape,
        tag = tag,
        trailing = if (granted) {
            {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.heightIn(min = 48.dp)) {
                    Icon(painterResource(R.drawable.settings_ic_check), contentDescription = null, tint = colors.primary, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.settings_perm_allowed), style = MaterialTheme.typography.labelLarge, color = colors.primary)
                }
            }
        } else {
            null
        },
        below = if (granted) {
            null
        } else {
            {
                FilledTonalButton(onClick = onAction, modifier = Modifier.heightIn(min = 48.dp).testTag("${tag}_fix")) {
                    Text(actionLabel)
                }
            }
        },
    )
}

/** A full-width dialog surface for the profile editors (not a sheet: the dial's drag must not fight a sheet). */
@Composable
internal fun EditorDialog(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        EditorSurface(content = content)
    }
}

/** The surface inside [EditorDialog]; separate so screenshot tests can render it without a dialog window. */
@Composable
internal fun EditorSurface(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier.padding(16.dp).widthIn(max = 520.dp),
        content = content,
    )
}

private const val DisabledAlpha = 0.38f
