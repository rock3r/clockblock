package dev.sebastiano.clockblocker.opus.feature.plan

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.sebastiano.clockblocker.opus.core.data.export.ExportLabels
import dev.sebastiano.clockblocker.opus.core.designsystem.advice.labelRes
import dev.sebastiano.clockblocker.opus.core.designsystem.illustration.TwoClocksArt
import dev.sebastiano.clockblocker.opus.core.designsystem.time.rememberTimeFormatter
import dev.zacsweers.metrox.viewmodel.assistedMetroViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The plan for a trip (navigation contract used by `:app`; keep these signatures, additive defaulted params OK).
 * [tripId] null = the current plan (`PlanRepository.currentPlan`, deep link `plan/current`).
 * [onBack] null = no up affordance (e.g. shown as a detail pane next to the list, or as a top-level tab).
 *
 * Hero: the Two Clocks dial (scrub to preview any time of the day), the Now card with Done / Skipped / Can't do
 * this, the adaptation wave and the day-by-day rail. The calendar export (.ics through the Storage Access
 * Framework) and the share summary are handled here; everything else is [PlanViewModel].
 */
@Composable
fun PlanScreen(
    tripId: String?,
    onBack: (() -> Unit)?,
    onEditTrip: (tripId: String) -> Unit,
    onCreateReturnTrip: (outboundTripId: String) -> Unit,
    onNewTrip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel = assistedMetroViewModel<PlanViewModel, PlanViewModel.Factory>(key = "plan:${tripId ?: "current"}") {
        create(tripId)
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val resources = context.resources
    val formatter = rememberTimeFormatter()
    val screenState = rememberPlanScreenState()
    val scope = rememberCoroutineScope()
    val ready = state as? PlanUiState.Ready

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/calendar")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val labels = ExportLabels(
            adviceTitle = { resources.getString(it.labelRes) },
            reasonText = { resources.getString(it.explanationRes) },
            disclaimer = resources.getString(R.string.plan_share_footer),
        )
        val ics = viewModel.calendar(labels)
        scope.launch {
            val ok = ics != null && runCatching {
                withContext(Dispatchers.IO) {
                    checkNotNull(context.contentResolver.openOutputStream(uri)).use { it.write(ics.toByteArray(Charsets.UTF_8)) }
                }
            }.isSuccess
            screenState.snackbar.showSnackbar(resources.getString(if (ok) R.string.plan_export_done else R.string.plan_export_failed))
        }
    }

    val actions = PlanActions(
        onBack = onBack,
        onEditTrip = { ready?.let { onEditTrip(it.tripId) } },
        onCreateReturnTrip = { ready?.let { onCreateReturnTrip(it.tripId) } },
        onExportCalendar = {
            ready?.let { exportLauncher.launch(fileNameFor(planTitle(it.plan, it.trip))) }
        },
        onShareSummary = {
            ready?.let {
                val text = buildPlanSummary(resources, formatter, it.plan, it.trip)
                val send = Intent(Intent.ACTION_SEND)
                    .setType("text/plain")
                    .putExtra(Intent.EXTRA_SUBJECT, resources.getString(R.string.plan_share_header, planTitle(it.plan, it.trip)))
                    .putExtra(Intent.EXTRA_TEXT, text)
                context.startActivity(Intent.createChooser(send, resources.getString(R.string.plan_share_chooser)))
            }
        },
        onNewTrip = onNewTrip,
        onLog = viewModel::log,
        onUndo = viewModel::undo,
        onSnooze = viewModel::snooze,
        onCelebrationShown = viewModel::celebrationShown,
    )
    PlanContent(state, actions, modifier, screenState)
}

/** "Lisbon → Tokyo" → "Lisbon-Tokyo-jet-lag-plan.ics" (SAF suggests it; the user can rename). */
internal fun fileNameFor(title: String): String {
    val base = title.replace(Regex("[^\\p{L}\\p{N}]+"), "-").trim('-').ifBlank { "trip" }
    return "$base-jet-lag-plan.ics"
}

/** Detail-pane placeholder on large screens when no trip is selected. */
@Composable
fun PlanEmptyPane(onNewTrip: () -> Unit, modifier: Modifier = Modifier) {
    PlanEmptyState(
        art = { TwoClocksArt(Modifier.size(200.dp), progress = 0.35f) },
        title = stringResource(R.string.plan_pane_title),
        body = stringResource(R.string.plan_pane_body),
        onNewTrip = onNewTrip,
        modifier = modifier.testTag(PlanTags.EmptyPane),
    )
}
