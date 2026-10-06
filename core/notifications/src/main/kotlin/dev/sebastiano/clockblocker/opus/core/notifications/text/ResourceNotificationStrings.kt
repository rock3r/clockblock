package dev.sebastiano.clockblocker.opus.core.notifications.text

import android.content.Context
import dev.sebastiano.clockblocker.opus.core.model.AdviceOutcome
import dev.sebastiano.clockblocker.opus.core.model.AdviceType
import dev.sebastiano.clockblocker.opus.core.notifications.R

/** [NotificationStrings] backed by string resources (and therefore by the device locale). */
internal class ResourceNotificationStrings(private val context: Context) : NotificationStrings {

    override fun label(type: AdviceType): String = context.getString(
        when (type) {
            AdviceType.SeeBrightLight -> R.string.advice_label_see_bright_light
            AdviceType.SeeLight -> R.string.advice_label_see_light
            AdviceType.AvoidLight -> R.string.advice_label_avoid_light
            AdviceType.Sleep -> R.string.advice_label_sleep
            AdviceType.Nap -> R.string.advice_label_nap
            AdviceType.OptionalNap -> R.string.advice_label_optional_nap
            AdviceType.Melatonin -> R.string.advice_label_melatonin
            AdviceType.Caffeine -> R.string.advice_label_caffeine
            AdviceType.AvoidCaffeine -> R.string.advice_label_avoid_caffeine
            AdviceType.PeakFatigue -> R.string.advice_label_peak_fatigue
            AdviceType.Flight -> R.string.advice_label_flight
        },
    )

    override fun tip(type: AdviceType): String = context.getString(
        when (type) {
            AdviceType.SeeBrightLight -> R.string.advice_tip_see_bright_light
            AdviceType.SeeLight -> R.string.advice_tip_see_light
            AdviceType.AvoidLight -> R.string.advice_tip_avoid_light
            AdviceType.Sleep -> R.string.advice_tip_sleep
            AdviceType.Nap -> R.string.advice_tip_nap
            AdviceType.OptionalNap -> R.string.advice_tip_optional_nap
            AdviceType.Melatonin -> R.string.advice_tip_melatonin
            AdviceType.Caffeine -> R.string.advice_tip_caffeine
            AdviceType.AvoidCaffeine -> R.string.advice_tip_avoid_caffeine
            AdviceType.PeakFatigue -> R.string.advice_tip_peak_fatigue
            AdviceType.Flight -> R.string.advice_tip_flight
        },
    )

    override fun until(time: String): String = context.getString(R.string.now_until, time)
    override fun then(label: String, range: String): String = context.getString(R.string.now_then, label, range)
    override fun next(label: String, range: String): String = context.getString(R.string.now_next, label, range)
    override fun nothingNow(): String = context.getString(R.string.now_nothing)

    override fun outcome(outcome: AdviceOutcome): String = context.getString(
        when (outcome) {
            AdviceOutcome.Done -> R.string.outcome_done
            AdviceOutcome.Skipped -> R.string.outcome_skipped
            AdviceOutcome.CantDo -> R.string.outcome_cant_do
        },
    )

    override fun join(first: String, second: String): String = context.getString(R.string.now_join, first, second)
    override fun inZone(city: String, text: String): String = context.getString(R.string.now_in_zone, city, text)

    override fun upcomingTitle(label: String, time: String): String =
        context.getString(R.string.reminder_upcoming_title, label, time)

    override fun nowTitle(label: String): String = context.getString(R.string.reminder_now_title, label)

    override fun wakeUpTitle(type: AdviceType): String = context.getString(
        if (type == AdviceType.Sleep) R.string.reminder_wake_sleep_title else R.string.reminder_wake_nap_title,
    )

    override fun wakeUpFallback(): String = context.getString(R.string.reminder_wake_fallback)
    override fun nowPrefix(label: String): String = context.getString(R.string.now_prefix, label)
    override fun snoozedTitle(label: String): String = context.getString(R.string.reminder_snoozed_title, label)
    override fun also(labels: String): String = context.getString(R.string.reminder_also, labels)
    override fun testTitle(): String = context.getString(R.string.reminder_test_title)
    override fun testText(): String = context.getString(R.string.reminder_test_text)
    override fun bodyBehind(hours: String): String = context.getString(R.string.body_behind, hours)
    override fun bodyAhead(hours: String): String = context.getString(R.string.body_ahead, hours)
    override fun bodyInSync(): String = context.getString(R.string.body_in_sync)
    override fun route(from: String, to: String): String = context.getString(R.string.travel_route, from, to)
    override fun departs(time: String): String = context.getString(R.string.travel_departs, time)
    override fun lands(time: String): String = context.getString(R.string.travel_lands, time)
    override fun nextFlight(time: String): String = context.getString(R.string.travel_next_flight, time)
    override fun landed(): String = context.getString(R.string.travel_landed)
    override fun redactedLabel(): String = context.getString(R.string.redacted_label)
    override fun redactedText(): String = context.getString(R.string.redacted_text)
}
