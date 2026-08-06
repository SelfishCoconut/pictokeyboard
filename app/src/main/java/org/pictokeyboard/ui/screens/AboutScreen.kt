package org.pictokeyboard.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.pictokeyboard.R
import org.pictokeyboard.ui.theme.PictoKeyboardTheme
import org.pictokeyboard.ui.theme.ScreenPreviews

/** Stateful wrapper: turns a URL into a browser intent. */
@Composable
fun AboutScreen(onBack: (() -> Unit)? = null) {
    val context = LocalContext.current
    AboutScreenContent(
        onBack = onBack,
        onOpenUrl = { url -> context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) },
    )
}

/**
 * The ARASAAC credit and licence links live here. They are a CC BY-NC-SA
 * obligation rather than a courtesy, so this screen always shows them.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreenContent(onBack: (() -> Unit)?, onOpenUrl: (String) -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.about_title)) },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.back),
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(stringResource(R.string.about_app), style = MaterialTheme.typography.bodyMedium)

            // Settings -> About -> Help. Both cards used to sit permanently on
            // the dashboard, above the board the caregiver came to look at.
            // They are worth reading once, which makes them reference material
            // rather than a home screen (#32).
            Text(stringResource(R.string.about_help_heading), style = MaterialTheme.typography.titleMedium)
            TipsCard()
            BlindControlsCard()

            AttributionCard()
            LicenceCard(onOpenUrl = onOpenUrl)
            PrivacyCard(onOpenUrl = onOpenUrl)
        }
    }
}

/** Who made the pictograms. Required by the licence, not a courtesy. */
@Composable
private fun AttributionCard() {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                stringResource(R.string.about_pictos_heading),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                stringResource(R.string.about_arasaac_attribution),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

/**
 * The licence terms, and the three places to read them in full.
 *
 * CC BY-NC-SA is non-commercial, which is a constraint on what this app may
 * ever become rather than a footnote — so it is stated in the app and not only
 * in the repository.
 */
@Composable
private fun LicenceCard(onOpenUrl: (String) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                stringResource(R.string.about_license_heading),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                stringResource(R.string.about_license_noncommercial),
                style = MaterialTheme.typography.bodySmall,
            )
            OutlinedButton(onClick = {
                onOpenUrl("https://creativecommons.org/licenses/by-nc-sa/4.0/deed.en")
            }) {
                Text(stringResource(R.string.about_license_link))
            }
            OutlinedButton(onClick = { onOpenUrl("https://arasaac.org/terms-of-use") }) {
                Text(stringResource(R.string.about_terms_link))
            }
            OutlinedButton(onClick = { onOpenUrl("https://arasaac.org") }) {
                Text(stringResource(R.string.about_website_link))
            }
        }
    }
}

/**
 * The privacy summary, in the app as well as in the store listing.
 *
 * A caregiver deciding whether to trust a keyboard with what someone says is
 * doing it here, on the phone — not in a Play listing they scrolled past weeks
 * ago. The summary sits above the link so that the question is answered without
 * having to leave the app to find out.
 */
@Composable
private fun PrivacyCard(onOpenUrl: (String) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                stringResource(R.string.about_privacy_heading),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                stringResource(R.string.about_privacy_summary),
                style = MaterialTheme.typography.bodySmall,
            )
            OutlinedButton(onClick = { onOpenUrl(PRIVACY_POLICY_URL) }) {
                Text(stringResource(R.string.about_privacy_link))
            }
        }
    }
}

/**
 * Where the privacy policy lives.
 *
 * Play requires this to be a public, stable URL, and "stable" is the operative
 * word: it is printed in the store listing and inside the app, so it outlives
 * any particular release. Published from `site/` by `.github/workflows/pages.yml`.
 */
const val PRIVACY_POLICY_URL = "https://selfishcoconut.github.io/pictokeyboard/privacy/"

@ScreenPreviews
@Composable
private fun AboutScreenPreview() {
    PictoKeyboardTheme {
        AboutScreenContent(onBack = {}, onOpenUrl = {})
    }
}
