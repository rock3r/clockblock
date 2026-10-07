package dev.sebastiano.clockblocker.opus.feature.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.sebastiano.clockblocker.opus.core.designsystem.theme.ClockblockTheme
import dev.sebastiano.clockblocker.opus.feature.onboarding.profile.segmentedShape

/** One attribution: what it is, who made it, its licence, and where it lives. */
private data class Credit(@StringRes val name: Int, @StringRes val detail: Int, @StringRes val license: Int, val url: String)

private val Sections: List<Pair<Int, List<Credit>>> = listOf(
    R.string.licenses_section_app to listOf(
        Credit(R.string.licenses_app_name, R.string.licenses_app_detail, R.string.license_apache2, SourceUrl),
    ),
    R.string.licenses_section_fonts to listOf(
        Credit(R.string.licenses_google_sans_flex, R.string.licenses_google_sans_flex_detail, R.string.license_ofl, "https://fonts.google.com/specimen/Google+Sans+Flex"),
        Credit(R.string.licenses_fraunces, R.string.licenses_fraunces_detail, R.string.license_ofl, "https://github.com/undercasetype/Fraunces"),
    ),
    R.string.licenses_section_icons to listOf(
        Credit(R.string.licenses_material_symbols, R.string.licenses_material_symbols_detail, R.string.license_apache2, "https://github.com/google/material-design-icons"),
    ),
    R.string.licenses_section_data to listOf(
        Credit(R.string.licenses_ourairports, R.string.licenses_ourairports_detail, R.string.license_public_domain, "https://ourairports.com/data/"),
        Credit(R.string.licenses_mwgg, R.string.licenses_mwgg_detail, R.string.license_mit, "https://github.com/mwgg/Airports"),
        Credit(R.string.licenses_tzdb, R.string.licenses_tzdb_detail, R.string.license_public_domain, "https://www.iana.org/time-zones"),
    ),
    R.string.licenses_section_libraries to listOf(
        Credit(R.string.licenses_androidx, R.string.licenses_androidx_detail, R.string.license_apache2, "https://developer.android.com/jetpack/androidx"),
        Credit(R.string.licenses_kotlin, R.string.licenses_kotlin_detail, R.string.license_apache2, "https://kotlinlang.org"),
        Credit(R.string.licenses_metro, R.string.licenses_metro_detail, R.string.license_apache2, "https://github.com/ZacSweers/metro"),
    ),
)

/** The web address shown on a credit card: no scheme, no `www.`, no trailing slash. */
internal fun displayUrl(url: String): String = url.substringAfter("://").removePrefix("www.").removeSuffix("/")

/** Open-source licences, font and data attributions; each card opens its project's page. */
@Composable
fun LicensesScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val uriHandler = LocalUriHandler.current
    LicensesContent(onBack = onBack, modifier = modifier, onOpenUrl = { url -> runCatching { uriHandler.openUri(url) } })
}

/**
 * Stateless licences list: credits grouped by kind, typeset like About (serif intro, segmented cards). Each card
 * is one target that opens [Credit.url] through [onOpenUrl]; the address is printed on the card, so the
 * destination is never a surprise.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LicensesContent(onBack: () -> Unit, modifier: Modifier = Modifier, onOpenUrl: (String) -> Unit = {}) {
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
                    style = ClockblockTheme.textStyles.editorialBody,
                    color = colors.onSurface,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                )
                Sections.forEach { (title, credits) ->
                    SectionHeader(stringResource(title))
                    SettingsGroup {
                        credits.forEachIndexed { index, credit ->
                            CreditRow(credit, segmentedShape(index, credits.size), onOpen = { onOpenUrl(credit.url) })
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
private fun CreditRow(credit: Credit, shape: Shape, onOpen: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val name = stringResource(credit.name)
    Surface(shape = shape, color = colors.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .clip(shape)
                .clickable(onClickLabel = stringResource(R.string.licenses_open, name), onClick = onOpen)
                .padding(horizontal = 20.dp, vertical = 16.dp)
                .testTag(AboutTags.credit(credit.url)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.titleMedium, color = colors.onSurface)
                Text(stringResource(credit.detail), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                Spacer(Modifier.size(10.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    itemVerticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(shape = CircleShape, color = colors.secondaryContainer) {
                        Text(
                            stringResource(credit.license),
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        )
                    }
                    Text(displayUrl(credit.url), style = MaterialTheme.typography.labelMedium, color = colors.primary)
                }
            }
            Spacer(Modifier.width(16.dp))
            Icon(painterResource(R.drawable.settings_ic_open_in_new), contentDescription = null, tint = colors.onSurfaceVariant)
        }
    }
}
