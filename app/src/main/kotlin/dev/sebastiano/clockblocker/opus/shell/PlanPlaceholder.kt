package dev.sebastiano.clockblocker.opus.shell

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.sebastiano.clockblocker.opus.R
import dev.sebastiano.clockblocker.opus.core.designsystem.illustration.TwoClocksArt
import dev.sebastiano.clockblocker.opus.core.designsystem.theme.ClockblockTheme

/**
 * The detail pane beside Trips while no trip is open (list-detail windows only). It says what the pane is for
 * and nothing more: Trips already offers "New trip" (its FAB or empty state), and a second button for the same
 * action beside it read as two different things. Static art: on a tablet this shows every time Trips does.
 *
 * @param hasTrips whether the user has any trip at all (with none, there is nothing to pick yet).
 */
@Composable
internal fun TripPlanPlaceholder(hasTrips: Boolean, modifier: Modifier = Modifier) {
    Surface(modifier.fillMaxSize().testTag(ShellTestTags.PlanPlaceholder), color = MaterialTheme.colorScheme.surface) {
        Column(
            Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 32.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            TwoClocksArt(Modifier.size(160.dp), progress = 0.35f, animated = false)
            Spacer(Modifier.height(24.dp))
            Text(
                stringResource(if (hasTrips) R.string.plan_placeholder_title else R.string.plan_placeholder_title_no_trips),
                style = ClockblockTheme.textStyles.editorialHeadline,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 420.dp).semantics { heading() },
            )
            Spacer(Modifier.height(12.dp))
            Text(
                stringResource(if (hasTrips) R.string.plan_placeholder_body else R.string.plan_placeholder_body_no_trips),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 420.dp),
            )
        }
    }
}
