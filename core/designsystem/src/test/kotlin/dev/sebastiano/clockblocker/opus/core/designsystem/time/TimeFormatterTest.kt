package dev.sebastiano.clockblocker.opus.core.designsystem.time

import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldEndWith
import org.junit.jupiter.api.Test
import java.time.LocalTime
import java.util.Locale

class TimeFormatterTest {

    private val morning = LocalTime.of(9, 5)
    private val evening = LocalTime.of(21, 5)

    @Test
    fun `the AM PM marker is the locale's own`() {
        TimeFormatter(is24Hour = false, Locale.US).marker(morning) shouldBe "AM"
        TimeFormatter(is24Hour = false, Locale.US).marker(evening) shouldBe "PM"
        TimeFormatter(is24Hour = false, Locale.KOREA).marker(morning) shouldBe "\uC624\uC804" // 오전
        TimeFormatter(is24Hour = false, Locale.KOREA).marker(evening) shouldBe "\uC624\uD6C4" // 오후
        TimeFormatter(is24Hour = false, Locale.forLanguageTag("es-ES")).marker(evening) shouldNotBe "PM"
    }

    @Test
    fun `the marker matches the one in the full time`() {
        for (locale in listOf(Locale.US, Locale.UK, Locale.KOREA, Locale.forLanguageTag("es-ES"), Locale.CHINA, Locale.forLanguageTag("ar-EG"))) {
            val formatter = TimeFormatter(is24Hour = false, locale)
            for (time in listOf(morning, evening)) {
                formatter.formatFull(time) shouldEndWith formatter.marker(time)!!
            }
        }
    }

    @Test
    fun `24-hour clocks have no marker`() {
        TimeFormatter(is24Hour = true, Locale.KOREA).marker(evening).shouldBeNull()
    }

    @Test
    fun `formatters for different locales differ, so remembered layouts follow a locale change`() {
        TimeFormatter(is24Hour = false, Locale.US) shouldBe TimeFormatter(is24Hour = false, Locale.US)
        TimeFormatter(is24Hour = false, Locale.US) shouldNotBe TimeFormatter(is24Hour = false, Locale.KOREA)
    }
}
