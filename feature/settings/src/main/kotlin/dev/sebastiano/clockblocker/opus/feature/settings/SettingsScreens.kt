package dev.sebastiano.clockblocker.opus.feature.settings

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.sebastiano.clockblocker.opus.core.data.PinnableWidget
import dev.sebastiano.clockblocker.opus.core.data.backup.ImportMode
import dev.sebastiano.clockblocker.opus.core.designsystem.advice.AdviceGlyph
import dev.sebastiano.clockblocker.opus.core.designsystem.advice.label
import dev.sebastiano.clockblocker.opus.core.designsystem.illustration.TwoClocksArt
import dev.sebastiano.clockblocker.opus.core.designsystem.theme.ClockblockTheme
import dev.sebastiano.clockblocker.opus.core.designsystem.time.cityName
import dev.sebastiano.clockblocker.opus.core.designsystem.time.rememberTimeFormatter
import dev.sebastiano.clockblocker.opus.core.model.AdviceType
import dev.sebastiano.clockblocker.opus.core.model.Chronotype
import dev.sebastiano.clockblocker.opus.core.model.Intensity
import dev.sebastiano.clockblocker.opus.core.model.Place
import dev.sebastiano.clockblocker.opus.core.model.SleepWindow
import dev.sebastiano.clockblocker.opus.core.model.ThemeMode
import dev.sebastiano.clockblocker.opus.core.model.UserProfile
import dev.sebastiano.clockblocker.opus.core.model.ZoneLabels
import dev.sebastiano.clockblocker.opus.core.notifications.NotificationPermissionState
import dev.sebastiano.clockblocker.opus.feature.onboarding.profile.ChronotypeHelperDialog
import dev.sebastiano.clockblocker.opus.feature.onboarding.profile.ChronotypePicker
import dev.sebastiano.clockblocker.opus.feature.onboarding.profile.ChronotypeSky
import dev.sebastiano.clockblocker.opus.feature.onboarding.profile.DefaultMaxDialSize
import dev.sebastiano.clockblocker.opus.feature.onboarding.profile.EffortSelector
import dev.sebastiano.clockblocker.opus.feature.onboarding.profile.SegmentedGap
import dev.sebastiano.clockblocker.opus.feature.onboarding.profile.SleepDial
import dev.sebastiano.clockblocker.opus.feature.onboarding.profile.SleepDialMath
import dev.sebastiano.clockblocker.opus.feature.onboarding.profile.ToolSwitchRow
import dev.sebastiano.clockblocker.opus.feature.onboarding.profile.ToolsEditor
import dev.sebastiano.clockblocker.opus.feature.onboarding.profile.formatDuration
import dev.sebastiano.clockblocker.opus.feature.onboarding.profile.label
import dev.sebastiano.clockblocker.opus.feature.onboarding.profile.segmentedShape
import dev.zacsweers.metrox.viewmodel.metroViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** Test tags for the e2e suite. */
object SettingsTags {
    const val List = "settings_list"
    const val Home = "settings_home"
    const val HomeSearch = "settings_home_search"
    const val Sleep = "settings_sleep"
    const val Chronotype = "settings_chronotype"
    const val EditorSave = "settings_editor_save"
    const val DynamicColor = "settings_dynamic_color"
    const val ReduceMotion = "settings_reduce_motion"
    const val NightSafe = "settings_night_safe"
    const val OpusMode = "settings_opus_mode"
    const val RemindersEnabled = "settings_reminders_enabled"
    const val PermNotifications = "settings_perm_notifications"
    const val PermExact = "settings_perm_exact"
    const val PermLive = "settings_perm_live"
    const val PermBattery = "settings_perm_battery"
    const val TestReminder = "settings_test_reminder"
    const val LockScreen = "settings_lock_screen"
    const val PermSummary = "settings_perm_summary"
    const val Export = "settings_export"
    const val Import = "settings_import"
    const val ImportReplace = "settings_import_replace"
    const val ImportMerge = "settings_import_merge"
    const val ReplayOnboarding = "settings_replay_onboarding"
    const val About = "settings_about"

    /** `settings_theme_System`, `settings_theme_Light`, `settings_theme_Dark`. */
    fun theme(mode: ThemeMode): String = "settings_theme_${mode.name}"

    /** `settings_lead_0` … `settings_lead_30`. */
    fun lead(minutes: Int): String = "settings_lead_$minutes"

    /** `settings_home_result_LHR` etc. */
    fun homeResult(place: Place): String = "settings_home_result_${place.code.ifBlank { place.city }}"

    /** `settings_pin_TwoClocks`, `settings_pin_NextUp`: the widget card's Add buttons. */
    fun pinWidget(widget: PinnableWidget): String = "settings_pin_${widget.name}"
}

/** Everything the Settings UI can ask for. The route binds it to [SettingsViewModel]; tests use no-ops. */
interface SettingsActions {
    fun updateProfile(transform: (UserProfile) -> UserProfile)
    fun searchHome(query: String)
    fun selectHome(place: Place)
    fun clearHomeSearch()
    fun setMelatonin(enabled: Boolean)
    fun acknowledgeMelatonin(acknowledged: Boolean)
    fun setThemeMode(mode: ThemeMode)
    fun setDynamicColor(enabled: Boolean)
    fun setReduceMotion(enabled: Boolean)
    fun setNightSafeAuto(enabled: Boolean)
    fun setOpusModeEnabled(enabled: Boolean)
    fun setRemindersEnabled(enabled: Boolean)
    fun setReminderLead(minutes: Int)
    fun setHideLockScreenDetails(enabled: Boolean)
    fun pinWidget(widget: PinnableWidget)
    fun fixNotifications()
    fun fixExactAlarms()
    fun fixLiveUpdates()
    fun fixBattery()
    fun sendTestReminder()
    fun export()
    fun import()
    fun confirmImport(mode: ImportMode)
    fun cancelImport()
    fun replayOnboarding()
    fun openAbout()

    /** Does nothing; for previews and screenshot tests. */
    object None : SettingsActions {
        override fun updateProfile(transform: (UserProfile) -> UserProfile) = Unit
        override fun searchHome(query: String) = Unit
        override fun selectHome(place: Place) = Unit
        override fun clearHomeSearch() = Unit
        override fun setMelatonin(enabled: Boolean) = Unit
        override fun acknowledgeMelatonin(acknowledged: Boolean) = Unit
        override fun setThemeMode(mode: ThemeMode) = Unit
        override fun setDynamicColor(enabled: Boolean) = Unit
        override fun setReduceMotion(enabled: Boolean) = Unit
        override fun setNightSafeAuto(enabled: Boolean) = Unit
        override fun setOpusModeEnabled(enabled: Boolean) = Unit
        override fun setRemindersEnabled(enabled: Boolean) = Unit
        override fun setReminderLead(minutes: Int) = Unit
        override fun setHideLockScreenDetails(enabled: Boolean) = Unit
        override fun pinWidget(widget: PinnableWidget) = Unit
        override fun fixNotifications() = Unit
        override fun fixExactAlarms() = Unit
        override fun fixLiveUpdates() = Unit
        override fun fixBattery() = Unit
        override fun sendTestReminder() = Unit
        override fun export() = Unit
        override fun import() = Unit
        override fun confirmImport(mode: ImportMode) = Unit
        override fun cancelImport() = Unit
        override fun replayOnboarding() = Unit
        override fun openAbout() = Unit
    }
}

/**
 * Settings (navigation contract used by `:app`; keep these signatures, additive defaulted params OK).
 * [onBack] null when shown as a top-level destination.
 */
@Composable
fun SettingsScreen(
    onBack: (() -> Unit)?,
    onOpenAbout: () -> Unit,
    onReplayOnboarding: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel = metroViewModel<SettingsViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbars = remember { SnackbarHostState() }

    LifecycleResumeEffect(viewModel) {
        viewModel.refreshPermissions()
        onPauseOrDispose { }
    }
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event -> snackbars.showSnackbar(event.message(context)) }
    }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(BackupMimeType)) { uri ->
        if (uri != null) viewModel.export { text -> writeText(context, uri, text) }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.prepareImport { readText(context, uri) }
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        viewModel.refreshPermissions()
    }

    val actions = remember(viewModel) {
        object : SettingsActions {
            override fun updateProfile(transform: (UserProfile) -> UserProfile) = viewModel.updateProfile(transform)
            override fun searchHome(query: String) = viewModel.searchHome(query)
            override fun selectHome(place: Place) = viewModel.setHomePlace(place)
            override fun clearHomeSearch() = viewModel.clearHomeSearch()
            override fun setMelatonin(enabled: Boolean) = viewModel.setMelatonin(enabled)
            override fun acknowledgeMelatonin(acknowledged: Boolean) = viewModel.acknowledgeMelatoninNote(acknowledged)
            override fun setThemeMode(mode: ThemeMode) = viewModel.setThemeMode(mode)
            override fun setDynamicColor(enabled: Boolean) = viewModel.setDynamicColor(enabled)
            override fun setReduceMotion(enabled: Boolean) = viewModel.setReduceMotion(enabled)
            override fun setNightSafeAuto(enabled: Boolean) = viewModel.setNightSafeAuto(enabled)
            override fun setOpusModeEnabled(enabled: Boolean) = viewModel.setOpusModeEnabled(enabled)
            override fun setRemindersEnabled(enabled: Boolean) = viewModel.setRemindersEnabled(enabled)
            override fun setReminderLead(minutes: Int) = viewModel.setReminderLead(minutes)
            override fun setHideLockScreenDetails(enabled: Boolean) = viewModel.setHideLockScreenDetails(enabled)
            override fun pinWidget(widget: PinnableWidget) = viewModel.pinWidget(widget)
            override fun fixNotifications() {
                // Once denied twice the system stops asking; the settings screen is the only way back.
                if (!state.permissions.notificationsGranted) {
                    permissionLauncher.launch(viewModel.runtimePermission)
                } else {
                    launchSafely(context, viewModel.notificationSettingsIntent())
                }
            }
            override fun fixExactAlarms() = launchSafely(context, viewModel.exactAlarmSettingsIntent())
            override fun fixLiveUpdates() = launchSafely(context, viewModel.promotedSettingsIntent())
            override fun fixBattery() = launchSafely(context, viewModel.batteryOptimizationSettingsIntent())
            override fun sendTestReminder() = viewModel.sendTestReminder()
            override fun export() = launchSafely { exportLauncher.launch(viewModel.exportFileName()) }
            override fun import() = launchSafely { importLauncher.launch(arrayOf(BackupMimeType, "text/plain", "application/octet-stream")) }
            override fun confirmImport(mode: ImportMode) = viewModel.confirmImport(mode)
            override fun cancelImport() = viewModel.cancelImport()
            override fun replayOnboarding() = onReplayOnboarding()
            override fun openAbout() = onOpenAbout()
        }
    }
    SettingsContent(state = state, actions = actions, onBack = onBack, snackbarHostState = snackbars, modifier = modifier)
}

private const val BackupMimeType = "application/json"

private suspend fun writeText(context: Context, uri: Uri, text: String) = withContext(Dispatchers.IO) {
    val stream = context.contentResolver.openOutputStream(uri, "wt") ?: throw IOException("No output stream for $uri")
    stream.use { it.write(text.encodeToByteArray()) }
}

private suspend fun readText(context: Context, uri: Uri): String = withContext(Dispatchers.IO) {
    val stream = context.contentResolver.openInputStream(uri) ?: throw IOException("No input stream for $uri")
    stream.use { it.readBytes().decodeToString() }
}

private fun launchSafely(context: Context, intent: Intent) = launchSafely { context.startActivity(intent) }

private inline fun launchSafely(block: () -> Unit) {
    try {
        block()
    } catch (_: ActivityNotFoundException) {
        // No document provider or settings screen on this build; the row stays as a reminder.
    }
}

private fun SettingsEvent.message(context: Context): String = with(context.resources) {
    when (this@message) {
        SettingsEvent.Exported -> getString(R.string.settings_event_exported)
        SettingsEvent.ExportFailed -> getString(R.string.settings_event_export_failed)
        is SettingsEvent.Imported -> when (result.summary()) {
            ImportSummary.Trips -> getQuantityString(R.plurals.settings_event_imported, result.tripsImported, result.tripsImported)
            ImportSummary.CheckIns ->
                getQuantityString(R.plurals.settings_event_merged_check_ins, result.logsImported, result.logsImported)
            ImportSummary.Profile -> getString(R.string.settings_event_merged_profile)
            ImportSummary.NothingNew -> getString(R.string.settings_event_merged_nothing)
        }
        is SettingsEvent.ImportFailed -> getString(
            when (reason) {
                ImportFailure.NotABackup -> R.string.settings_event_not_backup
                ImportFailure.NewerVersion -> R.string.settings_event_newer
                ImportFailure.Unreadable -> R.string.settings_event_unreadable
            },
        )
        SettingsEvent.TestReminderSent -> getString(R.string.settings_event_test_sent)
        SettingsEvent.TestReminderBlocked -> getString(R.string.settings_event_test_blocked)
        SettingsEvent.WidgetPinFailed -> getString(R.string.settings_event_widget_failed)
    }
}

private enum class ProfileEditor { None, Home, Sleep, Chronotype }

/**
 * Stateless Settings UI: a large flexible app bar over grouped, segmented rows. Profile edits open focused
 * editors (sleep dial, chronotype cards, home search); everything else changes in place. From [TwoPaneMinWidth]
 * the sections split into two balanced columns: you, the app's look and More on the left; reminders, widgets
 * and data on the right.
 *
 * @param now the instant used for GMT offset labels (fixed in screenshot tests; offsets move with DST).
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SettingsContent(
    state: SettingsUiState,
    actions: SettingsActions,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    now: Instant = remember { Instant.now() },
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    var editor by rememberSaveable { mutableStateOf(ProfileEditor.None) }
    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                // Wider than the content: the bar narrows to line its title up with the rows.
                val contentWidth = if (maxWidth >= TwoPaneMinWidth) TwoPaneMaxWidth else ContentMaxWidth
                val wide = maxWidth > contentWidth
                LargeFlexibleTopAppBar(
                    title = { Text(stringResource(R.string.settings_title)) },
                    navigationIcon = { if (onBack != null) BackButton(onBack) },
                    scrollBehavior = scrollBehavior,
                    colors = if (wide) {
                        TopAppBarDefaults.topAppBarColors(scrolledContainerColor = MaterialTheme.colorScheme.surface)
                    } else {
                        TopAppBarDefaults.topAppBarColors()
                    },
                    modifier = if (wide) Modifier.widthIn(max = contentWidth) else Modifier,
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.surface,
    ) { padding ->
        BoxWithConstraints(Modifier.fillMaxSize().padding(padding)) {
            val twoPane = maxWidth >= TwoPaneMinWidth
            val you: @Composable ColumnScope.() -> Unit = {
                state.profile?.let { profile ->
                    ProfileSection(profile, state.melatoninAcknowledged, actions, now, onEdit = { editor = it })
                }
                AppearanceSection(state, actions)
            }
            val system: @Composable ColumnScope.() -> Unit = {
                RemindersSection(state, actions)
                if (state.widgetPinningSupported) WidgetsSection(actions)
                DataSection(actions, busy = state.isWorking)
            }
            val end: @Composable ColumnScope.() -> Unit = {
                Spacer(Modifier.size(24.dp))
                Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
            }
            Box(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).testTag(SettingsTags.List),
                contentAlignment = Alignment.TopCenter,
            ) {
                if (twoPane) {
                    Row(
                        Modifier.widthIn(max = TwoPaneMaxWidth).fillMaxWidth().padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(TwoPaneGutter),
                    ) {
                        // "More" closes the shorter left column, so the two land at about the same height.
                        Column(Modifier.weight(1f)) { you(); MoreSection(actions); end() }
                        Column(Modifier.weight(1f)) { system(); end() }
                    }
                } else {
                    Column(Modifier.widthIn(max = ContentMaxWidth).fillMaxWidth().padding(horizontal = 16.dp)) {
                        you()
                        system()
                        MoreSection(actions)
                        end()
                    }
                }
            }
        }
    }

    val profile = state.profile
    if (profile != null) {
        when (editor) {
            ProfileEditor.None -> Unit
            ProfileEditor.Home -> {
                val close = { actions.clearHomeSearch(); editor = ProfileEditor.None }
                EditorDialog(onDismiss = close) {
                    HomeZoneEditor(
                        profile = profile,
                        query = state.homeQuery,
                        results = state.homeResults,
                        onQueryChange = actions::searchHome,
                        onSelect = { actions.selectHome(it); editor = ProfileEditor.None },
                        onClose = close,
                        now = now,
                    )
                }
            }
            ProfileEditor.Sleep -> EditorDialog(onDismiss = { editor = ProfileEditor.None }) {
                SleepEditor(
                    initial = profile.sleep,
                    onSave = { window -> actions.updateProfile { it.copy(sleep = window) }; editor = ProfileEditor.None },
                    onCancel = { editor = ProfileEditor.None },
                    easterEggs = state.easterEggs,
                )
            }
            ProfileEditor.Chronotype -> EditorDialog(onDismiss = { editor = ProfileEditor.None }) {
                ChronotypeEditor(
                    initial = profile.chronotype,
                    workSleep = profile.sleep,
                    onSave = { chronotype -> actions.updateProfile { it.copy(chronotype = chronotype) }; editor = ProfileEditor.None },
                    onCancel = { editor = ProfileEditor.None },
                )
            }
        }
    }
    state.pendingImport?.let { pending ->
        EditorDialog(onDismiss = actions::cancelImport) {
            ImportConfirm(pending, onReplace = { actions.confirmImport(ImportMode.Replace) }, onMerge = { actions.confirmImport(ImportMode.Merge) }, onCancel = actions::cancelImport)
        }
    }
}

private val ContentMaxWidth = 720.dp

/** Expanded width class: from here Settings lays its sections out in two columns. */
private val TwoPaneMinWidth = 840.dp
private val TwoPaneMaxWidth = 1200.dp
private val TwoPaneGutter = 24.dp

@Composable
private fun MoreSection(actions: SettingsActions) {
    SectionHeader(stringResource(R.string.settings_section_more))
    SettingsGroup {
        SettingsRow(
            title = stringResource(R.string.settings_replay),
            supporting = stringResource(R.string.settings_replay_description),
            onClick = actions::replayOnboarding,
            shape = segmentedShape(0, 2),
            tag = SettingsTags.ReplayOnboarding,
        )
        SettingsRow(
            title = stringResource(R.string.settings_about),
            supporting = stringResource(R.string.settings_about_description),
            onClick = actions::openAbout,
            shape = segmentedShape(1, 2),
            tag = SettingsTags.About,
        )
    }
}

@Composable
internal fun BackButton(onBack: () -> Unit) {
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    IconButton(onClick = onBack) {
        Icon(
            painterResource(R.drawable.settings_ic_arrow_back),
            contentDescription = stringResource(R.string.settings_back),
            modifier = Modifier.graphicsLayer { scaleX = if (rtl) -1f else 1f },
        )
    }
}

@Composable
private fun ProfileSection(
    profile: UserProfile,
    melatoninAcknowledged: Boolean,
    actions: SettingsActions,
    now: Instant,
    onEdit: (ProfileEditor) -> Unit,
) {
    val formatter = rememberTimeFormatter()
    val zone = remember(profile.homeZoneId) { ZoneId.of(profile.homeZoneId) }
    SectionHeader(stringResource(R.string.settings_section_profile))
    SettingsGroup {
        SettingsRow(
            title = stringResource(R.string.settings_home),
            supporting = stringResource(R.string.settings_home_value, zone.cityName(), gmtOffsetLabel(zone, now)),
            onClick = { onEdit(ProfileEditor.Home) },
            shape = segmentedShape(0, 3),
            tag = SettingsTags.Home,
        )
        SettingsRow(
            title = stringResource(R.string.settings_sleep),
            supporting = stringResource(
                R.string.settings_sleep_value,
                formatter.format(profile.sleep.bedtime),
                formatter.format(profile.sleep.wake),
                formatDuration(SleepDialMath.durationMinutes(profile.sleep)),
            ),
            onClick = { onEdit(ProfileEditor.Sleep) },
            shape = segmentedShape(1, 3),
            tag = SettingsTags.Sleep,
        )
        SettingsRow(
            title = stringResource(R.string.settings_chronotype),
            supporting = profile.chronotype.label(),
            onClick = { onEdit(ProfileEditor.Chronotype) },
            shape = segmentedShape(2, 3),
            tag = SettingsTags.Chronotype,
            trailing = {
                ChronotypeSky(profile.chronotype, Modifier.size(36.dp))
                Spacer(Modifier.width(8.dp))
                Icon(painterResource(R.drawable.settings_ic_chevron_right), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            },
        )
    }
    SectionHeader(stringResource(R.string.settings_tools))
    ToolsEditor(
        profile = profile,
        melatoninAcknowledged = melatoninAcknowledged,
        onProfileChange = { updated -> actions.updateProfile { updated } },
        onMelatoninChange = actions::setMelatonin,
        onAcknowledgeMelatonin = actions::acknowledgeMelatonin,
    )
    SectionHeader(stringResource(R.string.settings_effort))
    EffortSelector(profile.intensity, onSelect = { intensity: Intensity -> actions.updateProfile { it.copy(intensity = intensity) } })
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun AppearanceSection(state: SettingsUiState, actions: SettingsActions) {
    val settings = state.settings
    SectionHeader(stringResource(R.string.settings_section_appearance))
    val rows = buildList {
        add("theme")
        add("dynamic")
        add("motion")
        add("night")
        if (settings.opusModeUnlocked) add("opus")
    }
    SettingsGroup {
        rows.forEachIndexed { index, row ->
            val shape = segmentedShape(index, rows.size)
            when (row) {
                "theme" -> Surface(shape = shape, color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
                        Text(stringResource(R.string.settings_theme), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                        Spacer(Modifier.size(12.dp))
                        ConnectedChoice(
                            options = ThemeMode.entries,
                            selected = settings.themeMode,
                            onSelect = actions::setThemeMode,
                            label = { mode ->
                                stringResource(
                                    when (mode) {
                                        ThemeMode.System -> R.string.settings_theme_system
                                        ThemeMode.Light -> R.string.settings_theme_light
                                        ThemeMode.Dark -> R.string.settings_theme_dark
                                    },
                                )
                            },
                            tag = SettingsTags::theme,
                        )
                    }
                }
                "dynamic" -> ToolSwitchRow(
                    title = stringResource(R.string.settings_dynamic_color),
                    description = stringResource(R.string.settings_dynamic_color_description),
                    checked = settings.dynamicColor,
                    onCheckedChange = actions::setDynamicColor,
                    shape = shape,
                    tag = SettingsTags.DynamicColor,
                )
                "motion" -> ToolSwitchRow(
                    title = stringResource(R.string.settings_reduce_motion),
                    description = stringResource(R.string.settings_reduce_motion_description),
                    checked = settings.reduceMotion,
                    onCheckedChange = actions::setReduceMotion,
                    shape = shape,
                    tag = SettingsTags.ReduceMotion,
                )
                "night" -> ToolSwitchRow(
                    title = stringResource(R.string.settings_night_safe),
                    description = stringResource(R.string.settings_night_safe_description),
                    checked = settings.nightSafeAuto,
                    onCheckedChange = actions::setNightSafeAuto,
                    shape = shape,
                    tag = SettingsTags.NightSafe,
                )
                "opus" -> ToolSwitchRow(
                    title = stringResource(R.string.settings_opus_mode),
                    description = stringResource(R.string.settings_opus_mode_description),
                    checked = settings.opusModeEnabled,
                    onCheckedChange = actions::setOpusModeEnabled,
                    shape = shape,
                    tag = SettingsTags.OpusMode,
                )
            }
        }
    }
}

/**
 * A connected single-choice button group (M3 Expressive). When the options can't each get [minOptionWidth]
 * (scaled with the font) they split into two connected rows, so every label keeps the same, readable size.
 * [showCheck] adds a tick to the selected option.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun <T> ConnectedChoice(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    label: @Composable (T) -> String,
    tag: (T) -> String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    showCheck: Boolean = true,
    minOptionWidth: Dp = 64.dp,
) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val gap = ButtonGroupDefaults.ConnectedSpaceBetween
        val fontScale = LocalDensity.current.fontScale
        val perOption = (maxWidth - gap * (options.size - 1)) / options.size
        val rows = if (options.size > 2 && perOption < minOptionWidth * fontScale) {
            options.chunked((options.size + 1) / 2)
        } else {
            listOf(options)
        }
        Column(verticalArrangement = Arrangement.spacedBy(gap)) {
            rows.forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap)) {
                    row.forEachIndexed { index, option ->
                        val checked = option == selected
                        ToggleButton(
                            checked = checked,
                            onCheckedChange = { onSelect(option) },
                            enabled = enabled,
                            // These rows sit on surfaceContainer cards, the toggle's default unchecked container:
                            // lift it a step so unselected options still read as buttons.
                            colors = ToggleButtonDefaults.colors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            ),
                            shapes = when (index) {
                                0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                                row.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                                else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                            },
                            contentPadding = PaddingValues(horizontal = 6.dp),
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .semantics { role = Role.RadioButton }
                                .testTag(tag(option)),
                        ) {
                            if (checked && showCheck) {
                                Icon(painterResource(R.drawable.settings_ic_check), contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                            }
                            Text(
                                label(option),
                                maxLines = 1,
                                autoSize = TextAutoSize.StepBased(minFontSize = 10.sp, maxFontSize = LocalTextStyle.current.fontSize),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RemindersSection(state: SettingsUiState, actions: SettingsActions) {
    val settings = state.settings
    val permissions = state.permissions
    val motion = ClockblockTheme.motion
    SectionHeader(stringResource(R.string.settings_section_reminders))
    SettingsGroup {
        ToolSwitchRow(
            title = stringResource(R.string.settings_reminders_enabled),
            description = stringResource(R.string.settings_reminders_enabled_description),
            checked = settings.remindersEnabled,
            onCheckedChange = actions::setRemindersEnabled,
            shape = segmentedShape(0, 4),
            tag = SettingsTags.RemindersEnabled,
        )
        Surface(shape = segmentedShape(1, 4), color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp).graphicsLayer { alpha = if (settings.remindersEnabled) 1f else 0.38f }) {
                Text(stringResource(R.string.settings_lead), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                Text(
                    if (settings.reminderLeadMinutes == 0) {
                        stringResource(R.string.settings_lead_description_zero)
                    } else {
                        stringResource(R.string.settings_lead_description, settings.reminderLeadMinutes)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.size(12.dp))
                ConnectedChoice(
                    options = ReminderLeadOptions,
                    selected = settings.reminderLeadMinutes,
                    onSelect = actions::setReminderLead,
                    label = { if (it == 0) stringResource(R.string.settings_lead_at_start) else stringResource(R.string.settings_lead_option, it) },
                    tag = SettingsTags::lead,
                    enabled = settings.remindersEnabled,
                    // Five narrow options: the fill alone marks the choice, so labels keep their full size.
                    showCheck = false,
                )
            }
        }
        ToolSwitchRow(
            title = stringResource(R.string.settings_lock_screen),
            description = stringResource(R.string.settings_lock_screen_description),
            checked = settings.hideLockScreenDetails,
            onCheckedChange = actions::setHideLockScreenDetails,
            shape = segmentedShape(2, 4),
            tag = SettingsTags.LockScreen,
        )
        SettingsRow(
            title = stringResource(R.string.settings_test_reminder),
            supporting = stringResource(R.string.settings_test_reminder_description),
            shape = segmentedShape(3, 4),
            tag = "${SettingsTags.TestReminder}_row",
            trailing = {
                FilledTonalButton(onClick = actions::sendTestReminder, modifier = Modifier.heightIn(min = 48.dp).testTag(SettingsTags.TestReminder)) {
                    Text(stringResource(R.string.settings_send))
                }
            },
        )
    }
    AnimatedVisibility(
        visible = settings.remindersEnabled,
        // One tier: the permissions section grows in place, so its fade rides the same container spring as its
        // height and both land together (and snap together under reduced motion).
        enter = expandVertically(motion.containerSpatial()) + fadeIn(motion.containerSpatial()),
        exit = shrinkVertically(motion.containerSpatial()) + fadeOut(motion.containerSpatial()),
    ) {
        PermissionsSection(permissions, actions)
    }
}

private enum class PermissionKind { Notifications, Exact, Live, Battery }

/**
 * "What Android allows": a one-line summary that expands to the permission rows. It starts expanded, and
 * expands again by itself, whenever reminders aren't dependable (notifications or exact timing missing); the
 * optional extras (Live Updates, battery) alone never force it open.
 */
@Composable
private fun PermissionsSection(permissions: NotificationPermissionState, actions: SettingsActions) {
    val motion = ClockblockTheme.motion
    val rows = PermissionKind.entries
    val needsAttention = !permissions.isReliable
    var expanded by rememberSaveable { mutableStateOf(needsAttention) }
    LaunchedEffect(needsAttention) { if (needsAttention) expanded = true }
    Column {
        SectionHeader(stringResource(R.string.settings_permissions))
        SettingsGroup {
            PermissionSummary(permissions, expanded, onToggle = { expanded = !expanded }, shape = segmentedShape(0, if (expanded) rows.size + 1 else 1))
            AnimatedVisibility(
                visible = expanded,
                // The rows grow in place under the summary: size and alpha share the container spring.
                enter = expandVertically(motion.containerSpatial()) + fadeIn(motion.containerSpatial()),
                exit = shrinkVertically(motion.containerSpatial()) + fadeOut(motion.containerSpatial()),
            ) {
                SettingsGroup {
                    rows.forEachIndexed { index, row ->
                        val shape = segmentedShape(index + 1, rows.size + 1)
                        when (row) {
                            PermissionKind.Notifications -> PermissionRow(
                                title = stringResource(R.string.settings_perm_notifications),
                                description = stringResource(R.string.settings_perm_notifications_description),
                                granted = permissions.notificationsGranted,
                                actionLabel = stringResource(R.string.settings_perm_allow),
                                onAction = actions::fixNotifications,
                                shape = shape,
                                tag = SettingsTags.PermNotifications,
                            )
                            PermissionKind.Exact -> PermissionRow(
                                title = stringResource(R.string.settings_perm_exact),
                                description = stringResource(R.string.settings_perm_exact_description),
                                granted = permissions.exactAlarmsAllowed,
                                actionLabel = stringResource(R.string.settings_perm_fix),
                                onAction = actions::fixExactAlarms,
                                shape = shape,
                                tag = SettingsTags.PermExact,
                            )
                            PermissionKind.Live -> PermissionRow(
                                title = stringResource(R.string.settings_perm_live),
                                description = stringResource(R.string.settings_perm_live_description),
                                granted = permissions.promotedAllowed,
                                actionLabel = stringResource(R.string.settings_perm_fix),
                                onAction = actions::fixLiveUpdates,
                                shape = shape,
                                tag = SettingsTags.PermLive,
                            )
                            PermissionKind.Battery -> PermissionRow(
                                title = stringResource(R.string.settings_perm_battery),
                                description = stringResource(R.string.settings_perm_battery_description),
                                granted = permissions.batteryOptimizationIgnored,
                                actionLabel = stringResource(R.string.settings_perm_fix),
                                onAction = actions::fixBattery,
                                shape = shape,
                                tag = SettingsTags.PermBattery,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * The always-visible line of "What Android allows": an icon plus words for the overall state (never colour
 * alone), and a chevron that turns on the data tier. The whole row toggles the details.
 */
@Composable
private fun PermissionSummary(permissions: NotificationPermissionState, expanded: Boolean, onToggle: () -> Unit, shape: Shape) {
    val colors = MaterialTheme.colorScheme
    val motion = ClockblockTheme.motion
    val attention = listOf(permissions.notificationsGranted, permissions.exactAlarmsAllowed).count { !it }
    val optionalOff = listOfNotNull(
        permissions.promotedAllowed,
        permissions.batteryOptimizationIgnored,
    ).count { !it }
    val title = when {
        attention > 0 -> pluralStringResource(R.plurals.settings_perm_summary_attention, attention, attention)
        optionalOff > 0 -> stringResource(R.string.settings_perm_summary_ready)
        else -> stringResource(R.string.settings_perm_summary_all)
    }
    val supporting = when {
        attention > 0 -> stringResource(R.string.settings_perm_summary_attention_description)
        optionalOff > 0 -> pluralStringResource(R.plurals.settings_perm_summary_optional, optionalOff, optionalOff)
        else -> stringResource(R.string.settings_perm_summary_all_description)
    }
    val stateLabel = stringResource(if (expanded) R.string.settings_perm_expanded else R.string.settings_perm_collapsed)
    val clickLabel = stringResource(if (expanded) R.string.settings_perm_hide else R.string.settings_perm_show)
    // Reports state (open/closed), so no bounce; read in the draw phase so turning never recomposes the row.
    val rotation = animateFloatAsState(if (expanded) 180f else 0f, motion.dataSpatial(), label = "permChevron")
    Surface(shape = shape, color = colors.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .clip(shape)
                .clickable(onClickLabel = clickLabel, onClick = onToggle)
                .semantics { stateDescription = stateLabel }
                .heightIn(min = 64.dp)
                .padding(horizontal = 20.dp, vertical = 14.dp)
                .testTag(SettingsTags.PermSummary),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painterResource(if (attention > 0) R.drawable.settings_ic_warning else R.drawable.settings_ic_check),
                contentDescription = null,
                tint = if (attention > 0) colors.error else colors.primary,
                modifier = Modifier.size(24.dp),
            )
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = colors.onSurface)
                Text(supporting, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
            }
            Spacer(Modifier.width(16.dp))
            Icon(
                painterResource(R.drawable.settings_ic_expand_more),
                contentDescription = null,
                tint = colors.onSurfaceVariant,
                modifier = Modifier.size(24.dp).graphicsLayer { rotationZ = rotation.value },
            )
        }
    }
}

/**
 * One-tap widget pinning: a still preview of each widget, its name and what it shows, and an Add button that
 * asks the launcher to place it. Only shown when the launcher supports pinning.
 */
@Composable
private fun WidgetsSection(actions: SettingsActions) {
    SectionHeader(stringResource(R.string.settings_section_widgets))
    Surface(shape = segmentedShape(0, 1), color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                stringResource(R.string.settings_widgets_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val gap = 12.dp
                // Side by side while each tile keeps a comfortable measure for its text; stacked otherwise (large
                // fonts, narrow windows), so names and descriptions never squeeze.
                val sideBySide = (maxWidth - gap) / 2 >= WidgetTileMinWidth * LocalDensity.current.fontScale
                if (sideBySide) {
                    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(gap)) {
                        PinnableWidget.entries.forEach { widget ->
                            WidgetTile(widget, onAdd = { actions.pinWidget(widget) }, modifier = Modifier.weight(1f).fillMaxHeight())
                        }
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(gap)) {
                        PinnableWidget.entries.forEach { widget ->
                            WidgetTile(widget, onAdd = { actions.pinWidget(widget) }, modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WidgetTile(widget: PinnableWidget, onAdd: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val name = stringResource(
        when (widget) {
            PinnableWidget.TwoClocks -> R.string.settings_widget_two_clocks
            PinnableWidget.NextUp -> R.string.settings_widget_next_up
        },
    )
    val description = stringResource(
        when (widget) {
            PinnableWidget.TwoClocks -> R.string.settings_widget_two_clocks_description
            PinnableWidget.NextUp -> R.string.settings_widget_next_up_description
        },
    )
    val addDescription = stringResource(R.string.settings_widget_add_description, name)
    Surface(shape = MaterialTheme.shapes.large, color = colors.surfaceContainerHighest, modifier = modifier) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.fillMaxWidth().height(WidgetPreviewHeight), contentAlignment = Alignment.Center) {
                when (widget) {
                    PinnableWidget.TwoClocks -> TwoClocksArt(Modifier.size(WidgetPreviewHeight), progress = 0.35f, animated = false)
                    PinnableWidget.NextUp -> NextUpPreview()
                }
            }
            Text(name, style = MaterialTheme.typography.titleMedium, color = colors.onSurface)
            Text(description, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
            Spacer(Modifier.weight(1f))
            FilledTonalButton(
                onClick = onAdd,
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .semantics { contentDescription = addDescription }
                    .testTag(SettingsTags.pinWidget(widget)),
            ) {
                Text(stringResource(R.string.settings_widget_add))
            }
        }
    }
}

/** A still, miniature Next up row: the glyph, its label and a countdown, as the widget shows them (a short label, so it fits a tile). */
@Composable
private fun NextUpPreview() {
    val colors = MaterialTheme.colorScheme
    Surface(shape = MaterialTheme.shapes.medium, color = colors.surfaceContainerLow, modifier = Modifier.clearAndSetSemantics { }) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            AdviceGlyph(AdviceType.Nap, active = true, size = 28.dp)
            Spacer(Modifier.width(8.dp))
            Column {
                Text(AdviceType.Nap.label(), style = MaterialTheme.typography.labelLarge, color = colors.onSurface, maxLines = 1)
                Text(stringResource(R.string.settings_widget_preview_countdown), style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant, maxLines = 1)
            }
        }
    }
}

private val WidgetPreviewHeight = 72.dp
private val WidgetTileMinWidth = 148.dp

@Composable
private fun DataSection(actions: SettingsActions, busy: Boolean) {
    SectionHeader(stringResource(R.string.settings_section_data))
    SettingsGroup {
        SettingsRow(
            title = stringResource(R.string.settings_export),
            supporting = stringResource(R.string.settings_export_description),
            onClick = actions::export,
            enabled = !busy,
            shape = segmentedShape(0, 2),
            tag = SettingsTags.Export,
        )
        SettingsRow(
            title = stringResource(R.string.settings_import),
            supporting = stringResource(R.string.settings_import_description),
            onClick = actions::import,
            enabled = !busy,
            shape = segmentedShape(1, 2),
            tag = SettingsTags.Import,
        )
    }
}

/** Editor title in the shared serif. */
@Composable
private fun EditorTitle(text: String) {
    Text(text, style = ClockblockTheme.textStyles.editorialTitle, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.fillMaxWidth())
}

@Composable
private fun EditorButtons(onCancel: () -> Unit, onSave: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = onCancel, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.settings_cancel)) }
        Spacer(Modifier.width(8.dp))
        Button(onClick = onSave, modifier = Modifier.heightIn(min = 48.dp).testTag(SettingsTags.EditorSave)) { Text(stringResource(R.string.settings_save)) }
    }
}

/** Sleep editor: the onboarding dial on a draft; saved only on Save. */
@Composable
internal fun SleepEditor(initial: SleepWindow, onSave: (SleepWindow) -> Unit, onCancel: () -> Unit, easterEggs: Boolean = false) {
    var draft by rememberSaveable(stateSaver = SleepWindowSaver) { mutableStateOf(initial) }
    Column(Modifier.verticalScroll(rememberScrollState()).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        EditorTitle(stringResource(R.string.settings_sleep_dialog_title))
        Spacer(Modifier.size(16.dp))
        // Landscape phones: a full-width dial pushed Save below the fold (and dragging the dial fought the scroll),
        // so the dial shrinks to what the window height leaves after the title, times and buttons.
        val windowHeight = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.height.toDp() }
        val maxDial = (windowHeight - SleepEditorChrome).coerceIn(SleepDialMinSize, DefaultMaxDialSize)
        SleepDial(draft, { draft = it }, Modifier.fillMaxWidth(), easterEggEnabled = easterEggs, maxDialSize = maxDial)
        Spacer(Modifier.size(16.dp))
        EditorButtons(onCancel = onCancel, onSave = { onSave(draft) })
    }
}

/** Title, bedtime/wake times, buttons, paddings and the dialog's own margins. */
private val SleepEditorChrome = 290.dp
private val SleepDialMinSize = 150.dp

private val SleepWindowSaver = androidx.compose.runtime.saveable.Saver<SleepWindow, IntArray>(
    save = { intArrayOf(it.bedtime.toSecondOfDay(), it.wake.toSecondOfDay()) },
    restore = { SleepWindow(java.time.LocalTime.ofSecondOfDay(it[0].toLong()), java.time.LocalTime.ofSecondOfDay(it[1].toLong())) },
)

/** Chronotype editor: the five cards plus the "Not sure?" helper, on a draft. */
@Composable
internal fun ChronotypeEditor(initial: Chronotype, workSleep: SleepWindow, onSave: (Chronotype) -> Unit, onCancel: () -> Unit) {
    var draft by rememberSaveable { mutableStateOf(initial) }
    var helper by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.verticalScroll(rememberScrollState()).padding(24.dp)) {
        EditorTitle(stringResource(R.string.settings_chronotype_dialog_title))
        Spacer(Modifier.size(16.dp))
        ChronotypePicker(selected = draft, onSelect = { draft = it }, onNotSure = { helper = true })
        Spacer(Modifier.size(16.dp))
        EditorButtons(onCancel = onCancel, onSave = { onSave(draft) })
    }
    if (helper) {
        ChronotypeHelperDialog(workSleep = workSleep, onUse = { draft = it; helper = false }, onDismiss = { helper = false })
    }
}

/** Home time zone editor: offline place search; picking a result saves straight away. */
@Composable
internal fun HomeZoneEditor(
    profile: UserProfile,
    query: String,
    results: List<Place>,
    onQueryChange: (String) -> Unit,
    onSelect: (Place) -> Unit,
    onClose: () -> Unit,
    now: Instant = remember { Instant.now() },
) {
    val zone = remember(profile.homeZoneId) { ZoneId.of(profile.homeZoneId) }
    val formatter = rememberTimeFormatter()
    val colors = MaterialTheme.colorScheme
    Column(Modifier.verticalScroll(rememberScrollState()).padding(24.dp)) {
        EditorTitle(stringResource(R.string.settings_home_dialog_title))
        Spacer(Modifier.size(8.dp))
        Text(stringResource(R.string.settings_home_dialog_body), style = MaterialTheme.typography.bodyLarge, color = colors.onSurfaceVariant)
        Spacer(Modifier.size(16.dp))
        Surface(shape = MaterialTheme.shapes.large, color = colors.secondaryContainer, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text(zone.cityName(), style = MaterialTheme.typography.titleLarge, color = colors.onSecondaryContainer)
                Text(
                    "${formatter.format(java.time.LocalTime.ofInstant(now, zone))} · ${gmtOffsetLabel(zone, now)}" +
                        ZoneLabels.longName(zone, now, LocalConfiguration.current.locales[0])?.let { " · $it" }.orEmpty(),
                    style = ClockblockTheme.textStyles.timeLabel,
                    color = colors.onSecondaryContainer,
                )
            }
        }
        Spacer(Modifier.size(16.dp))
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            label = { Text(stringResource(R.string.settings_home_search)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            shape = MaterialTheme.shapes.extraLarge,
            modifier = Modifier.fillMaxWidth().testTag(SettingsTags.HomeSearch),
        )
        Spacer(Modifier.size(12.dp))
        if (query.isNotBlank() && results.isEmpty()) {
            Text(stringResource(R.string.settings_home_no_results, query.trim()), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
        }
        Column(verticalArrangement = Arrangement.spacedBy(SegmentedGap)) {
            results.forEachIndexed { index, place ->
                val shape = segmentedShape(index, results.size)
                val description = stringResource(R.string.settings_home_result_description, place.city, place.name, place.zoneId)
                Surface(shape = shape, color = colors.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
                    Row(
                        Modifier
                            .clip(shape)
                            .clickable { onSelect(place) }
                            .semantics(mergeDescendants = true) { contentDescription = description }
                            .heightIn(min = 56.dp)
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                            .testTag(SettingsTags.homeResult(place)),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(place.displayCode, style = ClockblockTheme.textStyles.timeLabel, color = colors.primary, textAlign = TextAlign.Center, modifier = Modifier.widthIn(min = 44.dp))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(place.city, style = MaterialTheme.typography.titleMedium, color = colors.onSurface, maxLines = 1)
                            Text(place.name, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, maxLines = 1)
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(gmtOffsetLabel(place.zone, now), style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
                    }
                }
            }
        }
        Spacer(Modifier.size(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onClose, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.settings_close)) }
        }
    }
}

/** Replace/Merge confirmation for a decoded backup. */
@Composable
internal fun ImportConfirm(pending: PendingImport, onReplace: () -> Unit, onMerge: () -> Unit, onCancel: () -> Unit, zone: ZoneId = ZoneId.systemDefault()) {
    val date = remember(pending.exportedAt, zone) {
        DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withZone(zone).format(pending.exportedAt)
    }
    Column(Modifier.verticalScroll(rememberScrollState()).padding(24.dp)) {
        EditorTitle(stringResource(R.string.settings_import_dialog_title))
        Spacer(Modifier.size(8.dp))
        Text(
            pluralStringResource(R.plurals.settings_import_dialog_trips, pending.tripCount, pending.tripCount, date),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.size(8.dp))
        Text(stringResource(R.string.settings_import_dialog_body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.size(20.dp))
        // Wraps at large font scales instead of squeezing a label onto three lines.
        FlowRow(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onCancel, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.settings_cancel)) }
            OutlinedButton(onClick = onMerge, modifier = Modifier.heightIn(min = 48.dp).testTag(SettingsTags.ImportMerge)) { Text(stringResource(R.string.settings_import_merge)) }
            Button(onClick = onReplace, modifier = Modifier.heightIn(min = 48.dp).testTag(SettingsTags.ImportReplace)) { Text(stringResource(R.string.settings_import_replace)) }
        }
    }
}

/** "GMT", "GMT+2", "GMT+5:30", "GMT−3" for the zone's offset at [at]. */
internal fun gmtOffsetLabel(zone: ZoneId, at: Instant): String = ZoneLabels.offset(zone, at)
