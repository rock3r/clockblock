package dev.sebastiano.clockblocker.opus.core.model

import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.boolean
import io.kotest.property.arbitrary.int
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.junit.jupiter.api.Test

/** The head start migration: the old `adjustBeforeDeparture` switch becomes a number of days. */
class UserProfileSerializationTest {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private fun legacy(adjust: Boolean) =
        """{"homeZoneId":"Europe/Lisbon","sleep":{"bedtime":"00:30","wake":"08:15"},"chronotype":"ModerateEvening",""" +
            """"useMelatonin":false,"useCaffeine":true,"canSleepOnPlanes":true,"adjustBeforeDeparture":$adjust,"intensity":"Gentle"}"""

    @Test
    fun `a profile saved with the switch on gets the old default head start`() {
        val profile = json.decodeFromString(UserProfile.serializer(), legacy(adjust = true))
        profile.headStartDays shouldBe HeadStart.DefaultDays
        profile.chronotype shouldBe Chronotype.ModerateEvening
        profile.intensity shouldBe Intensity.Gentle
    }

    @Test
    fun `a profile saved with the switch off gets no head start`() {
        json.decodeFromString(UserProfile.serializer(), legacy(adjust = false)).headStartDays shouldBe 0
    }

    @Test
    fun `a profile with neither field gets the defaults`() {
        val profile = json.decodeFromString(UserProfile.serializer(), """{"homeZoneId":"Asia/Tokyo"}""")
        profile shouldBe UserProfile(homeZoneId = "Asia/Tokyo")
        profile.headStartDays shouldBe HeadStart.DefaultDays
        profile.napsOk shouldBe true
    }

    @Test
    fun `profiles round-trip and write a compatible switch for older builds`() = runTest {
        checkAll(Arb.int(HeadStart.Choices.first()..HeadStart.Choices.last()), Arb.boolean()) { days, naps ->
            val profile = UserProfile(homeZoneId = "Europe/London", headStartDays = days, napsOk = naps)
            val text = json.encodeToString(UserProfile.serializer(), profile)
            json.parseToJsonElement(text).jsonObject["adjustBeforeDeparture"] shouldBe JsonPrimitive(days > 0)
            json.decodeFromString(UserProfile.serializer(), text) shouldBe profile
        }
    }

    @Test
    fun `the day count wins over a leftover switch, and is clamped to the choices`() = runTest {
        checkAll(Arb.int(-10..10), Arb.boolean()) { days, adjust ->
            val text = """{"homeZoneId":"Europe/London","adjustBeforeDeparture":$adjust,"headStartDays":$days}"""
            json.decodeFromString(UserProfile.serializer(), text).headStartDays shouldBe days.coerceIn(0, HeadStart.MaxDays)
        }
    }
}
