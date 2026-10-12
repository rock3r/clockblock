package dev.sebastiano.clockblocker.opus.feature.onboarding.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * A connected single-choice button group (M3 Expressive), shared by onboarding (Head start) and Settings (theme,
 * reminder lead, the You editor's tabs). Each option is a radio button inside one selectable group. When more than two
 * options can't each get [minOptionWidth] (scaled with the font) they split into two connected rows, so every label
 * keeps the same, readable size. [checkIcon], when set, adds a tick to the selected option.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun <T> ConnectedChoice(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    label: @Composable (T) -> String,
    tag: (T) -> String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    checkIcon: Painter? = null,
    minOptionWidth: Dp = 64.dp,
) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val gap = ButtonGroupDefaults.ConnectedSpaceBetween
        val fontScale = LocalDensity.current.fontScale
        val perOption = (maxWidth - gap * (options.size - 1)) / options.size
        val rows = if (options.size > 2 && perOption < minOptionWidth * fontScale) {
            options.chunked((options.size + 1) / 2)
        } else {
            listOf(options)
        }
        Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(gap)) {
            rows.forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap)) {
                    row.forEachIndexed { index, option ->
                        val checked = option == selected
                        ToggleButton(
                            checked = checked,
                            onCheckedChange = { onSelect(option) },
                            enabled = enabled,
                            // These rows sit on surfaceContainer cards, the toggle's default unchecked container:
                            // lift it a step so unselected options still read as buttons.
                            colors = ToggleButtonDefaults.colors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            ),
                            shapes = when (index) {
                                0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                                row.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                                else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                            },
                            contentPadding = PaddingValues(horizontal = 6.dp),
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .semantics { role = Role.RadioButton }
                                .testTag(tag(option)),
                        ) {
                            if (checked && checkIcon != null) {
                                Icon(checkIcon, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                            }
                            Text(
                                label(option),
                                maxLines = 1,
                                autoSize = TextAutoSize.StepBased(minFontSize = 10.sp, maxFontSize = LocalTextStyle.current.fontSize),
                            )
                        }
                    }
                }
            }
        }
    }
}
