package dev.sebastiano.clockblocker.opus.core.designsystem.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import dev.sebastiano.clockblocker.opus.core.designsystem.component.contentColor
import dev.sebastiano.clockblocker.opus.core.model.AdviceType
import io.kotest.assertions.assertSoftly
import io.kotest.assertions.withClue
import io.kotest.matchers.floats.plusOrMinus
import io.kotest.matchers.floats.shouldBeGreaterThanOrEqual
import io.kotest.matchers.floats.shouldBeLessThan
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/**
 * WCAG contrast of the token pairs the UI actually draws (computed, not eyeballed): 4.5:1 for body text, 3:1 for
 * large text and UI parts.
 */
class ContrastAuditTest {

    @Test
    fun `contrast ratio matches WCAG reference values`() {
        ColorMath.contrast(Color.Black, Color.White) shouldBe (21f plusOrMinus 0.01f)
        ColorMath.contrast(Color.White, Color.White) shouldBe (1f plusOrMinus 0.001f)
        // #767676 on white is the classic 4.54:1.
        ColorMath.contrast(Color(0xFF767676), Color.White) shouldBe (4.54f plusOrMinus 0.02f)
    }

    @Test
    fun `body text roles reach 4_5 to 1 on every container they sit on`() = assertSoftly {
        listOf("light" to OpusLightColors, "dark" to OpusDarkColors, "night-safe" to NightSafeColors).forEach { (name, s) ->
            val containers = listOf(
                "surface" to s.surface,
                "surfaceContainerLow" to s.surfaceContainerLow,
                "surfaceContainer" to s.surfaceContainer,
                "surfaceContainerHigh" to s.surfaceContainerHigh,
                "surfaceContainerHighest" to s.surfaceContainerHighest,
            )
            containers.forEach { (cName, c) ->
                withClue("$name onSurface on $cName") { ColorMath.contrast(s.onSurface, c) shouldBeGreaterThanOrEqual 4.5f }
                withClue("$name onSurfaceVariant on $cName") { ColorMath.contrast(s.onSurfaceVariant, c) shouldBeGreaterThanOrEqual 4.5f }
            }
            withClue("$name onPrimaryContainer") { ColorMath.contrast(s.onPrimaryContainer, s.primaryContainer) shouldBeGreaterThanOrEqual 4.5f }
            withClue("$name onSecondaryContainer") { ColorMath.contrast(s.onSecondaryContainer, s.secondaryContainer) shouldBeGreaterThanOrEqual 4.5f }
            withClue("$name onTertiaryContainer") { ColorMath.contrast(s.onTertiaryContainer, s.tertiaryContainer) shouldBeGreaterThanOrEqual 4.5f }
        }
    }

    @Test
    fun `trip card secondary lines stay readable on every card colour`() = assertSoftly {
        listOf("light" to OpusLightColors, "dark" to OpusDarkColors).forEach { (name, s) ->
            listOf(
                "in progress" to (s.primaryContainer to s.onPrimaryContainer),
                "upcoming" to (s.surfaceContainerHigh to s.onSurface),
                "past" to (s.surfaceContainerLow to s.onSurface),
            ).forEach { (card, pair) ->
                val (container, content) = pair
                // TripCard draws its route line and endpoint cities at 86 % of the content colour (SecondaryContentAlpha).
                val faint = ColorMath.over(content.copy(alpha = 0.86f), container)
                withClue("$name $card faint line") { ColorMath.contrast(faint, container) shouldBeGreaterThanOrEqual 4.5f }
            }
        }
    }

    @Test
    fun `advice cards keep their text readable`() = assertSoftly {
        listOf("light" to AdviceColors.Light, "dark" to AdviceColors.Dark).forEach { (name, palette) ->
            AdviceType.entries.forEach { type ->
                val role = palette[type]
                withClue("$name $type onContainer") { ColorMath.contrast(role.onContainer, role.container) shouldBeGreaterThanOrEqual 4.5f }
                // Secondary lines on the Now card draw onContainer at 80 % alpha.
                val secondary = ColorMath.over(role.onContainer.copy(alpha = 0.8f), role.container)
                withClue("$name $type secondary") { ColorMath.contrast(secondary, role.container) shouldBeGreaterThanOrEqual 4.5f }
            }
        }
    }

    @Test
    fun `bright dynamic dark containers are quietened and keep readable text`() {
        // The Expressive wallpaper scheme seen on a Pixel: light, saturated cyan containers in dark theme.
        val glaring = darkColorScheme(
            primaryContainer = Color(0xFF00B6FF),
            onPrimaryContainer = Color(0xFF001E2C),
            secondaryContainer = Color(0xFF0F5A8A),
            onSecondaryContainer = Color(0xFFCDE5FF),
            surface = Color(0xFF000F1E),
        )
        val quiet = glaring.withQuietDarkContainers()
        ColorMath.lightness(quiet.primaryContainer) shouldBeLessThan 0.5f
        ColorMath.contrast(quiet.onPrimaryContainer, quiet.primaryContainer) shouldBeGreaterThanOrEqual 4.5f
        // Hue survives, so it still reads as the wallpaper's colour.
        kotlin.math.abs(ColorMath.hueDelta(ColorMath.hue(glaring.primaryContainer), ColorMath.hue(quiet.primaryContainer))) shouldBeLessThan 8f
        // Already-dark containers are left exactly as the system made them.
        quiet.secondaryContainer shouldBe glaring.secondaryContainer
        quiet.onSecondaryContainer shouldBe glaring.onSecondaryContainer
    }

    @Test
    fun `dark theme skies are toned down but keep night as night`() {
        val dark = SkyPalette.Default.forDarkTheme()
        val day = dark[SkyPhase.Day]
        withClue("day sky mid luminance") { day.mid.luminance() shouldBeLessThan 0.3f }
        // Text over a dark-theme day sky flips to the light ink.
        day.contentColor() shouldBe Color.White
        // Night is already dark: barely touched.
        val night = SkyPalette.Default[SkyPhase.Night]
        ColorMath.lightness(dark[SkyPhase.Night].top) shouldBe (ColorMath.lightness(night.top) plusOrMinus 0.02f)
        // Hue is kept so the meaning (body time of day) survives.
        val dayHue = ColorMath.hue(SkyPalette.Default[SkyPhase.Day].top)
        kotlin.math.abs(ColorMath.hueDelta(dayHue, ColorMath.hue(day.top))) shouldBeLessThan 8f
    }
}
