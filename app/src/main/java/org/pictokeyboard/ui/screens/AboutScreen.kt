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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.pictokeyboard.R
import org.pictokeyboard.ui.theme.PictoKeyboardTheme

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

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.about_pictos_heading), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.about_arasaac_attribution), style = MaterialTheme.typography.bodySmall)
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.about_license_heading), style = MaterialTheme.typography.titleMedium)
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
    }
}

@Preview(name = "About", showBackground = true)
@Composable
private fun AboutScreenPreview() {
    PictoKeyboardTheme {
        AboutScreenContent(onBack = {}, onOpenUrl = {})
    }
}
