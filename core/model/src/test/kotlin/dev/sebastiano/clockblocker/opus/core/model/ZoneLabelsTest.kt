package dev.sebastiano.clockblocker.opus.core.model

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Locale

class ZoneLabelsTest {
    private val summer = Instant.parse("2026-07-01T12:00:00Z")
    private val winter = Instant.parse("2026-12-01T12:00:00Z")

    @Test
    fun `region ids read as their city`() {
        ZoneLabels.city(ZoneId.of("Europe/Lisbon")) shouldBe "Lisbon"
        ZoneLabels.city(ZoneId.of("America/Argentina/Buenos_Aires")) shouldBe "Buenos Aires"
        ZoneLabels.city("Asia/Tokyo") shouldBe "Tokyo"
    }

    @Test
    fun `offset-only zones never leak raw ids like Z`() {
        ZoneLabels.city(ZoneOffset.UTC) shouldBe "GMT"
        ZoneLabels.city("Z") shouldBe "GMT"
        ZoneLabels.city("UTC") shouldBe "GMT"
        ZoneLabels.city("Etc/UTC") shouldBe "GMT"
        ZoneLabels.city("GMT") shouldBe "GMT"
        ZoneLabels.city(ZoneOffset.ofHoursMinutes(5, 30)) shouldBe "GMT+5:30"
        // POSIX sign inversion: Etc/GMT+5 is five hours *behind* GMT.
        ZoneLabels.city("Etc/GMT+5") shouldBe "GMT\u22125"
        ZoneLabels.isOffsetOnly(ZoneId.of("Europe/London")) shouldBe false
        ZoneLabels.isOffsetOnly(ZoneId.of("Etc/UTC")) shouldBe true
    }

    @Test
    fun `offsets read GMT style with a real minus sign`() {
        ZoneLabels.offset(ZoneId.of("Europe/London"), winter) shouldBe "GMT"
        ZoneLabels.offset(ZoneId.of("Europe/London"), summer) shouldBe "GMT+1"
        ZoneLabels.offset(ZoneId.of("Asia/Kolkata"), summer) shouldBe "GMT+5:30"
        ZoneLabels.offset(ZoneId.of("America/St_Johns"), winter) shouldBe "GMT\u22123:30"
    }

    @Test
    fun `long names follow daylight saving`() {
        ZoneLabels.longName(ZoneId.of("Europe/London"), summer, Locale.UK) shouldBe "British Summer Time"
        ZoneLabels.longName(ZoneId.of("Europe/London"), winter, Locale.UK) shouldBe "Greenwich Mean Time"
        // Nothing useful to add for offset-only zones: the offset label already says it all.
        ZoneLabels.longName(ZoneOffset.UTC, summer, Locale.UK) shouldBe null
    }
}
