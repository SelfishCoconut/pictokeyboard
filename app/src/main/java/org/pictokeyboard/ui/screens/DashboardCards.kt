package org.pictokeyboard.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.unit.dp
import org.pictokeyboard.R
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
        Text(
            if (done) doneTitle else title,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        if (!done) {
            FilledTonalButton(onClick = onAction) { Text(action) }
        }
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
