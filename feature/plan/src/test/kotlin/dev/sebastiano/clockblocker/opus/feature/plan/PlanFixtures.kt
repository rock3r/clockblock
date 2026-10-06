package dev.sebastiano.clockblocker.opus.feature.plan

import dev.sebastiano.clockblocker.opus.core.circadian.DefaultJetLagPlanner
import dev.sebastiano.clockblocker.opus.core.data.demo.DemoData
import dev.sebastiano.clockblocker.opus.core.model.AdviceOutcome
import dev.sebastiano.clockblocker.opus.core.model.FlightLeg
import dev.sebastiano.clockblocker.opus.core.model.JetLagPlan
import dev.sebastiano.clockblocker.opus.core.model.Trip
import dev.sebastiano.clockblocker.opus.core.testing.FakeJetLagPlanner
import kotlinx.collections.immutable.toImmutableMap
import java.time.Instant
import java.time.LocalDateTime

/**
 * Shared plans and instants. SFO 19:30 (15 Jun, PDT) → LHR 13:50 (16 Jun, BST), eastbound 8 h.
 *
 * [fakePlan] (FakeJetLagPlanner) has predictable blocks: each day bright light wake–wake+2 h, avoid light
 * bed−2 h–bed, sleep 23:00–07:00 in that day's zone; days −2, −1, 0 (travel), 1…3 arrival, 4 adapted.
 * [realPlan] (DefaultJetLagPlanner) is what the app shows, used for the screenshots.
 */
internal object PlanFixtures {
    val trip: Trip = DemoData.sfoToLhr()
    val fakePlan: JetLagPlan by lazy { FakeJetLagPlanner().plan(trip, DemoData.profile, DemoData.Now) }
    val realPlan: JetLagPlan by lazy { DefaultJetLagPlanner().plan(trip, DemoData.profile, DemoData.Now) }

    /** LAX 16:00 (15 Jun, PDT) → DXB (UTC+4), 16 h: 11 h east, which the planner solves by delaying (the long way round). */
    val longWayTrip: Trip = Trip(
        id = "long-way",
        title = "Los Angeles → Dubai",
        legs = listOf(
            FlightLeg(
                id = "long-way-1",
                origin = DemoData.LAX,
                destination = DemoData.DXB,
                departureLocal = LocalDateTime.of(2026, 6, 15, 16, 0),
                arrivalLocal = LocalDateTime.of(2026, 6, 15, 16, 0).atZone(DemoData.LAX.zone).plusHours(16)
                    .withZoneSameInstant(DemoData.DXB.zone).toLocalDateTime(),
                flightNumber = "EK 216",
            ),
        ),
        createdAt = Instant.parse("2026-05-15T00:00:00Z"),
    )
    val longWayPlan: JetLagPlan by lazy { DefaultJetLagPlanner().plan(longWayTrip, DemoData.profile, DemoData.Now) }

    // Fake plan instants.
    /** 07:30 PDT on day −1: bright light (07:00–09:00). */
    val FakePreTrip: Instant = Instant.parse("2026-06-14T14:30:00Z")

    /** Over the Atlantic. */
    val FakeInFlight: Instant = Instant.parse("2026-06-16T04:00:00Z")

    /** 07:30 BST on day 2: bright light. */
    val FakeArrival: Instant = Instant.parse("2026-06-17T06:30:00Z")

    /** 21:30 BST on day 2: avoid light (21:00–23:00). */
    val FakeAvoidLight: Instant = Instant.parse("2026-06-17T20:30:00Z")

    /** Day 4, adapted. */
    val FakeAdapted: Instant = Instant.parse("2026-06-19T12:00:00Z")

    // Real plan instants.
    /** 05:30 PDT, pre-trip day −1: bright light 05:00–08:00. */
    val PreTrip: Instant = Instant.parse("2026-06-14T12:30:00Z")

    /** On BA 286, sleeping. */
    val InFlight: Instant = Instant.parse("2026-06-16T05:00:00Z")

    /** 11:00 BST on day 2: bright light 10:00–13:30. */
    val MidAdaptation: Instant = Instant.parse("2026-06-17T10:00:00Z")

    /** 22:30 BST on day 2: avoid light 22:00–01:00. */
    val Evening: Instant = Instant.parse("2026-06-17T21:30:00Z")

    /** Day 4: adapted. */
    val Adapted: Instant = Instant.parse("2026-06-19T08:00:00Z")

    fun ready(
        at: Instant,
        plan: JetLagPlan = realPlan,
        trip: Trip? = this.trip,
        outcomes: Map<String, AdviceOutcome> = emptyMap(),
        nightSafe: Boolean = false,
        celebrate: Boolean = false,
        easterEggs: Boolean = false,
    ) = PlanUiState.Ready(
        plan = plan,
        trip = trip,
        now = at,
        moment = plan.momentAt(at),
        outcomes = outcomes.toImmutableMap(),
        nightSafe = nightSafe,
        celebrate = celebrate,
        easterEggs = easterEggs,
        sleep = DemoData.profile.sleep,
    )
}
