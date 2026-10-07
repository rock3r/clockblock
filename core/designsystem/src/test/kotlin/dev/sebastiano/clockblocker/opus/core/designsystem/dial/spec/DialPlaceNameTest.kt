package dev.sebastiano.clockblocker.opus.core.designsystem.dial.spec

import dev.sebastiano.clockblocker.opus.core.designsystem.dial.DialPalettes
import dev.sebastiano.clockblocker.opus.core.designsystem.dial.DialState
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.time.Instant

/** The dial names the place from the trip's stop, falling back to the zone's city when it has none or it won't fit. */
class DialPlaceNameTest {

    private val labels = DefaultDialLabels(is24Hour = true)

    /** Tromsø keeps Oslo's zone id: 13:00, body 3 h behind. */
    private val tromso = DialState(
        instant = Instant.parse("2026-02-10T12:00:00Z"),
        displayZoneId = "Europe/Oslo",
        localMinute = 13 * 60f,
        bodyAheadMinutes = -180f,
        sunriseMinute = 519f,
        sunsetMinute = 919f,
        placeName = "Tromsø",
    )

    private fun texts(state: DialState, side: Float = 328f): List<String> =
        TwoSkies.spec(state, DialPalettes.Light, labels, side, side).ops.mapNotNull {
            when (it) {
                is DialOp.Text -> it.text
                is DialOp.CurvedText -> it.text
                else -> null
            }
        }

    @Test
    fun `the full dial names the stop, not the zone`() {
        val texts = texts(tromso)
        texts shouldContainAll listOf("TROMSØ", "TROMSØ DAY", "TROMSØ NIGHT")
        texts.filter { "OSLO" in it } shouldBe emptyList()
    }

    @Test
    fun `the simple dial names the stop on its ring`() {
        texts(tromso, side = 180f) shouldContain "TROMSØ"
    }

    @Test
    fun `a name too long for the dial falls back to the zone's city`() {
        val long = tromso.copy(displayZoneId = "Asia/Shanghai", placeName = "Qian Gorlos Mongol Autonomous County")
        val texts = texts(long)
        texts shouldContainAll listOf("SHANGHAI", "SHANGHAI DAY", "SHANGHAI NIGHT")
        texts.filter { "QIAN" in it } shouldBe emptyList()
    }

    @Test
    fun `without a stop the dial names the zone's city`() {
        val texts = texts(tromso.copy(placeName = null))
        texts shouldContain "OSLO"
        texts shouldNotContain "TROMSØ"
    }
}
