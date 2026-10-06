package dev.sebastiano.clockblocker.opus.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.remote.core.SystemClock
import androidx.compose.remote.creation.compose.capture.captureSingleRemoteDocument
import androidx.compose.remote.creation.compose.capture.createCreationDisplayInfo
import androidx.compose.remote.player.core.RemoteDocument
import androidx.compose.remote.player.view.RemoteComposePlayer
import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Size
import androidx.core.view.ViewCompat
import androidx.test.core.app.ApplicationProvider
import dev.sebastiano.clockblocker.opus.core.model.AdviceLog
import dev.sebastiano.clockblocker.opus.core.model.AdviceOutcome
import dev.sebastiano.clockblocker.opus.widget.draw.WidgetPalette
import dev.sebastiano.clockblocker.opus.widget.draw.WidgetTheme
import dev.sebastiano.clockblocker.opus.widget.preview.DemoPlans
import dev.sebastiano.clockblocker.opus.widget.rc.NextUpLayout
import dev.sebastiano.clockblocker.opus.widget.rc.NextUpRemote
import dev.sebastiano.clockblocker.opus.widget.rc.TwoClocksLayout
import dev.sebastiano.clockblocker.opus.widget.rc.TwoClocksRemote
import dev.sebastiano.clockblocker.opus.widget.rc.WidgetModel
import dev.sebastiano.clockblocker.opus.widget.rc.widgetProfileFor
import dev.sebastiano.clockblocker.opus.widget.state.WidgetState
import dev.sebastiano.clockblocker.opus.widget.state.WidgetStateMapper
import dev.sebastiano.clockblocker.opus.widget.text.WidgetTexts
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlinx.coroutines.runBlocking
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

/**
 * What TalkBack gets from the Remote Compose widgets: each document is played in the View player
 * ([RemoteComposePlayer], the same accessibility code as the platform widget player) and its virtual accessibility
 * nodes are read back. Host-evaluated text (the live countdown) resolves at the player's clock.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w800dp-h1800dp-xhdpi")
class RemoteSemanticsTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val zone = ZoneId.of("Asia/Tokyo")

    // 15:20 in Tokyo: avoid light runs until 16:30 (08:30 in Lisbon), 1 h 10 min from now.
    private val now = Instant.parse("2026-10-06T06:20:00Z")

    private data class Node(val clickable: Boolean, val description: String?, val text: String?)

    private fun model(logged: AdviceOutcome? = null): WidgetModel {
        val plan = DemoPlans.lisbonTokyo(now, DemoPlans.Scenario.AvoidLight)
        val current = (WidgetStateMapper.map(plan, now, zone) as WidgetState.Active).current.shouldNotBeNull()
        val logs = listOfNotNull(logged?.let { AdviceLog(current.adviceId, it) })
        val state = WidgetStateMapper.map(plan, now, zone, logs, route = DemoPlans.ROUTE, placeNames = DemoPlans.PLACE_NAMES)
        return WidgetModel(state, WidgetTexts.from(context, state, is24Hour = true), WidgetPalette.of(WidgetTheme.Light))
    }

    private fun nodes(widthDp: Int, heightDp: Int, content: @Composable () -> Unit): List<Node> {
        val density = context.resources.displayMetrics.density
        val w = (widthDp * density).roundToInt()
        val h = (heightDp * density).roundToInt()
        val bytes = runBlocking {
            captureSingleRemoteDocument(
                context = context,
                creationDisplayInfo = createCreationDisplayInfo(context, Size(w.toFloat(), h.toFloat())),
                profile = checkNotNull(widgetProfileFor(7)),
                content = content,
            ).bytes
        }
        val player = RemoteComposePlayer(context)
        player.setDocument(RemoteDocument(ByteArrayInputStream(bytes), SystemClock(Clock.fixed(now, zone))))
        player.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY))
        player.layout(0, 0, w, h)
        player.draw(Canvas(Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)))
        val helper = ViewCompat.getAccessibilityDelegate(player).shouldNotBeNull()
        val provider = player.accessibilityNodeProvider.shouldNotBeNull()
        // ExploreByTouchHelper.getVisibleVirtualViews is protected: the only way to list the virtual nodes here.
        val ids = mutableListOf<Int>()
        generateSequence<Class<*>>(helper.javaClass) { it.superclass }
            .firstNotNullOf { c -> c.declaredMethods.firstOrNull { it.name == "getVisibleVirtualViews" } }
            .apply { isAccessible = true }
            .invoke(helper, ids)
        return ids.map { id ->
            val info = provider.createAccessibilityNodeInfo(id).shouldNotBeNull()
            Node(info.isClickable, info.contentDescription?.toString(), info.text?.toString())
        }
    }

    @Test
    fun `Next up speaks the other zone's time and the live countdown`() {
        val model = model()
        val main = nodes(300, 300) { NextUpRemote(model, NextUpLayout.Tall) }.first { it.description?.startsWith("Avoid light") == true }
        main.clickable shouldBe true
        main.description shouldBe "Avoid light. until 16:30 (08:30 in Lisbon) · then Melatonin. 1 hour 10 minutes left"
    }

    @Test
    fun `every Next up size speaks the countdown`() {
        val model = model()
        NextUpLayout.entries.forEach { layout ->
            val (w, h) = when (layout) {
                NextUpLayout.Small -> 70 to 70
                NextUpLayout.Medium -> 180 to 70
                NextUpLayout.Wide -> 300 to 70
                NextUpLayout.Square -> 180 to 180
                NextUpLayout.Tall -> 180 to 300
                NextUpLayout.Ribbon -> 300 to 180
            }
            val main = nodes(w, h) { NextUpRemote(model, layout) }.first { it.description?.startsWith("Avoid light") == true }
            main.description.shouldNotBeNull() shouldContain "(08:30 in Lisbon)"
            main.description shouldContain "1 hour 10 minutes left"
        }
    }

    @Test
    fun `the 4x3 Two Clocks header strip is its own node with the place, day and route`() {
        val model = model()
        val all = nodes(300, 300) { TwoClocksRemote(model, TwoClocksLayout.Large) }
        val strip = all.filter { it.description == "Tokyo · Day 2. L I S to H N D" }
        strip shouldHaveSize 1
        strip.single().clickable shouldBe true
        all.first { it.description?.contains(" local, ") == true }.description.shouldNotBeNull() shouldContain
            "until 16:30 (08:30 in Lisbon)"
    }

    @Test
    fun `Two Clocks with the now card speaks its header and the other zone's time`() {
        val model = model()
        listOf(TwoClocksLayout.Tall to (180 to 300), TwoClocksLayout.Wide to (300 to 180)).forEach { (layout, size) ->
            val main = nodes(size.first, size.second) { TwoClocksRemote(model, layout) }
                .first { it.description?.contains(" local, ") == true }
            main.description.shouldNotBeNull() shouldContain "Tokyo · Day 2. "
            main.description shouldContain "until 16:30 (08:30 in Lisbon)"
        }
    }

    @Test
    fun `the logged chip opens the plan`() {
        val model = model(logged = AdviceOutcome.Done)
        val chip = nodes(300, 300) { NextUpRemote(model, NextUpLayout.Tall) }.first { it.description == "Avoid light: done" }
        chip.clickable shouldBe true
    }
}
