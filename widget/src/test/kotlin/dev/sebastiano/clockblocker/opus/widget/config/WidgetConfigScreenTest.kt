package dev.sebastiano.clockblocker.opus.widget.config

import android.content.Context
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.remote.core.SystemClock
import androidx.compose.remote.creation.compose.capture.captureSingleRemoteDocument
import androidx.compose.remote.creation.compose.capture.createCreationDisplayInfo
import androidx.compose.remote.player.compose.RemoteDocumentPlayer
import androidx.compose.remote.player.core.RemoteDocument
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.test.core.app.ApplicationProvider
import dev.sebastiano.clockblocker.opus.core.designsystem.theme.ClockblockTheme
import dev.sebastiano.clockblocker.opus.core.model.BodyRingMode
import dev.sebastiano.clockblocker.opus.core.testing.captureRoboImageInvalidated
import dev.sebastiano.clockblocker.opus.widget.WidgetKind
import dev.sebastiano.clockblocker.opus.widget.draw.WidgetPalette
import dev.sebastiano.clockblocker.opus.widget.draw.WidgetTheme
import dev.sebastiano.clockblocker.opus.widget.preview.DemoPlans
import dev.sebastiano.clockblocker.opus.widget.rc.CellDp
import dev.sebastiano.clockblocker.opus.widget.rc.TwoClocksRemote
import dev.sebastiano.clockblocker.opus.widget.rc.WidgetModel
import dev.sebastiano.clockblocker.opus.widget.rc.WidgetSizes
import dev.sebastiano.clockblocker.opus.widget.rc.widgetProfileFor
import dev.sebastiano.clockblocker.opus.widget.state.WidgetState
import dev.sebastiano.clockblocker.opus.widget.state.WidgetStateMapper
import dev.sebastiano.clockblocker.opus.widget.text.WidgetTexts
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayInputStream
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlin.math.roundToInt

/** Builds the screen's state and its preview the way the activity does, from the sample Lisbon → Tokyo trip. */
private class ConfigFixture {
    val context: Context = ApplicationProvider.getApplicationContext()
    private val zone = ZoneId.of("Asia/Tokyo")
    private val now = Instant.parse("2026-10-06T06:20:00Z") // 15:20 in Tokyo

    fun model(theme: WidgetTheme, bodyRing: BodyRingMode): WidgetModel {
        val plan = DemoPlans.lisbonTokyo(now, DemoPlans.Scenario.AvoidLight)
        val state = WidgetStateMapper.map(plan, now, route = DemoPlans.ROUTE, placeNames = DemoPlans.PLACE_NAMES, places = DemoPlans.PLACES)
        return WidgetModel(state, WidgetTexts.from(context, state, is24Hour = true), WidgetPalette.of(theme), bodyRing = bodyRing)
    }

    fun state(model: WidgetModel, sample: Boolean = false, widthDp: Float = 176f, heightDp: Float = 176f) = WidgetConfigUiState(
        kind = WidgetKind.TwoClocks,
        bodyRing = model.bodyRing,
        dial = (model.state as WidgetState.Active).dial,
        sample = sample,
        previewDescription = model.texts.contentDescription,
        preview = null,
        widthDp = widthDp,
        heightDp = heightDp,
    )

    /** The Two clocks widget for [model] at [widthDp] × [heightDp], played by the Remote Compose player. */
    fun preview(model: WidgetModel, widthDp: Float, heightDp: Float): @Composable (Modifier) -> Unit {
        val bucket = WidgetSizes.pick(WidgetSizes.TWO_CLOCKS, CellDp(widthDp, heightDp))
        val density = context.resources.displayMetrics.density
        val w = (widthDp * density).roundToInt()
        val h = (heightDp * density).roundToInt()
        val bytes = runBlocking {
            captureSingleRemoteDocument(
                context = context,
                creationDisplayInfo = createCreationDisplayInfo(context, Size(w.toFloat(), h.toFloat())),
                profile = checkNotNull(widgetProfileFor(7)),
                content = { TwoClocksRemote(model, bucket.layout, bucket.fitAt) },
            ).bytes
        }
        @Suppress("RestrictedApiAndroidX") // test-only: pin the player clock so goldens are stable
        val document = RemoteDocument(ByteArrayInputStream(bytes), SystemClock(Clock.fixed(now, zone)))
        return { modifier -> RemoteDocumentPlayer(document.document, w, h, modifier) }
    }
}

/** Goldens for the configuration screen: phone light/dark, the sample trip at 1.5× text, and two panes when wide. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [37], qualifiers = "w400dp-h860dp-xxhdpi")
class WidgetConfigScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private val fixture = ConfigFixture()

    @Before
    fun use24Hour() {
        Settings.System.putString(fixture.context.contentResolver, Settings.System.TIME_12_24, "24")
    }

    private fun snap(name: String, dark: Boolean, bodyRing: BodyRingMode, fontScale: Float? = null, sample: Boolean = false, widthDp: Float = 176f, heightDp: Float = 176f) {
        val model = fixture.model(if (dark) WidgetTheme.Dark else WidgetTheme.Light, bodyRing)
        val preview = fixture.preview(model, widthDp, heightDp)
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides if (fontScale != null) Density(density.density, fontScale) else density) {
                ClockblockTheme(darkTheme = dark, dynamicColor = false, reduceMotion = true) {
                    Box(Modifier.background(MaterialTheme.colorScheme.surface)) {
                        WidgetConfigScreen(fixture.state(model, sample, widthDp, heightDp), onBodyRingChange = {}, onDone = {}, preview = preview)
                    }
                }
            }
        }
        compose.mainClock.advanceTimeBy(2_000)
        compose.waitForIdle()
        compose.captureRoboImageInvalidated("src/test/screenshots/$name.png")
    }

    @Test
    fun configLight() = snap("widget_config", dark = false, bodyRing = BodyRingMode.Simple)

    @Test
    fun configDark() = snap("widget_config_dark", dark = true, bodyRing = BodyRingMode.Precise)

    /** No trip yet: the sample trip, captioned, at 1.5× text; the preview gives way so the choices still show. */
    @Test
    fun configSampleLargeText() = snap("widget_config_sample_150", dark = false, bodyRing = BodyRingMode.Simple, fontScale = 1.5f, sample = true)

    /** A wide 4×2 widget on a landscape tablet: preview and choices side by side. */
    @Test
    @Config(qualifiers = "w900dp-h560dp-xhdpi")
    fun configTwoPane() = snap("widget_config_two_pane", dark = false, bodyRing = BodyRingMode.Precise, widthDp = 360f, heightDp = 172f)
}

/** The screen's behaviour: the choices are a radio group, a tap reports the choice, Done closes. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [37], qualifiers = "w400dp-h860dp-xxhdpi")
class WidgetConfigScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val fixture = ConfigFixture()

    @Test
    fun `tapping a body ring reports it, and Done closes`() {
        val model = fixture.model(WidgetTheme.Light, BodyRingMode.Simple)
        val chosen = mutableListOf<BodyRingMode>()
        var done = 0
        compose.setContent {
            ClockblockTheme(darkTheme = false, dynamicColor = false, reduceMotion = true) {
                WidgetConfigScreen(fixture.state(model), onBodyRingChange = { chosen += it }, onDone = { done++ }, preview = {})
            }
        }

        compose.onNodeWithTag(WidgetConfigTags.bodyRing(BodyRingMode.Simple)).assertIsSelected()
        compose.onNodeWithTag(WidgetConfigTags.bodyRing(BodyRingMode.Precise)).assertIsNotSelected().performClick()
        chosen shouldContainExactly listOf(BodyRingMode.Precise)

        compose.onNodeWithTag(WidgetConfigTags.Done).performClick()
        done shouldBe 1
    }

    @Test
    fun `the preview reads as the widget's own description`() {
        val model = fixture.model(WidgetTheme.Light, BodyRingMode.Simple)
        compose.setContent {
            ClockblockTheme(darkTheme = false, dynamicColor = false, reduceMotion = true) {
                WidgetConfigScreen(fixture.state(model), onBodyRingChange = {}, onDone = {}, preview = {})
            }
        }

        compose.onNodeWithTag(WidgetConfigTags.Preview)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf(model.texts.contentDescription)))
    }
}
