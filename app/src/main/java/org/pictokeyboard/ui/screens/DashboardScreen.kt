package org.pictokeyboard.ui.screens

import android.content.ComponentName
import android.content.Context
import android.content.res.Configuration
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.flowOf
import org.pictokeyboard.R
import org.pictokeyboard.data.db.CategoryEntity
import org.pictokeyboard.data.db.PictoEntity
import org.pictokeyboard.ui.ConfigViewModel
import org.pictokeyboard.ui.theme.PictoKeyboardTheme
import org.pictokeyboard.ui.theme.Spacing

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
 * Stateful wrapper: the board contents come from the view model, and the keyboard
 * status from the system, which is the part a `@Preview` cannot answer.
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

    // The hero shows the first category's pictos, which is what the keyboard opens
    // on, so the preview matches what the user will actually see first.
    val firstCategoryId = categories.firstOrNull()?.id
    val heroPictos by remember(firstCategoryId) {
        firstCategoryId?.let(viewModel::pictos) ?: flowOf(emptyList())
    }.collectAsStateWithLifecycle(emptyList())

    DashboardScreenContent(
        categories = categories,
        heroPictos = heroPictos,
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
    categories: List<CategoryEntity>,
    heroPictos: List<PictoEntity>,
    pictoCount: Int,
    status: KeyboardStatus,
    onEnableKeyboard: () -> Unit,
    onSelectKeyboard: () -> Unit,
    onOpenBoard: () -> Unit,
) {
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
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
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            BoardMiniature(
                categories = categories,
                pictos = heroPictos,
                pictoCount = pictoCount,
                onClick = onOpenBoard,
            )

            SetupStatusCard(
                status = status,
                onEnable = onEnableKeyboard,
                onSelect = onSelectKeyboard,
            )

            BuildBoardCard(onClick = onOpenBoard)

            BlindControlsCard()

            TipsCard()

            Spacer(Modifier.height(Spacing.xs))
        }
    }
}

/** Enough pictos to fill the hero's 4x2 grid. */
private const val PREVIEW_PICTOS = 8

/** A board just big enough to fill the hero, for the previews. */
private fun sampleBoard(): Pair<List<CategoryEntity>, List<PictoEntity>> {
    val categories = listOf(
        CategoryEntity(id = "people", name = "Personas", colorArgb = 0xFFFFC107.toInt(), position = 0),
        CategoryEntity(id = "actions", name = "Acciones", colorArgb = 0xFF4CAF50.toInt(), position = 1),
        CategoryEntity(id = "food", name = "Comida", colorArgb = 0xFFFF9800.toInt(), position = 2),
    )
    val pictos = List(PREVIEW_PICTOS) { index ->
        PictoEntity(
            id = "p$index",
            categoryId = "people",
            label = "picto $index",
            spokenText = "picto $index",
            language = "es",
            position = index,
        )
    }
    return categories to pictos
}

@Preview(name = "Dashboard · ready", showBackground = true)
@Composable
private fun DashboardReadyPreview() {
    val (categories, pictos) = sampleBoard()
    PictoKeyboardTheme {
        DashboardScreenContent(
            categories = categories,
            heroPictos = pictos,
            pictoCount = 108,
            status = KeyboardStatus(enabled = true, selected = true),
            onEnableKeyboard = {},
            onSelectKeyboard = {},
            onOpenBoard = {},
        )
    }
}

@Preview(name = "Dashboard · dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun DashboardDarkPreview() {
    val (categories, pictos) = sampleBoard()
    PictoKeyboardTheme(darkTheme = true) {
        DashboardScreenContent(
            categories = categories,
            heroPictos = pictos,
            pictoCount = 108,
            status = KeyboardStatus(enabled = true, selected = true),
            onEnableKeyboard = {},
            onSelectKeyboard = {},
            onOpenBoard = {},
        )
    }
}

@Preview(name = "Dashboard · large text", showBackground = true, fontScale = 2f)
@Composable
private fun DashboardLargeTextPreview() {
    val (categories, pictos) = sampleBoard()
    PictoKeyboardTheme {
        DashboardScreenContent(
            categories = categories,
            heroPictos = pictos,
            pictoCount = 108,
            status = KeyboardStatus(enabled = true, selected = true),
            onEnableKeyboard = {},
            onSelectKeyboard = {},
            onOpenBoard = {},
        )
    }
}

/** Empty state: no board yet, and the keyboard not yet enabled. */
@Preview(name = "Dashboard · setup needed", showBackground = true)
@Composable
private fun DashboardSetupPreview() {
    PictoKeyboardTheme {
        DashboardScreenContent(
            categories = emptyList(),
            heroPictos = emptyList(),
            pictoCount = 0,
            status = KeyboardStatus(enabled = false, selected = false),
            onEnableKeyboard = {},
            onSelectKeyboard = {},
            onOpenBoard = {},
        )
    }
}
