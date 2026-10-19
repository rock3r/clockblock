package dev.sebastiano.clockblocker.opus.widget.rc

import android.app.Application
import android.app.Notification
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.provider.Settings
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
import dev.sebastiano.clockblocker.opus.core.model.AdviceType
import dev.sebastiano.clockblocker.opus.core.notifications.NotificationFactory
import dev.sebastiano.clockblocker.opus.core.notifications.PlatformCapabilities
import dev.sebastiano.clockblocker.opus.core.notifications.now.NowStateCalculator
import dev.sebastiano.clockblocker.opus.core.notifications.nowNotificationAccent
import dev.sebastiano.clockblocker.opus.core.notifications.R as NotificationsR
import dev.sebastiano.clockblocker.opus.core.testing.FakeTripRepository
import dev.sebastiano.clockblocker.opus.widget.RemoteNowNotificationContent
import dev.sebastiano.clockblocker.opus.widget.preview.DemoPlans
import dev.sebastiano.clockblocker.opus.widget.state.tintType
import io.kotest.matchers.floats.plusOrMinus
import io.kotest.matchers.ints.shouldBeLessThanOrEqual
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.coroutines.runBlocking
import org.junit.Before
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
import java.util.Locale
import kotlin.math.roundToInt

private const val DIR = "src/test/screenshots"

/**
 * The Now notification's Remote Compose documents (#49), built the way the real notification builds them
 * ([RemoteNowNotificationContent.model]: the notification's own pick of advice): notification-sized (48 dp collapsed,
 * the expanded view wraps its content under the shade's 252 dp cap) and themed (one document, drawn light or dark by
 * the player's theme). No Done inside: the notification's actions sit under the content. Goldens show each in a
 * stand-in for the shade (a card with an app icon, header, expand chip and the action row: not SystemUI's own drawing)
 * at the Pixel 11 Pro's slot widths, played by the androidx view player with the theme `RemoteViews` would set.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [37], qualifiers = "w410dp-h900dp-xhdpi")
class NowNotificationRemoteTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val zone = ZoneId.of("Asia/Tokyo")
    private val now = Instant.parse("2026-10-06T06:20:00Z") // 15:20 in Tokyo
    private val slots = NotificationSlots.forScreenWidth(SCREEN_DP)

    /** The players' clock (the shade's); the preview tests move it on from the capture. */
    private var playerNow = now

    @Before
    fun use24Hour() {
        Settings.System.putString(context.contentResolver, Settings.System.TIME_12_24, "24")
    }

    private val content get() = RemoteNowNotificationContent(context as Application, FakeTripRepository(listOf(DemoPlans.trip())))

    /** The Now notification's state for [scenario], as [RemoteNowNotificationContent] turns it into a document model. */
    private fun model(
        scenario: DemoPlans.Scenario = DemoPlans.Scenario.AvoidLight,
        logged: AdviceOutcome? = null,
        withNext: Boolean = true,
    ): ThemedWidgetModel {
        val plan = DemoPlans.lisbonTokyo(now, scenario)
        val headline = NowStateCalculator.compute(plan, now)?.headline
        val logs = listOfNotNull(headline?.takeIf { logged != null }?.let { AdviceLog(it.id, logged!!) })
        val state = checkNotNull(NowStateCalculator.compute(plan, now, logs)).let { if (withNext) it else it.copy(next = null) }
        return checkNotNull(content.model(state, plan, now, DemoPlans.trip())).also { model ->
            // The header line the real notification posts above the content (the body clock).
            val notification = NotificationFactory(context as Application, Capabilities).now(state, plan, now)
            headers[model] = notification.extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString()
        }
    }

    /** The stand-in cards' header line per model: the real notification's sub text. */
    private val headers = mutableMapOf<ThemedWidgetModel, String?>()

    private object Capabilities : PlatformCapabilities {
        override fun areNotificationsEnabled() = true
        override fun canScheduleExactAlarms() = true
        override fun canPostPromotedNotifications() = false
        override fun isIgnoringBatteryOptimizations() = false
        override fun is24HourFormat() = true
        override fun locale(): Locale = Locale.UK
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
        return RemoteDocument(ByteArrayInputStream(bytes), SystemClock(Clock.fixed(playerNow, zone)))
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
        listOf(DemoPlans.Scenario.AvoidLight, DemoPlans.Scenario.Sleep, DemoPlans.Scenario.FreeTime).forEach {
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
        // With no Done pill beside it, the collapsed line has room for the full place name.
        NotificationFit.collapsed(context, model().texts, slots.collapsedWidthDp).line?.text?.replace('\u00A0', ' ') shouldBe
            "until 16:30 · 08:30 in Lisbon"
        val texts = model().texts
        NotificationFit.untilOptions(context, texts).all { "08:30" in it } shouldBe true
        texts.upcoming.forEach { item -> NotificationFit.nextOptions(context, item).all { item.secondary!!.substringBefore(' ') in it } shouldBe true }
    }

    @Test
    @Config(fontScale = 1.3f)
    fun `at a large font scale the collapsed row keeps its until line, the label giving up some size`() {
        val fit = NotificationFit.collapsed(context, model().texts, slots.collapsedWidthDp)
        fit.line shouldNotBe null
        fit.glyph shouldBe true
    }

    @Test
    fun `in free time the next row names what's next without repeating the until line's times`() {
        val texts = model(DemoPlans.Scenario.FreeTime).texts
        val fit = NotificationFit.expanded(context, texts, slots.expandedWidthDp, progress = false)
        fit.line?.text?.replace('\u00A0', ' ') shouldBe "until 17:30 · 09:30 in Lisbon"
        fit.next?.text?.text shouldBe "Next: Sleep"
    }

    @Test
    fun `once the block is logged, the until line says so first`() {
        val texts = model(logged = AdviceOutcome.Done).texts
        NotificationFit.expanded(context, texts, slots.expandedWidthDp, progress = true).line?.text?.replace('\u00A0', ' ') shouldBe
            "✓ Done · until 16:30 · 08:30 in Lisbon"
        // Collapsed, the logged forms come first; a plain one only when none of them fits.
        NotificationFit.untilOptions(context, texts).first().replace('\u00A0', ' ') shouldBe "✓ Done · until 16:30 · 08:30 in Lisbon"
        NotificationFit.untilOptions(context, texts).last() shouldBe texts.untilCompact
    }

    @Test
    fun `the documents name the notification's headline and next window, not the widgets' pick`() {
        // Free time with melatonin (a moment) due before the next window: the notification leaves moments to their
        // reminders, so its Next is the next window; the widgets' queue starts with whatever starts first.
        val plan = DemoPlans.lisbonTokyo(now, DemoPlans.Scenario.FreeTime)
        val state = checkNotNull(NowStateCalculator.compute(plan, now))
        val texts = checkNotNull(content.model(state, plan, now, DemoPlans.trip())).texts
        texts.upcoming.map { it.type } shouldBe listOfNotNull(state.next?.type)
        state.next?.type shouldNotBe AdviceType.Melatonin
    }

    @Test
    fun `render plays both documents, and returns nothing without a supported document version`(): Unit = runBlocking {
        val plan = DemoPlans.lisbonTokyo(now)
        val state = checkNotNull(NowStateCalculator.compute(plan, now))
        val content = content
        content.profileProvider = { null }
        content.render(state, plan, now) shouldBe null

        content.profileProvider = { widgetProfileFor(7) }
        content.capturer = { profile, size, body ->
            captureSingleRemoteDocument(context = context, creationDisplayInfo = createCreationDisplayInfo(context, size), profile = profile, content = body)
        }
        content.render(state, plan, now) shouldNotBe null
    }

    @Test
    fun `the onboarding preview plays the sample plan, and nothing without a supported document version`(): Unit = runBlocking {
        val content = content
        content.profileProvider = { null }
        content.preview(now, 280) shouldBe null

        content.profileProvider = { widgetProfileFor(7) }
        var captures = 0
        content.capturer = { profile, size, body ->
            captures++
            captureSingleRemoteDocument(context = context, creationDisplayInfo = createCreationDisplayInfo(context, size), profile = profile, content = body)
        }
        content.preview(now, 280) shouldNotBe null
        captures shouldBe 1
    }

    /** 01:40 in Tokyo, 17:40 in Lisbon: someone's evening, when an unpinned sample would read "until 05:00". */
    private val evening = Instant.parse("2026-10-06T16:40:00Z")

    @Test
    fun `the onboarding preview is pinned to mid-afternoon in Tokyo, its countdown running on from the real clock`() {
        val model = checkNotNull(content.previewModel(evening))
        val state = model.state as dev.sebastiano.clockblocker.opus.widget.state.WidgetState.Active
        state.capturedAt shouldBe Instant.parse("2026-10-07T06:20:00Z") // 15:20 in Tokyo, that day
        state.hostClockFrom shouldBe evening
        val fit = NotificationFit.expanded(context, model.texts, slots.expandedWidthDp, progress = true)
        fit.line?.text?.replace('\u00A0', ' ') shouldBe "until 18:00 · 10:00 in Lisbon"
        fit.title.text shouldBe "See bright light"
    }

    /** The preview played ten minutes after it was captured in someone's evening: still daytime, 2h 30m left. */
    @Test
    fun preview() {
        val model = checkNotNull(content.previewModel(evening))
        headers[model] = "Body 7 h behind"
        playerNow = evening.plusSeconds(10 * 60)
        shade("$DIR/remote_notification_preview.png", dark = false, listOf(model), expandedOnly = true)
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

    /**
     * Asleep with Done logged (said in the until line), free time (no bar), and the plan's last block (no Next row),
     * alternating light and dark.
     */
    @Test
    fun states() {
        val models = listOf(
            model(DemoPlans.Scenario.Sleep, logged = AdviceOutcome.Done),
            model(DemoPlans.Scenario.FreeTime),
            model(withNext = false),
        )
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
                        listOfNotNull("Clockblock", headers[model]).joinToString(" • "),
                        color = onCard,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(top = 8.dp, bottom = 10.dp),
                    )
                }
                content()
                if (expanded) Actions(model, onCard)
            }
            if (!expanded) {
                Spacer(Modifier.width(8.dp))
                Box(Modifier.padding(top = 12.dp).size(width = 28.dp, height = 20.dp).clip(RoundedCornerShape(10.dp)).background(onCard.copy(alpha = 0.12f)))
            }
        }
    }

    /**
     * The notification's own actions under the content, as the real notification posts them: Done, Can't do this and
     * Snooze; Undo once something is logged; none without a block to act on.
     */
    @Composable
    private fun Actions(model: ThemedWidgetModel, onCard: Color) {
        val done = model.texts.done ?: return
        val labels = if (done.logged != null) listOf("Undo") else listOf("Done", "Can't do this", "Snooze 15 min")
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            labels.forEach { Text(it, color = onCard, fontSize = 14.sp, modifier = Modifier.padding(vertical = 8.dp)) }
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
