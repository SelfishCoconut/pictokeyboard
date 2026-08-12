package org.pictokeyboard.ui.screens

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import org.pictokeyboard.R
import org.pictokeyboard.data.prefs.Settings
import org.pictokeyboard.ui.ConfigViewModel
import org.pictokeyboard.ui.theme.PictoKeyboardTheme
import org.pictokeyboard.ui.theme.ScreenPreviews
import org.pictokeyboard.ui.theme.Spacing

/**
 * Stateful wrapper: owns the view model, the file pickers and the toasts, so
 * that [SettingsScreenContent] can stay free of anything a `@Preview` cannot
 * supply -- an activity result registry, in particular.
 */
@Composable
fun SettingsScreen(
    viewModel: ConfigViewModel,
    onOpenAbout: () -> Unit,
    onBack: (() -> Unit)? = null,
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var message by remember { mutableStateOf<BackupMessage?>(null) }

    val backup = rememberBackupActions(viewModel) { message = it }
    val sentenceModel by viewModel.sentenceModel.collectAsStateWithLifecycle()

    // Whether the keyboard's bell dials or only opens the dialler (#144). Read
    // into state rather than on every recomposition so that granting it from the
    // dialog below updates the screen the caregiver is looking at.
    var canDialDirectly by remember { mutableStateOf(context.canPlaceCalls()) }
    val callPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { canDialDirectly = it }

    SettingsScreenContent(
        settings = settings,
        canDialDirectly = canDialDirectly,
        onAssistanceContact = viewModel::setAssistanceContact,
        onRequestCallPermission = { callPermission.launch(Manifest.permission.CALL_PHONE) },
        sentenceModel = sentenceModel,
        onSentenceHelp = viewModel::setSentenceHelp,
        onDownloadModel = viewModel::downloadModel,
        onCancelModelDownload = viewModel::cancelModelDownload,
        onDeleteModel = viewModel::deleteModel,
        onBenchmarkModel = viewModel::benchmarkModel,
        onBack = onBack,
        onOpenAbout = onOpenAbout,
        onLanguage = viewModel::setLanguage,
        onAddSpace = viewModel::setAddSpace,
        onHaptics = viewModel::setHaptics,
        onHighContrast = viewModel::setHighContrast,
        onSpeak = viewModel::setSpeak,
        onTtsRate = viewModel::setTtsRate,
        onTtsPitch = viewModel::setTtsPitch,
        onBlindMode = viewModel::setBlindMode,
        onSetPin = { pin, onDone -> viewModel.setPin(pin, onDone) },
        onRemovePin = viewModel::removePin,
        backupMessage = message,
        onExport = backup.save,
        onImport = backup.restore,
    )
}

/**
 * The two groups that reach outside this app: a model that has to be downloaded,
 * and a bell that places a call.
 *
 * Together because that is what they have in common, and apart from the rest
 * because the rest are switches with no consequences beyond the keyboard.
 */
@Composable
private fun SentenceAndAssistanceGroups(
    settings: Settings,
    sentenceModel: SentenceModelState,
    canDialDirectly: Boolean,
    onSentenceHelp: (Boolean) -> Unit,
    onDownloadModel: () -> Unit,
    onCancelModelDownload: () -> Unit,
    onDeleteModel: () -> Unit,
    onBenchmarkModel: () -> Unit,
    onAssistanceContact: (String, String) -> Unit,
    onRequestCallPermission: () -> Unit,
) {
    SettingsGroup(stringResource(R.string.settings_group_sentence)) {
        SentenceHelpSection(
            enabled = settings.sentenceHelp,
            model = sentenceModel,
            speed = settings.sentenceSpeed,
            onEnabled = onSentenceHelp,
            onDownload = onDownloadModel,
            onCancel = onCancelModelDownload,
            onDelete = onDeleteModel,
            onBenchmark = onBenchmarkModel,
        )
    }
    SettingsGroup(stringResource(R.string.settings_group_assistance)) {
        AssistanceSection(
            name = settings.assistanceName,
            number = settings.assistanceNumber,
            canDialDirectly = canDialDirectly,
            onContact = onAssistanceContact,
            onRequestPermission = onRequestCallPermission,
        )
    }
}

/** Stateless settings screen. Everything it needs arrives as a value or a callback. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreenContent(
    settings: Settings,
    onBack: (() -> Unit)?,
    canDialDirectly: Boolean = false,
    onAssistanceContact: (String, String) -> Unit = { _, _ -> },
    onRequestCallPermission: () -> Unit = {},
    sentenceModel: SentenceModelState = SentenceModelState(),
    onSentenceHelp: (Boolean) -> Unit = {},
    onDownloadModel: () -> Unit = {},
    onCancelModelDownload: () -> Unit = {},
    onDeleteModel: () -> Unit = {},
    onBenchmarkModel: () -> Unit = {},
    onOpenAbout: () -> Unit,
    onLanguage: (String) -> Unit,
    onAddSpace: (Boolean) -> Unit,
    onHaptics: (Boolean) -> Unit,
    onHighContrast: (Boolean) -> Unit,
    onSpeak: (Boolean) -> Unit,
    onTtsRate: (Float) -> Unit,
    onTtsPitch: (Float) -> Unit,
    onBlindMode: (Boolean) -> Unit,
    onSetPin: (String, () -> Unit) -> Unit,
    onRemovePin: () -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
    backupMessage: BackupMessage? = null,
) {
    var showPinDialog by remember { mutableStateOf(false) }

    Scaffold(topBar = { SettingsTopBar(onBack) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            SettingsGroups(
                settings = settings,
                onLanguage = onLanguage,
                onAddSpace = onAddSpace,
                onHaptics = onHaptics,
                onHighContrast = onHighContrast,
                onSpeak = onSpeak,
                onTtsRate = onTtsRate,
                onTtsPitch = onTtsPitch,
                onBlindMode = onBlindMode,
                onOpenAbout = onOpenAbout,
            )
            SentenceAndAssistanceGroups(
                settings = settings,
                sentenceModel = sentenceModel,
                canDialDirectly = canDialDirectly,
                onSentenceHelp = onSentenceHelp,
                onDownloadModel = onDownloadModel,
                onCancelModelDownload = onCancelModelDownload,
                onDeleteModel = onDeleteModel,
                onBenchmarkModel = onBenchmarkModel,
                onAssistanceContact = onAssistanceContact,
                onRequestCallPermission = onRequestCallPermission,
            )
            SettingsGroup(stringResource(R.string.settings_group_security)) {
                PinSection(
                    hasPin = settings.hasPin,
                    onSetPinClick = { showPinDialog = true },
                    onRemovePin = onRemovePin,
                )
            }
            SettingsGroup(stringResource(R.string.settings_group_backup)) {
                BackupSection(onExport, onImport, backupMessage)
            }
        }
    }

    if (showPinDialog) {
        SetPinDialog(
            onDismiss = { showPinDialog = false },
            onSet = { pin -> onSetPin(pin) { showPinDialog = false } },
        )
    }
}

@ScreenPreviews
@Composable
private fun SettingsScreenPreview() {
    PictoKeyboardTheme {
        SettingsScreenContent(
            settings = Settings(),
            onBack = {},
            onOpenAbout = {},
            onLanguage = {},
            onAddSpace = {},
            onHaptics = {},
            onHighContrast = {},
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

@ScreenPreviews
@Composable
private fun SettingsScreenWithPinPreview() {
    PictoKeyboardTheme {
        SettingsScreenContent(
            settings = Settings(hasPin = true, blindMode = true, defaultLanguage = "en"),
            onBack = {},
            onOpenAbout = {},
            onLanguage = {},
            onAddSpace = {},
            onHaptics = {},
            onHighContrast = {},
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

/**
 * The settings groups themselves, split out of the screen so that file stays a
 * scaffold and a scroll container.
 */
@Composable
private fun ColumnScope.SettingsGroups(
    settings: Settings,
    onLanguage: (String) -> Unit,
    onAddSpace: (Boolean) -> Unit,
    onHaptics: (Boolean) -> Unit,
    onHighContrast: (Boolean) -> Unit,
    onSpeak: (Boolean) -> Unit,
    onTtsRate: (Float) -> Unit,
    onTtsPitch: (Float) -> Unit,
    onBlindMode: (Boolean) -> Unit,
    onOpenAbout: () -> Unit,
) {
    SettingsGroup(stringResource(R.string.settings_group_language)) {
        LanguageSection(settings.defaultLanguage, onLanguage)
    }
    SettingsGroup(stringResource(R.string.settings_group_keyboard)) {
        KeyboardSection(settings, onAddSpace, onHaptics, onHighContrast)
    }
    SettingsGroup(stringResource(R.string.settings_group_voice)) {
        SpeechSection(settings, onSpeak, onTtsRate, onTtsPitch)
    }
    SettingsGroup(stringResource(R.string.settings_group_accessibility)) {
        BlindModeSection(settings.blindMode, onBlindMode)
    }
    // About is a row, not a destination in the navigation bar. It was a
    // quarter of the bottom bar for a screen read once (#32).
    SettingsGroup(stringResource(R.string.settings_group_about)) {
        NavigationRow(stringResource(R.string.settings_about_row), onOpenAbout)
    }
}

/** Title, plus a back arrow only when this screen was pushed rather than tabbed to. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsTopBar(onBack: (() -> Unit)?) {
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
}
