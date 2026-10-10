package dev.sebastiano.clockblocker.opus.debug

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.sebastiano.clockblocker.opus.R
import dev.sebastiano.clockblocker.opus.widget.debug.RemoteComposeNotificationProbe
import dev.sebastiano.clockblocker.opus.widget.debug.RemoteComposeNotificationProbe.Result
import dev.sebastiano.clockblocker.opus.widget.debug.RemoteComposeNotificationProbe.Variant
import dev.sebastiano.clockblocker.opus.widget.debug.RemoteComposeNotificationProbe.Verdict

/** Where the notification probe is. */
sealed interface ProbeState {
    data object Idle : ProbeState
    data object Running : ProbeState
    data class Done(val results: List<Result>) : ProbeState
}

/** The widget gallery's pages ([dev.sebastiano.clockblocker.opus.widget.debug.WidgetGalleryActivity]). */
enum class GalleryPage(val extra: String, val title: String, val description: String) {
    TwoClocks("twoclocks", "Two clocks", "Every size bucket, in every widget theme, on the demo plan."),
    NextUp("nextup", "Next up", "Every size bucket, in every widget theme, on the demo plan."),
    Live(
        "live",
        "Live providers",
        "The real widgets through AppWidgetHost. Binding them needs a one-time adb shell appwidget grantbind.",
    ),
    All("all", "Everything", "All of the above on one long page."),
}

object DebugTags {
    const val RunProbe = "debug_run_probe"
    const val ClearProbe = "debug_clear_probe"
    fun gallery(page: GalleryPage) = "debug_gallery_${page.extra}"
}

/**
 * Debug builds only: tools to try things on a phone without adb. Plain and labelled; no motion beyond the
 * platform's state layers.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebugScreen(
    probe: ProbeState,
    notificationsAllowed: Boolean,
    onRunProbe: () -> Unit,
    onClearProbe: () -> Unit,
    onAllowNotifications: () -> Unit,
    onOpenGallery: (GalleryPage) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Debug") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(painterResource(R.drawable.debug_ic_arrow_back), contentDescription = "Back") }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(Modifier.widthIn(max = 720.dp).fillMaxWidth()) {
                Text(
                    "Debug builds only. These tools post test notifications and host widgets in-process; they don't " +
                        "touch your trips.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
                )
                ProbeSection(probe, notificationsAllowed, onRunProbe, onClearProbe, onAllowNotifications)
                GallerySection(onOpenGallery)
                Spacer(Modifier.height(32.dp))
            }
        }
    }
}

@Composable
private fun ProbeSection(
    probe: ProbeState,
    notificationsAllowed: Boolean,
    onRunProbe: () -> Unit,
    onClearProbe: () -> Unit,
    onAllowNotifications: () -> Unit,
) {
    Header("Remote Compose notification probe (#49)")
    Card {
        Text(
            "Posts ${Variant.entries.size} test notifications whose custom views are the widgets' Remote Compose " +
                "documents, waits ${RemoteComposeNotificationProbe.SETTLE_MS / 1000} s, then reports what the system did " +
                "with each. Open the shade too: \"kept\" means the system didn't drop it, not that it drew.",
            style = MaterialTheme.typography.bodyMedium,
        )
        if (!notificationsAllowed) {
            Text(
                "Notifications are off for this app, so the probe can't post.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
            OutlinedButton(onClick = onAllowNotifications) { Text("Allow notifications") }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = onRunProbe,
                enabled = probe != ProbeState.Running && notificationsAllowed,
                modifier = Modifier.testTag(DebugTags.RunProbe),
            ) { Text(if (probe == ProbeState.Running) "Running…" else "Run the probe") }
            OutlinedButton(onClick = onClearProbe, modifier = Modifier.testTag(DebugTags.ClearProbe)) {
                Text("Clear its notifications")
            }
        }
        when (probe) {
            ProbeState.Idle -> Text(
                "Not run yet.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            ProbeState.Running -> Text(
                "Posting and waiting for the system…",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            is ProbeState.Done -> probe.results.forEach { ResultRow(it) }
        }
    }
}

@Composable
private fun ResultRow(result: Result) {
    val (label, container, content) = when (result.verdict) {
        Verdict.Kept -> Triple("Kept", MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer)
        Verdict.Gone -> Triple("Dropped", MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer)
        Verdict.Threw -> Triple("Threw", MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer)
        Verdict.Unsupported -> Triple(
            "Not run",
            MaterialTheme.colorScheme.surfaceContainerHighest,
            MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
        // A fixed width keeps the variants' names in one column whatever the verdict.
        Surface(color = container, contentColor = content, shape = RoundedCornerShape(8.dp), modifier = Modifier.width(76.dp)) {
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(vertical = 4.dp),
            )
        }
        Column(Modifier.weight(1f)) {
            Text(result.variant.description, style = MaterialTheme.typography.titleSmall)
            Text(result.outcome, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun GallerySection(onOpenGallery: (GalleryPage) -> Unit) {
    Header("Widget gallery")
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        GalleryPage.entries.forEachIndexed { index, page ->
            val last = GalleryPage.entries.lastIndex
            val shape = RoundedCornerShape(
                topStart = if (index == 0) 20.dp else 4.dp,
                topEnd = if (index == 0) 20.dp else 4.dp,
                bottomStart = if (index == last) 20.dp else 4.dp,
                bottomEnd = if (index == last) 20.dp else 4.dp,
            )
            Surface(
                onClick = { onOpenGallery(page) },
                shape = shape,
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp).testTag(DebugTags.gallery(page)),
            ) {
                Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                    Text(page.title, style = MaterialTheme.typography.titleMedium)
                    Text(
                        page.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun Header(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.fillMaxWidth().padding(start = 4.dp, top = 24.dp, bottom = 10.dp).semantics { heading() },
    )
}

@Composable
private fun Card(content: @Composable ColumnScope.() -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}
