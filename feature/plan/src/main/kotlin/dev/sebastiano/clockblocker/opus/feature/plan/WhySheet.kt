package dev.sebastiano.clockblocker.opus.feature.plan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.sebastiano.clockblocker.opus.core.designsystem.advice.label
import dev.sebastiano.clockblocker.opus.core.designsystem.illustration.AdviceArt
import dev.sebastiano.clockblocker.opus.core.designsystem.theme.OpusTheme
import dev.sebastiano.clockblocker.opus.core.designsystem.time.cityName
import dev.sebastiano.clockblocker.opus.core.designsystem.time.rememberTimeFormatter
import dev.sebastiano.clockblocker.opus.core.model.Advice
import dev.sebastiano.clockblocker.opus.core.model.AdviceType
import java.time.ZoneId

/**
 * The "Why?" sheet (design.md §2.5): the illustration, the mechanism in two plain sentences, how to do it (with
 * alternatives), what skipping costs, the evidence, and the not-medical-advice line. Keeps the science
 * transparent (Timeshifter complaint #11).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WhySheet(
    advice: Advice,
    zone: ZoneId,
    secondaryZone: ZoneId,
    flightRoute: String?,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.testTag(PlanTags.WhySheet),
    ) {
        WhySheetContent(advice, zone, secondaryZone, flightRoute, Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding())
    }
}

/** Body of [WhySheet]; separate so it can be previewed and screenshotted without a window. */
@Composable
internal fun WhySheetContent(
    advice: Advice,
    zone: ZoneId,
    secondaryZone: ZoneId,
    flightRoute: String?,
    modifier: Modifier = Modifier,
) {
    val role = OpusTheme.adviceColors[advice.type]
    val formatter = rememberTimeFormatter()
    val resources = LocalContext.current.resources
    Column(modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 32.dp)) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = role.container.copy(alpha = 0.55f),
            modifier = Modifier.fillMaxWidth().height(184.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                // A rare surface: the illustration may breathe (ambient loop; stills under reduce motion).
                AdviceArt(advice.type, modifier = Modifier.size(152.dp))
            }
        }
        Spacer(Modifier.height(20.dp))
        Text(
            advice.type.label(),
            style = MaterialTheme.typography.headlineMediumEmphasized,
            modifier = Modifier.semantics { heading() },
        )
        val subtitle = listOfNotNull(
            formatter.range(advice.start, advice.end, zone, resources) + " " + zone.cityName(),
            if (advice.type == AdviceType.Flight) flightRoute else null,
            advice.detail,
        ).joinToString(" · ")
        Text(subtitle, style = OpusTheme.textStyles.timeLabel, color = MaterialTheme.colorScheme.onSurface)
        Text(
            formatter.range(advice.start, advice.end, secondaryZone, resources) + " " + secondaryZone.cityName(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(20.dp))
        Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
            WhySection(stringResource(R.string.plan_why_heading), stringResource(advice.reason.explanationRes))
            WhySection(stringResource(R.string.plan_why_how), stringResource(advice.type.howRes))
            if (advice.type != AdviceType.Flight) {
                WhySection(stringResource(R.string.plan_why_skip), stringResource(R.string.plan_why_skip_body))
            }
            WhySection(stringResource(R.string.plan_why_evidence), stringResource(advice.reason.evidenceRes), small = true)
        }
        Spacer(Modifier.height(20.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Spacer(Modifier.height(12.dp))
        Text(
            stringResource(R.string.plan_not_medical),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun WhySection(title: String, body: String, small: Boolean = false) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.titleSmallEmphasized,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            body,
            style = if (small) MaterialTheme.typography.bodyMedium else OpusTheme.textStyles.editorialBody,
            color = if (small) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
        )
    }
}
