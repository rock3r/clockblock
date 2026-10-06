package dev.sebastiano.clockblocker.opus.feature.onboarding.profile

import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.LocalTextStyle
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.sebastiano.clockblocker.opus.core.designsystem.advice.AdviceGlyph
import dev.sebastiano.clockblocker.opus.core.designsystem.theme.ClockblockTheme
import dev.sebastiano.clockblocker.opus.core.model.AdviceType
import dev.sebastiano.clockblocker.opus.core.model.Intensity
import dev.sebastiano.clockblocker.opus.core.model.UserProfile
import dev.sebastiano.clockblocker.opus.feature.onboarding.R

/** Test tags for the e2e suite. */
object ToolsTags {
    const val Caffeine = "tool_caffeine"
    const val Planes = "tool_sleep_on_planes"
    const val AdjustBefore = "tool_adjust_before"
    const val Melatonin = "tool_melatonin"
    const val MelatoninNote = "melatonin_note_toggle"
    const val MelatoninAck = "melatonin_ack"

    /** `effort_Gentle`, `effort_Balanced`, `effort_Max`. */
    fun effort(intensity: Intensity): String = "effort_${intensity.name}"
}

/**
 * The tool toggles: caffeine, sleeping on planes, pre-departure adjustment and melatonin. Melatonin is off by
 * default and sits behind an expandable safety note; it can only be switched on once the note is acknowledged
 * (tapping the switch before that opens the note instead).
 *
 * Each row leads with the glyph of the advice it unlocks (Caffeine, Sleep, See light, Melatonin). Switching a tool
 * on morphs the glyph from its resting circle into the advice shape and tints the row with that advice's container
 * colour, so the choice reads at a glance; the switch and the text label still carry it on their own (and under
 * reduce motion the morph snaps).
 */
@Composable
fun ToolsEditor(
    profile: UserProfile,
    melatoninAcknowledged: Boolean,
    onProfileChange: (UserProfile) -> Unit,
    onMelatoninChange: (Boolean) -> Unit,
    onAcknowledgeMelatonin: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    var noteOpen by rememberSaveable { mutableStateOf(false) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(SegmentedGap)) {
        ToolSwitchRow(
            title = stringResource(R.string.tool_caffeine),
            description = stringResource(R.string.tool_caffeine_description),
            checked = profile.useCaffeine,
            onCheckedChange = { onProfileChange(profile.copy(useCaffeine = it)) },
            shape = segmentedShape(0, 4),
            tag = ToolsTags.Caffeine,
            glyph = AdviceType.Caffeine,
        )
        ToolSwitchRow(
            title = stringResource(R.string.tool_planes),
            description = stringResource(R.string.tool_planes_description),
            checked = profile.canSleepOnPlanes,
            onCheckedChange = { onProfileChange(profile.copy(canSleepOnPlanes = it)) },
            shape = segmentedShape(1, 4),
            tag = ToolsTags.Planes,
            glyph = AdviceType.Sleep,
        )
        ToolSwitchRow(
            title = stringResource(R.string.tool_adjust_before),
            description = stringResource(R.string.tool_adjust_before_description),
            checked = profile.adjustBeforeDeparture,
            onCheckedChange = { onProfileChange(profile.copy(adjustBeforeDeparture = it)) },
            shape = segmentedShape(2, 4),
            tag = ToolsTags.AdjustBefore,
            glyph = AdviceType.SeeLight,
        )
        // Melatonin's card holds the row and its safety note, so the whole card takes the tint.
        val melatoninShape = segmentedShape(3, 4)
        val melatoninColors = rememberToolRowColors(AdviceType.Melatonin, profile.useMelatonin, MaterialTheme.colorScheme.surfaceContainer)
        Column(Modifier.clip(melatoninShape).drawBehind { drawRect(melatoninColors.container()) }) {
            ToolSwitchRowContent(
                title = stringResource(R.string.tool_melatonin),
                description = stringResource(
                    if (profile.useMelatonin) R.string.tool_melatonin_on_description else R.string.tool_melatonin_description,
                ),
                checked = profile.useMelatonin,
                onCheckedChange = { wanted ->
                    if (wanted && !melatoninAcknowledged) noteOpen = true else onMelatoninChange(wanted)
                },
                shape = segmentedShape(0, 1),
                tag = ToolsTags.Melatonin,
                enabled = true,
                glyph = AdviceType.Melatonin,
                colors = melatoninColors,
            )
            MelatoninNote(
                open = noteOpen,
                onToggle = { noteOpen = !noteOpen },
                acknowledged = melatoninAcknowledged,
                onAcknowledge = onAcknowledgeMelatonin,
            )
        }
    }
}

@Composable
private fun MelatoninNote(open: Boolean, onToggle: () -> Unit, acknowledged: Boolean, onAcknowledge: (Boolean) -> Unit) {
    val motion = ClockblockTheme.motion
    // The chevron reports state (open/closed), so it turns on the no-bounce data tier; the rotation is read in
    // the draw phase so the animation never recomposes the row.
    val rotation = animateFloatAsState(if (open) 180f else 0f, motion.dataSpatial(), label = "noteChevron")
    Column(Modifier.padding(start = 8.dp, end = 8.dp, bottom = 8.dp)) {
        TextButton(onClick = onToggle, modifier = Modifier.heightIn(min = 48.dp).testTag(ToolsTags.MelatoninNote)) {
            Icon(painterResource(R.drawable.onboarding_ic_info), contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(if (open) R.string.melatonin_note_hide else R.string.melatonin_note_show))
            Spacer(Modifier.width(4.dp))
            Icon(
                painterResource(R.drawable.onboarding_ic_expand_more),
                contentDescription = null,
                modifier = Modifier.size(18.dp).graphicsLayer { rotationZ = rotation.value },
            )
        }
        // One tier per block: the note is a container growing in place, so size and alpha both ride the
        // container spatial spec and finish together (no fast fade racing a slow expand).
        AnimatedVisibility(
            visible = open,
            enter = expandVertically(motion.containerSpatial()) + fadeIn(motion.containerSpatial()),
            exit = shrinkVertically(motion.containerSpatial()) + fadeOut(motion.containerSpatial()),
        ) {
            Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerHighest) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        stringResource(R.string.melatonin_note_title),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    listOf(
                        R.string.melatonin_note_dose,
                        R.string.melatonin_note_doctor,
                        R.string.melatonin_note_driving,
                        R.string.melatonin_note_legal,
                    ).forEach { NoteBullet(stringResource(it)) }
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.medium)
                            .toggleable(value = acknowledged, role = Role.Checkbox, onValueChange = onAcknowledge)
                            .heightIn(min = 48.dp)
                            .testTag(ToolsTags.MelatoninAck),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = acknowledged, onCheckedChange = null)
                        Spacer(Modifier.width(12.dp))
                        Text(
                            stringResource(R.string.melatonin_note_ack),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NoteBullet(text: String) {
    Row {
        BasicText("•", style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.primary))
        Spacer(Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * A full-width row with title, supporting text and a trailing switch; the whole row toggles (48 dp+).
 *
 * With a [glyph], the row leads with that advice's [AdviceGlyph] and becomes semantic: switched on, the glyph
 * morphs into the advice shape (`glyphMorph`, snapping under reduce motion) and the row takes the advice
 * container colour (`colour()`); the text label and the switch carry the state on their own.
 */
@Composable
fun ToolSwitchRow(
    title: String,
    description: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    shape: Shape,
    tag: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    container: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.surfaceContainer,
    glyph: AdviceType? = null,
) {
    if (glyph == null) {
        Surface(shape = shape, color = container, modifier = modifier.fillMaxWidth()) {
            Row(
                Modifier
                    .clip(shape)
                    .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange)
                    .heightIn(min = 64.dp)
                    .padding(horizontal = 20.dp, vertical = 14.dp)
                    .testTag(tag),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                    if (description != null) {
                        Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Spacer(Modifier.width(16.dp))
                Switch(checked = checked, onCheckedChange = null, enabled = enabled)
            }
        }
    } else {
        val colors = rememberToolRowColors(glyph, checked, container)
        ToolSwitchRowContent(
            title = title,
            description = description,
            checked = checked,
            onCheckedChange = onCheckedChange,
            shape = shape,
            tag = tag,
            enabled = enabled,
            glyph = glyph,
            colors = colors,
            modifier = modifier.clip(shape).drawBehind { drawRect(colors.container()) },
        )
    }
}

/**
 * A tool row's colours, animated on `ClockblockMotion.colour()` between the neutral container (off) and the advice
 * container (on). They are read through lambdas in the draw phase, so the cross-fade never recomposes the row.
 */
@Stable
internal class ToolRowColors(
    private val containerState: State<Color>,
    private val contentState: State<Color>,
    private val supportingState: State<Color>,
) {
    fun container(): Color = containerState.value
    fun content(): Color = contentState.value
    fun supporting(): Color = supportingState.value
}

@Composable
internal fun rememberToolRowColors(glyph: AdviceType, checked: Boolean, offContainer: Color): ToolRowColors {
    val scheme = MaterialTheme.colorScheme
    val role = ClockblockTheme.adviceColors[glyph]
    val motion = ClockblockTheme.motion
    val container = animateColorAsState(if (checked) role.container else offContainer, motion.colour(), label = "toolContainer")
    val content = animateColorAsState(if (checked) role.onContainer else scheme.onSurface, motion.colour(), label = "toolContent")
    val supporting = animateColorAsState(
        if (checked) role.onContainer.copy(alpha = SupportingOnTint) else scheme.onSurfaceVariant,
        motion.colour(),
        label = "toolSupporting",
    )
    return remember(container, content, supporting) { ToolRowColors(container, content, supporting) }
}

/** The inside of a glyph tool row; the caller draws the (tinted) container behind it. */
@Composable
internal fun ToolSwitchRowContent(
    title: String,
    description: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    shape: Shape,
    tag: String,
    enabled: Boolean,
    glyph: AdviceType,
    colors: ToolRowColors,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange)
            .heightIn(min = 64.dp)
            .padding(start = 16.dp, end = 20.dp, top = 14.dp, bottom = 14.dp)
            .testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AdviceGlyph(glyph, active = checked, size = ToolGlyphSize)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            BasicText(title, style = MaterialTheme.typography.titleMedium, color = { colors.content() })
            if (description != null) {
                BasicText(description, style = MaterialTheme.typography.bodyMedium, color = { colors.supporting() })
            }
        }
        Spacer(Modifier.width(16.dp))
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}

/** Supporting text on a tinted row: the advice ink, a step quieter than the title (still well above 4.5:1). */
private const val SupportingOnTint = 0.8f
private val ToolGlyphSize = 40.dp

/**
 * Effort as an M3 Expressive connected button group (Gentle / Balanced / Max) with a one-line explanation of the
 * selected level underneath.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun EffortSelector(selected: Intensity, onSelect: (Intensity) -> Unit, modifier: Modifier = Modifier) {
    val motion = ClockblockTheme.motion
    val options = Intensity.entries
    Column(modifier) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
        ) {
            options.forEachIndexed { index, intensity ->
                ToggleButton(
                    checked = intensity == selected,
                    onCheckedChange = { onSelect(intensity) },
                    shapes = when (index) {
                        0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                        options.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                        else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                    },
                    contentPadding = PaddingValues(horizontal = 8.dp),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 56.dp)
                        .semantics { role = Role.RadioButton }
                        .testTag(ToolsTags.effort(intensity)),
                ) {
                    if (intensity == selected) {
                        Icon(painterResource(R.drawable.onboarding_ic_check), contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                    }
                    // Three equal segments on a narrow phone: shrink the label rather than clip it.
                    val labelStyle = LocalTextStyle.current
                    Text(
                        intensity.label(),
                        maxLines = 1,
                        autoSize = TextAutoSize.StepBased(minFontSize = 10.sp, maxFontSize = labelStyle.fontSize),
                    )
                }
            }
        }
        AnimatedContent(
            targetState = selected,
            transitionSpec = { fadeIn(motion.fade()) togetherWith fadeOut(motion.fade()) },
            label = "effortDescription",
        ) { intensity ->
            Text(
                intensity.description(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp, start = 4.dp, end = 4.dp),
            )
        }
    }
}
