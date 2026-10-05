package dev.sebastiano.clockblocker.opus.feature.plan

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.SeekableTransitionState
import androidx.compose.animation.core.rememberTransition
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.sebastiano.clockblocker.opus.core.designsystem.component.ConfettiBurst
import dev.sebastiano.clockblocker.opus.core.designsystem.dial.toDialState
import dev.sebastiano.clockblocker.opus.core.designsystem.illustration.BloomArt
import dev.sebastiano.clockblocker.opus.core.designsystem.theme.OpusTheme
import dev.sebastiano.clockblocker.opus.core.designsystem.theme.OpusThemeVariant
import dev.sebastiano.clockblocker.opus.core.designsystem.time.cityName
import dev.sebastiano.clockblocker.opus.core.model.AdviceType
import dev.sebastiano.clockblocker.opus.core.model.JetLagPlan
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.ZoneId
import kotlin.math.roundToInt

/**
 * The celebration's beats, in order (MOTION.md "Celebration sequence"). One mover at a time:
 * [Waiting] for the navigation transition to settle → [Aligning] the dial's rings (they turn together and click
 * with a CONFIRM haptic) → [Showing] the overlay (fade, then Bloom scales, then the check trims as one confetti
 * burst leaves the Bloom's centre) → [Done] once dismissed.
 */
internal enum class CelebrationStage { Waiting, Aligning, Showing, Done }

/** How long the plan waits for the entering navigation transition before the rings start to turn. */
internal const val CelebrationNavSettleMillis = 450L

/** Fallback if the dial never reports alignment (e.g. scrolled away): don't hold the moment hostage. */
internal const val CelebrationAlignTimeoutMillis = 2_500L

/**
 * The dial's body offset (minutes, body relative to local) when the last flight landed, shown in [zone]: where
 * the rings start before turning into alignment for the celebration. Falls back to the planned shift.
 */
internal fun JetLagPlan.arrivalBodyAheadMinutes(zone: ZoneId): Float {
    val arrival = allAdvice.filter { it.type == AdviceType.Flight }.maxOfOrNull { it.end }
        ?: return (-shiftHours * 60.0).toFloat()
    return toDialState(arrival, zone).bodyAheadMinutes
}

/**
 * The once-per-trip "Clockblocked." moment (design.md §3, MOTION.md "rare delight"): Fraunces headline, the
 * destination the body is now on, the time the plan saved, a blooming check and one burst of confetti from the
 * Bloom. The words carry the meaning, so under reduce motion the confetti is simply skipped (ConfettiBurst
 * handles that) and every beat sits at its end state. No confetti in Night-safe: it's a dimmed, calm surface.
 *
 * Predictive back is seekable: the overlay fades with the finger and comes back if the gesture is cancelled.
 */
@Composable
internal fun CelebrationOverlay(plan: JetLagPlan, visible: Boolean, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val motion = OpusTheme.motion
    val reduce = OpusTheme.reduceMotion
    val currentOnDismiss by rememberUpdatedState(onDismiss)
    val scope = rememberCoroutineScope()
    // Starts hidden so the entrance fade actually plays (an AnimatedVisibility created visible never fades).
    val overlay = remember { SeekableTransitionState(false) }
    LaunchedEffect(visible) { overlay.animateTo(visible) }
    val transition = rememberTransition(overlay, label = "celebration")

    // Bloom scale once the overlay has faded in, then check + burst together once the flower has landed.
    var bloom by remember { mutableStateOf(if (reduce) 1f else 0f) }
    var finale by remember { mutableStateOf(reduce) }
    LaunchedEffect(transition.currentState, transition.isRunning) {
        if (transition.currentState && !transition.isRunning && bloom == 0f) {
            bloom = 1f
            delay(BloomLandMillis)
            finale = true
        }
    }
    var bloomCentre by remember { mutableStateOf<Offset?>(null) }
    var overlayOrigin by remember { mutableStateOf(Offset.Zero) }

    PredictiveBackHandler(enabled = visible) { progress ->
        try {
            progress.collect { event -> overlay.seekTo(event.progress, targetState = false) }
            overlay.animateTo(false)
            currentOnDismiss()
        } catch (e: CancellationException) {
            scope.launch { overlay.animateTo(true) }
            throw e
        }
    }

    transition.AnimatedVisibility(
        visible = { it },
        enter = fadeIn(motion.colour()),
        exit = fadeOut(motion.fade()),
        modifier = modifier.onGloballyPositioned { overlayOrigin = it.boundsInRoot().topLeft },
    ) {
        Box(Modifier.fillMaxSize()) {
            Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxSize()) {
                CelebrationContent(
                    plan = plan,
                    onDismiss = onDismiss,
                    bloomProgress = bloom,
                    checkProgress = if (finale) 1f else 0f,
                    onBloomPositioned = { bloomCentre = it },
                )
            }
            if (OpusTheme.variant != OpusThemeVariant.NightSafe) {
                ConfettiBurst(
                    playing = finale,
                    modifier = Modifier.fillMaxSize(),
                    origin = { bloomCentre?.let { it - overlayOrigin } },
                )
            }
        }
    }
}

/** The flower's bouncy scale (`glyphMorph`) is short; the check and burst follow as it lands. */
private const val BloomLandMillis = 320L

@Composable
internal fun CelebrationContent(
    plan: JetLagPlan,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    bloomProgress: Float = 1f,
    checkProgress: Float = 1f,
    onBloomPositioned: (Offset) -> Unit = {},
) {
    val saved = (plan.estimatedDaysWithoutPlan - plan.estimatedDaysToAdapt).roundToInt()
    val city = ZoneId.of(plan.destinationZoneId).cityName()
    Column(
        modifier
            .fillMaxSize()
            .testTag(PlanTags.Celebration)
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 32.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        BloomArt(
            modifier = Modifier
                .size(200.dp)
                .onGloballyPositioned { onBloomPositioned(it.boundsInRoot().center) },
            progress = bloomProgress,
            checkProgress = checkProgress,
        )
        Spacer(Modifier.height(24.dp))
        Text(
            stringResource(R.string.plan_celebration_title),
            style = OpusTheme.textStyles.editorialDisplay,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics {
                heading()
                liveRegion = LiveRegionMode.Polite
            },
        )
        Spacer(Modifier.height(12.dp))
        Text(
            stringResource(R.string.plan_celebration_body, city),
            style = OpusTheme.textStyles.editorialHeadline,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 420.dp),
        )
        if (saved >= 1) {
            Spacer(Modifier.height(12.dp))
            Text(
                pluralStringResource(R.plurals.plan_celebration_saved, saved, saved),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 420.dp),
            )
        }
        Spacer(Modifier.height(32.dp))
        Button(onClick = onDismiss, modifier = Modifier.heightIn(min = 56.dp).testTag(PlanTags.CelebrationDismiss)) {
            Text(stringResource(R.string.plan_celebration_dismiss), style = MaterialTheme.typography.titleMedium)
        }
    }
}
