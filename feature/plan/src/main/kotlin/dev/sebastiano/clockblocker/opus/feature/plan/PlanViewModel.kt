package dev.sebastiano.clockblocker.opus.feature.plan

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.sebastiano.clockblocker.opus.core.data.AdviceLogRepository
import dev.sebastiano.clockblocker.opus.core.data.PlanRepository
import dev.sebastiano.clockblocker.opus.core.data.ProfileRepository
import dev.sebastiano.clockblocker.opus.core.data.SettingsRepository
import dev.sebastiano.clockblocker.opus.core.data.TripRepository
import dev.sebastiano.clockblocker.opus.core.data.export.ExportLabels
import dev.sebastiano.clockblocker.opus.core.data.export.IcsExporter
import dev.sebastiano.clockblocker.opus.core.data.time.Ticker
import dev.sebastiano.clockblocker.opus.core.model.AdviceLog
import dev.sebastiano.clockblocker.opus.core.model.AdviceOutcome
import dev.sebastiano.clockblocker.opus.core.model.AppSettings
import dev.sebastiano.clockblocker.opus.core.model.JetLagPlan
import dev.sebastiano.clockblocker.opus.core.model.SleepWindow
import dev.sebastiano.clockblocker.opus.core.model.Trip
import dev.sebastiano.clockblocker.opus.core.model.UserProfile
import dev.sebastiano.clockblocker.opus.core.model.easterEggsAllowed
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactory
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactoryKey
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Instant
import java.time.temporal.ChronoUnit

/** UI state of the plan screen. */
sealed interface PlanUiState {
    data object Loading : PlanUiState

    /**
     * Nothing to show. [missing] = a specific trip was asked for and no longer exists (deleted elsewhere); otherwise
     * there is simply no current or upcoming trip.
     */
    data class NoPlan(val missing: Boolean) : PlanUiState

    /**
     * A plan, at [now].
     *
     * @property moment what the screen shows at [now] (the dial preview recomputes it for other instants).
     * @property outcomes logged Done / Skipped / Can't-do per advice id.
     * @property nightSafe the screen should render in the Night-safe theme (setting on and the plan says rest).
     * @property celebrate show the once-per-trip "Clockblocked." moment.
     * @property easterEggs playful extras allowed (not reduced motion, not in a sleep window).
     */
    @Immutable
    data class Ready(
        val plan: JetLagPlan,
        val trip: Trip?,
        val now: Instant,
        val moment: PlanMoment,
        val outcomes: ImmutableMap<String, AdviceOutcome>,
        val nightSafe: Boolean,
        val celebrate: Boolean,
        val easterEggs: Boolean,
        val sleep: SleepWindow,
    ) : PlanUiState {
        val tripId: String get() = plan.tripId
        val kind: PlanKind get() = plan.kind
    }
}

/**
 * Drives [PlanScreen]: follows one trip's plan ([tripId]) or, when it is null, whichever plan is current, and
 * re-evaluates every tick so "now" advances by itself.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@AssistedInject
class PlanViewModel(
    @Assisted private val tripId: String?,
    planRepository: PlanRepository,
    private val tripRepository: TripRepository,
    private val adviceLogRepository: AdviceLogRepository,
    private val settingsRepository: SettingsRepository,
    private val profileRepository: ProfileRepository,
    private val celebrationStore: CelebrationStore,
    private val snoozer: AdviceSnoozer,
    private val icsExporter: IcsExporter,
    private val clock: Clock,
    private val ticker: Ticker,
) : ViewModel() {

    private val planFlow = if (tripId == null) planRepository.currentPlan else planRepository.plan(tripId)

    /** Minute-resolution "now": recomposition happens at most once a minute from the clock alone. */
    private val minutes = ticker.ticks()
        .map { clock.instant().truncatedTo(ChronoUnit.MINUTES) }
        .distinctUntilChanged()

    val state: StateFlow<PlanUiState> = planFlow
        .distinctUntilChanged()
        .flatMapLatest { plan ->
            if (plan == null) flowOf(PlanUiState.NoPlan(missing = tripId != null)) else readyStates(plan)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PlanUiState.Loading)

    private fun readyStates(plan: JetLagPlan) = combine(
        combine(
            tripRepository.trip(plan.tripId),
            adviceLogRepository.logs(plan.tripId),
            settingsRepository.settings,
            profileRepository.profile,
            ::Inputs,
        ),
        celebrationStore.celebrated(plan.tripId),
        minutes,
    ) { inputs, celebrated, now -> ready(plan, inputs, celebrated, now) }

    private data class Inputs(
        val trip: Trip?,
        val logs: List<AdviceLog>,
        val settings: AppSettings,
        val profile: UserProfile?,
    )

    private fun ready(plan: JetLagPlan, inputs: Inputs, celebrated: Boolean, now: Instant): PlanUiState.Ready {
        val moment = plan.momentAt(now)
        return PlanUiState.Ready(
            plan = plan,
            trip = inputs.trip,
            now = now,
            moment = moment,
            outcomes = inputs.logs.associate { it.adviceId to it.outcome }.toImmutableMap(),
            nightSafe = inputs.settings.nightSafeAuto && moment.wantsNightSafe,
            celebrate = !celebrated && plan.isCelebrationDue(now),
            easterEggs = easterEggsAllowed(inputs.settings.reduceMotion, plan, now),
            sleep = inputs.profile?.sleep ?: SleepWindow.Default,
        )
    }

    private val readyPlan: PlanUiState.Ready? get() = state.value as? PlanUiState.Ready

    /** Logs Done / Skipped / Can't do this for [adviceId] in the shown plan. */
    fun log(adviceId: String, outcome: AdviceOutcome) {
        val id = readyPlan?.tripId ?: return
        viewModelScope.launch { adviceLogRepository.log(id, adviceId, outcome) }
    }

    /**
     * Undoes the last [log] of [adviceId]: puts back the outcome it replaced ([previous]), or forgets the entry
     * when there was none.
     */
    fun undo(adviceId: String, previous: AdviceOutcome?) {
        val id = readyPlan?.tripId ?: return
        viewModelScope.launch {
            if (previous != null) adviceLogRepository.log(id, adviceId, previous) else adviceLogRepository.clear(id, adviceId)
        }
    }

    /** "Snooze 15 min": reminders stay quiet, then come back for [adviceId]. */
    fun snooze(adviceId: String) {
        viewModelScope.launch { snoozer.snooze(adviceId) }
    }

    /** The celebration was seen: never show it again for this trip. */
    fun celebrationShown() {
        val id = readyPlan?.tripId ?: return
        viewModelScope.launch { celebrationStore.markCelebrated(id) }
    }

    /** The plan as an iCalendar document (null while nothing is loaded). */
    fun calendar(labels: ExportLabels): String? = readyPlan?.let { icsExporter.export(it.plan, it.trip, labels) }

    @AssistedFactory
    @ManualViewModelAssistedFactoryKey(Factory::class)
    @ContributesIntoMap(AppScope::class)
    fun interface Factory : ManualViewModelAssistedFactory {
        /** [tripId] null = follow the current plan. */
        fun create(tripId: String?): PlanViewModel
    }
}
