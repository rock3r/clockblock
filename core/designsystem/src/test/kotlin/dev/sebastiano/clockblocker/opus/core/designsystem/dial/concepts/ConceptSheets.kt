package dev.sebastiano.clockblocker.opus.core.designsystem.dial.concepts

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.sebastiano.clockblocker.opus.core.designsystem.dial.DialArc
import dev.sebastiano.clockblocker.opus.core.designsystem.dial.DialState
import dev.sebastiano.clockblocker.opus.core.designsystem.dial.TwoClocksDial
import dev.sebastiano.clockblocker.opus.core.designsystem.theme.OpusTheme
import dev.sebastiano.clockblocker.opus.core.model.AdviceType
import kotlinx.collections.immutable.persistentListOf
import java.time.Instant

/** A concept: its spec function and the sizes (dp) it is shown at for each detail level. */
data class Concept(
    val name: String,
    val tagline: String,
    val spec: (ConceptState, ConceptPalette, Float, Float) -> List<DialOp>,
    val full: Pair<Float, Float>,
    val fullSmall: Pair<Float, Float>,
    val simple: Pair<Float, Float>,
    val glance: Pair<Float, Float>,
    val glanceSmall: Pair<Float, Float>,
) {
    companion object {
        val Skies = Concept(
            "1 · Two skies",
            "Outer ring: the sky where you are. Inner ring: the same sky turned by your jet lag. The offset is the jet lag.",
            TwoSkies::spec, 328f to 328f, 256f to 256f, 160f to 160f, 88f to 88f, 64f to 64f,
        )
        val Hands = Concept(
            "2 · Two hands",
            "One 24 h face, read like a dual-time watch: a sun hand for local time, a moon hand for your body. The gap is the jet lag.",
            TwoHands::spec, 328f to 328f, 256f to 256f, 160f to 160f, 88f to 88f, 64f to 64f,
        )
        val Strips = Concept(
            "3 · Two strips",
            "Two day bars on one time axis: the sky where you are over the sky your body expects. A now line reads both.",
            TwoStrips::spec, 328f to 170f, 260f to 170f, 240f to 112f, 88f to 88f, 88f to 88f,
        )
        val All = listOf(Skies, Hands, Strips)
    }
}

@Composable
private fun Themed(dark: Boolean, content: @Composable () -> Unit) {
    OpusTheme(darkTheme = dark, dynamicColor = false, reduceMotion = true, content = content)
}

@Composable
private fun Caption(text: String) {
    Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** One rendering of a concept at a size, with a caption. [widget] puts it on a widget-like card. */
@Composable
private fun Specimen(concept: Concept, state: ConceptState, size: Pair<Float, Float>, caption: String, dark: Boolean, widget: Boolean) {
    val p = ConceptPalette.current(dark)
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val ops = concept.spec(state, p.copy(card = if (widget) p.card else MaterialTheme.colorScheme.surface), size.first, size.second)
        if (widget) {
            Box(
                Modifier.background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(if (size.first < 110f) 22.dp else 26.dp))
                    .padding(if (size.first < 110f) 8.dp else 12.dp),
            ) { ConceptCanvas(ops, size.first, size.second) }
        } else {
            ConceptCanvas(ops, size.first, size.second)
        }
        Caption(caption)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Panel(dark: Boolean, title: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Themed(dark) {
        Column(
            modifier.background(MaterialTheme.colorScheme.surface, RoundedCornerShape(28.dp)).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(18.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
                itemVerticalAlignment = Alignment.Bottom,
            ) { content() }
        }
    }
}

private fun dp(size: Pair<Float, Float>) = if (size.first == size.second) "${size.first.toInt()} dp" else "${size.first.toInt()}×${size.second.toInt()} dp"

/** Contact sheet: Full / Simple / Glance in light and dark, plus the adapted state. 940 dp wide. */
@Composable
fun ConceptSheet(concept: Concept) {
    val mid = ConceptState.MidAdaptation
    val adapted = ConceptState.Adapted
    Themed(dark = false) {
        Column(
            Modifier.width(940.dp).background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(concept.name, style = OpusTheme.textStyles.editorialHeadline, color = MaterialTheme.colorScheme.onSurface)
            Text(concept.tagline, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                "Lisbon → Tokyo · Day 2 · 15:20 in Tokyo · body 7 h behind · Avoid light until 16:30, then melatonin",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                listOf(false, true).forEach { dark ->
                    Panel(dark, if (dark) "Full · dark" else "Full · light", Modifier.width(438.dp)) {
                        Specimen(concept, mid, concept.full, "Full · ${dp(concept.full)} (in-app hero, large widget)", dark, widget = false)
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                listOf(false, true).forEach { dark ->
                    Panel(dark, if (dark) "Simple + Glance · dark (on widget cards)" else "Simple + Glance · light (on widget cards)", Modifier.width(438.dp)) {
                        Specimen(concept, mid, concept.simple, "Simple · ${dp(concept.simple)}", dark, widget = true)
                        Specimen(concept, mid, concept.glance, "Glance · ${dp(concept.glance)}", dark, widget = true)
                        if (concept.glanceSmall != concept.glance) {
                            Specimen(concept, mid, concept.glanceSmall, dp(concept.glanceSmall), dark, widget = true)
                        }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Panel(false, "Adapted · Day 5, 09:10 · light", Modifier.width(438.dp)) {
                    Specimen(concept, adapted, concept.fullSmall, "Full · ${dp(concept.fullSmall)}", false, widget = false)
                    Specimen(concept, adapted, concept.glance, "Glance", false, widget = true)
                }
                Panel(true, "Adapted · dark", Modifier.width(438.dp)) {
                    Specimen(concept, adapted, concept.simple, "Simple · ${dp(concept.simple)}", true, widget = true)
                    Specimen(concept, adapted, concept.glance, "Glance", true, widget = true)
                }
            }
        }
    }
}

/** Today's dial in the same state (Tokyo 15:20, body 7 h behind), for the comparison sheet. */
val TodayDialState: DialState by lazy {
    DialState(
        instant = Instant.parse("2026-10-14T06:20:00Z"),
        displayZoneId = "Asia/Tokyo",
        localMinute = 15 * 60f + 20f,
        bodyAheadMinutes = -420f,
        cbtMinBodyMinute = 270f,
        arcs = persistentListOf(
            DialArc("a", AdviceType.AvoidLight, 13 * 60f + 30f, 180f, isNow = true),
            DialArc("m", AdviceType.Melatonin, 16 * 60f + 30f, 0f),
            DialArc("b", AdviceType.SeeBrightLight, 9 * 60f, 150f),
            DialArc("s", AdviceType.Sleep, 23 * 60f + 30f, 480f),
            DialArc("c", AdviceType.Caffeine, 9 * 60f, 180f),
        ),
        sunriseMinute = 5 * 60f + 45f,
        sunsetMinute = 17 * 60f + 15f,
    )
}

/** All three concepts next to today's dial: Full light, Full dark, Simple + Glance light. */
@Composable
fun ComparisonSheet() {
    val mid = ConceptState.MidAdaptation
    Themed(dark = false) {
        Column(
            Modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Two Clocks dial · today vs. three directions", style = OpusTheme.textStyles.editorialHeadline, color = MaterialTheme.colorScheme.onSurface)
            Text(
                "Same state everywhere: Tokyo 15:20, body 7 h behind (08:20), Avoid light until 16:30, then melatonin.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            listOf(false, true).forEach { dark ->
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Panel(dark, "Today", Modifier.width(440.dp)) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(Modifier.size(328.dp)) { TwoClocksDial(TodayDialState, Modifier.size(328.dp)) }
                            Caption("Full · 328 dp")
                        }
                    }
                    Concept.All.forEach { c ->
                        Panel(dark, c.name, Modifier.width(440.dp)) {
                            Column(Modifier.height(if (c == Concept.Strips) 352.dp else 352.dp), verticalArrangement = Arrangement.Center) {
                                Specimen(c, mid, c.full, "Full · ${dp(c.full)}", dark, widget = false)
                            }
                        }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Panel(false, "Today · small", Modifier.width(440.dp)) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(
                            Modifier.background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(26.dp)).padding(12.dp),
                        ) { TwoClocksDial(TodayDialState, Modifier.size(160.dp)) }
                        Caption("in-app dial at 160 dp")
                    }
                    Spacer(Modifier.width(1.dp))
                }
                Concept.All.forEach { c ->
                    Panel(false, "${c.name} · small", Modifier.width(440.dp)) {
                        Specimen(c, mid, c.simple, "Simple", false, widget = true)
                        Specimen(c, mid, c.glance, "Glance", false, widget = true)
                    }
                }
            }
        }
    }
}
