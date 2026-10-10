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
import androidx.compose.remote.creation.compose.state.RemotePaint
import androidx.compose.remote.creation.compose.state.RemoteString
import androidx.compose.remote.creation.compose.state.rc
import androidx.compose.remote.creation.compose.state.rdp
import androidx.compose.remote.creation.compose.state.rf
import androidx.compose.remote.creation.compose.state.rs
import androidx.compose.remote.creation.compose.state.rsp
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PaintingStyle
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import dev.sebastiano.clockblocker.opus.core.model.BodyRingMode
import dev.sebastiano.clockblocker.opus.widget.R
import dev.sebastiano.clockblocker.opus.widget.draw.GlyphKind
import dev.sebastiano.clockblocker.opus.widget.draw.Glyphs
import dev.sebastiano.clockblocker.opus.widget.draw.RouteStrip
import dev.sebastiano.clockblocker.opus.widget.draw.WidgetPalette
import dev.sebastiano.clockblocker.opus.widget.state.DialMath
import dev.sebastiano.clockblocker.opus.widget.state.WidgetRoute
import dev.sebastiano.clockblocker.opus.widget.state.WidgetState
import dev.sebastiano.clockblocker.opus.widget.state.tintType
import dev.sebastiano.clockblocker.opus.widget.text.DoneText
import dev.sebastiano.clockblocker.opus.widget.text.Fitted
import dev.sebastiano.clockblocker.opus.widget.text.TextFit
import dev.sebastiano.clockblocker.opus.widget.text.WidgetTexts
import kotlin.math.ceil
import java.time.Duration

/**
 * Everything one capture needs.
 *
 * @property bodyRing how the dial draws the body ring: the widget's own option (`WidgetConfig`, #52), Simple by
 *   default.
 */
data class WidgetModel(
    val state: WidgetState,
    val texts: WidgetTexts,
    val palette: WidgetPalette,
    val bodyRing: BodyRingMode = BodyRingMode.Simple,
)

/** Two Clocks size buckets (docs/surfaces.md), each drawing one of the shared dial designs ([DialDesign]). */
enum class TwoClocksLayout {
    /** 1×1: the Two strips alone. */
    Compact,

    /** 2×1 and 4×1 rows: the Two strips, wider. */
    Strip,

    /** 2×2: the Two skies dial and a two-line caption. */
    Square,

    /** 2×3: the Two skies dial above the now card and Done. */
    Tall,

    /** 2×2 landscape and 3×2: the Two strips beside the now card, and Done. */
    Wide,

    /** 4×2 portrait and up: the Two skies dial beside the now card, with the header strip and Up next. */
    Large,
}

/**
 * Next up size buckets: 1×1, 2×1, 4×1 (+ Done), 2×2 stacked (+ Done), 2×3 / 4×3 with the Up next queue, 4×2 ribbon
 * (now + Done above Up next capsules).
 */
enum class NextUpLayout { Small, Medium, Wide, Square, Tall, Ribbon }

private val CornerRadius = 24.rdp
private val InnerRadius = 16.rdp

/** How much a card leans towards the current advice colour (the text stays on-surface, so keep it light). */
private const val CARD_TINT = 0.6f
private const val NEXT_UP_TINT = 0.35f

/**
 * Two Clocks widget: the app's dial ([WidgetDialCanvas]: Two strips in the small and wide buckets, Two skies
 * elsewhere) with the next action. Its text and dial are fitted for [cell], the smallest size the host draws this
 * document at (the bucket's minimum, see [WidgetSizes]).
 */
@RemoteComposable
@Composable
fun TwoClocksRemote(
    model: WidgetModel,
    layout: TwoClocksLayout = TwoClocksLayout.Square,
    cell: CellDp = WidgetSizes.smallest(WidgetSizes.TWO_CLOCKS, layout).fitAt,
) {
    val p = model.palette
    val texts = model.texts
    // Sizes, line counts and the optional parts are decided at capture time, so no label clips on the host.
    val fit = LabelFit.twoClocks(LocalContext.current, texts, layout, cell)
    // The now card shows "Tokyo · Day 2" (Tall, Wide): speak it too. The 4×3 strip speaks for itself (HeaderStrip).
    val withCardHeader = listOfNotNull(texts.header, texts.contentDescription).joinToString(". ").rs
    val now = fit.now
    val empty = fit.empty
    val rounded = RemoteModifier.fillMaxSize().clip(RemoteRoundedCornerShape(CornerRadius)).background(Color(p.surface).rc)
    if (model.state !is WidgetState.Active || empty != null) {
        MainRegion(model, texts.contentDescription.rs, rounded) { empty?.let { EmptyContent(model, it) } }
        return
    }
    // The region the dial is laid out for: its level (Glance, Simple, Full) follows from it.
    val box = LabelFit.dialBox(texts, layout, cell, fit)
    when {
        layout == TwoClocksLayout.Compact || layout == TwoClocksLayout.Strip -> MainRegion(model, texts.contentDescription.rs, rounded) {
            RemoteBox(modifier = RemoteModifier.fillMaxSize().padding(STRIP_PAD_DP.rdp), contentAlignment = RemoteAlignment.Center) {
                WidgetDialCanvas(model, DialDesign.TwoStrips, box.width, box.height)
            }
        }
        layout == TwoClocksLayout.Square || now == null -> MainRegion(model, texts.contentDescription.rs, rounded) {
            SquareDial(model, fit.caption, box)
        }
        layout == TwoClocksLayout.Tall -> Surface(p.surface) {
            RemoteColumn(modifier = RemoteModifier.fillMaxSize().padding(CLOCKS_TALL_PAD_DP.rdp)) {
                MainRegion(model, withCardHeader, RemoteModifier.fillMaxWidth().weight(1f)) {
                    RemoteColumn(modifier = RemoteModifier.fillMaxSize()) {
                        RemoteBox(modifier = RemoteModifier.fillMaxWidth().weight(1f), contentAlignment = RemoteAlignment.Center) {
                            WidgetDialCanvas(model, DialDesign.TwoSkies, box.width, box.height)
                        }
                        NowCard(model, now, RemoteModifier.fillMaxWidth().padding(start = 0.rdp, top = CARD_TOP_DP.rdp, end = 0.rdp, bottom = 0.rdp))
                    }
                }
                DoneRegion(model, fit.done, RemoteModifier.fillMaxWidth().height(DoneHeight).padding(start = 0.rdp, top = DONE_TOP_DP.rdp, end = 0.rdp, bottom = 0.rdp))
            }
        }
        else -> Surface(p.surface) {
            val large = layout == TwoClocksLayout.Large
            RemoteColumn(modifier = RemoteModifier.fillMaxSize().padding(SURFACE_PAD_DP.rdp)) {
                // 4×3: "Tokyo · Day 2" moves out of the card into a full-width strip, with the route in dot matrix.
                if (large) {
                    HeaderStrip(
                        model,
                        fit,
                        RemoteModifier.fillMaxWidth().padding(
                            start = HEADER_STRIP_SIDE_DP.rdp,
                            top = 0.rdp,
                            end = HEADER_STRIP_SIDE_DP.rdp,
                            bottom = HEADER_STRIP_BOTTOM_DP.rdp,
                        ),
                    )
                }
                RemoteRow(modifier = RemoteModifier.fillMaxWidth().weight(1f), verticalAlignment = RemoteAlignment.CenterVertically) {
                    MainRegion(
                        model,
                        if (large) texts.contentDescription.rs else withCardHeader,
                        RemoteModifier.fillMaxHeight().weight(1f),
                    ) {
                        RemoteRow(modifier = RemoteModifier.fillMaxSize(), verticalAlignment = RemoteAlignment.CenterVertically) {
                            RemoteBox(modifier = RemoteModifier.fillMaxHeight().weight(1f), contentAlignment = RemoteAlignment.Center) {
                                // Wide is short: the strips read better there than a small round dial.
                                WidgetDialCanvas(model, if (large) DialDesign.TwoSkies else DialDesign.TwoStrips, box.width, box.height)
                            }
                            // The card sits in a weighted full-height box: as a weighted Row child itself, the player
                            // gave it the height of one-line text, so a label that wraps at the card's width ellipsized.
                            // Gap as padding, not spacedBy(): weight children ignore arrangement spacing.
                            RemoteBox(
                                modifier = RemoteModifier
                                    .fillMaxHeight()
                                    .weight(CARD_WEIGHT)
                                    .padding(start = CARD_GAP_DP.rdp, top = 0.rdp, end = 0.rdp, bottom = 0.rdp),
                                contentAlignment = RemoteAlignment.CenterStart,
                            ) {
                                NowCard(model, now, RemoteModifier.fillMaxWidth(), withHeader = !large)
                            }
                        }
                    }
                    if (texts.done != null) {
                        DoneRegion(
                            model,
                            fit.done,
                            RemoteModifier.fillMaxHeight().weight(DONE_WEIGHT).padding(start = CLOCKS_DONE_GAP_DP.rdp, top = 0.rdp, end = 0.rdp, bottom = 0.rdp),
                        )
                    }
                }
                if (large) {
                    UpNext(
                        model,
                        fit.upNext,
                        RemoteModifier.fillMaxWidth().padding(
                            start = UP_NEXT_SIDE_LARGE_DP.rdp,
                            top = UP_NEXT_TOP_LARGE_DP.rdp,
                            end = UP_NEXT_SIDE_LARGE_DP.rdp,
                            bottom = 0.rdp,
                        ),
                    )
                }
            }
        }
    }
}

/** 2×2: the Two skies dial laid out for [box], with a two-line caption under it. */
@RemoteComposable
@Composable
private fun SquareDial(model: WidgetModel, caption: CaptionFit?, box: CellDp) {
    val p = model.palette
    RemoteColumn(
        modifier = RemoteModifier.fillMaxSize().padding(SQUARE_DIAL_PAD_DP.rdp),
        horizontalAlignment = RemoteAlignment.CenterHorizontally,
        verticalArrangement = RemoteArrangement.spacedBy(LINE_GAP_DP.rdp),
    ) {
        RemoteBox(
            modifier = RemoteModifier.fillMaxWidth().weight(1f),
            contentAlignment = RemoteAlignment.Center,
        ) { WidgetDialCanvas(model, DialDesign.TwoSkies, box.width, box.height) }
        val title = caption?.title
        if (title != null) {
            // Two short lines rather than "Avoid light · until 18:00": that never fit a real 2×2 cell.
            // Width-constrained (fillMaxWidth): centred text needs it (see [Label]).
            FittedLabel(title, p.onSurface, weight = FontWeight.SemiBold, align = TextAlign.Center, modifier = RemoteModifier.fillMaxWidth())
            caption.detail?.let {
                FittedLabel(it, p.onSurfaceVariant, align = TextAlign.Center, modifier = RemoteModifier.fillMaxWidth())
            }
        }
    }
}

/** Without a plan, at every size: the no-trip glyph (when it fits), "No trip" and "Plan one", as [fit] decided. */
@RemoteComposable
@Composable
private fun EmptyContent(model: WidgetModel, fit: EmptyFit) {
    val p = model.palette
    RemoteColumn(
        modifier = RemoteModifier.fillMaxSize().padding(EMPTY_PAD_DP.rdp),
        horizontalAlignment = RemoteAlignment.CenterHorizontally,
        verticalArrangement = RemoteArrangement.spacedBy(EMPTY_GAP_DP.rdp, RemoteAlignment.CenterVertically),
    ) {
        if (fit.glyph) Glyph(model.texts.glyph, p, EMPTY_GLYPH_DP)
        FittedLabel(fit.title, p.onSurface, weight = FontWeight.SemiBold, align = TextAlign.Center, modifier = RemoteModifier.fillMaxWidth())
        fit.action?.let {
            FittedLabel(it, p.primary, weight = FontWeight.SemiBold, align = TextAlign.Center, modifier = RemoteModifier.fillMaxWidth())
        }
    }
}

/** "Tokyo · Day 2", the label, until / then and the secondary zone on a card tinted towards the current advice. */
@RemoteComposable
@Composable
private fun NowCard(model: WidgetModel, now: NowFit, modifier: RemoteModifier, withHeader: Boolean = true) {
    val p = model.palette
    val type = model.state.tintType
    RemoteColumn(
        modifier = modifier
            .clip(RemoteRoundedCornerShape(InnerRadius))
            .background(Color(p.card(type, CARD_TINT)).rc)
            .padding(horizontal = CARD_H_PAD_DP.rdp, vertical = CARD_V_PAD_DP.rdp),
        verticalArrangement = RemoteArrangement.spacedBy(LINE_GAP_DP.rdp),
    ) {
        now.header?.takeIf { withHeader }?.let { FittedLabel(it, p.onSurfaceVariant, modifier = RemoteModifier.fillMaxWidth()) }
        NowLines(model, now, RemoteModifier.fillMaxWidth())
    }
}

/** The label, the until / then lines and the other zone's time, at their fitted sizes. */
@RemoteComposable
@Composable
private fun NowLines(model: WidgetModel, now: NowFit, modifier: RemoteModifier = RemoteModifier) {
    val p = model.palette
    FittedLabel(now.title, p.onSurface, weight = FontWeight.SemiBold, modifier = modifier)
    now.details.forEach { FittedLabel(it, p.onSurfaceVariant, modifier = modifier) }
    now.secondary?.let { FittedLabel(it, p.onSurfaceVariant, modifier = modifier) }
}

/**
 * "Tokyo · Day 2" with the trip's route in dot matrix at the end, as [fit] decided (the route goes first when both
 * don't fit). Nothing when there is neither, e.g. a redacted lock-screen widget outside the plan's days. Opens the
 * plan like the main region: the players only expose clickable regions to accessibility services, so without it the
 * place, day and route were never spoken.
 */
@RemoteComposable
@Composable
private fun HeaderStrip(model: WidgetModel, fit: WidgetFit, modifier: RemoteModifier) {
    val p = model.palette
    val texts = model.texts
    val route = texts.route?.takeIf { fit.headerRoute }
    val header = fit.headerStrip
    if (route == null && header == null) return
    val description = listOfNotNull(texts.header, texts.routeDescription).joinToString(". ")
    RemoteBox(
        modifier = modifier.clickable(deepLinkAction(texts.deepLink)).semantics { contentDescription = description.rs },
        contentAlignment = RemoteAlignment.CenterStart,
    ) {
        header?.let {
            // The route is overlaid, not a trailing Row child (see NextUpRemote): keep the text clear of it.
            val end = if (route != null) RouteWidthDp + HEADER_ROUTE_GAP_DP else 0
            FittedLabel(it, p.onSurfaceVariant, modifier = RemoteModifier.fillMaxWidth().padding(end = end.rdp))
        }
        if (route != null) {
            RemoteBox(modifier = RemoteModifier.fillMaxWidth(), contentAlignment = RemoteAlignment.CenterEnd) { RouteDots(route, p) }
        }
    }
}

internal val RouteWidthDp = ceil(RouteStrip.WIDTH * RouteStrip.PITCH_DP).toInt()
internal val RouteHeightDp = ceil(RouteStrip.HEIGHT * RouteStrip.PITCH_DP).toInt()

/** The route strip ([RouteStrip]): origin and destination codes in the design system's dot-matrix face. */
@RemoteComposable
@Composable
private fun RouteDots(route: WidgetRoute, palette: WidgetPalette) {
    val ops = RouteStrip.build(route, palette.onSurface, palette.onSurfaceVariant)
    RemoteCanvas(modifier = RemoteModifier.width(RouteWidthDp.rdp).height(RouteHeightDp.rdp)) {
        val unit = (width / RouteStrip.WIDTH.toFloat().rf).min(height / RouteStrip.HEIGHT.toFloat().rf)
        drawOps(ops, width / 2f.rf, height / 2f.rf, unit)
    }
}

/**
 * Next up widget: glyph + label + until/then, live countdown, Done and the Up next queue on larger sizes. Its text is
 * fitted for [cell], the smallest size the host draws this document at (the bucket's minimum, see [WidgetSizes]).
 */
@RemoteComposable
@Composable
fun NextUpRemote(
    model: WidgetModel,
    layout: NextUpLayout = NextUpLayout.Medium,
    cell: CellDp = WidgetSizes.smallest(WidgetSizes.NEXT_UP, layout).fitAt,
) {
    val p = model.palette
    val texts = model.texts
    val type = model.state.tintType
    val bg = p.tinted(type, NEXT_UP_TINT)
    // Sizes, line counts and the optional parts are decided at capture time, so no label clips on the host.
    val fit = LabelFit.nextUp(LocalContext.current, texts, layout, cell, smallCountdown(model)?.second)
    val now = fit.now
    val end = LabelFit.rowEndDp(layout, texts)
    // Spoken: the visible text plus the other zone's time and the live countdown (words, host-evaluated).
    val description = spokenCountdown(model)?.let { texts.spokenNow.rs + ". ".rs + it } ?: texts.spokenNow.rs
    when (layout) {
        NextUpLayout.Small, NextUpLayout.Medium -> MainRegion(
            model,
            description,
            RemoteModifier.fillMaxSize().clip(RemoteRoundedCornerShape(CornerRadius)).background(Color(bg).rc),
        ) {
            val small = fit.small
            when {
                small != null -> SmallNextUp(model, small)
                now != null -> NowRow(model, now, end)
            }
        }
        NextUpLayout.Wide -> Surface(bg) {
            // Rows only take weights: alpha20's RemoteRow hands a weight(1f) sibling the space of trailing fixed-size
            // children (that pushed the countdown off the card), so Done is a weighted column, never a fixed one.
            RemoteRow(modifier = RemoteModifier.fillMaxSize(), verticalAlignment = RemoteAlignment.CenterVertically) {
                MainRegion(model, description, RemoteModifier.fillMaxHeight().weight(1f)) {
                    now?.let { NowRow(model, it, end) }
                }
                if (texts.done != null) {
                    DoneRegion(
                        model,
                        fit.done,
                        RemoteModifier.fillMaxHeight().weight(DONE_WEIGHT).padding(start = 0.rdp, top = DONE_COLUMN_PAD_DP.rdp, end = DONE_COLUMN_PAD_DP.rdp, bottom = DONE_COLUMN_PAD_DP.rdp),
                    )
                }
            }
        }
        NextUpLayout.Square, NextUpLayout.Tall -> Surface(bg) {
            RemoteColumn(modifier = RemoteModifier.fillMaxSize().padding(SURFACE_PAD_DP.rdp)) {
                val nowWeight = if (layout == NextUpLayout.Tall) 0f else 1f
                MainRegion(
                    model,
                    description,
                    if (nowWeight > 0f) RemoteModifier.fillMaxWidth().weight(1f) else RemoteModifier.fillMaxWidth(),
                ) {
                    now?.let { NowStack(model, it) }
                }
                DoneRegion(model, fit.done, RemoteModifier.fillMaxWidth().height(DoneHeight).padding(start = 0.rdp, top = DONE_TOP_DP.rdp, end = 0.rdp, bottom = 0.rdp))
                if (layout == NextUpLayout.Tall) {
                    // The trip's route in dot matrix at the end of the "Up next" title: no extra line in 2×3.
                    UpNext(
                        model,
                        fit.upNext,
                        RemoteModifier.fillMaxWidth().weight(1f).padding(start = 0.rdp, top = UP_NEXT_TOP_STACK_DP.rdp, end = 0.rdp, bottom = 0.rdp),
                    )
                }
            }
        }
        NextUpLayout.Ribbon -> Surface(bg) {
            RemoteColumn(modifier = RemoteModifier.fillMaxSize().padding(start = 0.rdp, top = 0.rdp, end = 0.rdp, bottom = RIBBON_BOTTOM_DP.rdp)) {
                RemoteRow(modifier = RemoteModifier.fillMaxWidth().weight(1f), verticalAlignment = RemoteAlignment.CenterVertically) {
                    MainRegion(model, description, RemoteModifier.fillMaxHeight().weight(1f)) {
                        now?.let { NowRow(model, it, end) }
                    }
                    if (texts.done != null) {
                        DoneRegion(
                            model,
                            fit.done,
                            RemoteModifier.fillMaxHeight().weight(DONE_WEIGHT).padding(start = 0.rdp, top = DONE_COLUMN_PAD_DP.rdp, end = DONE_COLUMN_PAD_DP.rdp, bottom = (DONE_COLUMN_PAD_DP / 2).rdp),
                        )
                    }
                }
                UpNext(model, fit.upNext, RemoteModifier.fillMaxWidth().padding(horizontal = CAPSULE_SIDE_DP.rdp, vertical = 0.rdp))
            }
        }
    }
}

/** 1×1: glyph and countdown share the top row so the label can take more lines; [fit] says what shows. */
@RemoteComposable
@Composable
private fun SmallNextUp(model: WidgetModel, fit: SmallFit) {
    val p = model.palette
    val texts = model.texts
    val countdown = smallCountdown(model)?.first?.takeIf { fit.countdownSp != null }
    RemoteColumn(
        modifier = RemoteModifier.fillMaxSize().padding(horizontal = SMALL_PAD_DP.rdp, vertical = SMALL_PAD_DP.rdp),
        horizontalAlignment = RemoteAlignment.CenterHorizontally,
        verticalArrangement = RemoteArrangement.spacedBy(SMALL_GAP_DP.rdp, RemoteAlignment.CenterVertically),
    ) {
        // The countdown is compact ("1h22m") and sized at capture so it is never ellipsized ("1h 22…").
        if (fit.glyph) {
            RemoteRow(
                verticalAlignment = RemoteAlignment.CenterVertically,
                horizontalArrangement = RemoteArrangement.spacedBy(TextFit.SMALL_GLYPH_GAP_DP.rdp),
            ) {
                Glyph(texts.glyph, p, if (countdown != null) TextFit.SMALL_GLYPH_DP else SMALL_LONE_GLYPH_DP)
                if (countdown != null && fit.countdownSp != null) {
                    Label(countdown, p.primary, fit.countdownSp, weight = FontWeight.SemiBold)
                }
            }
        }
        // The label always stays: never an icon (or a bare countdown) alone. Sized so that no word has to break.
        FittedLabel(fit.label, p.onSurface, weight = FontWeight.SemiBold, align = TextAlign.Center, modifier = RemoteModifier.fillMaxWidth())
    }
}

/**
 * Glyph, label, until/then and the secondary zone in a row, with the countdown overlaid at the end. [now] says what
 * fits (see [LabelFit]): at large font sizes the "then …" tail, then the countdown, then the glyph give way.
 */
@RemoteComposable
@Composable
private fun NowRow(model: WidgetModel, now: NowFit, endPadding: Int) {
    val p = model.palette
    val texts = model.texts
    val countdown = countdownText(model)?.takeIf { now.countdown }
    // The countdown is sp-sized: keep its slot in step so the label never runs under it.
    val countdownSlot = LabelFit.countdownSlotDp(LocalContext.current, endPadding)
    RemoteBox(modifier = RemoteModifier.fillMaxSize(), contentAlignment = RemoteAlignment.Center) {
        RemoteRow(
            modifier = RemoteModifier
                .fillMaxSize()
                .padding(
                    start = ROW_START_DP.rdp,
                    top = ROW_V_PAD_DP.rdp,
                    end = if (countdown != null) countdownSlot.rdp else endPadding.rdp,
                    bottom = ROW_V_PAD_DP.rdp,
                ),
            verticalAlignment = RemoteAlignment.CenterVertically,
        ) {
            if (now.glyph) Glyph(texts.glyph, p, ROW_GLYPH_DP)
            RemoteColumn(
                // The glyph gap is padding, not spacedBy(): the platform player doesn't subtract arrangement spacing
                // from a weight(1f) child, so the text ran 12 dp into the rounded corner.
                modifier = RemoteModifier.weight(1f).padding(start = (if (now.glyph) ROW_GLYPH_GAP_DP else 0).rdp, top = 0.rdp, end = 0.rdp, bottom = 0.rdp),
                verticalArrangement = RemoteArrangement.spacedBy(LINE_GAP_DP.rdp),
            ) {
                NowLines(model, now)
            }
        }
        if (countdown != null) {
            // Overlaid rather than a trailing Row child (see NextUpRemote on trailing fixed children).
            RemoteBox(
                modifier = RemoteModifier.fillMaxSize().padding(end = endPadding.rdp + COUNTDOWN_END_DP.rdp),
                contentAlignment = RemoteAlignment.CenterEnd,
            ) {
                // Start-aligned on purpose: the box places it at the end (see [Label] on alignment).
                Label(countdown, p.primary, LabelFit.COUNTDOWN_SP, weight = FontWeight.Medium)
            }
        }
    }
}

/** 2×2 / 2×3: glyph + countdown, then label, until/then and the secondary zone stacked. */
@RemoteComposable
@Composable
private fun NowStack(model: WidgetModel, now: NowFit) {
    val p = model.palette
    val texts = model.texts
    RemoteColumn(modifier = RemoteModifier.fillMaxWidth(), verticalArrangement = RemoteArrangement.spacedBy(LINE_GAP_DP.rdp)) {
        if (now.glyph) {
            RemoteRow(verticalAlignment = RemoteAlignment.CenterVertically) {
                Glyph(texts.glyph, p, STACK_GLYPH_DP)
                countdownText(model)?.takeIf { now.countdown }?.let { countdown ->
                    RemoteBox(modifier = RemoteModifier.padding(start = STACK_COUNTDOWN_GAP_DP.rdp, top = 0.rdp, end = 0.rdp, bottom = 0.rdp)) {
                        Label(countdown, p.primary, LabelFit.COUNTDOWN_SP, weight = FontWeight.Medium)
                    }
                }
            }
        }
        NowLines(model, now, RemoteModifier.fillMaxWidth())
    }
}

/**
 * "Up next": the next blocks (glyph + start time + label, never a glyph alone, with the secondary-zone time below)
 * and the adaptation bar, as [fit] decided: rows stacked (2×3, 4×3) or capsules side by side (4×2 ribbon). Entries
 * that cannot show their label whole are left out, the last first. Opens the plan like the main region.
 */
@RemoteComposable
@Composable
private fun UpNext(model: WidgetModel, fit: UpNextFit, modifier: RemoteModifier) {
    val p = model.palette
    if (fit.rows.isEmpty() && fit.bar == null) return
    // Spoken: what is shown.
    val texts = model.texts.copy(upcoming = fit.rows.map { it.item })
    val description = listOfNotNull(
        texts.routeDescription?.takeIf { fit.route },
        texts.upcomingDescription,
        texts.adaptationLabel?.takeIf { fit.bar != null },
    ).joinToString(". ")
    RemoteColumn(
        modifier = modifier.clickable(deepLinkAction(texts.deepLink)).semantics { contentDescription = description.rs },
        verticalArrangement = RemoteArrangement.spacedBy(UP_NEXT_SPACING_DP.rdp),
    ) {
        if (!fit.capsules) {
            if (fit.rows.isNotEmpty()) {
                RemoteBox(modifier = RemoteModifier.fillMaxWidth(), contentAlignment = RemoteAlignment.CenterStart) {
                    Label(LocalContext.current.getString(R.string.widget_up_next).rs, p.onSurfaceVariant, LabelFit.UP_NEXT_TITLE_SP, weight = FontWeight.SemiBold)
                    // Overlaid at the end, not a trailing Row child (see NextUpRemote).
                    texts.route?.takeIf { fit.route }?.let {
                        RemoteBox(modifier = RemoteModifier.fillMaxWidth(), contentAlignment = RemoteAlignment.CenterEnd) { RouteDots(it, p) }
                    }
                }
            }
            fit.rows.forEach { row ->
                RemoteRow(modifier = RemoteModifier.fillMaxWidth(), verticalAlignment = RemoteAlignment.CenterVertically) {
                    Glyph(row.item.glyph, p, UP_NEXT_GLYPH_DP)
                    RemoteColumn(modifier = RemoteModifier.weight(1f).padding(start = UP_NEXT_GLYPH_GAP_DP.rdp, top = 0.rdp, end = 0.rdp, bottom = 0.rdp)) {
                        FittedLabel(row.line, p.onSurface)
                        row.secondary?.let { FittedLabel(it, p.onSurfaceVariant) }
                    }
                }
            }
        } else {
            RemoteRow(modifier = RemoteModifier.fillMaxWidth().height(CAPSULE_HEIGHT_DP.rdp), verticalAlignment = RemoteAlignment.CenterVertically) {
                fit.rows.forEachIndexed { i, row ->
                    val start = if (i == 0) 0 else CAPSULE_GAP_DP
                    val end = if (i == fit.rows.lastIndex) 0 else CAPSULE_GAP_DP
                    RemoteRow(
                        modifier = RemoteModifier
                            .weight(1f)
                            .fillMaxHeight()
                            .padding(start = start.rdp, top = 0.rdp, end = end.rdp, bottom = 0.rdp)
                            .clip(RemoteRoundedCornerShape(InnerRadius))
                            .background(Color(p.surfaceContainer).rc)
                            .padding(horizontal = CAPSULE_PAD_DP.rdp, vertical = 0.rdp),
                        verticalAlignment = RemoteAlignment.CenterVertically,
                    ) {
                        Glyph(row.item.glyph, p, CAPSULE_GLYPH_DP)
                        RemoteColumn(modifier = RemoteModifier.weight(1f).padding(start = CAPSULE_GLYPH_GAP_DP.rdp, top = 0.rdp, end = 0.rdp, bottom = 0.rdp)) {
                            FittedLabel(row.line, p.onSurface)
                            row.secondary?.let { FittedLabel(it, p.onSurfaceVariant) }
                        }
                    }
                }
            }
        }
        val fraction = texts.adaptation
        val bar = fit.bar
        if (bar != null && fraction != null) {
            FittedLabel(
                bar,
                p.onSurfaceVariant,
                modifier = RemoteModifier.padding(start = 0.rdp, top = ADAPTATION_TOP_DP.rdp, end = 0.rdp, bottom = 0.rdp),
            )
            AdaptationBar(fraction, p)
        }
    }
}

/** Static bar: share of the planned shift done. Data, not delight: no motion. */
@RemoteComposable
@Composable
private fun AdaptationBar(fraction: Float, p: WidgetPalette) {
    RemoteCanvas(modifier = RemoteModifier.fillMaxWidth().height(6.rdp)) {
        val r = height / 2f.rf
        drawRoundRect(
            paint = RemotePaint { color = Color(p.track).rc; style = PaintingStyle.Fill },
            topLeft = RemoteOffset(0f.rf, 0f.rf),
            size = RemoteSize(width, height),
            cornerRadius = RemoteOffset(r, r),
        )
        if (fraction > 0f) {
            drawRoundRect(
                paint = RemotePaint { color = Color(p.primary).rc; style = PaintingStyle.Fill },
                topLeft = RemoteOffset(0f.rf, 0f.rf),
                size = RemoteSize(width * fraction.coerceIn(0.04f, 1f).rf, height),
                cornerRadius = RemoteOffset(r, r),
            )
        }
    }
}

/** Height of the Done row in stacked layouts: a 48 dp touch target. */
private val DoneHeight = DONE_HEIGHT_DP.rdp

/**
 * The Done button (a sibling click region of the main one, never nested: some document versions fire every
 * containing click handler). Once an outcome is logged it turns into a chip in the same footprint that opens the plan
 * like the main region, so the spot is never dead. Absent for free time, flights and the empty state.
 */
@RemoteComposable
@Composable
private fun DoneRegion(model: WidgetModel, label: Fitted?, modifier: RemoteModifier) {
    val done = model.texts.done ?: return
    val p = model.palette
    val logged = done.logged != null
    val region = modifier.clickable(if (logged) deepLinkAction(model.texts.deepLink) else doneAction(done))
    RemoteBox(
        modifier = region.semantics { contentDescription = done.contentDescription.rs },
        contentAlignment = RemoteAlignment.Center,
    ) {
        RemoteBox(
            modifier = RemoteModifier
                .fillMaxSize()
                .clip(RemoteRoundedCornerShape(InnerRadius))
                .background(Color(if (logged) p.surfaceContainer else p.primary).rc)
                .padding(horizontal = DONE_TEXT_PAD_DP.rdp, vertical = 0.rdp),
            contentAlignment = RemoteAlignment.Center,
        ) {
            val color = if (logged) p.onSurfaceVariant else p.onPrimary
            if (label != null) {
                FittedLabel(label, color, weight = FontWeight.SemiBold, align = TextAlign.Center, modifier = RemoteModifier.fillMaxWidth())
            } else {
                Label(done.label.rs, color, LabelFit.DONE_SP, weight = FontWeight.SemiBold, align = TextAlign.Center, modifier = RemoteModifier.fillMaxWidth())
            }
        }
    }
}

/** Rounded widget background for layouts made of several click regions (the root itself is not clickable). */
@RemoteComposable
@Composable
private fun Surface(color: Int, content: @RemoteComposable @Composable () -> Unit) {
    RemoteBox(
        modifier = RemoteModifier.fillMaxSize().clip(RemoteRoundedCornerShape(CornerRadius)).background(Color(color).rc),
        contentAlignment = RemoteAlignment.Center,
    ) { content() }
}

/** The region that opens the plan (or the new-trip flow), spoken as [description]. */
@RemoteComposable
@Composable
private fun MainRegion(
    model: WidgetModel,
    description: RemoteString,
    modifier: RemoteModifier,
    content: @RemoteComposable @Composable () -> Unit,
) {
    RemoteBox(
        modifier = modifier.clickable(deepLinkAction(model.texts.deepLink)).semantics { contentDescription = description },
        contentAlignment = RemoteAlignment.Center,
    ) { content() }
}

@RemoteComposable
@Composable
internal fun Glyph(kind: GlyphKind, palette: WidgetPalette, sizeDp: Int) {
    val ops = Glyphs.build(kind, palette)
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

/** A [Label] at the size and line count [TextFit] measured for it at capture time, so it shows whole. */
@RemoteComposable
@Composable
private fun FittedLabel(
    fitted: Fitted,
    color: Int,
    weight: FontWeight = FontWeight.Normal,
    align: TextAlign = TextAlign.Start,
    modifier: RemoteModifier = RemoteModifier,
) {
    Label(fitted.text.rs, color, fitted.sp, weight = weight, maxLines = fitted.lines, align = align, modifier = modifier)
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

/** The live countdown in words for the spoken description ("1 hour 10 minutes left"); null without one. */
private fun spokenCountdown(model: WidgetModel): RemoteString? {
    val total = countdownMinutes(model) ?: return null
    val state = model.state as WidgetState.Active
    return HostText.countdownSpoken(total, DialMath.minuteOfDay(state.capturedAt, 0), model.texts.countdownWords)
}

/** Compact 1×1 countdown plus the widest text it shows before the next refresh (to size it). */
private fun smallCountdown(model: WidgetModel): Pair<RemoteString, String>? {
    val total = countdownMinutes(model) ?: return null
    val state = model.state as WidgetState.Active
    return HostText.countdown(total, DialMath.minuteOfDay(state.capturedAt, 0), compact = true) to
        HostText.countdownWidest(total, compact = true)
}

/**
 * The main region opens [deepLink] through an id host action ([idHostAction]) answered by the click PendingIntent
 * that [RemoteComposeRenderer.remoteViews] registers under [DeepLinkIntents.CLICK_ACTION_ID].
 */
private fun deepLinkAction(deepLink: String): Action = idHostAction(DeepLinkIntents.CLICK_ACTION_ID, deepLink)

/** Done: an id host action answered by the Done broadcast registered under [DeepLinkIntents.DONE_ACTION_ID]. */
private fun doneAction(done: DoneText): Action = idHostAction(DeepLinkIntents.DONE_ACTION_ID, done.adviceId)

/** Deep-link PendingIntents shared by both backends. */
object DeepLinkIntents {
    /** Host action id of the card click in every Remote Compose widget document (any non-zero int). */
    const val CLICK_ACTION_ID = 0x0C10C

    /** Host action id of the Done button (answered by `NotificationIntents.widgetDone`). */
    const val DONE_ACTION_ID = 0x0D0E

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
