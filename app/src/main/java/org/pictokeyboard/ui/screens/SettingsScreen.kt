package org.pictokeyboard.ui.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import org.pictokeyboard.R
import org.pictokeyboard.data.prefs.Settings
import org.pictokeyboard.ui.ConfigViewModel
import org.pictokeyboard.ui.theme.PictoKeyboardTheme

/**
 * Stateful wrapper: owns the view model, the file pickers and the toasts, so
 * that [SettingsScreenContent] can stay free of anything a `@Preview` cannot
 * supply -- an activity result registry, in particular.
 */
@Composable
fun SettingsScreen(viewModel: ConfigViewModel, onBack: (() -> Unit)? = null) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
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

    SettingsScreenContent(
        settings = settings,
        onBack = onBack,
        onLanguage = viewModel::setLanguage,
        onColumns = viewModel::setColumns,
        onRows = viewModel::setRows,
        onShowLabels = viewModel::setShowLabels,
        onAddSpace = viewModel::setAddSpace,
        onSpeak = viewModel::setSpeak,
        onTtsRate = viewModel::setTtsRate,
        onTtsPitch = viewModel::setTtsPitch,
        onBlindMode = viewModel::setBlindMode,
        onSetPin = { pin, onDone -> viewModel.setPin(pin, onDone) },
        onRemovePin = viewModel::removePin,
        onExport = {
            scope.launch {
                pendingExportJson = viewModel.exportJson()
                exportLauncher.launch("pictokeyboard-board.json")
            }
        },
        onImport = { importLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) },
    )
}

/** Stateless settings screen. Everything it needs arrives as a value or a callback. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreenContent(
    settings: Settings,
    onBack: (() -> Unit)?,
    onLanguage: (String) -> Unit,
    onColumns: (Int) -> Unit,
    onRows: (Int) -> Unit,
    onShowLabels: (Boolean) -> Unit,
    onAddSpace: (Boolean) -> Unit,
    onSpeak: (Boolean) -> Unit,
    onTtsRate: (Float) -> Unit,
    onTtsPitch: (Float) -> Unit,
    onBlindMode: (Boolean) -> Unit,
    onSetPin: (String, () -> Unit) -> Unit,
    onRemovePin: () -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
) {
    var showPinDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
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
            LanguageSection(settings.defaultLanguage, onLanguage)
            HorizontalDivider()
            GridSection(settings, onColumns, onRows, onShowLabels, onAddSpace)
            SpeechSection(settings, onSpeak, onTtsRate, onTtsPitch)
            HorizontalDivider()
            BlindModeSection(settings.blindMode, onBlindMode)
            HorizontalDivider()
            PinSection(settings.hasPin, onSetPinClick = { showPinDialog = true }, onRemovePin = onRemovePin)
            HorizontalDivider()
            BackupSection(onExport, onImport)
        }
    }

    if (showPinDialog) {
        SetPinDialog(
            onDismiss = { showPinDialog = false },
            onSet = { pin -> onSetPin(pin) { showPinDialog = false } },
        )
    }
}

@Preview(name = "Settings", showBackground = true)
@Composable
private fun SettingsScreenPreview() {
    PictoKeyboardTheme {
        SettingsScreenContent(
            settings = Settings(),
            onBack = {},
            onLanguage = {},
            onColumns = {},
            onRows = {},
            onShowLabels = {},
            onAddSpace = {},
            onSpeak = {},
            onTtsRate = {},
            onTtsPitch = {},
            onBlindMode = {},
            onSetPin = { _, done -> done() },
            onRemovePin = {},
            onExport = {},
            onImport = {},
        )
    }
}

@Preview(name = "Settings · PIN set", showBackground = true)
@Composable
private fun SettingsScreenWithPinPreview() {
    PictoKeyboardTheme {
        SettingsScreenContent(
            settings = Settings(hasPin = true, blindMode = true, defaultLanguage = "en"),
            onBack = {},
            onLanguage = {},
            onColumns = {},
            onRows = {},
            onShowLabels = {},
            onAddSpace = {},
            onSpeak = {},
            onTtsRate = {},
            onTtsPitch = {},
            onBlindMode = {},
            onSetPin = { _, done -> done() },
            onRemovePin = {},
            onExport = {},
            onImport = {},
        )
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
