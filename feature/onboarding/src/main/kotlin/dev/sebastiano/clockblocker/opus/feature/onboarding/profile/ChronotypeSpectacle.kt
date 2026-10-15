package dev.sebastiano.clockblocker.opus.feature.onboarding.profile

import androidx.compose.animation.core.Animatable
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import androidx.graphics.shapes.CornerRounding
import androidx.graphics.shapes.RoundedPolygon
import dev.sebastiano.clockblocker.opus.core.designsystem.shape.ShapeMorph
import dev.sebastiano.clockblocker.opus.core.designsystem.theme.ClockblockMotion
import dev.sebastiano.clockblocker.opus.core.designsystem.theme.SkyGradient
import kotlin.math.roundToInt
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** How the chronotype reveal enters: the first-time spectacle from the sleep dial, its own layered entrance, or at rest. */
internal enum class RevealStart { Spectacle, Entrance, Rest }

/**
 * The reveal's entrance progress, every value 0 → 1 and read in layout or draw only:
 *
 * 1. **Spectacle only.** [spin] 0 → [ClockblockMotion.SpectacleSpins]: the shape spins through `SoftBurst → Sunny →
 *    Cookie9Sided`, one morph per spin (`spectacleSpin()`), while [rise] carries it from the dial's face to the card
 *    (`spectacleRise()`). Then [pop] morphs it into the card's rounded rectangle and grows it to the card, overshooting
 *    a little (`spectaclePop()`). The illustration fades in on the card as the pop lands.
 * 2. [art]: the chronotype's landscape enters layer by layer (`revealLayers()`).
 * 3. [words]: the kicker, name, description, midpoint, plan and link stagger down into place (`revealWords()`).
 */
@Stable
internal class RevealEntrance(val start: RevealStart) {
    private val spectacle = start == RevealStart.Spectacle
    private val atRest = start == RevealStart.Rest
    val spin = Animatable(if (spectacle) 0f else ClockblockMotion.SpectacleSpins.toFloat())
    val rise = Animatable(if (spectacle) 0f else 1f)
    val pop = Animatable(if (spectacle) 0f else 1f)
    val art = Animatable(if (atRest) 1f else 0f)
    val words = Animatable(if (atRest) 1f else 0f)

    /** True while the spectacle plays; it ends at rest, or with a skip. */
    var spectacleRunning: Boolean by mutableStateOf(spectacle)
        private set

    private var popLanded: Boolean = !spectacle

    /** The illustration's opacity: hidden behind the shape until the pop lands. */
    val artAlpha: Float
        get() {
            if (!spectacleRunning) return 1f
            val p = pop.value
            if (p >= 1f) popLanded = true
            return if (popLanded) 1f else ((p - ArtFadeFromPop) / (1f - ArtFadeFromPop)).coerceIn(0f, 1f)
        }

    /** The shape's size as a share of the card's, once it's popping (the illustration grows with it). */
    val popScale: Float get() = if (!spectacleRunning) 1f else lerp(PopFrom, 1f, pop.value)

    /** The link shows clearly enough to be meant: a tap on it opens the sheet instead of only skipping. */
    val linkShown: Boolean get() = staggerProgress(words.value, LinkStagger) >= LinkShownAlpha

    suspend fun play(motion: ClockblockMotion) {
        when (start) {
            RevealStart.Spectacle -> playSpectacle(motion)
            RevealStart.Entrance -> playEntrance(motion)
            RevealStart.Rest -> Unit
        }
        spectacleRunning = false
    }

    private suspend fun playSpectacle(motion: ClockblockMotion) = coroutineScope {
        val rising = launch { rise.animateTo(1f, motion.spectacleRise()) }
        for (turn in 1..ClockblockMotion.SpectacleSpins) spin.animateTo(turn.toFloat(), motion.spectacleSpin())
        rising.join()
        launch { pop.animateTo(1f, motion.spectaclePop()) }
        snapshotFlow { pop.value }.first { it >= ArtFadeFromPop }
        playEntrance(motion)
    }

    private suspend fun playEntrance(motion: ClockblockMotion) = coroutineScope {
        launch { art.animateTo(1f, motion.revealLayers()) }
        // One mover at a time: the words follow once the landscape is mostly in.
        snapshotFlow { art.value }.first { it >= WordsStartAt }
        words.animateTo(1f, motion.revealWords())
    }

    /** Everything at rest at once (a tap during the spectacle). */
    suspend fun skip() {
        spin.snapTo(ClockblockMotion.SpectacleSpins.toFloat())
        rise.snapTo(1f)
        pop.snapTo(1f)
        art.snapTo(1f)
        words.snapTo(1f)
        popLanded = true
        spectacleRunning = false
    }

    private companion object {
        /** The illustration fades in over the last stretch of the pop's first approach. */
        const val ArtFadeFromPop = 0.6f

        const val WordsStartAt = 0.55f
        const val LinkShownAlpha = 0.5f
    }
}

/**
 * The spectacle's three morphs: two spins, then the pop into the card, a rounded square whose corner is
 * [cornerFraction] of its side. The card is built in the unit square `MaterialShapes` live in, so each morph fits the
 * shape it shares with the next the same way and nothing jumps at a hand-over.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
internal fun spectacleMorphs(cornerFraction: Float): List<ShapeMorph> {
    val clamped = cornerFraction.coerceIn(0f, 0.5f)
    val key = (clamped * 10_000f).roundToInt()
    synchronized(spectacleMorphCache) {
        spectacleMorphCache[key]?.let { return it }
        val card = RoundedPolygon(
            vertices = floatArrayOf(0f, 0f, 1f, 0f, 1f, 1f, 0f, 1f),
            rounding = CornerRounding(radius = clamped),
        )
        return listOf(SpinMorphs[0], SpinMorphs[1], ShapeMorph(MaterialShapes.Cookie9Sided, card)).also {
            spectacleMorphCache[key] = it
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private val SpinMorphs: List<ShapeMorph> by lazy(LazyThreadSafetyMode.PUBLICATION) {
    listOf(
        ShapeMorph(MaterialShapes.SoftBurst, MaterialShapes.Sunny),
        ShapeMorph(MaterialShapes.Sunny, MaterialShapes.Cookie9Sided),
    )
}

private val spectacleMorphCache = HashMap<Int, List<ShapeMorph>>()

/**
 * Draws the spectacle's shape in the reveal's coordinates: from [face] (the sleep dial's inner face) to [card] (the
 * illustration's bounds). [fill] is the sky the illustration starts from, so the card fades in over its own colour.
 */
internal fun DrawScope.drawSpectacleShape(
    entrance: RevealEntrance,
    morphs: List<ShapeMorph>,
    face: Rect,
    card: Rect,
    fill: SkyGradient,
    rim: Color,
    path: Path,
) {
    // Hidden once the illustration covers it.
    if (entrance.artAlpha >= 1f) return
    val spin = entrance.spin.value
    val rise = entrance.rise.value
    val pop = entrance.pop.value
    val from = if (face.isEmpty) Rect(card.center, card.width * FallbackStartShare / 2f) else face
    val side = if (pop > 0f) card.width * entrance.popScale else lerp(from.minDimension, card.width * PopFrom, rise)
    val centre = lerp(from.center, card.center, rise)
    val size = Size(side, side)
    // Each spin turns half a revolution: two make one, so the pop starts upright with no jump.
    val shape = when {
        pop > 0f -> morphs[2].toPath(pop.coerceIn(0f, 1f), size, out = path)
        spin < 1f -> morphs[0].toPath(spin, size, spin * SpinDegrees, out = path)
        else -> morphs[1].toPath(spin - 1f, size, spin * SpinDegrees, out = path)
    }
    val alpha = (rise / EmergeShare).coerceIn(0f, 1f)
    translate(left = centre.x - side / 2f, top = centre.y - side / 2f) {
        drawPath(shape, Brush.verticalGradient(0f to fill.top.copy(alpha = alpha), 1f to fill.bottom.copy(alpha = alpha), endY = side))
        drawPath(shape, rim.copy(alpha = RimAlpha * alpha * (1f - pop.coerceIn(0f, 1f))), style = Stroke(RimWidth.toPx()))
    }
}

/** The shape's size, as a share of the card's, when it reaches the card and before the pop grows it into it. */
private const val PopFrom = 0.62f

private const val FallbackStartShare = 0.4f
private const val SpinDegrees = 180f
private const val EmergeShare = 0.08f
private const val RimAlpha = 0.38f
private val RimWidth = 2.dp
