package dev.sebastiano.clockblocker.opus.core.circadian.engine

import dev.sebastiano.clockblocker.opus.core.circadian.CircadianMath.mod24
import dev.sebastiano.clockblocker.opus.core.circadian.CircadianMath.norm12
import dev.sebastiano.clockblocker.opus.core.circadian.EstimateModel
import dev.sebastiano.clockblocker.opus.core.circadian.PlannerConfig
import dev.sebastiano.clockblocker.opus.core.circadian.ode.Forger99
import dev.sebastiano.clockblocker.opus.core.circadian.ode.Hannay19
import dev.sebastiano.clockblocker.opus.core.circadian.toClass
import dev.sebastiano.clockblocker.opus.core.model.AdaptationStrategy
import dev.sebastiano.clockblocker.opus.core.model.Advice
import dev.sebastiano.clockblocker.opus.core.model.AdviceReason
import dev.sebastiano.clockblocker.opus.core.model.AdviceType
import dev.sebastiano.clockblocker.opus.core.model.DayKind
import dev.sebastiano.clockblocker.opus.core.model.JetLagPlan
import dev.sebastiano.clockblocker.opus.core.model.PhasePoint
import dev.sebastiano.clockblocker.opus.core.model.PlanDay
import dev.sebastiano.clockblocker.opus.core.model.ShiftDirection
import dev.sebastiano.clockblocker.opus.core.model.Trip
import dev.sebastiano.clockblocker.opus.core.model.UserProfile
import java.security.MessageDigest
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * Builds one [JetLagPlan]: converts the trip to planner hours, picks the mode (adapt / stay on home time /
 * no plan), runs the cycle planner per segment, estimates days-to-adapt with the ODE, and assembles cards,
 * calendar days and the phase trajectory.
 */
internal class PlanBuilder(
    private val config: PlannerConfig,
    private val trip: Trip,
    private val profile: UserProfile,
    private val now: Instant,
) {
    private val chrono = profile.chronotype.toClass()
    private val sortedLegs = trip.legs.sortedBy { it.departure }
    private val ref: Instant = sortedLegs.first().departure.truncatedTo(ChronoUnit.DAYS)
    private val originZone: ZoneId = sortedLegs.first().origin.zone
    private val destZone: ZoneId = sortedLegs.last().destination.zone
    private val legs: List<LegHours> = sanitizedLegs()
    private val homeOffset = legs.first().depOffset
    private val dep = legs.first().dep
    private val arr = legs.last().arr
    private val habOnset = profile.sleep.bedtime.toSecondOfDay() / 3600.0
    private val habWake = profile.sleep.wake.toSecondOfDay() / 3600.0
    private val sleepDuration = mod24(habWake - habOnset).coerceIn(CyclePlanner.MIN_SLEEP, CyclePlanner.MAX_SLEEP)
    private val t0Clock = CyclePlanner.cbtMinClock(habOnset, habOnset + sleepDuration, config.cbtFromMidSleep(chrono))
    private val returnH: Double? = trip.returnDeparture?.let(::h)
    private val planner = CyclePlanner(config)
    private val assembler = AdviceAssembler(config)
    private val model = when (config.estimateModel) {
        EstimateModel.Hannay19 -> Hannay19
        EstimateModel.Forger99 -> Forger99
    }
    private val validator = PlanValidator(model, habOnset, mod24(habOnset + sleepDuration), config.lightBox)
    private val horizon = config.estimateHorizonDays

    /** Planning segments: legs split at stops ≥ SHORT_TRIP (§10.3: such a stop is a destination). */
    private val segments: List<List<LegHours>> = buildList {
        var cur = mutableListOf(legs.first())
        for (i in 1 until legs.size) {
            if (legs[i].dep - legs[i - 1].arr >= config.shortTripHours) {
                add(cur)
                cur = mutableListOf()
            }
            cur += legs[i]
        }
        add(cur)
    }

    /** Groups for calendar days: legs split at stops ≥ 24 h. */
    private val travelGroups: List<IntRange> = buildList {
        var start = 0
        for (i in 1 until legs.size) {
            if (legs[i].dep - legs[i - 1].arr >= 24.0) {
                add(start until i)
                start = i
            }
        }
        add(start until legs.size)
    }

    private val rest: List<Interval> = if (profile.canSleepOnPlanes) emptyList() else legs.map { Interval(it.dep, it.arr) }

    /** Calendar day spans of the last [build] (what `JetLagPlan.daySpans()` must reconstruct). */
    var spans: List<DaySpan> = emptyList()
        private set

    fun build(): JetLagPlan {
        val deltas = buildList {
            var prev = homeOffset
            for (s in segments) {
                add(norm12(s.last().arrOffset - prev))
                prev = s.last().arrOffset
            }
        }
        val allSmall = deltas.all { abs(it) < config.minShift }
        val short = trip.strategyOverride == null && returnH != null && returnH - arr < config.shortTripHours
        return when {
            trip.strategyOverride == AdaptationStrategy.StayOnHomeTime -> homeTime()
            allSmall -> noPlan()
            short -> homeTime()
            else -> adapt() ?: noPlan()
        }
    }

    // ------------------------------------------------------------------------------------------------ modes

    private fun adapt(): JetLagPlan? {
        val segPlans = ArrayList<SegmentPlan>()
        val cycles = ArrayList<Cycle>()
        var body = homeOffset
        var startT: Double? = null
        var prevSleep: Interval? = null
        for ((j, seg) in segments.withIndex()) {
            val pre = preDaysFor(j)
            val stopAt = if (j < segments.lastIndex) {
                segments[j + 1].first().dep - 24.0 * (preDaysFor(j + 1) + 1)
            } else {
                returnH
            }
            val input = SegmentInput(
                bodyOffset = body,
                legs = seg,
                habitualOnset = habOnset,
                habitualWake = habWake,
                chronotype = chrono,
                preDays = pre,
                useMelatonin = profile.useMelatonin,
                startT = startT,
                stopAt = stopAt?.let { maxOf(it, seg.last().arr + 24.0) },
                previousSleep = prevSleep,
                extraSleepBlocks = layoverBlocks(seg) + returnBlock(),
                holdIntervals = oppositeStopovers(seg, body),
            )
            val forced = if (j == 0 && config.tier2DirectionChoice) tier2Direction(input) else null
            val sp = planner.plan(input.copy(forcedDirection = forced))
            segPlans += sp
            cycles += sp.cycles
            body += sp.nextPhi
            startT = if (sp.done) null else sp.nextT
            prevSleep = sp.cycles.lastOrNull()?.sleep ?: prevSleep
        }
        if (cycles.isEmpty()) return null
        val all = planner.fillCaffeineAndNaps(cycles)
        val raw = adviceFromCycles(all) + flightMarkers()
        val result = validator.validate(Itinerary(homeOffset, legs), LightPlan.of(all, rest), horizon)
        val main = segPlans.maxBy { abs(it.targetPhi) }
        val targets = segments.map { it.last().arrOffset }
        return assemble(
            raw = raw,
            track = PhaseTrack.Cycles(all, homeOffset),
            strategy = AdaptationStrategy.Adapt,
            direction = if (main.direction == Direction.Advance) ShiftDirection.Advance else ShiftDirection.Delay,
            shiftHours = segPlans.sumOf { it.targetPhi },
            daysToAdapt = result.plan.adaptDays ?: horizon.toDouble(),
            daysWithout = result.noPlan.adaptDays ?: horizon.toDouble(),
            segmentTargets = targets,
        )
    }

    /** §9 home-time mode: the body clock stays on the origin clock; cards keep the traveller on home time. */
    private fun homeTime(): JetLagPlan {
        val psiOn = mod24(t0Clock - habOnset)
        val end = returnH ?: (arr + config.shortTripHours)
        var t = t0Clock - homeOffset
        while (t < dep) t += 24.0
        while (t >= dep) t -= 24.0
        val blocks = transitBlocks() + legs.zipWithNext { a, b -> layoverBlock(a, b) } + returnBlock()
        val ts = generateSequence(t) { it + 24.0 }.takeWhile { it <= end + 12.0 }.toList()
        val sleeps = ts.map { subtract(Interval(it - psiOn, it - psiOn + sleepDuration), blocks) }
        val raw = ArrayList<RawAdvice>()
        for ((i, ti) in ts.withIndex()) {
            raw += sleepCards(sleeps[i], AdviceReason.StayOnHomeTime)
            raw += RawAdvice(AdviceType.AvoidLight, Interval(ti - 8.0, ti + 3.0), AdviceReason.StayOnHomeTime)
            raw += RawAdvice(AdviceType.SeeLight, Interval(ti + 3.0, ti + 16.0), AdviceReason.StayOnHomeTime)
            raw += fatigue(ti)
            if (i < ts.lastIndex && profile.useCaffeine) {
                val w = sleeps[i].lastOrNull()?.end ?: (ti - psiOn + sleepDuration)
                val s = sleeps[i + 1].firstOrNull()?.start ?: (ts[i + 1] - psiOn)
                raw += caffeineCards(w, s, config.caffeineCutoff, listOf(ti, ts[i + 1]))
            }
        }
        raw += flightMarkers()
        val noPlan = validator.validate(Itinerary(homeOffset, legs), null, horizon).noPlan.adaptDays ?: horizon.toDouble()
        return assemble(
            raw = raw,
            track = PhaseTrack.Constant(homeOffset, t0Clock),
            strategy = AdaptationStrategy.StayOnHomeTime,
            direction = ShiftDirection.None,
            shiftHours = 0.0,
            daysToAdapt = 0.0,
            daysWithout = noPlan,
            segmentTargets = null,
        )
    }

    /** |Δ| < MIN_SHIFT: flight markers and a few destination nights at the habitual local time. */
    private fun noPlan(): JetLagPlan {
        val destOff = legs.last().arrOffset
        val delta = norm12(destOff - homeOffset)
        val firstDate = trip.arrival.atZone(destZone).toLocalDate()
        val raw = ArrayList<RawAdvice>()
        var n = 0L
        var nights = 0
        while (nights < config.noPlanSleepNights && n < config.noPlanSleepNights + 2) {
            val onset = h(firstDate.plusDays(n).atTime(profile.sleep.bedtime).atZone(destZone).toInstant())
            n++
            if (onset < arr + config.postArrivalNoSleep) continue
            raw += RawAdvice(AdviceType.Sleep, Interval(onset, onset + sleepDuration), AdviceReason.DestinationSleep)
            nights++
        }
        raw += flightMarkers()
        val days = validator.validate(Itinerary(homeOffset, legs), null, horizon).noPlan.adaptDays ?: horizon.toDouble()
        return assemble(
            raw = raw,
            track = PhaseTrack.Drift(homeOffset, delta, arr, config.naturalDriftPerDay, t0Clock),
            strategy = AdaptationStrategy.Adapt,
            direction = ShiftDirection.None,
            shiftHours = delta,
            daysToAdapt = days,
            daysWithout = days,
            segmentTargets = listOf(destOff),
        )
    }

    // ------------------------------------------------------------------------------------------------ cards

    private fun adviceFromCycles(cycles: List<Cycle>): List<RawAdvice> {
        val out = ArrayList<RawAdvice>()
        val habitualWakeLength = 24.0 - sleepDuration
        for ((i, c) in cycles.withIndex()) {
            val lightReason = if (c.direction == Direction.Advance) AdviceReason.LightAdvancesClock else AdviceReason.LightDelaysClock
            val sleepReason = if (c.sleepClampHours != 0.0) AdviceReason.DestinationSleep else AdviceReason.ShiftedSleep
            out += sleepCards(c.sleepParts, sleepReason)
            c.seek.forEach { out += RawAdvice(AdviceType.SeeBrightLight, it, lightReason) }
            c.avoid.forEach { out += RawAdvice(AdviceType.AvoidLight, it, AdviceReason.AvoidCounterShift) }
            val see = if (c.direction == Direction.Advance) {
                Interval(c.t + config.seekLength, c.t + config.seekLength + config.seeLightLength)
            } else {
                Interval(c.t - config.seekLength - config.seeLightLength, c.t - config.seekLength)
            }
            out += RawAdvice(AdviceType.SeeLight, see, lightReason)
            c.melatonin?.let {
                val reason = if (c.direction == Direction.Advance) AdviceReason.MelatoninAdvances else AdviceReason.MelatoninDelays
                out += RawAdvice(AdviceType.Melatonin, Interval(it, it), reason, config.melatoninDose)
            }
            out += fatigue(c.t)
            if (i < cycles.lastIndex) {
                val w = c.sleep.end
                val s = cycles[i + 1].sleep.start
                if (profile.useCaffeine) {
                    c.caffeineUse.forEach { out += RawAdvice(AdviceType.Caffeine, it, AdviceReason.AlertnessSupport) }
                    val cut = if (c.direction == Direction.Advance) config.caffeineCutoffAdvance else config.caffeineCutoff
                    if (s > w) out += RawAdvice(AdviceType.AvoidCaffeine, Interval(maxOf(w, s - cut), s), AdviceReason.ProtectSleep)
                }
                c.nap?.let {
                    val type = if (s - w > habitualWakeLength + config.napRecommendedExtraWake) AdviceType.Nap else AdviceType.OptionalNap
                    out += RawAdvice(type, it, AdviceReason.SleepPressure)
                }
            }
        }
        return out
    }

    /** Sleep parts, with in-flight parts turned into "rest in the dark" when the user can't sleep on planes. */
    private fun sleepCards(parts: List<Interval>, reason: AdviceReason): List<RawAdvice> = parts.filter { it.length > EPS }.flatMap { p ->
        subtract(p, rest).map { RawAdvice(AdviceType.Sleep, it, reason) } +
            rest.mapNotNull { p.intersect(it) }.map { RawAdvice(AdviceType.AvoidLight, it, AdviceReason.RestInFlight) }
    }

    private fun fatigue(t: Double) = RawAdvice(AdviceType.PeakFatigue, Interval(t - config.peakFatigueBefore, t + config.peakFatigueAfter), AdviceReason.CircadianLow)

    private fun caffeineCards(w: Double, s: Double, cut: Double, cbts: List<Double>): List<RawAdvice> {
        if (s <= w) return emptyList()
        val out = ArrayList<RawAdvice>()
        for (t in cbts) {
            val a = maxOf(t - 7.0, w)
            val b = minOf(t + 3.0, s - cut)
            if (b - a > 0.25) out += RawAdvice(AdviceType.Caffeine, Interval(a, b), AdviceReason.AlertnessSupport)
        }
        out += RawAdvice(AdviceType.AvoidCaffeine, Interval(maxOf(w, s - cut), s), AdviceReason.ProtectSleep)
        return out
    }

    private fun flightMarkers(): List<RawAdvice> = sortedLegs.mapIndexed { i, leg ->
        RawAdvice(
            AdviceType.Flight,
            Interval(legs[i].dep, legs[i].arr),
            AdviceReason.TravelMarker,
            leg.flightNumber ?: "${leg.origin.displayCode}→${leg.destination.displayCode}",
        )
    }

    // ------------------------------------------------------------------------------------------------ helpers

    private fun preDaysFor(j: Int): Int {
        if (j == 0) return config.preFlightDays
        val stay = segments[j].first().dep - segments[j - 1].last().arr
        return (floor(stay / 24.0).toInt() - 2).coerceIn(0, config.preFlightDays)
    }

    private fun layoverBlock(a: LegHours, b: LegHours) = Interval(a.arr - config.arrivalSleepEnd, b.dep + config.postDepartureNoSleep)

    private fun layoverBlocks(seg: List<LegHours>): List<Interval> =
        if (config.blockLayoverSleep) seg.zipWithNext { a, b -> if (b.dep - a.arr < 24.0) layoverBlock(a, b) else null }.filterNotNull() else emptyList()

    private fun transitBlocks() = listOf(
        Interval(dep - config.preDepartureWake, dep + config.postDepartureNoSleep),
        Interval(arr - config.arrivalSleepEnd, arr + config.postArrivalNoSleep),
    )

    private fun returnBlock(): List<Interval> =
        returnH?.let { listOf(Interval(it - config.preDepartureWake, it + config.postDepartureNoSleep)) } ?: emptyList()

    /** §10.3: a 24–72 h stopover against the direction of travel holds the clock instead of shifting it. */
    private fun oppositeStopovers(seg: List<LegHours>, body: Double): List<Interval> {
        val finalDelta = norm12(seg.last().arrOffset - body)
        return seg.zipWithNext { a, b ->
            val stopDelta = norm12(a.arrOffset - body)
            if (b.dep - a.arr >= 24.0 && stopDelta * finalDelta < 0) Interval(a.arr, b.dep) else null
        }.filterNotNull()
    }

    /** §13.7 Tier 2: simulate both directions with the estimate model; ties go to the threshold default. */
    private fun tier2Direction(input: SegmentInput): Direction? {
        val a = mod24(norm12(input.legs.last().arrOffset - input.bodyOffset))
        if (a < config.tier2Min || a > config.tier2Max) return null
        val itinerary = Itinerary(input.bodyOffset, input.legs)
        fun days(d: Direction): Double {
            val sp = planner.plan(input.copy(forcedDirection = d))
            if (sp.cycles.isEmpty()) return Double.MAX_VALUE
            return validator.validate(itinerary, LightPlan.of(sp.cycles, rest), horizon).plan.adaptDays ?: (horizon + 1.0)
        }
        val adv = days(Direction.Advance)
        val del = days(Direction.Delay)
        return when {
            abs(adv - del) <= config.tier2TieDays -> planner.defaultDirection(a, input.chronotype)
            adv < del -> Direction.Advance
            else -> Direction.Delay
        }
    }

    // ------------------------------------------------------------------------------------------------ assembly

    private fun assemble(
        raw: List<RawAdvice>,
        track: PhaseTrack,
        strategy: AdaptationStrategy,
        direction: ShiftDirection,
        shiftHours: Double,
        daysToAdapt: Double,
        daysWithout: Double,
        segmentTargets: List<Double>?,
    ): JetLagPlan {
        var items = assembler.resolve(raw)
        returnH?.let { r ->
            items = items.mapNotNull { a ->
                when {
                    a.type == AdviceType.Flight -> a
                    a.start >= r -> null
                    a.end > r -> a.copy(interval = Interval(a.start, r))
                    else -> a
                }
            }
        }
        items = assembler.practicality(items)

        val advice = items.map { Triple(it, inst(it.start), inst(it.end)) }
        val minStart = advice.minOf { it.second }
        val maxInside = advice.maxOf { (a, s, e) -> if (a.type.isMoment || e == s) s else e.minusNanos(1) }
        val groups = travelGroups.map { range ->
            val first = range.first
            val last = range.last
            val segIndex = segments.indexOfFirst { seg -> seg.last() == legs[last] }
            TravelGroup(
                dep = inst(legs[first].dep),
                arr = inst(legs[last].arr),
                fromZone = sortedLegs[first].origin.zone,
                toZone = sortedLegs[last].destination.zone,
                stayKind = { s, e ->
                    val target = segmentTargets?.getOrNull(segIndex)
                    if (target == null || segIndex < 0) {
                        DayKind.Arrival
                    } else {
                        val mid = h(s) + (h(e) - h(s)) / 2
                        if (abs(norm12(target - track.bodyOffset(mid))) <= config.doneTolerance + EPS) DayKind.Adapted else DayKind.Arrival
                    }
                },
            )
        }
        val spans = DayBuilder.build(originZone, groups, minStart, maxInside)
        this.spans = spans

        val byDay = advice.groupBy { (_, s, _) -> spans.indexOfLast { !s.isBefore(it.start) }.coerceAtLeast(0) }
        val days = spans.mapIndexed { i, span ->
            val entries = (byDay[i] ?: emptyList()).sortedWith(
                compareBy<Triple<RawAdvice, Instant, Instant>> { it.second }.thenBy { it.first.type.ordinal }.thenBy { it.third },
            )
            val seq = HashMap<AdviceType, Int>()
            val cards = entries.map { (a, s, e) ->
                val n = seq.merge(a.type, 1, Int::plus)!! - 1
                Advice(stableId(span.index, a.type, n), a.type, s, e, a.reason, a.detail)
            }
            PlanDay(span.index, span.kind, span.date, span.zone.id, cards)
        }

        val phase = phasePoints(track, spans.first().start, spans.last().end)
        return JetLagPlan(
            tripId = trip.id,
            generatedAt = now,
            strategy = strategy,
            direction = direction,
            shiftHours = shiftHours,
            originZoneId = originZone.id,
            destinationZoneId = destZone.id,
            days = days,
            phase = phase,
            estimatedDaysToAdapt = daysToAdapt,
            estimatedDaysWithoutPlan = daysWithout,
        )
    }

    private fun phasePoints(track: PhaseTrack, from: Instant, to: Instant): List<PhasePoint> {
        val count = Duration.between(from, to).toHours().toInt()
        val instants = (0..count).map { from.plus(Duration.ofHours(it.toLong())) }
        val offsets = instants.map { track.bodyOffset(h(it)) }
        // keep the stored offsets continuous when they fit ZoneOffset's ±18 h, else wrap point by point
        val shift = listOf(0, 1, -1).firstOrNull { k -> offsets.all { it + 24 * k in -18.0..18.0 } }
        return instants.mapIndexed { i, instant ->
            val b = offsets[i]
            val stored = if (shift != null) b + 24 * shift else norm12(b)
            PhasePoint(instant, (stored * 60).roundToInt(), inst(track.nearestCbtMin(h(instant))))
        }
    }

    private fun stableId(dayIndex: Int, type: AdviceType, seq: Int): String {
        val digest = MessageDigest.getInstance("SHA-256").digest("${trip.id}|${type.name}|$dayIndex|$seq".toByteArray())
        return digest.take(8).joinToString("") { "%02x".format(it) }
    }

    private fun sanitizedLegs(): List<LegHours> {
        val out = ArrayList<LegHours>()
        var prevArr = Double.NEGATIVE_INFINITY
        for (leg in sortedLegs) {
            val d = maxOf(h(leg.departure), prevArr)
            val a = maxOf(h(leg.arrival), d + MIN_LEG_HOURS)
            out += LegHours(d, a, offsetHours(leg.origin.zone, leg.departure), offsetHours(leg.destination.zone, leg.arrival))
            prevArr = a
        }
        return out
    }

    private fun offsetHours(zone: ZoneId, at: Instant) = zone.rules.getOffset(at).totalSeconds / 3600.0

    private fun h(i: Instant): Double = (i.epochSecond - ref.epochSecond) / 3600.0 + i.nano / 3.6e12

    private fun inst(hours: Double): Instant = ref.plusMillis((hours * 3_600_000.0).roundToLong())

    companion object {
        /** Flights shorter than this (or with arrival before departure: bad data) are padded to it. */
        const val MIN_LEG_HOURS: Double = 1.0 / 60.0
    }
}
