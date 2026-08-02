package org.pictokeyboard.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import org.pictokeyboard.R
import org.pictokeyboard.ui.theme.PictoKeyboardTheme
import org.pictokeyboard.ui.theme.ScreenPreviews
import org.pictokeyboard.ui.theme.Spacing

/**
 * Discover, before it has anything to show.
 *
 * The destination exists from #32 because the boards list's empty state points
 * at it — a caregiver with no boards has to be sent somewhere, and "build one
 * from an empty grid" is the hardest moment in every AAC product. #37 fills
 * this with the bundled catalogue: boards and categories, name search and tag
 * filters, working offline with no account.
 *
 * A placeholder that says what will be here beats a tab that silently shows
 * nothing, which reads as a bug.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscoverScreen() {
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.discover_title)) },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = Spacing.xl, vertical = Spacing.xxl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Text(
                stringResource(R.string.discover_coming_soon),
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                stringResource(R.string.discover_coming_soon_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@ScreenPreviews
@Composable
private fun DiscoverPreview() {
    PictoKeyboardTheme { DiscoverScreen() }
}
