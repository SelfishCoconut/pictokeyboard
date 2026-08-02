package org.pictokeyboard.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Accessibility
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.pictokeyboard.R

// Reference cards on the dashboard: the eyes-free gesture list and the usage
// tips. They teach rather than do, which is why they sit apart from the cards
// that carry actions.

/**
 * A card that holds reference material rather than a control.
 *
 * It stays on `paper` and draws its own outline instead of floating on `card`
 * with a shadow. That reads as a note rather than a thing to press, and it makes
 * the boundary explicit rather than implied by an elevation that the token layer
 * no longer tints. The outline is `line` — decorative, which is the correct
 * choice here precisely because the card itself is not interactive.
 */
@Composable
private fun ReferenceCard(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.background),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        content()
    }
}

@Composable
internal fun TipsCard() {
    ReferenceCard {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Filled.Lightbulb, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(stringResource(R.string.dashboard_tips_title), style = MaterialTheme.typography.titleMedium)
            }
            // The ⚙ tip is gone with the key it described (#36). A help card
            // naming a key that no longer exists sends the reader looking for it.
            TipRow(Icons.Filled.Language, stringResource(R.string.dashboard_tip_switch))
            TipRow(Icons.Filled.GridView, stringResource(R.string.dashboard_tip_boards))
        }
    }
}

@Composable
internal fun BlindControlsCard() {
    ReferenceCard {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Filled.Accessibility, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.blind_controls_title), style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(R.string.blind_controls_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            BlindControlRow(
                Icons.Filled.SwapVert,
                stringResource(R.string.blind_ctrl_category_action),
                stringResource(R.string.blind_ctrl_category_gesture),
            )
            BlindControlRow(
                Icons.Filled.SwapHoriz,
                stringResource(R.string.blind_ctrl_picto_action),
                stringResource(R.string.blind_ctrl_picto_gesture),
            )
            BlindControlRow(
                Icons.AutoMirrored.Filled.VolumeUp,
                stringResource(R.string.blind_ctrl_read_action),
                stringResource(R.string.blind_ctrl_read_gesture),
            )
            BlindControlRow(
                Icons.Filled.Edit,
                stringResource(R.string.blind_ctrl_write_action),
                stringResource(R.string.blind_ctrl_write_gesture),
            )
            BlindControlRow(
                Icons.AutoMirrored.Filled.Backspace,
                stringResource(R.string.blind_ctrl_delete_action),
                stringResource(R.string.blind_ctrl_delete_gesture),
            )
            BlindControlRow(
                Icons.Filled.TouchApp,
                stringResource(R.string.blind_ctrl_toggle_action),
                stringResource(R.string.blind_ctrl_toggle_gesture),
            )
        }
    }
}

@Composable
private fun BlindControlRow(icon: ImageVector, action: String, gesture: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(22.dp),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(action, style = MaterialTheme.typography.bodyLarge)
            Text(
                gesture,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun TipRow(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
