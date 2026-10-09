package dev.sebastiano.clockblocker.opus.core.model

import io.kotest.matchers.longs.shouldBeLessThanOrEqual
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.property.Arb
import io.kotest.property.arbitrary.numericDouble
import io.kotest.property.arbitrary.localDate
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.math.abs

/**
 * Fixtures from the US Naval Observatory (aa.usno.navy.mil, fetched October 2026) at airports in `places.tsv`, as
 * local wall-clock times rounded to the minute. NOAA's equations agree with them to well under a minute; we allow
 * one. (api.sunrise-sunset.org runs about two minutes off, more near the polar circle, so it isn't used here.)
 */
class SunTest {

    @ParameterizedTest(name = "{0} on {4}")
    @CsvSource(
        "LHR, Europe/London, 51.471, -0.460, 2026-06-21, 04:45, 21:23",
        "LHR, Europe/London, 51.471, -0.460, 2026-12-21, 08:05, 15:55",
        "HND, Asia/Tokyo, 35.550, 139.787, 2026-06-21, 04:26, 19:00",
        "HND, Asia/Tokyo, 35.550, 139.787, 2026-12-21, 06:46, 16:31",
        "SYD, Australia/Sydney, -33.946, 151.177, 2026-06-21, 07:00, 16:54",
        "TOS, Europe/Oslo, 69.683, 18.919, 2026-02-10, 08:39, 15:19",
        // DST starts at 02:00 that morning: both times are on daylight time.
        "SFO, America/Los_Angeles, 37.620, -122.375, 2026-03-08, 07:31, 19:10",
    )
    fun `sunrise and sunset match published times`(
        code: String,
        zone: String,
        latitude: Double,
        longitude: Double,
        date: String,
        sunrise: String,
        sunset: String,
    ) {
        val z = ZoneId.of(zone)
        val day = Sun.on(LocalDate.parse(date), z, latitude, longitude).shouldBeInstanceOf<SunDay.RisesAndSets>()
        val d = LocalDate.parse(date)
        minutesApart(day.sunrise.atZone(z).toLocalDateTime(), LocalDateTime.of(d, java.time.LocalTime.parse(sunrise))) shouldBeLessThanOrEqual 1
        minutesApart(day.sunset.atZone(z).toLocalDateTime(), LocalDateTime.of(d, java.time.LocalTime.parse(sunset))) shouldBeLessThanOrEqual 1
    }

    @Test
    fun `Tromsø has polar night in December and the midnight sun in June`() {
        val oslo = ZoneId.of("Europe/Oslo")
        Sun.on(LocalDate.of(2026, 12, 21), oslo, 69.683, 18.919).shouldBeInstanceOf<SunDay.AlwaysDown>()
        Sun.on(LocalDate.of(2026, 6, 21), oslo, 69.683, 18.919).shouldBeInstanceOf<SunDay.AlwaysUp>()
    }

    @Test
    fun `the southern hemisphere has its polar seasons the other way round`() {
        // McMurdo Station, Antarctica.
        val mcMurdo = ZoneId.of("Antarctica/McMurdo")
        Sun.on(LocalDate.of(2026, 6, 21), mcMurdo, -77.85, 166.67).shouldBeInstanceOf<SunDay.AlwaysDown>()
        Sun.on(LocalDate.of(2026, 12, 21), mcMurdo, -77.85, 166.67).shouldBeInstanceOf<SunDay.AlwaysUp>()
    }

    @Test
    fun `the times fall on the asked local date even across the date line`() {
        // Kiritimati is UTC+14: its local noon is the previous UTC day.
        val kiritimati = ZoneId.of("Pacific/Kiritimati")
        val day = Sun.on(LocalDate.of(2026, 6, 21), kiritimati, 1.87, -157.40).shouldBeInstanceOf<SunDay.RisesAndSets>()
        day.sunrise.atZone(kiritimati).toLocalDate() shouldBe LocalDate.of(2026, 6, 21)
        day.sunset.atZone(kiritimati).toLocalDate() shouldBe LocalDate.of(2026, 6, 21)
    }

    @Test
    fun `a day that sets after midnight ends with tonight's sunset`() {
        // Tromsø, 17 May 2026, the eve of the midnight sun: up at about 01:14, down after midnight.
        val oslo = ZoneId.of("Europe/Oslo")
        val day = Sun.on(LocalDate.of(2026, 5, 17), oslo, 69.683, 18.919).shouldBeInstanceOf<SunDay.RisesAndSets>()
        day.sunrise.atZone(oslo).toLocalDate() shouldBe LocalDate.of(2026, 5, 17)
        day.sunset.atZone(oslo).toLocalDate() shouldBe LocalDate.of(2026, 5, 18)
        (day.sunrise < day.solarNoon && day.solarNoon < day.sunset) shouldBe true
    }

    @Test
    fun `a dial's window takes the sunrise and sunset inside it, from the days either side if need be`() {
        // Tromsø, May 2026: the sun sets after midnight (about 00:06 on the 17th, 00:40 on the 18th), and from the
        // 18th it doesn't set at all.
        val oslo = ZoneId.of("Europe/Oslo")
        fun on(d: LocalDate) = Sun.on(d, oslo, 69.683, 18.919)
        fun window(d: LocalDate, h: Int, m: Int): SunDay {
            val at = d.atTime(h, m).atZone(oslo).toInstant()
            return Sun.on(d, oslo, 69.683, 18.919, at.minus(Duration.ofHours(8)), at.plus(Duration.ofHours(16)))
        }
        val may16 = on(LocalDate.of(2026, 5, 16)).shouldBeInstanceOf<SunDay.RisesAndSets>()
        val may17 = on(LocalDate.of(2026, 5, 17)).shouldBeInstanceOf<SunDay.RisesAndSets>()
        may16.sunset.atZone(oslo).toLocalDate() shouldBe LocalDate.of(2026, 5, 17)

        // 00:20 on the 17th, between last night's sunset and this morning's sunrise: the dial (16:20 yesterday to
        // 16:20) shows last night's sunset, not tonight's, so the small hours read as night.
        window(LocalDate.of(2026, 5, 17), 0, 20) shouldBe may17.copy(sunset = may16.sunset)
        // 22:00 on the 16th: tonight's sunset, and tomorrow morning's sunrise (the dial runs to 14:00 tomorrow).
        window(LocalDate.of(2026, 5, 16), 22, 0) shouldBe may16.copy(sunrise = may17.sunrise)
        // Noon: both of the day's own.
        window(LocalDate.of(2026, 5, 17), 12, 0) shouldBe may17
        // 22:00 on the 17th: the 18th has no sunrise (midnight sun), so the day keeps its own.
        window(LocalDate.of(2026, 5, 17), 22, 0) shouldBe may17
    }

    @Test
    fun `solar noon is the middle of the day, and of a polar night too`() {
        val london = ZoneId.of("Europe/London")
        val day = Sun.on(LocalDate.of(2026, 6, 21), london, 51.471, -0.460).shouldBeInstanceOf<SunDay.RisesAndSets>()
        val middle = day.sunrise.plus(Duration.between(day.sunrise, day.sunset).dividedBy(2))
        abs(Duration.between(middle, day.solarNoon).toSeconds()) shouldBeLessThanOrEqual 60
        // Tromsø's polar night still has a noon: halfway through USNO's civil twilight, 09:32–13:53.
        val oslo = ZoneId.of("Europe/Oslo")
        val polar = Sun.on(LocalDate.of(2026, 12, 21), oslo, 69.683, 18.919).shouldBeInstanceOf<SunDay.AlwaysDown>()
        minutesApart(polar.solarNoon.atZone(oslo).toLocalDateTime(), LocalDateTime.of(2026, 12, 21, 11, 42, 30)) shouldBeLessThanOrEqual 2
    }

    @Test
    fun `sunrise comes before sunset and the day is never longer than one`() = runTest {
        checkAll(
            Arb.localDate(LocalDate.of(2000, 1, 1), LocalDate.of(2100, 12, 31)),
            Arb.numericDouble(-89.0, 89.0),
            Arb.numericDouble(-180.0, 180.0),
        ) { date, latitude, longitude ->
            when (val day = Sun.on(date, ZoneOffset.UTC, latitude, longitude)) {
                is SunDay.RisesAndSets -> {
                    (day.sunrise < day.sunset) shouldBe true
                    (Duration.between(day.sunrise, day.sunset) <= Duration.ofDays(1)) shouldBe true
                }
                is SunDay.AlwaysUp, is SunDay.AlwaysDown -> (abs(latitude) > 60.0) shouldBe true
            }
        }
    }

    private fun minutesApart(a: LocalDateTime, b: LocalDateTime): Long = abs(Duration.between(a, b).toSeconds()) / 60
}
