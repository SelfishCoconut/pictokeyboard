package org.pictokeyboard.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.pictokeyboard.R
import org.pictokeyboard.data.prefs.Settings

// The bands of the settings screen, one composable each. They live beside
// SettingsScreen rather than inside it so that file stays a layout and this one
// holds the controls.

@Composable
internal fun LanguageSection(language: String, onLanguage: (String) -> Unit) {
    Text(stringResource(R.string.settings_language), style = MaterialTheme.typography.titleSmall)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(language == "es", { onLanguage("es") }, { Text("Español") })
        FilterChip(language == "en", { onLanguage("en") }, { Text("English") })
    }
}

@Composable
internal fun GridSection(
    settings: Settings,
    onColumns: (Int) -> Unit,
    onRows: (Int) -> Unit,
    onShowLabels: (Boolean) -> Unit,
    onAddSpace: (Boolean) -> Unit,
) {
    SliderRow(
        label = stringResource(R.string.settings_grid_columns),
        value = settings.gridColumns.toFloat(),
        range = 2f..6f,
        steps = 3,
        valueText = settings.gridColumns.toString(),
        onChange = { onColumns(it.toInt()) },
    )
    SliderRow(
        label = stringResource(R.string.settings_grid_rows),
        value = settings.gridRows.toFloat(),
        range = 2f..8f,
        steps = 5,
        valueText = settings.gridRows.toString(),
        onChange = { onRows(it.toInt()) },
    )
    SwitchRow(stringResource(R.string.settings_show_labels), settings.showLabels, onShowLabels)
    SwitchRow(stringResource(R.string.settings_add_space), settings.addSpaceAfter, onAddSpace)
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
    Text(stringResource(R.string.settings_pin), style = MaterialTheme.typography.titleSmall)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
    Text(stringResource(R.string.settings_backup), style = MaterialTheme.typography.titleSmall)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = onExport) { Text(stringResource(R.string.settings_export)) }
        OutlinedButton(onClick = onImport) { Text(stringResource(R.string.settings_import)) }
    }
}

@Composable
internal fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
internal fun SliderRow(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    valueText: String,
    onChange: (Float) -> Unit,
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, modifier = Modifier.weight(1f))
            Text(valueText, style = MaterialTheme.typography.labelLarge)
        }
        Slider(value = value, onValueChange = onChange, valueRange = range, steps = steps)
    }
}
