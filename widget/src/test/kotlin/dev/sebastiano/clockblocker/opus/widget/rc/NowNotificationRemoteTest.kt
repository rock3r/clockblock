package dev.sebastiano.clockblocker.opus.widget.rc

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.remote.core.SystemClock
import androidx.compose.remote.creation.compose.capture.captureSingleRemoteDocument
import androidx.compose.remote.creation.compose.capture.createCreationDisplayInfo
import androidx.compose.remote.player.core.RemoteDocument
import androidx.compose.remote.player.view.RemoteComposePlayer
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import dev.sebastiano.clockblocker.opus.core.model.AdviceLog
import dev.sebastiano.clockblocker.opus.core.model.AdviceOutcome
import dev.sebastiano.clockblocker.opus.core.notifications.nowNotificationAccent
import dev.sebastiano.clockblocker.opus.core.notifications.R as NotificationsR
import dev.sebastiano.clockblocker.opus.widget.preview.DemoPlans
import dev.sebastiano.clockblocker.opus.widget.state.WidgetState
import dev.sebastiano.clockblocker.opus.widget.state.WidgetStateMapper
import dev.sebastiano.clockblocker.opus.widget.state.tintType
import dev.sebastiano.clockblocker.opus.widget.text.WidgetTexts
import io.kotest.matchers.floats.plusOrMinus
import io.kotest.matchers.ints.shouldBeLessThanOrEqual
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayInputStream
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlin.math.roundToInt

private const val DIR = "src/test/screenshots"

/**
 * The Now notification documents (#49 preview, what the debug probe posts): notification-sized (48 dp collapsed, the
 * expanded view wraps its content under the shade's 252 dp cap) and themed (one document, drawn light or dark by the
 * player's theme). Goldens show each in a stand-in for the shade (a card with an app icon, header and expand chip:
 * not SystemUI's own drawing) at the Pixel 11 Pro's slot widths, played by the androidx view player with the theme
 * `RemoteViews` would set.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [37], qualifiers = "w410dp-h900dp-xhdpi")
class NowNotificationRemoteTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val zone = ZoneId.of("Asia/Tokyo")
    private val now = Instant.parse("2026-10-06T06:20:00Z") // 15:20 in Tokyo
    private val slots = NotificationSlots.forScreenWidth(SCREEN_DP)

    private fun model(scenario: DemoPlans.Scenario = DemoPlans.Scenario.AvoidLight, logged: AdviceOutcome? = null): ThemedWidgetModel {
        val plan = DemoPlans.lisbonTokyo(now, scenario)
        val current = (WidgetStateMapper.map(plan, now) as? WidgetState.Active)?.current
        val logs = listOfNotNull(current?.takeIf { logged != null }?.let { AdviceLog(it.adviceId, logged!!) })
        val state = WidgetStateMapper.map(plan, now, logs, route = DemoPlans.ROUTE, placeNames = DemoPlans.PLACE_NAMES, places = DemoPlans.PLACES)
        return ThemedWidgetModel.of(state, WidgetTexts.from(context, state, is24Hour = true))
    }

    private fun documents(model: ThemedWidgetModel) = runBlocking {
        // Captured right here: the renderer's hop to Dispatchers.Main would wait on this (the main) thread forever.
        NowNotificationDocuments.capture(context, model, slots) { size, content ->
            captureSingleRemoteDocument(
                context = context,
                creationDisplayInfo = createCreationDisplayInfo(context, size),
                profile = checkNotNull(widgetProfileFor(7)),
                content = content,
            )
        }
    }

    private fun document(bytes: ByteArray): RemoteDocument {
        @Suppress("RestrictedApiAndroidX") // test-only: pin the player clock so the countdown and bar are stable
        return RemoteDocument(ByteArrayInputStream(bytes), SystemClock(Clock.fixed(now, zone)))
    }

    /** The height the platform-like view player gives [bytes] at [widthDp] under the shade's cap, after a first draw. */
    private fun playedHeightDp(bytes: ByteArray, widthDp: Int): Float {
        val density = context.resources.displayMetrics.density
        val widthPx = (widthDp * density).roundToInt()
        val player = RemoteComposePlayer(context)
        player.setTheme(RemoteComposePlayer.THEME_LIGHT)
        player.setDocument(document(bytes))
        val widthSpec = View.MeasureSpec.makeMeasureSpec(widthPx, View.MeasureSpec.EXACTLY)
        val heightSpec = View.MeasureSpec.makeMeasureSpec((NotificationSlots.EXPANDED_MAX_HEIGHT_DP * density).roundToInt(), View.MeasureSpec.AT_MOST)
        repeat(2) {
            player.measure(widthSpec, heightSpec)
            player.layout(0, 0, widthPx, player.measuredHeight)
            player.draw(Canvas(Bitmap.createBitmap(widthPx, player.measuredHeight.coerceAtLeast(1), Bitmap.Config.ARGB_8888)))
        }
        return player.measuredHeight / density
    }

    // --- Size ----------------------------------------------------------------------------------------------------

    @Test
    fun `the expanded view wraps its content well under the shade's cap`() {
        listOf(DemoPlans.Scenario.AvoidLight, DemoPlans.Scenario.Sleep, DemoPlans.Scenario.FreeTime, DemoPlans.Scenario.Adapted).forEach {
            val model = model(it)
            val height = playedHeightDp(documents(model).expanded.bytes, slots.expandedWidthDp)
            height.roundToInt() shouldBeLessThanOrEqual NotificationSlots.EXPANDED_MAX_HEIGHT_DP
            height shouldBe (NowNotificationDocuments.expandedHeightDp(context, model, slots.expandedWidthDp) plusOrMinus 2f)
        }
    }

    @Test
    @Config(fontScale = 2f)
    fun `at the largest font scale the expanded view still fits, the next row giving way first`() {
        val model = model()
        val height = playedHeightDp(documents(model).expanded.bytes, slots.expandedWidthDp)
        height.roundToInt() shouldBeLessThanOrEqual NotificationSlots.EXPANDED_MAX_HEIGHT_DP
    }

    @Test
    fun `the collapsed view is one 48 dp row`() {
        playedHeightDp(documents(model()).collapsed.bytes, slots.collapsedWidthDp) shouldBe (48f plusOrMinus 0.5f)
    }

    @Test
    fun `the until line keeps the other zone's time with its full place name`() {
        val fit = NotificationFit.expanded(context, model().texts, slots.expandedWidthDp, progress = true)
        fit.line?.text shouldBe "until\u00A016:30 · 08:30\u00A0in\u00A0Lisbon"
        fit.next shouldNotBe null
    }

    @Test
    fun `the collapsed until line keeps the other zone's time too, and is left out rather than shown without it`() {
        NotificationFit.collapsed(context, model().texts, slots.collapsedWidthDp).line?.text shouldBe "until 16:30 · 08:30 Lisbon"
        val texts = model().texts
        NotificationFit.untilOptions(context, texts).all { "08:30" in it } shouldBe true
        texts.upcoming.forEach { item -> NotificationFit.nextOptions(context, item).all { item.secondary!!.substringBefore(' ') in it } shouldBe true }
    }

    @Test
    fun `both documents follow the theme on their own`() {
        val docs = documents(model())
        (document(docs.collapsed.bytes).themedColors.size > 0) shouldBe true
        (document(docs.expanded.bytes).themedColors.size > 0) shouldBe true
    }

    // --- Goldens -------------------------------------------------------------------------------------------------

    /** The same two documents in a light shade: collapsed, then expanded. */
    @Test
    fun light() = shade("$DIR/remote_notification_light.png", dark = false, listOf(model()))

    /** The very same documents played in a dark shade: only the player's theme differs. */
    @Test
    fun dark() = shade("$DIR/remote_notification_dark.png", dark = true, listOf(model()))

    /** Asleep with Done logged (the chip), free time (no bar, no Done) and adapted, light and dark. */
    @Test
    fun states() {
        val models = listOf(model(DemoPlans.Scenario.Sleep, logged = AdviceOutcome.Done), model(DemoPlans.Scenario.FreeTime))
        shade("$DIR/remote_notification_states.png", dark = null, models, expandedOnly = true)
    }

    @Test
    @Config(fontScale = 1.3f)
    fun fontScale() = shade("$DIR/remote_notification_font_1_3.png", dark = false, listOf(model()))

    /** [models] in the shade, collapsed then expanded; [dark] null alternates light and dark per model. */
    private fun shade(path: String, dark: Boolean?, models: List<ThemedWidgetModel>, expandedOnly: Boolean = false) {
        val docs = models.map { it to documents(it) }
        captureRoboImage(path) {
            val page = when (dark) {
                true -> ShadeDark
                false -> ShadeLight
                null -> ShadeMixed
            }
            Column(Modifier.background(page).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                docs.forEachIndexed { i, (model, doc) ->
                    val isDark = dark ?: (i % 2 == 1)
                    if (!expandedOnly) Card(model, isDark, expanded = false) { Played(doc.collapsed.bytes, slots.collapsedWidthDp, 48f, isDark) }
                    Card(model, isDark, expanded = true) {
                        val h = NowNotificationDocuments.expandedHeightDp(context, model, slots.expandedWidthDp)
                        Played(doc.expanded.bytes, slots.expandedWidthDp, h, isDark)
                    }
                }
            }
        }
    }

    /**
     * [bytes] in the androidx *view* player, as `RemoteViews` hosts it: the theme set before the document, and
     * `setDocument` collecting the themed colours (the Compose `RemoteDocumentPlayer` doesn't, so it stays light).
     */
    @Composable
    private fun Played(bytes: ByteArray, widthDp: Int, heightDp: Float, dark: Boolean) {
        AndroidView(
            factory = { context ->
                RemoteComposePlayer(context).apply {
                    setTheme(if (dark) RemoteComposePlayer.THEME_DARK else RemoteComposePlayer.THEME_LIGHT)
                    setDocument(document(bytes))
                }
            },
            modifier = Modifier.size(widthDp.dp, kotlin.math.ceil(heightDp).toInt().dp),
        )
    }

    /**
     * A stand-in for a decorated notification: a [CARD_DP]-wide card, the icon column (the small icon on the accent,
     * as the probe posts it), the app name and sub text when expanded, and the expand chip beside the collapsed row.
     */
    @Composable
    private fun Card(model: ThemedWidgetModel, dark: Boolean, expanded: Boolean, content: @Composable () -> Unit) {
        val onCard = if (dark) Color(0xFFE2E2E9) else Color(0xFF1A1C20)
        Row(
            Modifier
                .width(CARD_DP.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(if (dark) CardDark else CardLight)
                .padding(top = if (expanded) 14.dp else 11.dp, bottom = if (expanded) 16.dp else 11.dp),
        ) {
            Box(Modifier.width(CONTENT_START_DP.dp), contentAlignment = Alignment.TopCenter) {
                Box(
                    Modifier.padding(top = if (expanded) 0.dp else 4.dp).size(40.dp).clip(CircleShape).background(Color(nowNotificationAccent(model.state.tintType))),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(painterResource(NotificationsR.drawable.ic_notif_app), null, tint = Color.White, modifier = Modifier.size(24.dp))
                }
            }
            Column {
                if (expanded) {
                    Text(
                        listOfNotNull("Clockblock", model.texts.header).joinToString(" • "),
                        color = onCard,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(top = 8.dp, bottom = 10.dp),
                    )
                }
                content()
            }
            if (!expanded) {
                Spacer(Modifier.width(8.dp))
                Box(Modifier.padding(top = 12.dp).size(width = 28.dp, height = 20.dp).clip(RoundedCornerShape(10.dp)).background(onCard.copy(alpha = 0.12f)))
            }
        }
    }

    private companion object {
        /** The Pixel 11 Pro the probe ran on: 410 dp wide, so 260 dp collapsed and 294 dp expanded. */
        const val SCREEN_DP = 410
        const val CARD_DP = 378
        const val CONTENT_START_DP = 69
        val ShadeLight = Color(0xFFC9D6EA)
        val ShadeDark = Color(0xFF0B141C)
        val ShadeMixed = Color(0xFF6B7480)
        val CardLight = Color(0xFFE8EEF8)
        val CardDark = Color(0xFF16202A)
    }
}
