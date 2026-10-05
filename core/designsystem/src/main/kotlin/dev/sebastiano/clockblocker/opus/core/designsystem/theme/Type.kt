package dev.sebastiano.clockblocker.opus.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import dev.sebastiano.clockblocker.opus.core.designsystem.R

/**
 * Axis recipe for one Google Sans Flex instance. The bundled font keeps `wght 1–1000`, `opsz 6–144`,
 * `ROND 0–100` and `slnt −10–0` (GRAD and wdth were pinned at subsetting time, see `licenses/README.md`).
 */
@Immutable
data class FlexAxes(
    val weight: Int = 400,
    val round: Float = 0f,
    val slant: Float = 0f,
    val opticalSize: Float? = null,
)

/** Builds a single-face [FontFamily] for an axis recipe. Families are cached so styles share Typefaces. */
object OpusFonts {
    private val sansCache = HashMap<FlexAxes, FontFamily>()
    private val serifCache = HashMap<Pair<Int, Float>, FontFamily>()

    fun sans(axes: FlexAxes): FontFamily = synchronized(sansCache) {
        sansCache.getOrPut(axes) {
            val settings = buildList {
                add(FontVariation.weight(axes.weight))
                add(FontVariation.Setting("ROND", axes.round))
                if (axes.slant != 0f) add(FontVariation.Setting("slnt", axes.slant))
                axes.opticalSize?.let { add(FontVariation.Setting("opsz", it)) }
            }
            FontFamily(
                Font(
                    R.font.google_sans_flex,
                    weight = FontWeight(axes.weight),
                    // Slant comes from the slnt axis; keep the descriptor upright so nothing synthesises a fake skew.
                    style = FontStyle.Normal,
                    variationSettings = FontVariation.Settings(*settings.toTypedArray()),
                ),
            )
        }
    }

    /** Fraunces with `SOFT 100, WONK 1` baked in (pinned at subsetting time); [opticalSize] 9–144. */
    fun serif(weight: Int, opticalSize: Float = 144f): FontFamily = synchronized(serifCache) {
        serifCache.getOrPut(weight to opticalSize) {
            FontFamily(
                Font(
                    R.font.fraunces,
                    weight = FontWeight(weight),
                    variationSettings = FontVariation.Settings(
                        FontVariation.weight(weight),
                        FontVariation.Setting("opsz", opticalSize.coerceIn(9f, 144f)),
                    ),
                ),
            )
        }
    }
}

private const val TABULAR = "tnum"

private fun flex(
    size: Float,
    lineHeight: Float,
    tracking: Float,
    weight: Int,
    round: Float = 0f,
    slant: Float = 0f,
    features: String? = null,
): TextStyle = TextStyle(
    fontFamily = OpusFonts.sans(FlexAxes(weight, round, slant, opticalSize = size)),
    fontWeight = FontWeight(weight),
    fontStyle = FontStyle.Normal,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    letterSpacing = tracking.trackingSp(),
    fontFeatureSettings = features,
)

private fun serif(size: Float, lineHeight: Float, tracking: Float, weight: Int, opsz: Float = size.coerceAtLeast(9f)) =
    TextStyle(
        fontFamily = OpusFonts.serif(weight, opsz),
        fontWeight = FontWeight(weight),
        fontSize = size.sp,
        lineHeight = lineHeight.sp,
        letterSpacing = tracking.trackingSp(),
    )

private fun Float.trackingSp(): TextUnit = if (this == 0f) 0.em else this.sp

/** M3 baseline sizes, mapped onto Google Sans Flex axes per design.md §2.4 Typography. */
private data class Slot(val size: Float, val line: Float, val tracking: Float)

private val DisplayL = Slot(57f, 64f, -0.25f)
private val DisplayM = Slot(45f, 52f, 0f)
private val DisplayS = Slot(36f, 44f, 0f)
private val HeadlineL = Slot(32f, 40f, 0f)
private val HeadlineM = Slot(28f, 36f, 0f)
private val HeadlineS = Slot(24f, 32f, 0f)
private val TitleL = Slot(22f, 28f, 0f)
private val TitleM = Slot(16f, 24f, 0.15f)
private val TitleS = Slot(14f, 20f, 0.1f)
private val BodyL = Slot(16f, 24f, 0.5f)
private val BodyM = Slot(14f, 20f, 0.25f)
private val BodyS = Slot(12f, 16f, 0.4f)
private val LabelL = Slot(14f, 20f, 0.1f)
private val LabelM = Slot(12f, 16f, 0.5f)
private val LabelS = Slot(11f, 16f, 0.5f)

/**
 * Display, headline and title-large lines are headings: balanced breaking so a wrap never leaves a lone
 * orphan word ("Your plan starts Mon 19 / Oct"). Body and labels keep the default (paragraph) strategy.
 */
private const val HEADING_MIN_SIZE = 22f

private fun TextStyle.headingBreaks(size: Float): TextStyle =
    if (size >= HEADING_MIN_SIZE) copy(lineBreak = LineBreak.Heading) else this

private fun Slot.flex(weight: Int, round: Float = 0f) = flex(size, line, tracking, weight, round).headingBreaks(size)
private fun Slot.serif(weight: Int) = serif(size, line, tracking, weight).headingBreaks(size)

/**
 * The M3 type scale on Google Sans Flex.
 * - Display: `wght 300`; emphasized `wght 500, ROND 100`.
 * - Headline: `wght 600, ROND 40`; emphasized `wght 800`.
 * - Title/label: `wght 500, ROND 20`; emphasized `wght 700`.
 * - Body: `wght 400, ROND 0`; emphasized `wght 600`.
 * `opsz` tracks the font size (static "optical sizing auto").
 */
val OpusTypography: Typography = Typography(
    displayLarge = DisplayL.flex(300),
    displayMedium = DisplayM.flex(300),
    displaySmall = DisplayS.flex(320),
    headlineLarge = HeadlineL.flex(600, 40f),
    headlineMedium = HeadlineM.flex(600, 40f),
    headlineSmall = HeadlineS.flex(600, 40f),
    titleLarge = TitleL.flex(500, 20f),
    titleMedium = TitleM.flex(500, 20f),
    titleSmall = TitleS.flex(500, 20f),
    bodyLarge = BodyL.flex(400),
    bodyMedium = BodyM.flex(400),
    bodySmall = BodyS.flex(400),
    labelLarge = LabelL.flex(500, 20f),
    labelMedium = LabelM.flex(500, 20f),
    labelSmall = LabelS.flex(500, 20f),
    displayLargeEmphasized = DisplayL.flex(500, 100f),
    displayMediumEmphasized = DisplayM.flex(500, 100f),
    displaySmallEmphasized = DisplayS.flex(520, 100f),
    headlineLargeEmphasized = HeadlineL.flex(800, 40f),
    headlineMediumEmphasized = HeadlineM.flex(800, 40f),
    headlineSmallEmphasized = HeadlineS.flex(800, 40f),
    titleLargeEmphasized = TitleL.flex(700, 20f),
    titleMediumEmphasized = TitleM.flex(700, 20f),
    titleSmallEmphasized = TitleS.flex(700, 20f),
    bodyLargeEmphasized = BodyL.flex(600),
    bodyMediumEmphasized = BodyM.flex(600),
    bodySmallEmphasized = BodyS.flex(600),
    labelLargeEmphasized = LabelL.flex(700, 20f),
    labelMediumEmphasized = LabelM.flex(700, 20f),
    labelSmallEmphasized = LabelS.flex(700, 20f),
)

/** Opus concert mode: Fraunces for display, headline and title; Google Sans Flex keeps body and labels. */
val OpusConcertTypography: Typography = OpusTypography.copy(
    displayLarge = DisplayL.serif(400),
    displayMedium = DisplayM.serif(400),
    displaySmall = DisplayS.serif(400),
    headlineLarge = HeadlineL.serif(600),
    headlineMedium = HeadlineM.serif(600),
    headlineSmall = HeadlineS.serif(600),
    titleLarge = TitleL.serif(600),
    titleMedium = TitleM.serif(600),
    titleSmall = TitleS.serif(600),
    displayLargeEmphasized = DisplayL.serif(700),
    displayMediumEmphasized = DisplayM.serif(700),
    displaySmallEmphasized = DisplayS.serif(700),
    headlineLargeEmphasized = HeadlineL.serif(800),
    headlineMediumEmphasized = HeadlineM.serif(800),
    headlineSmallEmphasized = HeadlineS.serif(800),
    titleLargeEmphasized = TitleL.serif(800),
    titleMediumEmphasized = TitleM.serif(800),
    titleSmallEmphasized = TitleS.serif(800),
)

/**
 * Opus-specific styles beyond the M3 scale. Type rule: **upright = local time, slanted = body clock**.
 * All time styles use tabular numerals so digits never jitter.
 */
@Immutable
data class OpusTextStyles(
    /** Hero local time (dial centre, Now card). */
    val timeDisplay: TextStyle,
    /** Emphasised hero local time (the current minute). */
    val timeDisplayEmphasized: TextStyle,
    val timeHeadline: TextStyle,
    val timeTitle: TextStyle,
    val timeLabel: TextStyle,
    /** "Your body thinks it's 04:12": `slnt −10, ROND 100`, the dreamy twin of local time. */
    val bodyClockDisplay: TextStyle,
    val bodyClockTitle: TextStyle,
    val bodyClockLabel: TextStyle,
    /** Fraunces (`opsz 144, SOFT 100, WONK 1`): onboarding headlines, celebration, empty states, Opus. */
    val editorialDisplay: TextStyle,
    val editorialHeadline: TextStyle,
    val editorialTitle: TextStyle,
    val editorialBody: TextStyle,
)

internal val DefaultOpusTextStyles = OpusTextStyles(
    timeDisplay = flex(57f, 64f, -0.5f, 400, round = 0f, features = TABULAR),
    timeDisplayEmphasized = flex(57f, 64f, -0.5f, 700, round = 0f, features = TABULAR),
    timeHeadline = flex(32f, 40f, 0f, 500, features = TABULAR),
    timeTitle = flex(22f, 28f, 0f, 600, features = TABULAR),
    timeLabel = flex(14f, 20f, 0.1f, 500, features = TABULAR),
    bodyClockDisplay = flex(45f, 52f, 0f, 380, round = 100f, slant = -10f, features = TABULAR),
    bodyClockTitle = flex(22f, 28f, 0f, 450, round = 100f, slant = -10f, features = TABULAR),
    bodyClockLabel = flex(14f, 20f, 0.1f, 500, round = 100f, slant = -10f, features = TABULAR),
    editorialDisplay = serif(52f, 58f, -0.5f, 600, opsz = 144f).headingBreaks(52f),
    editorialHeadline = serif(32f, 38f, -0.25f, 600, opsz = 144f).headingBreaks(32f),
    editorialTitle = serif(22f, 28f, 0f, 600, opsz = 72f).headingBreaks(22f),
    editorialBody = serif(17f, 26f, 0.1f, 400, opsz = 18f),
)

internal val ConcertOpusTextStyles = DefaultOpusTextStyles.copy(
    timeDisplay = serif(57f, 64f, -0.5f, 500, opsz = 144f).copy(fontFeatureSettings = "lnum, tnum"),
    timeDisplayEmphasized = serif(57f, 64f, -0.5f, 800, opsz = 144f).copy(fontFeatureSettings = "lnum, tnum"),
)
