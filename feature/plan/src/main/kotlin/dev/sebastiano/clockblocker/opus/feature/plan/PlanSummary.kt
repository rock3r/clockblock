package dev.sebastiano.clockblocker.opus.feature.plan

import android.content.res.Resources
import dev.sebastiano.clockblocker.opus.core.designsystem.advice.labelRes
import dev.sebastiano.clockblocker.opus.core.designsystem.dial.formatJetLagHours
import dev.sebastiano.clockblocker.opus.core.designsystem.time.TimeFormatter
import dev.sebastiano.clockblocker.opus.core.designsystem.time.cityName
import dev.sebastiano.clockblocker.opus.core.model.JetLagPlan
import dev.sebastiano.clockblocker.opus.core.model.Trip
import java.time.ZoneId

/**
 * Plain-text summary of a plan for "Share summary": title, the shift, then every day's blocks as
 * "07:00 – 09:00  See bright light" in that day's zone, and the not-medical-advice footer.
 */
internal fun buildPlanSummary(resources: Resources, formatter: TimeFormatter, plan: JetLagPlan, trip: Trip?): String =
    buildString {
        appendLine(resources.getString(R.string.plan_share_header, planTitle(plan, trip)))
        if (plan.kind == PlanKind.Adapt) {
            appendLine(
                resources.getString(
                    R.string.plan_share_shift,
                    formatJetLagHours(plan.shiftHours.toFloat()),
                    roundDays(plan.estimatedDaysToAdapt).toString(),
                ),
            )
        }
        plan.days.forEach { day ->
            val zone = ZoneId.of(day.zoneId)
            appendLine()
            appendLine(resources.dayTitle(day) + " · " + formatDayDate(day.date) + " · " + zone.cityName())
            day.advice.sortedBy { it.start }.forEach { advice ->
                val detail = advice.detail?.let { " ($it)" }.orEmpty()
                appendLine("  " + formatter.range(advice.start, advice.end, zone, resources) + "  " + resources.getString(advice.type.labelRes) + detail)
            }
        }
        appendLine()
        append(resources.getString(R.string.plan_share_footer))
    }
