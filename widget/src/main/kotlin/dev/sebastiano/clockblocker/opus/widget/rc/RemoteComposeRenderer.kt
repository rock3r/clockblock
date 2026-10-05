package dev.sebastiano.clockblocker.opus.widget.rc

import android.app.PendingIntent
import android.content.Context
import android.os.Build
import android.util.SizeF
import android.widget.RemoteViews
import androidx.annotation.RequiresApi
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

/** Whether (and with which document profile) the platform widget host can play Remote Compose documents. */
object RemoteComposeSupport {
    /** API 36 (Android 16) shipped the platform Remote Compose player behind `RemoteViews.DrawInstructions`. */
    const val MIN_SDK = Build.VERSION_CODES.BAKLAVA

    fun profileOrNull(sdkInt: Int = Build.VERSION.SDK_INT): Profile? {
        if (sdkInt < MIN_SDK) return null
        return runCatching { widgetProfileFor(supportedVersion()) }.getOrNull()
    }

    @RequiresApi(Build.VERSION_CODES.BAKLAVA)
    private fun supportedVersion(): Int = RemoteViews.DrawInstructions.getSupportedVersion().toInt()
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
     * Platform `RemoteViews` playing [document]. [click] answers the card's id host action
     * ([DeepLinkIntents.CLICK_ACTION_ID]): the platform player forwards id actions to the click response registered
     * under that id.
     */
    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    fun remoteViews(document: CapturedDocument, click: PendingIntent? = null): RemoteViews {
        val views = RemoteViews(RemoteViews.DrawInstructions.Builder(listOf(document.bytes)).build())
        document.pendingIntents.forEach { key, pendingIntent -> views.setOnClickPendingIntent(key, pendingIntent) }
        click?.let { views.setOnClickPendingIntent(DeepLinkIntents.CLICK_ACTION_ID, it) }
        return views
    }

    /** One document per responsive size; the host picks the largest that fits (API 31+ size mapping). */
    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    suspend fun <L> responsive(
        context: Context,
        profile: Profile,
        sizes: Map<SizeF, L>,
        click: PendingIntent? = null,
        content: @RemoteComposable @Composable (L) -> Unit,
    ): RemoteViews {
        val views = sizes.mapValues { (_, layout) -> remoteViews(capture(context, profile) { content(layout) }, click) }
        return if (views.size == 1) views.values.single() else RemoteViews(views)
    }
}
