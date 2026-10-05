package dev.sebastiano.clockblocker.opus.widget

import dev.sebastiano.clockblocker.opus.core.data.PlanRepository
import dev.sebastiano.clockblocker.opus.core.data.SettingsRepository
import dev.sebastiano.clockblocker.opus.core.model.AppSettings
import dev.sebastiano.clockblocker.opus.core.model.JetLagPlan
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

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
