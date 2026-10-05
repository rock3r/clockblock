package dev.sebastiano.clockblocker.opus.core.circadian.engine

import dev.sebastiano.clockblocker.opus.core.circadian.ChronotypeClass
import dev.sebastiano.clockblocker.opus.core.circadian.CircadianMath.mod24
import dev.sebastiano.clockblocker.opus.core.circadian.PlannerConfig
import io.kotest.matchers.shouldBe
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import java.util.Locale

/**
 * Bit-for-bit port check: the Kotlin cycle planner must print exactly what `reference/planner.py`'s
 * `describe()` printed for the four worked itineraries (§14), for every chronotype (`ex_*.txt`).
 */
class CyclePlannerGoldenTest {

    /** The itineraries of `reference/examples.py`, in UTC hours from 00:00 UTC of the departure date. */
    private val examples = listOf(
        Triple("SFO-LHR (2026-06-15, +8h)", -7.0, listOf(LegHours(23.5, 33.75, -7.0, 1.0))),
        Triple("LHR-SIN-SYD (2026-11-10, +11h)", 0.0, listOf(LegHours(21.0, 33.5, 0.0, 8.0), LegHours(36.0, 43.5, 8.0, 11.0))),
        Triple("JFK-LAX (2026-03-20, -3h)", -4.0, listOf(LegHours(12.0, 18.33, -4.0, -7.0))),
        Triple("NRT-JFK (2026-01-15, -14h)", 9.0, listOf(LegHours(2.0, 15.5, 9.0, -5.0))),
    )

    // The reference does not block sleep across intermediate layovers; the app does by default.
    private val planner = CyclePlanner(PlannerConfig(blockLayoverSleep = false))

    @ParameterizedTest
    @EnumSource(ChronotypeClass::class)
    fun `matches the reference planner output`(chrono: ChronotypeClass) {
        val name = chrono.name.lowercase()
        val expected = javaClass.getResource("/reference/ex_$name.txt")!!.readText().lines().map { it.trimEnd() }.filter { it.isNotEmpty() }
        val actual = examples.flatMap { (title, homeOffset, legs) ->
            val plan = planner.plan(
                SegmentInput(
                    bodyOffset = homeOffset,
                    legs = legs,
                    habitualOnset = 23.0,
                    habitualWake = 7.0,
                    chronotype = chrono,
                    preDays = 3,
                    useMelatonin = true,
                ),
            )
            listOf("===== $title $name") + describe(plan, homeOffset, legs.last().arrOffset).lines()
        }
        actual.joinToString("\n") shouldBe expected.joinToString("\n")
    }

    companion object {
        private fun fmt(h: Double): String {
            val m = Math.floorMod(Math.rint(mod24(h) * 60).toLong(), 1440L)
            return String.format(Locale.ROOT, "%02d:%02d", m / 60, m % 60)
        }

        private fun pyList(items: List<Interval>, off: Double) =
            items.joinToString(", ", "[", "]") { "'${fmt(it.start + off)}-${fmt(it.end + off)}'" }

        /** Port of `planner.describe()`. */
        internal fun describe(p: SegmentPlan, homeOffset: Double, destOffset: Double): String {
            val sb = StringBuilder()
            sb.append(String.format(Locale.ROOT, "delta=%+.1fh mode=plan", p.delta))
            sb.append(" direction=${p.direction.name.lowercase()}")
            sb.append(String.format(Locale.ROOT, " target_phi=%+.1f T0(home clock)=%s", p.targetPhi, fmt(p.t0Clock)))
            for (c in p.cycles) {
                val off = if (c.where == Where.Home) homeOffset else destOffset
                val tag = when (c.where) {
                    Where.Home -> "home"
                    Where.Dest -> "dest"
                    Where.Flight -> "flight(dest clock)"
                }
                sb.append('\n')
                sb.append(String.format(Locale.ROOT, " k=%2d %-18s phi=%+5.1f", c.k, tag, c.phi))
                sb.append(" CBTmin=${fmt(c.t + off)} sleep=${fmt(c.sleep.start + off)}-${fmt(c.sleep.end + off)}")
                sb.append(" seek=${pyList(c.seek, off)} avoid=${pyList(c.avoid, off)}")
                c.melatonin?.let { sb.append(" mel=${fmt(it + off)}") }
                c.caffeineOk?.let { sb.append(" caf_ok=${fmt(it.start + off)}-${fmt(it.end + off)}") }
                c.nap?.let { sb.append(" nap=${fmt(it.start + off)}") }
                val notes = buildList {
                    if (kotlin.math.abs(c.sleepClampHours) > 1e-6) {
                        add(String.format(Locale.ROOT, "sleep clamped by %+.1fh", c.sleepClampHours))
                    }
                    if (c.sleepTruncated) {
                        add("sleep split/truncated by travel: " + c.sleepParts.joinToString(",") {
                            String.format(Locale.ROOT, "%.2f-%.2f", it.start, it.end)
                        })
                    }
                }
                if (notes.isNotEmpty()) sb.append(' ').append(notes.joinToString(";"))
            }
            return sb.toString()
        }
    }
}
