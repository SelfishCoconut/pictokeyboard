package org.pictokeyboard.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.pictokeyboard.R
import org.pictokeyboard.ui.theme.PictoKeyboardTheme
import org.pictokeyboard.ui.theme.Spacing

// The dashboard's cards. Each is independent of the others and of the view
// model, so each can be previewed and restyled on its own.

@Composable
internal fun SetupStatusCard(status: KeyboardStatus, onEnable: () -> Unit, onSelect: () -> Unit) {
    if (status.ready) {
        SetupReadyRow()
    } else {
        SetupStepsCard(status = status, onEnable = onEnable, onSelect = onSelect)
    }
}

/**
 * Shown once the keyboard is both enabled and selected. The checklist is genuinely
 * useful while there is something to do, and dead weight afterwards — so when
 * there is nothing left to do it collapses from a card to a single quiet line,
 * still readable as confirmation but no longer competing with the board above it.
 */
@Composable
private fun SetupReadyRow() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Icon(
            Icons.Filled.CheckCircle,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp),
        )
        Text(
            stringResource(R.string.dashboard_setup_ready_title),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** The two-step walkthrough shown until the keyboard is live. */
@Composable
private fun SetupStepsCard(status: KeyboardStatus, onEnable: () -> Unit, onSelect: () -> Unit) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    stringResource(R.string.dashboard_setup_title),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    stringResource(R.string.dashboard_setup_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            SetupStep(
                index = 1,
                done = status.enabled,
                title = stringResource(R.string.onboarding_step_enable),
                doneTitle = stringResource(R.string.dashboard_step_enabled_done),
                action = stringResource(R.string.onboarding_enable_action),
                onAction = onEnable,
            )
            SetupStep(
                index = 2,
                done = status.selected,
                title = stringResource(R.string.onboarding_step_select),
                doneTitle = stringResource(R.string.dashboard_step_selected_done),
                action = stringResource(R.string.onboarding_select_action),
                onAction = onSelect,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SetupStep(
    index: Int,
    done: Boolean,
    title: String,
    doneTitle: String,
    action: String,
    onAction: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        val badgeColor = if (done) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
        val badgeContent = if (done) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(badgeColor, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (done) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = null,
                    tint = badgeContent,
                    modifier = Modifier.size(20.dp),
                )
            } else {
                Text("$index", style = MaterialTheme.typography.titleMedium, color = badgeContent)
            }
        }
        // The label and its button sit side by side while both fit, and the
        // button drops beneath the label when they do not.
        //
        // A plain Row could not do this. The button was unweighted, so it
        // measured at its full intrinsic width and the weighted label took
        // whatever was left over — and "Abrir ajustes de entrada" leaves less
        // than the width of the word "PictoKeyboard". Android's last resort for
        // a word wider than its line is to break it mid-word, so the product's
        // own name rendered as "PictoKeyb / oard" on the first screen a new
        // caregiver sees, in the card telling them how to turn the keyboard on
        // (#70). Moving the button is the right trade: the name is not
        // negotiable, and a button on its own line costs nothing.
        FlowRow(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Text(
                if (done) doneTitle else title,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.align(Alignment.CenterVertically),
            )
            if (!done) {
                FilledTonalButton(
                    onClick = onAction,
                    modifier = Modifier.align(Alignment.CenterVertically),
                ) { Text(action) }
            }
        }
    }
}

/**
 * The Spanish setup card on a narrow screen at the largest supported font scale
 * — the case that broke in #70, pinned so it cannot break again unnoticed.
 *
 * The locale is not incidental. "Abrir ajustes de entrada" is half again the
 * width of "Open input settings", so Spanish is where the label column runs out
 * of room first, and 360dp is the narrowest screen the app supports. Checking
 * English at default scale — which is what a casual look at a preview grid
 * gets you — shows nothing wrong at all.
 */
@Preview(name = "setup · es · 360dp · large text", locale = "es", fontScale = 2f, widthDp = 360, showBackground = true)
@Composable
private fun SetupStepsNarrowSpanishPreview() {
    PictoKeyboardTheme {
        SetupStatusCard(
            status = KeyboardStatus(enabled = false, selected = false),
            onEnable = {},
            onSelect = {},
        )
    }
}

// The two big-number stat cards that used to sit here are gone. "8" and "108" in
// 34sp are the template answer to a dashboard: they filled space, restated what
// the board itself shows, and neither number is one the caregiver acts on. The
// counts now live in the hero's caption, beside the board they describe.

@Composable
internal fun BuildBoardCard(onClick: () -> Unit) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(MaterialTheme.colorScheme.tertiaryContainer, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.TouchApp,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.dashboard_build), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.dashboard_build_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
