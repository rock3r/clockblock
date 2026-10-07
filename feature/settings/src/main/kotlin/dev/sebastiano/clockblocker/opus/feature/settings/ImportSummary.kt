package dev.sebastiano.clockblocker.opus.feature.settings

import dev.sebastiano.clockblocker.opus.core.data.backup.ImportResult

/** What an import's confirmation should talk about. */
internal enum class ImportSummary { Trips, CheckIns, Profile, NothingNew }

/**
 * A replace always reports its trips. A merge reports trips when it added any; otherwise what it did add (check-ins,
 * then the profile), so a merge that changed data never reads "Imported 0 trips".
 */
internal fun ImportResult.summary(): ImportSummary = when {
    !merged || tripsImported > 0 -> ImportSummary.Trips
    logsImported > 0 -> ImportSummary.CheckIns
    profileImported -> ImportSummary.Profile
    else -> ImportSummary.NothingNew
}
