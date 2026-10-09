package dev.sebastiano.clockblocker.opus.feature.onboarding

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.sebastiano.clockblocker.opus.core.designsystem.theme.ClockblockTheme

/**
 * "Not medical advice", pinned above Get started so it's on screen at any font size and in landscape (the welcome
 * words can scroll). Tapping it shows the full disclaimer underneath.
 */
@Composable
internal fun WelcomeDisclaimer(modifier: Modifier = Modifier) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val motion = ClockblockTheme.motion
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        modifier
            .clip(MaterialTheme.shapes.medium)
            .clickable(
                role = Role.Button,
                onClickLabel = stringResource(if (expanded) R.string.welcome_disclaimer_hide else R.string.welcome_disclaimer_show),
            ) { expanded = !expanded }
            .heightIn(min = 48.dp)
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .testTag(OnboardingTags.Disclaimer),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(painterResource(R.drawable.onboarding_ic_info), contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.welcome_disclaimer_short), style = MaterialTheme.typography.labelLarge, color = color)
            Spacer(Modifier.width(2.dp))
            Icon(
                painterResource(R.drawable.onboarding_ic_expand_more),
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(18.dp).graphicsLayer { rotationZ = if (expanded) 180f else 0f },
            )
        }
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(motion.containerSpatial()) + fadeIn(motion.fade()),
            exit = shrinkVertically(motion.containerSpatial()) + fadeOut(motion.fade()),
        ) {
            Text(
                stringResource(R.string.welcome_disclaimer),
                style = MaterialTheme.typography.bodySmall,
                color = color,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 480.dp).padding(top = 4.dp),
            )
        }
    }
}
