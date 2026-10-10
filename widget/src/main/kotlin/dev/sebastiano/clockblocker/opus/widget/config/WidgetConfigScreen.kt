package dev.sebastiano.clockblocker.opus.widget.config

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.sebastiano.clockblocker.opus.core.designsystem.dial.DialState
import dev.sebastiano.clockblocker.opus.core.designsystem.dial.StaticTwoSkiesDial
import dev.sebastiano.clockblocker.opus.core.designsystem.theme.ClockblockTheme
import dev.sebastiano.clockblocker.opus.core.model.BodyRingMode
import dev.sebastiano.clockblocker.opus.widget.R
import dev.sebastiano.clockblocker.opus.widget.WidgetKind

/** Test tags for the configuration screen. */
internal object WidgetConfigTags {
    const val Preview = "widget_config_preview"
    const val Done = "widget_config_done"

    /** `widget_config_body_ring_Simple`, `widget_config_body_ring_Precise`. */
    fun bodyRing(mode: BodyRingMode): String = "widget_config_body_ring_${mode.name}"
}

/** From this width the preview and the options sit side by side. */
private val TwoPaneMinWidth = 600.dp

/** On a phone the preview stays on screen above the options, but never takes more than this share of the height. */
private const val PreviewMaxHeightShare = 0.45f

/**
 * Options for one placed widget (#52): a live preview of the widget at its home-screen size, pinned above (or, on a
 * wide window, beside) the choices, so a change shows on the widget as it is made. Every choice is saved at once;
 * Done only closes the screen.
 *
 * [preview] draws the widget at its real size, `state.widthDp` × `state.heightDp`; the screen scales it down to fit.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun WidgetConfigScreen(
    state: WidgetConfigUiState,
    onBodyRingChange: (BodyRingMode) -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    preview: @Composable (Modifier) -> Unit,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.widget_config_title)) },
                subtitle = { Text(stringResource(state.kind.label())) },
                actions = {
                    TextButton(onClick = onDone, modifier = Modifier.testTag(WidgetConfigTags.Done)) {
                        Text(stringResource(R.string.widget_config_done))
                    }
                },
            )
        },
    ) { padding ->
        val direction = LocalLayoutDirection.current
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .padding(
                    start = padding.calculateStartPadding(direction),
                    end = padding.calculateEndPadding(direction),
                    top = padding.calculateTopPadding(),
                )
                .consumeWindowInsets(padding),
        ) {
            // The options scroll behind the navigation bar, then clear it at the end.
            val optionsPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp + padding.calculateBottomPadding())
            if (maxWidth >= TwoPaneMinWidth) {
                Row(Modifier.fillMaxSize()) {
                    PreviewPane(
                        state,
                        preview,
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .padding(start = 16.dp, top = 8.dp, bottom = 16.dp + padding.calculateBottomPadding()),
                    )
                    Options(state, onBodyRingChange, optionsPadding, Modifier.weight(1f).fillMaxHeight())
                }
            } else {
                val previewMax = maxHeight * PreviewMaxHeightShare
                Column(Modifier.fillMaxSize()) {
                    PreviewPane(
                        state,
                        preview,
                        Modifier
                            .fillMaxWidth()
                            .heightIn(max = previewMax)
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                    Options(state, onBodyRingChange, optionsPadding, Modifier.weight(1f).fillMaxWidth())
                }
            }
        }
    }
}

/** The widget, as the home screen draws it, scaled down to fit; a caption says it's a preview (of a sample trip). */
@Composable
private fun PreviewPane(state: WidgetConfigUiState, preview: @Composable (Modifier) -> Unit, modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    Surface(color = colors.surfaceContainer, shape = MaterialTheme.shapes.extraLarge, modifier = modifier) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            BoxWithConstraints(
                Modifier
                    .weight(1f, fill = false)
                    .fillMaxWidth()
                    .testTag(WidgetConfigTags.Preview)
                    .clearAndSetSemantics { contentDescription = state.previewDescription },
                contentAlignment = Alignment.Center,
            ) {
                val width = state.widthDp.dp
                val height = state.heightDp.dp
                val scale = minOf(1f, maxWidth / width, if (constraints.hasBoundedHeight) maxHeight / height else 1f)
                Box(Modifier.size(width * scale, height * scale), contentAlignment = Alignment.Center) {
                    preview(
                        Modifier
                            .requiredSize(width, height)
                            .graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                            },
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                stringResource(if (state.sample) R.string.widget_config_sample else R.string.widget_config_preview),
                style = MaterialTheme.typography.labelLarge,
                color = colors.onSurface,
            )
            if (state.sample) {
                Text(
                    stringResource(R.string.widget_config_sample_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun Options(
    state: WidgetConfigUiState,
    onBodyRingChange: (BodyRingMode) -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier,
) {
    Column(
        modifier
            .verticalScroll(rememberScrollState())
            .padding(contentPadding),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            stringResource(R.string.widget_config_body_ring),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 8.dp).semantics { heading() },
        )
        Text(
            stringResource(R.string.widget_config_body_ring_intro),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BodyRingMode.entries.forEach { mode ->
                BodyRingCard(
                    mode = mode,
                    dial = state.dial,
                    selected = mode == state.bodyRing,
                    onClick = { onBodyRingChange(mode) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** One body ring choice: the dial drawn that way, a radio button with the name, and what it shows. */
@Composable
private fun BodyRingCard(mode: BodyRingMode, dial: DialState?, selected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    val container by animateColorAsState(
        if (selected) colors.secondaryContainer else colors.surfaceContainer,
        ClockblockTheme.motion.colour(),
        label = "bodyRingContainer",
    )
    val shape = MaterialTheme.shapes.large
    Surface(
        shape = shape,
        color = container,
        border = if (selected) BorderStroke(2.dp, colors.primary) else null,
        modifier = modifier
            .clip(shape)
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
            .testTag(WidgetConfigTags.bodyRing(mode)),
    ) {
        Column(Modifier.padding(12.dp)) {
            if (dial != null) {
                // The name and description say what the card is; the dial's own description would only repeat the time.
                Box(Modifier.fillMaxWidth().aspectRatio(1f).clearAndSetSemantics {}) {
                    StaticTwoSkiesDial(dial, Modifier.fillMaxSize(), bodyRing = mode)
                }
                Spacer(Modifier.height(8.dp))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = selected, onClick = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(mode.label()), style = MaterialTheme.typography.titleSmall, color = colors.onSurface)
            }
            Spacer(Modifier.height(4.dp))
            Text(stringResource(mode.description()), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        }
    }
}

private fun WidgetKind.label(): Int = when (this) {
    WidgetKind.TwoClocks -> R.string.widget_two_clocks_label
    WidgetKind.NextUp -> R.string.widget_next_up_label
}

private fun BodyRingMode.label(): Int = when (this) {
    BodyRingMode.Simple -> R.string.widget_config_body_ring_simple
    BodyRingMode.Precise -> R.string.widget_config_body_ring_precise
}

private fun BodyRingMode.description(): Int = when (this) {
    BodyRingMode.Simple -> R.string.widget_config_body_ring_simple_description
    BodyRingMode.Precise -> R.string.widget_config_body_ring_precise_description
}
