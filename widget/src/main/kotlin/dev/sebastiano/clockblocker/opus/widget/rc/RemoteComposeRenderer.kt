package dev.sebastiano.clockblocker.opus.widget.rc

import android.app.PendingIntent
import android.content.Context
import android.util.SizeF
import android.widget.RemoteViews
import androidx.compose.remote.creation.ExperimentalRemoteCreationApi
import androidx.compose.remote.creation.compose.ExperimentalRemoteCreationComposeApi
import androidx.compose.remote.creation.compose.capture.CapturedDocument
import androidx.compose.remote.creation.compose.capture.captureSingleRemoteDocument
import androidx.compose.remote.creation.compose.capture.createCreationDisplayInfo
import androidx.compose.remote.creation.compose.layout.RemoteComposable
import androidx.compose.remote.creation.profile.Profile
import androidx.compose.runtime.Composable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Which document profile the platform widget host can play. Every supported Android version (API 37+) ships the
 * platform Remote Compose player behind `RemoteViews.DrawInstructions`, so this only checks the document version it
 * reports; null (an unknown, older version, or a failing query) means the widgets show their placeholder.
 */
object RemoteComposeSupport {
    fun profileOrNull(): Profile? = runCatching { widgetProfileFor(supportedVersion()) }.getOrNull()

    /** The document version the platform player reports. */
    fun supportedVersion(): Int = RemoteViews.DrawInstructions.getSupportedVersion().toInt()
}

/** Captures `@RemoteComposable` content into platform `RemoteViews(DrawInstructions)`. */
@OptIn(ExperimentalRemoteCreationComposeApi::class, ExperimentalRemoteCreationApi::class)
object RemoteComposeRenderer {

    suspend fun capture(
        context: Context,
        profile: Profile,
        content: @RemoteComposable @Composable () -> Unit,
    ): CapturedDocument = withContext(Dispatchers.Main) {
        // The androidx widget demo captures on Main; the recomposer runs on the caller's context.
        captureSingleRemoteDocument(
            context = context,
            creationDisplayInfo = createCreationDisplayInfo(context),
            profile = profile,
            content = content,
        )
    }

    /**
     * Platform `RemoteViews` playing [document]. [click] answers the main region's id host action
     * ([DeepLinkIntents.CLICK_ACTION_ID]) and [done] the Done button's ([DeepLinkIntents.DONE_ACTION_ID]): the
     * platform player forwards id actions to the click response registered under that id.
     */
    fun remoteViews(document: CapturedDocument, click: PendingIntent? = null, done: PendingIntent? = null): RemoteViews {
        val views = RemoteViews(RemoteViews.DrawInstructions.Builder(listOf(document.bytes)).build())
        document.pendingIntents.forEach { key, pendingIntent -> views.setOnClickPendingIntent(key, pendingIntent) }
        click?.let { views.setOnClickPendingIntent(DeepLinkIntents.CLICK_ACTION_ID, it) }
        done?.let { views.setOnClickPendingIntent(DeepLinkIntents.DONE_ACTION_ID, it) }
        return views
    }

    /**
     * One document per responsive size; the host plays the closest size that fits the widget, else the smallest
     * (see [WidgetSizes.pick]). Sizes that map to the same key share one capture.
     */
    suspend fun <L> responsive(
        context: Context,
        profile: Profile,
        sizes: Map<SizeF, L>,
        click: PendingIntent? = null,
        done: PendingIntent? = null,
        content: @RemoteComposable @Composable (L) -> Unit,
    ): RemoteViews {
        val documents = mutableMapOf<L, CapturedDocument>()
        val views = sizes.mapValues { (_, layout) ->
            val document = documents[layout] ?: capture(context, profile) { content(layout) }.also { documents[layout] = it }
            remoteViews(document, click, done)
        }
        return if (views.size == 1) views.values.single() else RemoteViews(views)
    }
}
