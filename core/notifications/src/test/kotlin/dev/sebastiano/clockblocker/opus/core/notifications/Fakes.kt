package dev.sebastiano.clockblocker.opus.core.notifications

import dev.sebastiano.clockblocker.opus.core.data.AdviceLogRepository
import dev.sebastiano.clockblocker.opus.core.data.PlanRepository
import dev.sebastiano.clockblocker.opus.core.data.PlanSurface
import dev.sebastiano.clockblocker.opus.core.data.SettingsRepository
import dev.sebastiano.clockblocker.opus.core.data.TripRepository
import dev.sebastiano.clockblocker.opus.core.model.AdviceLog
import dev.sebastiano.clockblocker.opus.core.model.AdviceOutcome
import dev.sebastiano.clockblocker.opus.core.model.AppSettings
import dev.sebastiano.clockblocker.opus.core.model.JetLagPlan
import dev.sebastiano.clockblocker.opus.core.model.Trip
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

class FakePlanRepository(plan: JetLagPlan? = null) : PlanRepository {
    val current = MutableStateFlow(plan)
    override fun plan(tripId: String): Flow<JetLagPlan?> = current.map { it?.takeIf { p -> p.tripId == tripId } }
    override val currentPlan: Flow<JetLagPlan?> get() = current
}

class FakeTripRepository(vararg trips: Trip) : TripRepository {
    val current = MutableStateFlow(trips.toList())
    override val trips: Flow<List<Trip>> get() = current
    override fun trip(id: String): Flow<Trip?> = current.map { list -> list.firstOrNull { it.id == id } }
    override suspend fun upsert(trip: Trip) {
        current.value = current.value.filterNot { it.id == trip.id } + trip
    }
    override suspend fun delete(id: String) {
        current.value = current.value.filterNot { it.id == id }
    }
}

class FakeSettingsRepository(settings: AppSettings = AppSettings()) : SettingsRepository {
    val current = MutableStateFlow(settings)
    override val settings: Flow<AppSettings> get() = current
    override suspend fun update(transform: (AppSettings) -> AppSettings) {
        current.value = transform(current.value)
    }
}

class FakeAdviceLogRepository : AdviceLogRepository {
    val logged = MutableStateFlow<Map<String, List<AdviceLog>>>(emptyMap())
    override fun logs(tripId: String): Flow<List<AdviceLog>> = logged.map { it[tripId].orEmpty() }
    override suspend fun log(tripId: String, adviceId: String, outcome: AdviceOutcome) {
        logged.value = logged.value + (tripId to logged.value[tripId].orEmpty() + AdviceLog(adviceId, outcome))
    }
    override suspend fun clear(tripId: String, adviceId: String) {
        logged.value = logged.value + (tripId to logged.value[tripId].orEmpty().filterNot { it.adviceId == adviceId })
    }
}

class FakeClock(var instant: Instant, var zoneId: ZoneId = ZoneId.of("Europe/London")) : NotificationClock {
    override fun now(): Instant = instant
    override fun zone(): ZoneId = zoneId
}

class FakeCapabilities(
    var notifications: Boolean = true,
    var exact: Boolean = true,
    var promoted: Boolean = true,
    var batteryExempt: Boolean = false,
) : PlatformCapabilities {
    override fun areNotificationsEnabled() = notifications
    override fun canScheduleExactAlarms() = exact
    override fun canPostPromotedNotifications() = promoted
    override fun isIgnoringBatteryOptimizations() = batteryExempt
    override fun is24HourFormat() = true
    override fun locale(): Locale = Locale.UK
}

/** Counts refreshes, standing in for a widget. */
class RecordingSurface : PlanSurface {
    var refreshes = 0
        private set

    override suspend fun refresh() {
        refreshes++
    }
}

/** A surface that always fails, to prove one broken widget can't block the others. */
class FailingSurface : PlanSurface {
    override suspend fun refresh(): Unit = error("widget exploded")
}
