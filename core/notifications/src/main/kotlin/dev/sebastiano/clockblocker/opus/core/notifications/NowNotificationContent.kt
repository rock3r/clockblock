package dev.sebastiano.clockblocker.opus.core.notifications

import android.widget.RemoteViews
import dev.sebastiano.clockblocker.opus.core.model.JetLagPlan
import dev.sebastiano.clockblocker.opus.core.notifications.now.NowState
import java.time.Instant

/**
 * The Now notification's Remote Compose content (#49): `RemoteViews(DrawInstructions)` for the collapsed and the
 * expanded view. `:widget` owns the documents (they share the widgets' state, texts, fitting and glyphs) and
 * contributes the implementation into the app graph; this module only asks for it, so the dependency stays
 * `:widget` → `:core:notifications`.
 *
 * The content shows the same advice as the notification's actions: it is built from the same [NowState].
 */
interface NowNotificationContent {
    /**
     * Content for [state] at [now], or null when this device can't play it (no supported Remote Compose document
     * version). May throw: [NowNotificationSurface] then posts the standard notification instead.
     */
    suspend fun render(state: NowState, plan: JetLagPlan, now: Instant): NowContentViews?

    /** No Remote Compose content: the standard notification. The default until `:widget` contributes its own. */
    object None : NowNotificationContent {
        override suspend fun render(state: NowState, plan: JetLagPlan, now: Instant): NowContentViews? = null
    }
}

/** The Now notification's custom content: [collapsed] (one 48 dp row) and [expanded] (at most 252 dp). */
class NowContentViews(val collapsed: RemoteViews, val expanded: RemoteViews)
