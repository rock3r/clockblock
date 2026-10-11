package dev.sebastiano.clockblocker.opus.widget

import android.app.Application
import androidx.compose.remote.creation.compose.capture.CapturedDocument
import androidx.compose.remote.creation.compose.layout.RemoteComposable
import androidx.compose.remote.creation.profile.Profile
import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Size
import dev.sebastiano.clockblocker.opus.core.data.TripRepository
import dev.sebastiano.clockblocker.opus.core.model.JetLagPlan
import dev.sebastiano.clockblocker.opus.core.model.Trip
import dev.sebastiano.clockblocker.opus.core.notifications.NotificationIntents
import dev.sebastiano.clockblocker.opus.core.notifications.NowContentViews
import dev.sebastiano.clockblocker.opus.core.notifications.NowNotificationContent
import dev.sebastiano.clockblocker.opus.core.notifications.now.NowState
import dev.sebastiano.clockblocker.opus.widget.rc.NotificationSlots
import dev.sebastiano.clockblocker.opus.widget.rc.NowNotificationDocuments
import dev.sebastiano.clockblocker.opus.widget.rc.RemoteComposeRenderer
import dev.sebastiano.clockblocker.opus.widget.rc.RemoteComposeSupport
import dev.sebastiano.clockblocker.opus.widget.rc.ThemedWidgetModel
import dev.sebastiano.clockblocker.opus.widget.state.WidgetRoute
import dev.sebastiano.clockblocker.opus.widget.state.WidgetState
import dev.sebastiano.clockblocker.opus.widget.state.WidgetStateMapper
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Instant

/**
 * The Now notification's Remote Compose content (#49): the notification documents ([NowNotificationDocuments]) for the
 * notification's own [NowState], sized for this screen's shade, in both palettes (the shade's theme picks one, no
 * re-post when night mode flips). A tap on the content opens the plan, like the notification's content intent.
 *
 * Returns null when the platform player reports no supported document version; a failing capture throws, and
 * `NowNotificationSurface` posts the standard notification either way.
 */
@ContributesBinding(AppScope::class)
@Inject
class RemoteNowNotificationContent(
    private val application: Application,
    private val tripRepository: TripRepository,
) : NowNotificationContent {
    internal var profileProvider: () -> Profile? = { RemoteComposeSupport.profileOrNull() }

    /** Test hook: captures one document (tests capture on the Robolectric main thread directly). */
    internal var capturer: suspend (Profile, Size, @RemoteComposable @Composable () -> Unit) -> CapturedDocument =
        { profile, size, content -> RemoteComposeRenderer.capture(application, profile, size, content) }

    override suspend fun render(state: NowState, plan: JetLagPlan, now: Instant): NowContentViews? {
        val profile = profileProvider() ?: return null
        val trip = withTimeoutOrNull(READ_TIMEOUT_MS) { tripRepository.trip(plan.tripId).first() }
        val model = model(state, plan, now, trip) ?: return null
        val slots = NotificationSlots.forScreenWidth(application.resources.configuration.screenWidthDp)
        val documents = NowNotificationDocuments.capture(application, model, slots) { size, content -> capturer(profile, size, content) }
        val click = NotificationIntents.openPlan(application, plan.tripId)
        return NowContentViews(
            collapsed = RemoteComposeRenderer.remoteViews(documents.collapsed, click),
            expanded = RemoteComposeRenderer.remoteViews(documents.expanded, click),
        )
    }

    /** What the documents show: the widgets' render of [plan] with the notification's pick ([WidgetStateMapper.forNowNotification]). */
    internal fun model(state: NowState, plan: JetLagPlan, now: Instant, trip: Trip?): ThemedWidgetModel? {
        val mapped = WidgetStateMapper.map(
            plan,
            now,
            route = trip?.let { WidgetRoute(it.origin.displayCode, it.destination.displayCode) },
            placeNames = trip?.let(WidgetStateMapper::placeNames).orEmpty(),
            placeCodes = trip?.let(WidgetStateMapper::placeCodes).orEmpty(),
            places = trip?.let(WidgetStateMapper::places).orEmpty(),
        ) as? WidgetState.Active ?: return null
        val shown = WidgetStateMapper.forNowNotification(mapped, state.headline, state.next, state.outcome)
        return WidgetRenderer(application).themedModel(shown)
    }

    private companion object {
        /** Like the widgets: a trip that can't be read in time leaves the place names to the zones. */
        const val READ_TIMEOUT_MS = 2_000L
    }
}
