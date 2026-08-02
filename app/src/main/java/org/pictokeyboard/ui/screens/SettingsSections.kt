package org.pictokeyboard.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import org.pictokeyboard.R
import org.pictokeyboard.data.prefs.Settings
import org.pictokeyboard.ui.theme.Spacing

// The bands of the settings screen, one composable each. They live beside
// SettingsScreen rather than inside it so that file stays a layout and this one
// holds the controls.

/**
 * One titled group of settings.
 *
 * The screen was a single flat wall of sliders and switches separated by
 * dividers, which made "change the speech rate" a scanning problem. Six named
 * cards turn it into a lookup: the caregiver reads the title, not the controls.
 */
@Composable
internal fun SettingsGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier.padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            content()
        }
    }
}

@Composable
internal fun LanguageSection(language: String, onLanguage: (String) -> Unit) {
    Text(
        stringResource(R.string.settings_language),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    LanguageChips(selected = language, onSelect = onLanguage)
}

/**
 * What is left of the keyboard group once the board's own layout leaves it.
 *
 * Columns, rows, captions and frame defaults describe the *situation* rather
 * than the person: a doctor board wants 3 huge tiles where a chat board wants 5
 * small ones, and one global value made every board compromise. #31 moved them
 * onto `BoardEntity` and #33 moved their controls onto the board's own detail
 * screen, next to a preview of the board they change.
 *
 * A space after each word is genuinely global — it is about how this person
 * writes, in every board — so it stays.
 */
@Composable
internal fun KeyboardSection(settings: Settings, onAddSpace: (Boolean) -> Unit) {
    SwitchRow(stringResource(R.string.settings_add_space), settings.addSpaceAfter, onAddSpace)
    Text(
        stringResource(R.string.settings_layout_moved),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
internal fun SpeechSection(
    settings: Settings,
    onSpeak: (Boolean) -> Unit,
    onTtsRate: (Float) -> Unit,
    onTtsPitch: (Float) -> Unit,
) {
    SwitchRow(stringResource(R.string.settings_speak), settings.speakOnTap, onSpeak)
    SliderRow(
        label = stringResource(R.string.settings_tts_rate),
        value = settings.ttsRate,
        range = 0.5f..2f,
        steps = 0,
        valueText = "%.1fx".format(settings.ttsRate),
        onChange = onTtsRate,
    )
    SliderRow(
        label = stringResource(R.string.settings_tts_pitch),
        value = settings.ttsPitch,
        range = 0.5f..2f,
        steps = 0,
        valueText = "%.1f".format(settings.ttsPitch),
        onChange = onTtsPitch,
    )
}

@Composable
internal fun BlindModeSection(blindMode: Boolean, onBlindMode: (Boolean) -> Unit) {
    SwitchRow(stringResource(R.string.settings_blind_mode), blindMode, onBlindMode)
    Text(
        stringResource(R.string.settings_blind_mode_desc),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
internal fun PinSection(hasPin: Boolean, onSetPinClick: () -> Unit, onRemovePin: () -> Unit) {
    Text(
        stringResource(R.string.settings_pin),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        OutlinedButton(onClick = onSetPinClick) {
            Text(stringResource(R.string.settings_pin_set))
        }
        if (hasPin) {
            OutlinedButton(onClick = onRemovePin) {
                Text(stringResource(R.string.settings_pin_remove))
            }
        }
    }
}

@Composable
internal fun BackupSection(onExport: () -> Unit, onImport: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Button(onClick = onExport) { Text(stringResource(R.string.settings_export)) }
        OutlinedButton(onClick = onImport) { Text(stringResource(R.string.settings_import)) }
    }
}

/**
 * A label and a switch, where **the whole row** is the control.
 *
 * Previously only the 52dp switch itself was tappable and the row carried no
 * role, so a screen reader announced the label and the switch as two unrelated
 * things and the obvious target — the words — did nothing. `toggleable` with
 * `Role.Switch` and merged semantics makes it one control that reads as "Speak
 * each picto aloud, switch, on", and gives the whole row a 48dp target.
 */
@Composable
internal fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Spacing.touchTarget)
            .toggleable(
                value = checked,
                onValueChange = onChange,
                role = Role.Switch,
            )
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, modifier = Modifier.weight(1f))
        // The row owns the gesture and the semantics; the switch is the picture of
        // the state, so it must not also be a separate target.
        Switch(checked = checked, onCheckedChange = null)
    }
}

/**
 * A labelled slider. [onChangeFinished] fires when the finger lifts, for values
 * whose home is the database: dragging then writes to local state at the frame
 * rate and to storage once.
 */
@Composable
internal fun SliderRow(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    valueText: String,
    onChange: (Float) -> Unit,
    onChangeFinished: (() -> Unit)? = null,
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, modifier = Modifier.weight(1f))
            Text(valueText, style = MaterialTheme.typography.labelLarge)
        }
        Slider(
            // The label is a sibling Text, so without this the screen reader
            // announces "slider, 50 percent" and never says of what.
            modifier = Modifier.semantics {
                contentDescription = label
                stateDescription = valueText
            },
            value = value,
            onValueChange = onChange,
            onValueChangeFinished = onChangeFinished,
            valueRange = range,
            steps = steps,
            // Material takes the inactive track from `secondaryContainer`, which in
            // this palette is `line` -- 1.34:1 on a card, 1.13:1 in dark. The track
            // shows how much range is left, so it is information, not decoration,
            // and it needs the 3:1 that `outline` gives it.
            colors = SliderDefaults.colors(
                inactiveTrackColor = MaterialTheme.colorScheme.outline,
                inactiveTickColor = MaterialTheme.colorScheme.surface,
            ),
        )
    }
}

/**
 * A settings row that goes somewhere, rather than changing a value.
 *
 * The whole row is the target and carries `Role.Button`, so TalkBack announces
 * one thing to activate instead of a label sitting next to an unrelated chevron.
 */
@Composable
internal fun NavigationRow(label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Spacing.touchTarget)
            .clickable(role = Role.Button, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
