package dev.sebastiano.clockblocker.opus.widget.rc

import android.content.Context
import androidx.compose.remote.creation.compose.capture.CapturedDocument
import androidx.compose.remote.creation.profile.Profile
import androidx.compose.ui.geometry.Size
import kotlin.math.ceil
import androidx.compose.remote.creation.compose.layout.RemoteAlignment
import androidx.compose.remote.creation.compose.layout.RemoteArrangement
import androidx.compose.remote.creation.compose.layout.RemoteBox
import androidx.compose.remote.creation.compose.layout.RemoteCanvas
import androidx.compose.remote.creation.compose.layout.RemoteColumn
import androidx.compose.remote.creation.compose.layout.RemoteComposable
import androidx.compose.remote.creation.compose.layout.RemoteOffset
import androidx.compose.remote.creation.compose.layout.RemoteRow
import androidx.compose.remote.creation.compose.layout.RemoteSize
import androidx.compose.remote.creation.compose.layout.RemoteText
import androidx.compose.remote.creation.compose.modifier.RemoteModifier
import androidx.compose.remote.creation.compose.modifier.background
import androidx.compose.remote.creation.compose.modifier.clickable
import androidx.compose.remote.creation.compose.modifier.clip
import androidx.compose.remote.creation.compose.modifier.contentDescription
import androidx.compose.remote.creation.compose.modifier.fillMaxHeight
import androidx.compose.remote.creation.compose.modifier.fillMaxSize
import androidx.compose.remote.creation.compose.modifier.fillMaxWidth
import androidx.compose.remote.creation.compose.modifier.height
import androidx.compose.remote.creation.compose.modifier.padding
import androidx.compose.remote.creation.compose.modifier.semantics
import androidx.compose.remote.creation.compose.modifier.size
import androidx.compose.remote.creation.compose.modifier.width
import androidx.compose.remote.creation.compose.shapes.RemoteRoundedCornerShape
import androidx.compose.remote.creation.compose.state.RemoteColor
import androidx.compose.remote.creation.compose.state.RemotePaint
import androidx.compose.remote.creation.compose.state.RemoteString
import androidx.compose.remote.creation.compose.state.rdp
import androidx.compose.remote.creation.compose.state.rf
import androidx.compose.remote.creation.compose.state.rs
import androidx.compose.remote.creation.compose.state.rsp
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.PaintingStyle
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import dev.sebastiano.clockblocker.opus.widget.draw.GlyphKind
import dev.sebastiano.clockblocker.opus.widget.draw.Glyphs
import dev.sebastiano.clockblocker.opus.widget.draw.WidgetPalette
import dev.sebastiano.clockblocker.opus.widget.state.DialMath
import dev.sebastiano.clockblocker.opus.widget.state.WidgetState
import dev.sebastiano.clockblocker.opus.widget.state.tintType
import dev.sebastiano.clockblocker.opus.widget.text.Fitted
import dev.sebastiano.clockblocker.opus.widget.text.WidgetTexts
import java.time.Duration

/**
 * Both looks of one render, for documents that follow the system theme on their own (the notification documents,
 * #49): the same state and texts, with the widgets' light and dark palettes. Every colour is a [themedColor] pair,
 * so the player draws the light or the dark one by the theme it plays with: no capture per theme, no re-post when
 * night mode flips.
 */
data class ThemedWidgetModel(val light: WidgetModel, val dark: WidgetModel) {
    init {
        require(light.state == dark.state && light.texts == dark.texts) { "Both looks must show the same render" }
    }

    val state: WidgetState get() = light.state
    val texts: WidgetTexts get() = light.texts

    /** The [slot] colour of both palettes, picked by the player's theme. */
    internal fun color(slot: (WidgetPalette) -> Int): RemoteColor = themedColor(slot(light.palette), slot(dark.palette))

    companion object {
        fun of(state: WidgetState, texts: WidgetTexts): ThemedWidgetModel = ThemedWidgetModel(
            WidgetModel(state, texts, WidgetPalette.Light),
            WidgetModel(state, texts, WidgetPalette.Dark),
        )
    }
}

/**
 * The Now notification's collapsed content as a Remote Compose document (a preview for #49: the debug probe posts it;
 * the real Now notification still uses classic views). One 48 dp row: the advice glyph beside its label, the until
 * line with the other zone's time ("until 20:30 · 12:30 in Lisbon"), and Done. No background: it sits on the shade's
 * own surface, like the classic content. Fitted for [widthDp] (see [NotificationSlots]).
 */
@RemoteComposable
@Composable
fun NowNotificationCollapsedRemote(model: ThemedWidgetModel, widthDp: Int) {
    val texts = model.texts
    val fit = NotificationFit.collapsed(LocalContext.current, texts, widthDp)
    val doneRoom = fit.done?.let { it.widthDp + COLLAPSED_DONE_GAP_DP } ?: 0
    RemoteBox(
        modifier = RemoteModifier.fillMaxWidth().height(NotificationSlots.COLLAPSED_HEIGHT_DP.rdp),
        contentAlignment = RemoteAlignment.CenterStart,
    ) {
        // Done is overlaid at the end rather than a trailing Row child (alpha20's RemoteRow gives a weighted sibling
        // the room of trailing fixed children, see NextUpRemote); the main region keeps clear of it.
        NotificationRegion(model, spokenNow(model, withNext = false), RemoteModifier.fillMaxSize().padding(end = doneRoom.rdp)) {
            RemoteRow(modifier = RemoteModifier.fillMaxSize(), verticalAlignment = RemoteAlignment.CenterVertically) {
                if (fit.glyph) ThemedGlyph(texts.glyph, model, COLLAPSED_GLYPH_DP)
                RemoteColumn(
                    modifier = RemoteModifier.weight(1f).padding(
                        start = (if (fit.glyph) COLLAPSED_GLYPH_GAP_DP else 0).rdp,
                        top = 0.rdp,
                        end = 0.rdp,
                        bottom = 0.rdp,
                    ),
                    verticalArrangement = RemoteArrangement.spacedBy(LINE_GAP_DP.rdp),
                ) {
                    NotificationLabel(fit.title, model.color { it.onSurface }, FontWeight.SemiBold, RemoteModifier.fillMaxWidth())
                    fit.line?.let { NotificationLabel(it, model.color { p -> p.onSurfaceVariant }, modifier = RemoteModifier.fillMaxWidth()) }
                }
            }
        }
        fit.done?.let { done ->
            RemoteBox(modifier = RemoteModifier.fillMaxSize(), contentAlignment = RemoteAlignment.CenterEnd) {
                NotificationDone(model, done, COLLAPSED_DONE_PILL_DP, RemoteModifier.width(done.widthDp.rdp).fillMaxHeight())
            }
        }
    }
}

/**
 * The Now notification's expanded content as a Remote Compose document (#49 preview, see
 * [NowNotificationCollapsedRemote]), in reading order: the glyph beside the label and the until line with the other
 * zone's time; the block's progress with its countdown, both live (the host moves them, no alarm or re-post); what
 * starts next, glyph beside its label; and a normal-height Done button. It wraps its content (the shade caps the view
 * at 252 dp; [NotificationFit] drops the next row first when a large font scale would reach the cap).
 */
@RemoteComposable
@Composable
fun NowNotificationExpandedRemote(model: ThemedWidgetModel, widthDp: Int) {
    val texts = model.texts
    val current = (model.state as? WidgetState.Active)?.current
    val fit = NotificationFit.expanded(LocalContext.current, texts, widthDp, progress = current != null)
    RemoteColumn(
        modifier = RemoteModifier.fillMaxWidth().padding(start = 0.rdp, top = 0.rdp, end = 0.rdp, bottom = EXPANDED_BOTTOM_DP.rdp),
    ) {
        NotificationRegion(model, spokenNow(model, withNext = fit.next != null), RemoteModifier.fillMaxWidth()) {
            RemoteColumn(modifier = RemoteModifier.fillMaxWidth()) {
                RemoteRow(modifier = RemoteModifier.fillMaxWidth(), verticalAlignment = RemoteAlignment.CenterVertically) {
                    ThemedGlyph(texts.glyph, model, EXPANDED_GLYPH_DP)
                    RemoteColumn(
                        modifier = RemoteModifier.weight(1f).padding(start = EXPANDED_GLYPH_GAP_DP.rdp, top = 0.rdp, end = 0.rdp, bottom = 0.rdp),
                        verticalArrangement = RemoteArrangement.spacedBy(LINE_GAP_DP.rdp),
                    ) {
                        NotificationLabel(fit.title, model.color { it.onSurface }, FontWeight.SemiBold, RemoteModifier.fillMaxWidth())
                        fit.line?.let { NotificationLabel(it, model.color { p -> p.onSurfaceVariant }, modifier = RemoteModifier.fillMaxWidth()) }
                    }
                }
                if (fit.progress && current != null) {
                    LiveProgress(
                        model,
                        fit,
                        RemoteModifier.fillMaxWidth().padding(start = 0.rdp, top = PROGRESS_TOP_DP.rdp, end = 0.rdp, bottom = 0.rdp),
                    )
                }
                fit.next?.let { next ->
                    RemoteRow(
                        modifier = RemoteModifier.fillMaxWidth().padding(start = 0.rdp, top = NEXT_TOP_DP.rdp, end = 0.rdp, bottom = 0.rdp),
                        verticalAlignment = RemoteAlignment.CenterVertically,
                    ) {
                        ThemedGlyph(next.item.glyph, model, NEXT_GLYPH_DP)
                        RemoteColumn(modifier = RemoteModifier.weight(1f).padding(start = NEXT_GLYPH_GAP_DP.rdp, top = 0.rdp, end = 0.rdp, bottom = 0.rdp)) {
                            NotificationLabel(next.text, model.color { it.onSurfaceVariant }, modifier = RemoteModifier.fillMaxWidth())
                        }
                    }
                }
            }
        }
        fit.done?.let { done ->
            RemoteBox(
                modifier = RemoteModifier
                    .fillMaxWidth()
                    .padding(start = 0.rdp, top = DONE_ROW_TOP_DP.rdp, end = 0.rdp, bottom = 0.rdp)
                    .height(DONE_TOUCH_DP.rdp),
                contentAlignment = RemoteAlignment.CenterEnd,
            ) {
                NotificationDone(model, done, EXPANDED_DONE_PILL_DP, RemoteModifier.width(done.widthDp.rdp).fillMaxHeight())
            }
        }
    }
}

/**
 * The block's progress bar and its countdown ("1h 22m"), both host-evaluated: the bar fills and the countdown runs
 * down on the shade's clock between captures. The bar takes the advice glyph's colours (container track, mark fill),
 * like the classic Now notification's bar.
 */
@RemoteComposable
@Composable
private fun LiveProgress(model: ThemedWidgetModel, fit: ExpandedNotificationFit, modifier: RemoteModifier) {
    val state = model.state as WidgetState.Active
    val current = state.current ?: return
    val type = model.state.tintType
    val track = model.color { p -> type?.let { p.advice(it).container } ?: p.track }
    val fill = model.color { p -> type?.let { p.advice(it).onContainer } ?: p.primary }
    val total = Duration.between(current.start, current.end).toMinutes().coerceAtLeast(1)
    val elapsed = Duration.between(current.start, state.capturedAt).toMinutes().coerceIn(0, total)
    val countdown = countdownText(model.light)?.takeIf { fit.countdownSp != null }
    RemoteBox(modifier = modifier, contentAlignment = RemoteAlignment.CenterStart) {
        RemoteBox(
            modifier = RemoteModifier.fillMaxWidth().padding(end = fit.countdownSlotDp.rdp),
            contentAlignment = RemoteAlignment.CenterStart,
        ) {
            RemoteCanvas(modifier = RemoteModifier.fillMaxWidth().height(PROGRESS_BAR_DP.rdp)) {
                val r = height / 2f.rf
                drawRoundRect(
                    paint = RemotePaint { color = track; style = PaintingStyle.Fill },
                    topLeft = RemoteOffset(0f.rf, 0f.rf),
                    size = RemoteSize(width, height),
                    cornerRadius = RemoteOffset(r, r),
                )
                // Minutes into the block on the host's clock, as a share of the block; a sliver from the start.
                val minutes = elapsed.toFloat().rf + HostTime.minutesSince(DialMath.minuteOfDay(state.capturedAt, 0))
                val share = (minutes / total.toFloat().rf).min(1f.rf)
                drawRoundRect(
                    paint = RemotePaint { color = fill; style = PaintingStyle.Fill },
                    topLeft = RemoteOffset(0f.rf, 0f.rf),
                    size = RemoteSize(width * (MIN_PROGRESS.rf + share * (1f - MIN_PROGRESS).rf), height),
                    cornerRadius = RemoteOffset(r, r),
                )
            }
        }
        if (countdown != null && fit.countdownSp != null) {
            RemoteBox(modifier = RemoteModifier.fillMaxWidth(), contentAlignment = RemoteAlignment.CenterEnd) {
                // Start-aligned on purpose: the box places it at the end (see Label in WidgetRemoteContent).
                RemoteText(
                    text = countdown,
                    color = model.color { it.primary },
                    fontSize = fit.countdownSp.rsp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** The bar never draws thinner than its own height. */
private const val MIN_PROGRESS = 0.04f

/**
 * Done, as a normal button: a [pillDp] tall pill in a full-height touch target. Once something is logged it turns
 * into a chip in the same footprint that opens the plan, like the widgets' Done.
 */
@RemoteComposable
@Composable
private fun NotificationDone(model: ThemedWidgetModel, fit: DoneFit, pillDp: Int, modifier: RemoteModifier) {
    val done = model.texts.done ?: return
    val logged = done.logged != null
    RemoteBox(
        modifier = modifier
            .clickable(if (logged) deepLinkAction(model.texts.deepLink) else doneAction(done))
            .semantics { contentDescription = done.contentDescription.rs },
        contentAlignment = RemoteAlignment.Center,
    ) {
        RemoteBox(
            modifier = RemoteModifier
                .fillMaxWidth()
                .height(pillDp.rdp)
                .clip(RemoteRoundedCornerShape((pillDp / 2).rdp))
                .background(model.color { if (logged) it.surfaceContainer else it.primary }),
            contentAlignment = RemoteAlignment.Center,
        ) {
            NotificationLabel(
                fit.label,
                model.color { if (logged) it.onSurfaceVariant else it.onPrimary },
                FontWeight.SemiBold,
                RemoteModifier.fillMaxWidth(),
                TextAlign.Center,
            )
        }
    }
}

/** The region that opens the plan, spoken as [description] (a sibling of Done, never its parent). */
@RemoteComposable
@Composable
private fun NotificationRegion(
    model: ThemedWidgetModel,
    description: RemoteString,
    modifier: RemoteModifier,
    content: @RemoteComposable @Composable () -> Unit,
) {
    RemoteBox(
        modifier = modifier.clickable(deepLinkAction(model.texts.deepLink)).semantics { contentDescription = description },
        contentAlignment = RemoteAlignment.CenterStart,
    ) { content() }
}

/** The glyph in both palettes: the same display list, each op's colour picked by the player's theme. */
@RemoteComposable
@Composable
private fun ThemedGlyph(kind: GlyphKind, model: ThemedWidgetModel, sizeDp: Int) {
    val light = Glyphs.build(kind, model.light.palette)
    val dark = Glyphs.build(kind, model.dark.palette)
    RemoteCanvas(modifier = RemoteModifier.size(sizeDp.rdp)) {
        drawThemedOps(light, dark, width / 2f.rf, height / 2f.rf, width.min(height) / 2f.rf)
    }
}

/** A [Fitted] text at its fitted size and line count. Non-Start alignment needs a width-constrained [modifier]. */
@RemoteComposable
@Composable
private fun NotificationLabel(
    fitted: Fitted,
    color: RemoteColor,
    weight: FontWeight = FontWeight.Normal,
    modifier: RemoteModifier = RemoteModifier,
    align: TextAlign = TextAlign.Start,
) {
    RemoteText(
        text = fitted.text.rs,
        modifier = modifier,
        color = color,
        fontSize = fitted.sp.rsp,
        fontWeight = weight,
        maxLines = fitted.lines,
        overflow = TextOverflow.Ellipsis,
        textAlign = align,
    )
}

/** What the main region says: the block with the other zone's time, the live countdown, and what starts next. */
private fun spokenNow(model: ThemedWidgetModel, withNext: Boolean): RemoteString {
    val texts = model.texts
    var spoken = texts.spokenNow.rs
    spokenCountdown(model.light)?.let { spoken = spoken + ". ".rs + it }
    if (withNext) {
        texts.copy(upcoming = texts.upcoming.take(1)).upcomingDescription?.let { spoken = spoken + ". ".rs + it.rs }
    }
    return spoken
}

/**
 * The Now notification's two documents for [slots] (#49 preview): collapsed ([NowNotificationCollapsedRemote]) and
 * expanded ([NowNotificationExpandedRemote]), each recorded at its natural size so the player measures it right even
 * before its first draw. Both follow the system theme by themselves ([ThemedWidgetModel]).
 */
object NowNotificationDocuments {
    data class Documents(val collapsed: CapturedDocument, val expanded: CapturedDocument)

    suspend fun capture(context: Context, profile: Profile, model: ThemedWidgetModel, slots: NotificationSlots): Documents =
        capture(context, model, slots) { size, content -> RemoteComposeRenderer.capture(context, profile, size, content) }

    /** [capture] through [capturer] (tests capture on the Robolectric main thread directly). */
    internal suspend fun capture(
        context: Context,
        model: ThemedWidgetModel,
        slots: NotificationSlots,
        capturer: suspend (Size, @RemoteComposable @Composable () -> Unit) -> CapturedDocument,
    ): Documents {
        val density = context.resources.displayMetrics.density
        fun size(widthDp: Int, heightDp: Float) = Size(widthDp * density, ceil(heightDp) * density)
        val collapsed = capturer(size(slots.collapsedWidthDp, NotificationSlots.COLLAPSED_HEIGHT_DP.toFloat())) {
            NowNotificationCollapsedRemote(model, slots.collapsedWidthDp)
        }
        val expanded = capturer(size(slots.expandedWidthDp, expandedHeightDp(context, model, slots.expandedWidthDp))) {
            NowNotificationExpandedRemote(model, slots.expandedWidthDp)
        }
        return Documents(collapsed, expanded)
    }

    /** How tall the expanded view comes out at [widthDp], in dp: what it wraps to, within the shade's 252 dp cap. */
    fun expandedHeightDp(context: Context, model: ThemedWidgetModel, widthDp: Int): Float {
        val progress = (model.state as? WidgetState.Active)?.current != null
        return NotificationFit.expanded(context, model.texts, widthDp, progress).heightDp
    }
}
