package dev.sebastiano.clockblocker.opus.core.notifications.text

import dev.sebastiano.clockblocker.opus.core.model.AdviceOutcome
import dev.sebastiano.clockblocker.opus.core.model.AdviceType

/** Plain English copy for pure formatter tests (the real copy lives in string resources). */
object EnglishStrings : NotificationStrings {
    override fun label(type: AdviceType) = when (type) {
        AdviceType.SeeBrightLight -> "See bright light"
        AdviceType.SeeLight -> "See some light"
        AdviceType.AvoidLight -> "Avoid light"
        AdviceType.Sleep -> "Sleep"
        AdviceType.Nap -> "Nap"
        AdviceType.OptionalNap -> "Optional nap"
        AdviceType.Melatonin -> "Melatonin"
        AdviceType.Caffeine -> "Caffeine OK"
        AdviceType.AvoidCaffeine -> "Avoid caffeine"
        AdviceType.PeakFatigue -> "Peak fatigue"
        AdviceType.Flight -> "In flight"
    }

    override fun tip(type: AdviceType) = "tip:${type.name}"
    override fun until(time: String) = "until $time"
    override fun then(label: String, range: String) = "then $label $range"
    override fun next(label: String, range: String) = "Next: $label $range"
    override fun nothingNow() = "Nothing right now"
    override fun outcome(outcome: AdviceOutcome) = outcome.name
    override fun join(first: String, second: String) = "$first · $second"
    override fun inZone(city: String, text: String) = "$city: $text"
    override fun upcomingTitle(label: String, time: String) = "$label at $time"
    override fun nowTitle(label: String) = "$label now"
    override fun wakeUpTitle(type: AdviceType) = "${label(type)} over"
    override fun wakeUpFallback() = "Time to get up."
    override fun nowPrefix(label: String) = "Now: $label"
    override fun snoozedTitle(label: String) = "Reminder: $label"
    override fun also(labels: String) = "Also: $labels"
    override fun testTitle() = "Test reminder"
    override fun testText() = "It works."
}
