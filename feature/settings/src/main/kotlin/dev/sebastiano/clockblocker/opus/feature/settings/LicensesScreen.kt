package dev.sebastiano.clockblocker.opus.feature.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.sebastiano.clockblocker.opus.feature.onboarding.profile.segmentedShape

/** One attribution: what it is, who made it, and its licence. */
private data class Credit(@StringRes val name: Int, @StringRes val detail: Int, @StringRes val license: Int)

private val Sections: List<Pair<Int, List<Credit>>> = listOf(
    R.string.licenses_section_app to listOf(
        Credit(R.string.licenses_app_name, R.string.licenses_app_detail, R.string.license_apache2),
    ),
    R.string.licenses_section_fonts to listOf(
        Credit(R.string.licenses_google_sans_flex, R.string.licenses_google_sans_flex_detail, R.string.license_ofl),
        Credit(R.string.licenses_fraunces, R.string.licenses_fraunces_detail, R.string.license_ofl),
    ),
    R.string.licenses_section_icons to listOf(
        Credit(R.string.licenses_material_symbols, R.string.licenses_material_symbols_detail, R.string.license_apache2),
    ),
    R.string.licenses_section_data to listOf(
        Credit(R.string.licenses_ourairports, R.string.licenses_ourairports_detail, R.string.license_public_domain),
        Credit(R.string.licenses_mwgg, R.string.licenses_mwgg_detail, R.string.license_mit),
        Credit(R.string.licenses_tzdb, R.string.licenses_tzdb_detail, R.string.license_public_domain),
    ),
    R.string.licenses_section_libraries to listOf(
        Credit(R.string.licenses_androidx, R.string.licenses_androidx_detail, R.string.license_apache2),
        Credit(R.string.licenses_kotlin, R.string.licenses_kotlin_detail, R.string.license_apache2),
        Credit(R.string.licenses_metro, R.string.licenses_metro_detail, R.string.license_apache2),
    ),
)

/** Open-source licences, font and data attributions. */
@Composable
fun LicensesScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    LicensesContent(onBack = onBack, modifier = modifier)
}

/** Stateless licences list: a static, typeset set of credits grouped by kind. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LicensesContent(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Scaffold(
        modifier = modifier,
        topBar = { TopAppBar(title = { Text(stringResource(R.string.licenses_title)) }, navigationIcon = { BackButton(onBack) }) },
        containerColor = colors.surface,
    ) { padding ->
        Box(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).testTag(AboutTags.LicensesList),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(Modifier.widthIn(max = 720.dp).fillMaxWidth().padding(horizontal = 16.dp)) {
                Text(
                    stringResource(R.string.licenses_intro),
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                )
                Sections.forEach { (title, credits) ->
                    SectionHeader(stringResource(title))
                    SettingsGroup {
                        credits.forEachIndexed { index, credit ->
                            CreditRow(credit, segmentedShape(index, credits.size))
                        }
                    }
                }
                Spacer(Modifier.size(32.dp))
                Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
            }
        }
    }
}

@Composable
private fun CreditRow(credit: Credit, shape: androidx.compose.ui.graphics.Shape) {
    val colors = MaterialTheme.colorScheme
    Surface(shape = shape, color = colors.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(credit.name), style = MaterialTheme.typography.titleMedium, color = colors.onSurface, modifier = Modifier.weight(1f, fill = false))
            }
            Text(stringResource(credit.detail), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
            Spacer(Modifier.size(10.dp))
            Surface(shape = CircleShape, color = colors.secondaryContainer) {
                Text(
                    stringResource(credit.license),
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.onSecondaryContainer,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
        }
    }
}
