package org.pictokeyboard.ui.screens

import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.pictokeyboard.R
import org.pictokeyboard.ui.ConfigViewModel
import org.pictokeyboard.ui.theme.PictoKeyboardTheme

/** Whether the PictoKeyboard IME is currently enabled / selected as active. */
data class KeyboardStatus(val enabled: Boolean, val selected: Boolean) {
    val ready: Boolean get() = enabled && selected
}

private fun readKeyboardStatus(context: Context): KeyboardStatus {
    val imeId = ComponentName(
        context.packageName,
        "org.pictokeyboard.ime.PictoKeyboardService",
    ).flattenToShortString()
    val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    val enabled = imm.enabledInputMethodList.any { it.id == imeId }
    val selected = Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.DEFAULT_INPUT_METHOD,
    ) == imeId
    return KeyboardStatus(enabled, selected)
}

/** Re-reads the keyboard status every time the screen resumes (e.g. back from system settings). */
@Composable
private fun rememberKeyboardStatus(): KeyboardStatus {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    var status by remember { mutableStateOf(readKeyboardStatus(context)) }
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) status = readKeyboardStatus(context)
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    return status
}

/**
 * Stateful wrapper: counts come from the view model, and the keyboard status
 * from the system, which is the part a `@Preview` cannot answer.
 */
@Composable
fun DashboardScreen(
    viewModel: ConfigViewModel,
    onEnableKeyboard: () -> Unit,
    onSelectKeyboard: () -> Unit,
    onOpenBoard: () -> Unit,
) {
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val pictoCount by viewModel.pictoCount.collectAsStateWithLifecycle()

    DashboardScreenContent(
        categoryCount = categories.size,
        pictoCount = pictoCount,
        status = rememberKeyboardStatus(),
        onEnableKeyboard = onEnableKeyboard,
        onSelectKeyboard = onSelectKeyboard,
        onOpenBoard = onOpenBoard,
    )
}

/** Stateless dashboard: the caregiver's landing screen. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreenContent(
    categoryCount: Int,
    pictoCount: Int,
    status: KeyboardStatus,
    onEnableKeyboard: () -> Unit,
    onSelectKeyboard: () -> Unit,
    onOpenBoard: () -> Unit,
) {
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.app_name), fontWeight = FontWeight.SemiBold) },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            WelcomeHero()

            SetupStatusCard(
                status = status,
                onEnable = onEnableKeyboard,
                onSelect = onSelectKeyboard,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard(
                    value = categoryCount.toString(),
                    label = stringResource(R.string.dashboard_stat_categories),
                    modifier = Modifier.weight(1f),
                )
                StatCard(
                    value = pictoCount.toString(),
                    label = stringResource(R.string.dashboard_stat_pictos),
                    modifier = Modifier.weight(1f),
                )
            }

            BuildBoardCard(onClick = onOpenBoard)

            BlindControlsCard()

            TipsCard()

            Spacer(Modifier.height(4.dp))
        }
    }
}

@Preview(name = "Dashboard · ready", showBackground = true)
@Composable
private fun DashboardReadyPreview() {
    PictoKeyboardTheme {
        DashboardScreenContent(
            categoryCount = 8,
            pictoCount = 108,
            status = KeyboardStatus(enabled = true, selected = true),
            onEnableKeyboard = {},
            onSelectKeyboard = {},
            onOpenBoard = {},
        )
    }
}

@Preview(name = "Dashboard · setup needed", showBackground = true)
@Composable
private fun DashboardSetupPreview() {
    PictoKeyboardTheme {
        DashboardScreenContent(
            categoryCount = 0,
            pictoCount = 0,
            status = KeyboardStatus(enabled = false, selected = false),
            onEnableKeyboard = {},
            onSelectKeyboard = {},
            onOpenBoard = {},
        )
    }
}
