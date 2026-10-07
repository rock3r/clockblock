package dev.sebastiano.clockblocker.opus.core.notifications

import androidx.compose.ui.graphics.toArgb
import dev.sebastiano.clockblocker.opus.core.designsystem.theme.AdviceColorRole
import dev.sebastiano.clockblocker.opus.core.designsystem.theme.AdviceColors
import dev.sebastiano.clockblocker.opus.core.model.AdviceType
import io.kotest.matchers.shouldBe
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

/** The notification palette is a copy of the design system's (this module has no Compose): keep the two in step. */
class NotificationPaletteTest {

    @ParameterizedTest
    @EnumSource(AdviceType::class)
    fun `light chips match the app's light advice colours`(type: AdviceType) {
        NotificationPalette.of(type, dark = false) shouldBe AdviceColors.Light[type].chip()
    }

    @ParameterizedTest
    @EnumSource(AdviceType::class)
    fun `dark chips match the app's dark advice colours`(type: AdviceType) {
        NotificationPalette.of(type, dark = true) shouldBe AdviceColors.Dark[type].chip()
    }

    /** The bar's mark is the vivid colour, or its deeper outline where the vivid one is too pale to read. */
    private fun AdviceColorRole.chip() = ChipColors(
        container = container.toArgb(),
        onContainer = onContainer.toArgb(),
        mark = (if (outline.alpha > 0f) outline else color).toArgb(),
    )
}
