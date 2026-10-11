package dev.sebastiano.clockblocker.opus.feature.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mikepenz.aboutlibraries.Libs
import com.mikepenz.aboutlibraries.ui.compose.m3.LibrariesContainer
import com.mikepenz.aboutlibraries.ui.compose.variant.LibraryDetailMode
import dev.sebastiano.clockblocker.opus.core.designsystem.theme.ClockblockTheme

/** The `tag` of the app's own entry (`app/config/aboutlibraries/libraries/clockblock.json`). */
internal const val AppLicenseTag = "clockblock"

/** The app's own entry first; everything else keeps the plugin's order (by name). */
internal fun Libs.appFirst(): Libs = copy(libraries = libraries.sortedBy { it.tag != AppLicenseTag })

/**
 * Open-source licences: every library the app is built with, collected at build time by the AboutLibraries Gradle
 * plugin in `:app` (`res/raw/aboutlibraries.json`), plus what the plugin can't see in the dependency graph (the app
 * itself, vendored code, fonts, icons and data sources), defined in `app/config/aboutlibraries`. The app leads; tapping
 * an entry opens its licence text in a dialog (bundled, so it works offline).
 *
 * [libraries] is null while the file is being read (the list then shows nothing yet). Back is the caller's, with the
 * predictive back gesture; the list draws edge to edge under the navigation bar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LicensesScreen(libraries: Libs?, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val ordered = remember(libraries) { libraries?.appFirst() }
    Scaffold(
        modifier = modifier,
        topBar = { TopAppBar(title = { Text(stringResource(R.string.licenses_title)) }, navigationIcon = { BackButton(onBack) }) },
        containerColor = colors.surface,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { padding ->
        val navigationBar = WindowInsets.navigationBars.asPaddingValues()
        Box(Modifier.fillMaxSize().padding(top = padding.calculateTopPadding()), contentAlignment = Alignment.TopCenter) {
            LibrariesContainer(
                libraries = ordered,
                contentPadding = PaddingValues(bottom = navigationBar.calculateBottomPadding() + 24.dp),
                header = {
                    item(key = "intro") {
                        Text(
                            stringResource(R.string.licenses_intro),
                            style = ClockblockTheme.textStyles.editorialBody,
                            color = colors.onSurface,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                        )
                    }
                },
                // A tap opens the licence text right here, offline, rather than a web page.
                detailMode = LibraryDetailMode.Dialog,
                licenseDialogConfirmText = stringResource(R.string.licenses_close),
                modifier = Modifier.widthIn(max = 720.dp).fillMaxWidth().testTag(AboutTags.LicensesList),
            )
        }
    }
}
