package dev.sebastiano.clockblocker.opus.widget

import dev.sebastiano.clockblocker.opus.core.data.AdviceLogRepository
import dev.sebastiano.clockblocker.opus.core.data.PlanRepository
import dev.sebastiano.clockblocker.opus.core.data.SettingsRepository
import dev.sebastiano.clockblocker.opus.core.model.AdviceLog
import dev.sebastiano.clockblocker.opus.core.model.AdviceOutcome
import dev.sebastiano.clockblocker.opus.core.model.AppSettings
import dev.sebastiano.clockblocker.opus.core.model.JetLagPlan
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakePlanRepository(plan: JetLagPlan? = null) : PlanRepository {
    val current = MutableStateFlow(plan)
    override fun plan(tripId: String): Flow<JetLagPlan?> = current
    override val currentPlan: Flow<JetLagPlan?> = current
}

class FakeSettingsRepository(settings: AppSettings = AppSettings()) : SettingsRepository {
    val current = MutableStateFlow(settings)
    override val settings: Flow<AppSettings> = current
    override suspend fun update(transform: (AppSettings) -> AppSettings) {
        current.value = transform(current.value)
    }
}

class FakeAdviceLogRepository : AdviceLogRepository {
    private val all = MutableStateFlow<Map<String, List<AdviceLog>>>(emptyMap())
    override fun logs(tripId: String): Flow<List<AdviceLog>> = all.map { it[tripId].orEmpty() }
    override suspend fun log(tripId: String, adviceId: String, outcome: AdviceOutcome) {
        all.value = all.value + (tripId to (all.value[tripId].orEmpty().filter { it.adviceId != adviceId } + AdviceLog(adviceId, outcome)))
    }
    override suspend fun clear(tripId: String, adviceId: String) {
        all.value = all.value + (tripId to all.value[tripId].orEmpty().filter { it.adviceId != adviceId })
    }
}
