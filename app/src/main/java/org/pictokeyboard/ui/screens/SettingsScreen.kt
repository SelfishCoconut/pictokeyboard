package org.pictokeyboard.ui.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import org.pictokeyboard.R
import org.pictokeyboard.ui.ConfigViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: ConfigViewModel, onBack: (() -> Unit)? = null) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showPinDialog by remember { mutableStateOf(false) }
    var pendingExportJson by remember { mutableStateOf<String?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        val json = pendingExportJson
        if (uri != null && json != null) {
            context.contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) }
            Toast.makeText(context, R.string.settings_export_done, Toast.LENGTH_SHORT).show()
        }
        pendingExportJson = null
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            if (text != null) {
                viewModel.importJson(text) { ok ->
                    val msg = if (ok) R.string.settings_import_done else R.string.settings_import_failed
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
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
            // Language
            Text(stringResource(R.string.settings_language), style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(settings.defaultLanguage == "es", { viewModel.setLanguage("es") }, { Text("Español") })
                FilterChip(settings.defaultLanguage == "en", { viewModel.setLanguage("en") }, { Text("English") })
            }

            HorizontalDivider()

            // Grid
            SliderRow(
                label = stringResource(R.string.settings_grid_columns),
                value = settings.gridColumns.toFloat(),
                range = 2f..6f,
                steps = 3,
                valueText = settings.gridColumns.toString(),
                onChange = { viewModel.setColumns(it.toInt()) },
            )
            SliderRow(
                label = stringResource(R.string.settings_grid_rows),
                value = settings.gridRows.toFloat(),
                range = 2f..8f,
                steps = 5,
                valueText = settings.gridRows.toString(),
                onChange = { viewModel.setRows(it.toInt()) },
            )

            SwitchRow(stringResource(R.string.settings_show_labels), settings.showLabels, viewModel::setShowLabels)
            SwitchRow(stringResource(R.string.settings_add_space), settings.addSpaceAfter, viewModel::setAddSpace)
            SwitchRow(stringResource(R.string.settings_speak), settings.speakOnTap, viewModel::setSpeak)

            SliderRow(
                label = stringResource(R.string.settings_tts_rate),
                value = settings.ttsRate,
                range = 0.5f..2f,
                steps = 0,
                valueText = "%.1fx".format(settings.ttsRate),
                onChange = { viewModel.setTtsRate(it) },
            )
            SliderRow(
                label = stringResource(R.string.settings_tts_pitch),
                value = settings.ttsPitch,
                range = 0.5f..2f,
                steps = 0,
                valueText = "%.1f".format(settings.ttsPitch),
                onChange = { viewModel.setTtsPitch(it) },
            )

            HorizontalDivider()

            // Blind mode
            SwitchRow(stringResource(R.string.settings_blind_mode), settings.blindMode, viewModel::setBlindMode)
            Text(
                stringResource(R.string.settings_blind_mode_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            HorizontalDivider()

            // PIN
            Text(stringResource(R.string.settings_pin), style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { showPinDialog = true }) {
                    Text(stringResource(R.string.settings_pin_set))
                }
                if (settings.hasPin) {
                    OutlinedButton(onClick = { viewModel.removePin() }) {
                        Text(stringResource(R.string.settings_pin_remove))
                    }
                }
            }

            HorizontalDivider()

            // Backup
            Text(stringResource(R.string.settings_backup), style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    scope.launch {
                        pendingExportJson = viewModel.exportJson()
                        exportLauncher.launch("pictokeyboard-board.json")
                    }
                }) { Text(stringResource(R.string.settings_export)) }
                OutlinedButton(onClick = {
                    importLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
                }) { Text(stringResource(R.string.settings_import)) }
            }
        }
    }

    if (showPinDialog) {
        SetPinDialog(
            onDismiss = { showPinDialog = false },
            onSet = { pin -> viewModel.setPin(pin) { showPinDialog = false } },
        )
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun SliderRow(
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

@Composable
private fun SetPinDialog(onDismiss: () -> Unit, onSet: (String) -> Unit) {
    var pin by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    val tooShort = pin.isNotEmpty() && pin.length < 4
    val mismatch = confirm.isNotEmpty() && pin != confirm

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.pin_set_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.pin_set_desc), style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(
                    value = pin,
                    onValueChange = { pin = it.filter(Char::isDigit) },
                    label = { Text(stringResource(R.string.pin_field)) },
                    singleLine = true,
                    isError = tooShort,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                )
                OutlinedTextField(
                    value = confirm,
                    onValueChange = { confirm = it.filter(Char::isDigit) },
                    label = { Text(stringResource(R.string.pin_confirm_field)) },
                    singleLine = true,
                    isError = mismatch,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                )
                if (tooShort) Text(stringResource(R.string.pin_too_short), color = MaterialTheme.colorScheme.error)
                if (mismatch) Text(stringResource(R.string.pin_mismatch), color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSet(pin) },
                enabled = pin.length >= 4 && pin == confirm,
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}
