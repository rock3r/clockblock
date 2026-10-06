package dev.sebastiano.clockblocker.opus.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class ThemeMode { System, Light, Dark }

@Serializable
data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.System,
    val dynamicColor: Boolean = true,
    /** In-app motion reduction on top of the system "Remove animations" setting. */
    val reduceMotion: Boolean = false,
    /** Dim, dark, calm UI automatically while the plan says Avoid light / Sleep. */
    val nightSafeAuto: Boolean = true,
    val remindersEnabled: Boolean = true,
    /** Minutes before an advice window starts to notify. */
    val reminderLeadMinutes: Int = 15,
    /** Easter egg: "Opus No. 1 in Jet-Lag Minor" concert theme, unlocked from About. */
    val opusModeUnlocked: Boolean = false,
    val opusModeEnabled: Boolean = false,
    /**
     * Keep places, flight numbers and supplements off the lock screen; times and labels stay. Notifications are
     * posted private with a redacted public version; keyguard widgets should honour it too.
     */
    val hideLockScreenDetails: Boolean = false,
)

/** Logged user feedback on an advice card. */
@Serializable
enum class AdviceOutcome { Done, Skipped, CantDo }

@Serializable
data class AdviceLog(val adviceId: String, val outcome: AdviceOutcome)
