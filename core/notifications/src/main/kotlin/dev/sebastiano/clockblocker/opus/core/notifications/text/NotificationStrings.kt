package dev.sebastiano.clockblocker.opus.core.notifications.text

import dev.sebastiano.clockblocker.opus.core.model.AdviceOutcome
import dev.sebastiano.clockblocker.opus.core.model.AdviceType

/**
 * The copy used by notifications. Implemented by [ResourceNotificationStrings] (string resources, translatable);
 * kept as an interface so [NotificationTextFormatter] is a pure, JVM-testable function of plan state.
 */
interface NotificationStrings {
    /** Short card label, e.g. "Avoid light". Always shown; an icon never stands alone. */
    fun label(type: AdviceType): String

    /** One-line, kind, practical tip in the app's voice. */
    fun tip(type: AdviceType): String

    /** "until 18:00" */
    fun until(time: String): String

    /** "Next: Sleep at 23:00" (the plan screen's wording). */
    fun next(label: String, time: String): String

    /** "Also now: Avoid caffeine until 20:00" (the plan screen's wording); [items] already joined. */
    fun alsoNow(items: String): String

    /** "Avoid caffeine until 20:00", an item of [alsoNow]. */
    fun labelUntil(label: String, time: String): String

    /** Title in a gap between windows. */
    fun nothingNow(): String

    /** "Done", "Noted, skip it"… */
    fun outcome(outcome: AdviceOutcome): String

    /** Joins clauses: "until 18:00 · 02:00 Tokyo". */
    fun join(first: String, second: String): String

    /** A time in the trip's other zone, as a tail: "02:00 Tokyo" (like the plan screen's "10:00 San Francisco"). */
    fun zoneTail(time: String, city: String): String

    /** "Avoid light at 18:00" */
    fun upcomingTitle(label: String, time: String): String

    /** "Avoid light now" */
    fun nowTitle(label: String): String

    /** "Sleep window over" / "Nap over" */
    fun wakeUpTitle(type: AdviceType): String

    /** Wake-up text when nothing is planned right after. */
    fun wakeUpFallback(): String

    /** "Now: See bright light" */
    fun nowPrefix(label: String): String

    /** "Reminder: Avoid light" */
    fun snoozedTitle(label: String): String

    /** "Also: Caffeine OK" */
    fun also(labels: String): String

    fun testTitle(): String
    fun testText(): String

    /** "Body 3½ h behind" (local time); [hours] is already formatted ("3½ h"). */
    fun bodyBehind(hours: String): String

    /** "Body 2 h ahead" */
    fun bodyAhead(hours: String): String

    /** The body clock within half an hour of local time. */
    fun bodyInSync(): String

    /** "SFO → LHR" */
    fun route(from: String, to: String): String

    /** "Departs 12:00" (travel day, before the first take-off). */
    fun departs(time: String): String

    /** "Lands 19:00" (on board). */
    fun lands(time: String): String

    /** "Next flight 21:00" (between legs). */
    fun nextFlight(time: String): String

    /** After the last landing. */
    fun landed(): String

    /** Stands in for advice that shouldn't be named on the lock screen (melatonin). */
    fun redactedLabel(): String

    /** Body of a lock-screen version with nothing else safe to say. */
    fun redactedText(): String
}
