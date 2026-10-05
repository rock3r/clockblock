package dev.sebastiano.clockblocker.opus.feature.settings

import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.sebastiano.clockblocker.opus.core.designsystem.illustration.TwoClocksArt
import dev.sebastiano.clockblocker.opus.core.designsystem.theme.OpusTheme
import dev.sebastiano.clockblocker.opus.feature.onboarding.profile.segmentedShape
import dev.zacsweers.metrox.viewmodel.metroViewModel

/** Test tags for the e2e suite. */
object AboutTags {
    const val Version = "settings_version"
    const val Licenses = "about_licenses"
    const val Source = "about_source"
    const val OpusTitleCard = "opus_title_card"
    const val OpusTitleDismiss = "opus_title_dismiss"
    const val LicensesList = "licenses_list"
}

/** Where the source lives (also shown as the row's supporting text). */
const val SourceUrl: String = "https://github.com/rock3r/clockblock"

/**
 * About: version (7 taps → Opus mode), the science, disclaimers, links.
 *
 * @param versionName shown on the version row; read from the package when `null`.
 */
@Composable
fun AboutScreen(onBack: () -> Unit, onOpenLicenses: () -> Unit, modifier: Modifier = Modifier, versionName: String? = null) {
    val viewModel = metroViewModel<AboutViewModel>()
    val showTitleCard by viewModel.showTitleCard.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val snackbars = remember { SnackbarHostState() }
    val version = versionName ?: remember(context) { installedVersionName(context) }
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            val message = when (event) {
                is AboutEvent.TapsAway -> context.resources.getQuantityString(R.plurals.about_taps_away, event.remaining, event.remaining)
                AboutEvent.AlreadyUnlocked -> context.getString(R.string.about_already_unlocked)
                AboutEvent.Unlocked -> null
            }
            if (message != null) {
                snackbars.currentSnackbarData?.dismiss()
                snackbars.showSnackbar(message)
            }
        }
    }
    AboutContent(
        versionName = version,
        onVersionTap = viewModel::onVersionTapped,
        onOpenSource = { runCatching { uriHandler.openUri(SourceUrl) } },
        onOpenLicenses = onOpenLicenses,
        onBack = onBack,
        snackbarHostState = snackbars,
        modifier = modifier,
    )
    OpusTitleCardDialog(visible = showTitleCard, onDismiss = viewModel::dismissTitleCard)
}

/**
 * Hosts [OpusTitleCard] in a dialog window with exactly one entrance and one exit, both ours: the window is
 * edge-to-edge with no platform animation or dim (`decorFitsSystemWindows = false`, window animations off), and
 * a single transition fades our scrim on `colour()` while the card fades in and grows on `artEntrance()`, then
 * fades out on `fade()`. The dialog stays composed until the exit has finished.
 */
@Composable
internal fun OpusTitleCardDialog(visible: Boolean, onDismiss: () -> Unit) {
    val state = remember { MutableTransitionState(false) }
    state.targetState = visible
    if (!state.currentState && !state.targetState) return
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        val window = (LocalView.current.parent as? DialogWindowProvider)?.window
        SideEffect { window?.setWindowAnimations(0) }
        val motion = OpusTheme.motion
        val transition = rememberTransition(state, label = "opusTitleCard")
        val scrimAlpha = transition.animateFloat(transitionSpec = { motion.colour() }, label = "scrim") { shown ->
            if (shown) 1f else 0f
        }
        val scrim = MaterialTheme.colorScheme.scrim.copy(alpha = TitleCardScrimAlpha)
        Box(
            Modifier
                .fillMaxSize()
                .drawBehind { drawRect(scrim, alpha = scrimAlpha.value) }
                // The window fills the screen, so "tap outside" is a tap on our scrim (the card's Surface blocks it).
                .pointerInput(onDismiss) { detectTapGestures { onDismiss() } },
            contentAlignment = Alignment.Center,
        ) {
            transition.AnimatedVisibility(
                visible = { it },
                enter = fadeIn(motion.fade()) + scaleIn(motion.artEntrance(), initialScale = TitleCardInitialScale),
                exit = fadeOut(motion.fade()),
            ) {
                OpusTitleCard(onDismiss = onDismiss)
            }
        }
    }
}

private fun installedVersionName(context: Context): String? = try {
    context.packageManager.getPackageInfo(context.packageName, 0).versionName
} catch (_: PackageManager.NameNotFoundException) {
    null
}

/** Stateless About UI. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutContent(
    versionName: String?,
    onVersionTap: () -> Unit,
    onOpenSource: () -> Unit,
    onOpenLicenses: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    val colors = MaterialTheme.colorScheme
    Scaffold(
        modifier = modifier,
        topBar = { TopAppBar(title = { Text(stringResource(R.string.about_title)) }, navigationIcon = { BackButton(onBack) }) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = colors.surface,
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.widthIn(max = 720.dp).fillMaxWidth().padding(horizontal = 16.dp)) {
                Column(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    TwoClocksArt(Modifier.size(144.dp), progress = 1f, animated = false)
                    Spacer(Modifier.size(12.dp))
                    Text(
                        stringResource(R.string.about_app_name),
                        style = OpusTheme.textStyles.editorialDisplay,
                        color = colors.onSurface,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.semantics { heading() },
                    )
                    Spacer(Modifier.size(8.dp))
                    Text(
                        stringResource(R.string.about_tagline),
                        style = MaterialTheme.typography.bodyLarge,
                        color = colors.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.widthIn(max = 420.dp),
                    )
                }
                Spacer(Modifier.size(16.dp))
                SettingsGroup {
                    SettingsRow(
                        title = stringResource(R.string.about_version),
                        supporting = versionName ?: stringResource(R.string.about_version_unknown),
                        onClick = onVersionTap,
                        trailing = null,
                        shape = segmentedShape(0, 3),
                        tag = AboutTags.Version,
                    )
                    SettingsRow(
                        title = stringResource(R.string.about_source),
                        supporting = stringResource(R.string.about_source_description),
                        onClick = onOpenSource,
                        trailing = {
                            Icon(painterResource(R.drawable.settings_ic_open_in_new), contentDescription = null, tint = colors.onSurfaceVariant)
                        },
                        shape = segmentedShape(1, 3),
                        tag = AboutTags.Source,
                    )
                    SettingsRow(
                        title = stringResource(R.string.about_licenses),
                        supporting = stringResource(R.string.about_licenses_description),
                        onClick = onOpenLicenses,
                        shape = segmentedShape(2, 3),
                        tag = AboutTags.Licenses,
                    )
                }
                Text(
                    stringResource(R.string.about_privacy),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                )

                SectionHeader(stringResource(R.string.about_how_title))
                SettingsGroup {
                    val points = listOf(
                        R.string.about_how_clock_title to R.string.about_how_clock_body,
                        R.string.about_how_light_title to R.string.about_how_light_body,
                        R.string.about_how_pace_title to R.string.about_how_pace_body,
                        R.string.about_how_extras_title to R.string.about_how_extras_body,
                        R.string.about_how_check_title to R.string.about_how_check_body,
                    )
                    points.forEachIndexed { index, (title, body) ->
                        Surface(shape = segmentedShape(index, points.size), color = colors.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
                            Row(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
                                Text(
                                    "${index + 1}",
                                    style = OpusTheme.textStyles.editorialTitle,
                                    color = colors.primary,
                                    modifier = Modifier.width(28.dp),
                                )
                                Column(Modifier.weight(1f)) {
                                    Text(stringResource(title), style = MaterialTheme.typography.titleMedium, color = colors.onSurface)
                                    Spacer(Modifier.size(4.dp))
                                    Text(stringResource(body), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }

                SectionHeader(stringResource(R.string.about_refs_title))
                Surface(shape = RoundedCornerShape(24.dp), color = colors.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        listOf(
                            R.string.about_ref_1, R.string.about_ref_2, R.string.about_ref_3,
                            R.string.about_ref_4, R.string.about_ref_5, R.string.about_ref_6,
                        ).forEach { ref ->
                            Text(stringResource(ref), style = OpusTheme.textStyles.editorialBody, color = colors.onSurface)
                        }
                    }
                }

                SectionHeader(stringResource(R.string.about_disclaimer_title))
                Surface(shape = RoundedCornerShape(24.dp), color = colors.tertiaryContainer, modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(20.dp)) {
                        Icon(
                            painterResource(R.drawable.settings_ic_warning),
                            contentDescription = null,
                            tint = colors.onTertiaryContainer,
                            modifier = Modifier.size(24.dp),
                        )
                        Spacer(Modifier.width(16.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(stringResource(R.string.about_disclaimer_body), style = MaterialTheme.typography.bodyMedium, color = colors.onTertiaryContainer)
                            Text(stringResource(R.string.about_disclaimer_melatonin), style = MaterialTheme.typography.bodyMedium, color = colors.onTertiaryContainer)
                            Text(stringResource(R.string.about_disclaimer_drive), style = MaterialTheme.typography.bodyMedium, color = colors.onTertiaryContainer)
                        }
                    }
                }
                Spacer(Modifier.size(32.dp))
                Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
            }
        }
    }
}

/**
 * The Opus mode title card (design.md §2.7 #3): concert-hall theme, a five-line staff with a few notes, and the
 * title in Fraunces. Static here; [OpusTitleCardDialog] gives it its single, slow entrance (a rare, earned
 * moment) and a quick exit. Under reduce motion only the fades remain.
 */
@Composable
fun OpusTitleCard(onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val reduce = OpusTheme.reduceMotion
    OpusTheme(darkTheme = true, dynamicColor = false, opusMode = true, reduceMotion = reduce) {
        val colors = MaterialTheme.colorScheme
        Surface(
            shape = RoundedCornerShape(32.dp),
            color = colors.surface,
            modifier = modifier.padding(24.dp).widthIn(max = 480.dp).testTag(AboutTags.OpusTitleCard),
        ) {
            Column(Modifier.padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(R.string.opus_title_kicker), style = MaterialTheme.typography.labelLarge, color = colors.primary)
                Spacer(Modifier.size(16.dp))
                Staff(Modifier.fillMaxWidth().height(72.dp))
                Spacer(Modifier.size(20.dp))
                Text(
                    stringResource(R.string.opus_title_card),
                    style = OpusTheme.textStyles.editorialDisplay,
                    color = colors.onSurface,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.semantics { heading() },
                )
                Spacer(Modifier.size(12.dp))
                Text(
                    stringResource(R.string.opus_title_body),
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.size(24.dp))
                Button(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp).testTag(AboutTags.OpusTitleDismiss)) {
                    Text(stringResource(R.string.opus_title_dismiss))
                }
            }
        }
    }
}

private const val TitleCardInitialScale = 0.92f
private const val TitleCardScrimAlpha = 0.6f

/** Five staff lines and a little phrase: high for light, low for sleep, ticks for caffeine. */
@Composable
private fun Staff(modifier: Modifier = Modifier) {
    val line = MaterialTheme.colorScheme.outline
    val note = MaterialTheme.colorScheme.primary
    Canvas(modifier) {
        val gap = size.height / 6f
        val stroke = 1.dp.toPx()
        for (i in 1..5) drawLine(line, Offset(0f, gap * i), Offset(size.width, gap * i), stroke)
        // (x fraction, staff step from the bottom line in half-gaps)
        val phrase = listOf(0.12f to 7, 0.26f to 6, 0.40f to 2, 0.54f to 1, 0.68f to 4, 0.80f to 4, 0.92f to 8)
        val headW = gap * 1.3f
        val headH = gap * 0.95f
        phrase.forEach { (fx, step) ->
            val cx = size.width * fx
            val cy = gap * 5 - step * gap / 2f
            rotate(-20f, Offset(cx, cy)) {
                drawOval(note, topLeft = Offset(cx - headW / 2, cy - headH / 2), size = Size(headW, headH))
            }
            val up = step < 4
            val stemX = if (up) cx + headW / 2 - stroke else cx - headW / 2 + stroke
            val stemEnd = if (up) cy - gap * 3.2f else cy + gap * 3.2f
            drawLine(note, Offset(stemX, cy), Offset(stemX, stemEnd), stroke * 1.5f)
        }
        // A treble-ish flourish on the left, kept abstract.
        drawArc(note, 200f, 300f, false, topLeft = Offset(0f, gap), size = Size(gap * 1.2f, gap * 3f), style = Stroke(stroke * 1.5f))
    }
}
