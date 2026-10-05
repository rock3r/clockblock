package dev.sebastiano.clockblocker.opus.widget.rc

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.remote.creation.compose.action.Action
import androidx.compose.remote.creation.compose.layout.RemoteAlignment
import androidx.compose.remote.creation.compose.layout.RemoteArrangement
import androidx.compose.remote.creation.compose.layout.RemoteBox
import androidx.compose.remote.creation.compose.layout.RemoteCanvas
import androidx.compose.remote.creation.compose.layout.RemoteColumn
import androidx.compose.remote.creation.compose.layout.RemoteComposable
import androidx.compose.remote.creation.compose.layout.RemoteRow
import androidx.compose.remote.creation.compose.layout.RemoteText
import androidx.compose.remote.creation.compose.modifier.RemoteModifier
import androidx.compose.remote.creation.compose.modifier.background
import androidx.compose.remote.creation.compose.modifier.clickable
import androidx.compose.remote.creation.compose.modifier.clip
import androidx.compose.remote.creation.compose.modifier.contentDescription
import androidx.compose.remote.creation.compose.modifier.fillMaxHeight
import androidx.compose.remote.creation.compose.modifier.fillMaxSize
import androidx.compose.remote.creation.compose.modifier.fillMaxWidth
import androidx.compose.remote.creation.compose.modifier.padding
import androidx.compose.remote.creation.compose.modifier.semantics
import androidx.compose.remote.creation.compose.modifier.size
import androidx.compose.remote.creation.compose.shapes.RemoteRoundedCornerShape
import androidx.compose.remote.creation.compose.state.RemoteString
import androidx.compose.remote.creation.compose.state.rc
import androidx.compose.remote.creation.compose.state.rdp
import androidx.compose.remote.creation.compose.state.rf
import androidx.compose.remote.creation.compose.state.rs
import androidx.compose.remote.creation.compose.state.rsp
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import dev.sebastiano.clockblocker.opus.widget.draw.Glyphs
import dev.sebastiano.clockblocker.opus.widget.draw.TwoClocksDial
import dev.sebastiano.clockblocker.opus.widget.draw.WidgetPalette
import dev.sebastiano.clockblocker.opus.widget.state.DialMath
import dev.sebastiano.clockblocker.opus.widget.state.WidgetState
import dev.sebastiano.clockblocker.opus.widget.text.TextFit
import dev.sebastiano.clockblocker.opus.widget.text.WidgetTexts
import java.time.Duration

/** Everything one capture needs. */
data class WidgetModel(val state: WidgetState, val texts: WidgetTexts, val palette: WidgetPalette)

enum class TwoClocksLayout { Square, Wide }

enum class NextUpLayout { Small, Medium, Wide }

private val CornerRadius = 24.rdp

// Room reserved at the end of wide Next up for the overlaid countdown (fits "23h 59m" at 18 sp).
private val CountdownSlot = 96.rdp

/** Two Clocks widget: 24 h dial with host-driven hand, local/body time readouts and the next action. */
@RemoteComposable
@Composable
fun TwoClocksRemote(model: WidgetModel, layout: TwoClocksLayout = TwoClocksLayout.Square) {
    val p = model.palette
    RemoteBox(
        modifier = RemoteModifier
            .fillMaxSize()
            .clip(RemoteRoundedCornerShape(CornerRadius))
            .background(Color(p.surface).rc)
            .clickable(deepLinkAction(model.texts.deepLink))
            .semantics { contentDescription = model.texts.contentDescription.rs },
        contentAlignment = RemoteAlignment.Center,
    ) {
        when (layout) {
            TwoClocksLayout.Square -> RemoteColumn(
                modifier = RemoteModifier.fillMaxSize().padding(8.rdp),
                horizontalAlignment = RemoteAlignment.CenterHorizontally,
                verticalArrangement = RemoteArrangement.spacedBy(1.rdp),
            ) {
                RemoteBox(
                    modifier = RemoteModifier.fillMaxWidth().weight(1f),
                    contentAlignment = RemoteAlignment.Center,
                ) { DialWithReadouts(model, compact = true) }
                // Without a plan the dial centre already reads "No trip / Plan one": give the dial the room.
                if (model.state is WidgetState.Active) {
                    // Two short lines rather than "Avoid light · until 18:00": that never fit a real 2×2 cell.
                    // Width-constrained (fillMaxWidth) so each line ellipsizes instead of being clipped.
                    Label(
                        model.texts.dialTitle.rs,
                        p.onSurface,
                        12,
                        weight = FontWeight.SemiBold,
                        align = TextAlign.Center,
                        modifier = RemoteModifier.fillMaxWidth(),
                    )
                    model.texts.dialDetail?.let {
                        Label(it.rs, p.onSurfaceVariant, 11, align = TextAlign.Center, modifier = RemoteModifier.fillMaxWidth())
                    }
                }
            }
            TwoClocksLayout.Wide -> RemoteRow(
                modifier = RemoteModifier.fillMaxSize().padding(start = 10.rdp, top = 10.rdp, end = 16.rdp, bottom = 10.rdp),
                verticalAlignment = RemoteAlignment.CenterVertically,
            ) {
                RemoteBox(
                    modifier = RemoteModifier.fillMaxHeight().weight(1f),
                    contentAlignment = RemoteAlignment.Center,
                ) { DialWithReadouts(model, compact = false) }
                RemoteColumn(
                    // Gap as padding, not spacedBy(): see NextUpRemote (weight children ignore arrangement spacing).
                    modifier = RemoteModifier.weight(1.1f).padding(start = 12.rdp, top = 0.rdp, end = 0.rdp, bottom = 0.rdp),
                    verticalArrangement = RemoteArrangement.spacedBy(2.rdp),
                ) {
                    // One line on purpose: a wrapped title pushes the secondary-zone line out of the card.
                    Label(model.texts.title.rs, p.onSurface, 18, weight = FontWeight.SemiBold, maxLines = 1)
                    model.texts.subtitleLines.forEach { Label(it.rs, p.onSurfaceVariant, 13, maxLines = 1) }
                    model.texts.secondary?.let { Label(it.rs, p.onSurfaceVariant, 11, maxLines = 1) }
                }
            }
        }
    }
}

@RemoteComposable
@Composable
private fun DialWithReadouts(model: WidgetModel, compact: Boolean) {
    val p = model.palette
    val state = model.state
    RemoteCanvas(modifier = RemoteModifier.fillMaxSize()) {
        val unit = width.min(height) / 2f.rf
        val cx = width / 2f.rf
        val cy = height / 2f.rf
        when (state) {
            is WidgetState.Active -> {
                drawOps(TwoClocksDial.build(state, p), cx, cy, unit)
                drawHostHand(state.displayOffsetMinutes, p, cx, cy, unit)
            }
            WidgetState.NoTrip -> drawOps(TwoClocksDial.empty(p), cx, cy, unit)
        }
    }
    val context = LocalContext.current
    // Inside the ring: capped font scale, or the clock runs into the arcs (see TextFit.dialSp).
    fun sp(compactSp: Int, wideSp: Int) = TextFit.dialSp(context, if (compact) compactSp else wideSp)
    RemoteColumn(horizontalAlignment = RemoteAlignment.CenterHorizontally) {
        when (state) {
            is WidgetState.Active -> {
                val is24 = model.texts.is24Hour
                Label(
                    HostText.clock(HostTime.minuteOfDayAt(state.displayOffsetMinutes), is24),
                    p.onSurface,
                    sp(20, 26),
                    weight = FontWeight.Medium,
                )
                Label(
                    "${model.texts.bodyPrefix} ".rs + HostText.clock(HostTime.minuteOfDayAt(state.bodyOffsetMinutes), is24),
                    p.onSurfaceVariant,
                    sp(10, 12),
                    style = FontStyle.Italic,
                )
                model.texts.misalignment?.let {
                    Label(it.rs, p.primary, sp(10, 12), weight = FontWeight.SemiBold)
                }
            }
            WidgetState.NoTrip -> {
                Label(model.texts.title.rs, p.onSurface, sp(13, 16), weight = FontWeight.Medium)
                Label(model.texts.subtitle.rs, p.primary, sp(11, 13), weight = FontWeight.SemiBold)
            }
        }
    }
}

/** Next up widget: glyph + label + until/then, live countdown on wide/small sizes. */
@RemoteComposable
@Composable
fun NextUpRemote(model: WidgetModel, layout: NextUpLayout = NextUpLayout.Medium) {
    val p = model.palette
    val texts = model.texts
    val countdown = countdownText(model)
    RemoteBox(
        modifier = RemoteModifier
            .fillMaxSize()
            .clip(RemoteRoundedCornerShape(CornerRadius))
            .background(Color(p.surface).rc)
            .clickable(deepLinkAction(texts.deepLink))
            .semantics { contentDescription = "${texts.title}. ${texts.subtitle}".rs },
        contentAlignment = RemoteAlignment.Center,
    ) {
        when (layout) {
            NextUpLayout.Small -> RemoteColumn(
                modifier = RemoteModifier.fillMaxSize().padding(horizontal = 4.rdp, vertical = 4.rdp),
                horizontalAlignment = RemoteAlignment.CenterHorizontally,
                verticalArrangement = RemoteArrangement.spacedBy(2.rdp, RemoteAlignment.CenterVertically),
            ) {
                val context = LocalContext.current
                // Glyph and countdown share the top row so the label can take two lines in a 1×1 cell. The
                // countdown is compact ("1h22m") and sized at capture so it is never ellipsized ("1h 22…").
                RemoteRow(
                    verticalAlignment = RemoteAlignment.CenterVertically,
                    horizontalArrangement = RemoteArrangement.spacedBy(TextFit.SMALL_GLYPH_GAP_DP.rdp),
                ) {
                    Glyph(model, if (countdown != null) TextFit.SMALL_GLYPH_DP else 24)
                    smallCountdown(model)?.let { (text, widest) ->
                        Label(text, p.primary, TextFit.smallCountdownSp(context, widest), weight = FontWeight.SemiBold)
                    }
                }
                // The label always stays: never an icon (or a bare countdown) alone. Up to two lines, sized so
                // that no single word has to break ("Clockblocked").
                Label(
                    texts.title.rs,
                    p.onSurface,
                    TextFit.smallLabelSp(context, texts.title),
                    weight = FontWeight.SemiBold,
                    maxLines = 2,
                    align = TextAlign.Center,
                    modifier = RemoteModifier.fillMaxWidth(),
                )
            }
            NextUpLayout.Medium, NextUpLayout.Wide -> {
                val showCountdown = layout == NextUpLayout.Wide && countdown != null
                RemoteRow(
                    modifier = RemoteModifier
                        .fillMaxSize()
                        .padding(start = 12.rdp, top = 8.rdp, end = if (showCountdown) CountdownSlot else 14.rdp, bottom = 8.rdp),
                    verticalAlignment = RemoteAlignment.CenterVertically,
                ) {
                    Glyph(model, 40)
                    RemoteColumn(
                        // The glyph gap is padding, not spacedBy(): the platform player doesn't subtract arrangement
                        // spacing from a weight(1f) child, so the text ran 12 dp into the rounded corner.
                        modifier = RemoteModifier
                            .weight(1f)
                            .padding(start = 12.rdp, top = 0.rdp, end = 0.rdp, bottom = 0.rdp),
                        verticalArrangement = RemoteArrangement.spacedBy(1.rdp),
                    ) {
                        Label(texts.title.rs, p.onSurface, 16, weight = FontWeight.SemiBold, maxLines = 1)
                        Label(texts.subtitle.rs, p.onSurfaceVariant, 12, maxLines = 1)
                        if (layout == NextUpLayout.Wide) {
                            texts.secondary?.let { Label(it.rs, p.onSurfaceVariant, 11, maxLines = 1) }
                        }
                    }
                }
                if (showCountdown) {
                    // Overlaid rather than a trailing Row child: alpha20's RemoteRow hands a weight(1f) sibling the
                    // space of trailing fixed-size children, which pushed the countdown off the card.
                    RemoteBox(
                        modifier = RemoteModifier.fillMaxSize().padding(end = 14.rdp),
                        contentAlignment = RemoteAlignment.CenterEnd,
                    ) {
                        // Start-aligned on purpose: the box places it at the end (see [Label] on alignment).
                        Label(countdown!!, p.primary, 18, weight = FontWeight.Medium)
                    }
                }
            }
        }
    }
}

@RemoteComposable
@Composable
private fun Glyph(model: WidgetModel, sizeDp: Int) {
    val ops = Glyphs.build(model.texts.glyph, model.palette)
    RemoteCanvas(modifier = RemoteModifier.size(sizeDp.rdp)) {
        val unit = width.min(height) / 2f.rf
        drawOps(ops, width / 2f.rf, height / 2f.rf, unit)
    }
}

/**
 * One line (or [maxLines]) of text that ellipsizes.
 *
 * A non-Start [align] needs a width-constrained [modifier] (`fillMaxWidth()`): the API 37 platform player aligns a
 * wrap-content text against the parent's max width but clips it to its own measured box, so centred text got cut
 * mid-glyph ("Avoid ligl") and end-aligned text vanished entirely (the wide countdown).
 */
@RemoteComposable
@Composable
private fun Label(
    text: RemoteString,
    color: Int,
    sizeSp: Int,
    weight: FontWeight = FontWeight.Normal,
    style: FontStyle = FontStyle.Normal,
    maxLines: Int = 1,
    align: TextAlign = TextAlign.Start,
    modifier: RemoteModifier = RemoteModifier,
) {
    RemoteText(
        text = text,
        modifier = modifier,
        color = Color(color).rc,
        fontSize = sizeSp.rsp,
        fontWeight = weight,
        fontStyle = style,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        textAlign = align,
    )
}

private fun countdownMinutes(model: WidgetModel): Int? {
    val state = model.state as? WidgetState.Active ?: return null
    val end = model.texts.countdownEnd ?: return null
    return Duration.between(state.capturedAt, end).toMinutes().toInt()
}

private fun countdownText(model: WidgetModel): RemoteString? {
    val total = countdownMinutes(model) ?: return null
    val state = model.state as WidgetState.Active
    return HostText.countdown(total, DialMath.minuteOfDay(state.capturedAt, 0))
}

/** Compact 1×1 countdown plus the widest text it shows before the next refresh (to size it). */
private fun smallCountdown(model: WidgetModel): Pair<RemoteString, String>? {
    val total = countdownMinutes(model) ?: return null
    val state = model.state as WidgetState.Active
    return HostText.countdown(total, DialMath.minuteOfDay(state.capturedAt, 0), compact = true) to
        HostText.countdownWidest(total, compact = true)
}

/**
 * The whole card opens [deepLink] through an id host action ([idHostAction]) answered by the click PendingIntent
 * that [RemoteComposeRenderer.remoteViews] registers under [DeepLinkIntents.CLICK_ACTION_ID].
 */
private fun deepLinkAction(deepLink: String): Action = idHostAction(DeepLinkIntents.CLICK_ACTION_ID, deepLink)

/** Deep-link PendingIntents shared by both backends. */
object DeepLinkIntents {
    /** Host action id of the card click in every Remote Compose widget document (any non-zero int). */
    const val CLICK_ACTION_ID = 0x0C10C

    fun intent(context: Context, deepLink: String): Intent {
        val uri = Uri.parse(deepLink)
        val view = Intent(Intent.ACTION_VIEW, uri).setPackage(context.packageName)
        if (view.resolveActivity(context.packageManager) != null) return view
        // The app shell may not declare the deep link filter yet: fall back to its launcher activity.
        return context.packageManager.getLaunchIntentForPackage(context.packageName)?.setData(uri) ?: view
    }

    fun pendingIntent(context: Context, deepLink: String): android.app.PendingIntent =
        android.app.PendingIntent.getActivity(
            context,
            deepLink.hashCode(),
            intent(context, deepLink).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT,
        )
}
