package dev.sebastiano.clockblocker.opus.feature.settings

import dev.sebastiano.clockblocker.opus.core.data.backup.ImportResult
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class ImportSummaryTest {
    @Test
    fun `a replace always reports trips, even none`() {
        ImportResult(tripsImported = 0, tripsDeleted = 2, logsImported = 0).summary() shouldBe ImportSummary.Trips
    }

    @Test
    fun `a merge that added trips reports trips`() {
        ImportResult(1, 0, 3, profileImported = true, merged = true).summary() shouldBe ImportSummary.Trips
    }

    @Test
    fun `a merge that added only check-ins says so`() {
        ImportResult(0, 0, 2, merged = true).summary() shouldBe ImportSummary.CheckIns
    }

    @Test
    fun `a merge that added only the profile says so`() {
        ImportResult(0, 0, 0, profileImported = true, merged = true).summary() shouldBe ImportSummary.Profile
    }

    @Test
    fun `a merge that added nothing says nothing was new`() {
        ImportResult(0, 0, 0, merged = true).summary() shouldBe ImportSummary.NothingNew
    }
}
