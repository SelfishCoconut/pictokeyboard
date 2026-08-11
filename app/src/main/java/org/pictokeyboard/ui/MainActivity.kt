package org.pictokeyboard.ui

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import org.pictokeyboard.R
import org.pictokeyboard.data.prefs.Settings
import org.pictokeyboard.ui.screens.AboutScreen
import org.pictokeyboard.ui.screens.AddPictosScreen
import org.pictokeyboard.ui.screens.BoardDetailScreen
import org.pictokeyboard.ui.screens.BoardSharing
import org.pictokeyboard.ui.screens.BoardsScreen
import org.pictokeyboard.ui.screens.PKB_MIME
import org.pictokeyboard.ui.screens.PictosScreen
import org.pictokeyboard.ui.screens.SettingsScreen
import org.pictokeyboard.ui.screens.UnlockScreen
import org.pictokeyboard.ui.screens.openInputMethodSettings
import org.pictokeyboard.ui.screens.rememberKeyboardStatus
import org.pictokeyboard.ui.screens.showKeyboardPicker
import org.pictokeyboard.ui.theme.PictoKeyboardTheme

object Routes {
    const val BOARDS = "boards"
    const val BOARD = "board"
    const val PICTOS = "pictos"
    const val ADD_PICTOS = "addpictos"
    const val SETTINGS = "settings"
    const val ABOUT = "about"
    fun board(boardId: String) = "$BOARD/$boardId"
    fun pictos(categoryId: String) = "$PICTOS/$categoryId"
    fun addPictos(categoryId: String) = "$ADD_PICTOS/$categoryId"
}

/** A top-level destination shown in the bottom navigation bar. */
private data class NavItem(val route: String, val label: Int, val icon: ImageVector)

/**
 * Two destinations, and it took three cuts to get here.
 *
 * About was a quarter of the navigation bar for a screen read once, and is now
 * a Settings row. The old Home tab was a dashboard *about the app* -- setup
 * status, a build-your-board CTA, a tips card -- sitting above the caregiver's
 * actual content; Boards makes the content the home. (#32)
 *
 * Discover was a third of the bar reserved for a catalogue of published boards,
 * and with no server there is nothing to publish and nothing to browse (#119).
 * A caregiver gets a board from another caregiver as a file now, which is the
 * boards screen's own empty state and not a destination. What is left is the
 * caregiver's content and the settings for it. (#32, #119)
 */
private val NavItems = listOf(
    NavItem(Routes.BOARDS, R.string.nav_boards, Icons.Filled.GridView),
    NavItem(Routes.SETTINGS, R.string.nav_settings, Icons.Filled.Settings),
)

/**
 * An [AppCompatActivity], not a [ComponentActivity], solely so the per-app
 * language API works below API 33: appcompat backports it by hooking
 * `attachBaseContext`, which only its own Activity base class does. The theme
 * already descends from `Theme.AppCompat` via Material 3, so nothing else moves.
 */
class MainActivity : AppCompatActivity() {

    private val viewModel: ConfigViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        // Android 15 (targetSdk 35) draws every activity edge-to-edge and ignores
        // android:statusBarColor. Opting in explicitly means API 26-34 devices lay
        // out the same way as API 35+ ones, so there is only one set of insets
        // behaviour to reason about, and it hands the system the light/dark icon
        // appearance the bars need to stay legible against our surface colour.
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            // Collected here rather than inside ConfigApp: the theme needs
            // `highContrast` and sits above it, and collecting the same flow
            // twice would give the palette and the content separate snapshots
            // to disagree over for a frame.
            val settings by viewModel.settings.collectAsStateWithLifecycle()
            PictoKeyboardTheme(highContrast = settings.highContrast) {
                ConfigApp(viewModel, settings)
            }
        }
    }
}

@Composable
private fun ConfigApp(viewModel: ConfigViewModel, settings: Settings) {
    ApplyAppLocale(settings.defaultLanguage)
    AppNavigation(viewModel, settings)
}

/**
 * Hands the in-app **Default language** to the platform's per-app language API.
 *
 * This replaces `ProvideAppLocale`, which re-skinned `LocalContext` for the
 * composition. That worked for screen content and silently failed everywhere
 * else: every Compose sub-window re-provides `LocalContext` from the platform,
 * so dialogs, dropdown menus and bottom sheets discarded the override and
 * rendered in the *system* locale while the screen behind them rendered in the
 * app's. Capturing and re-providing the context at each call site does fix it,
 * but it has to be repeated in every `AlertDialog` *slot* — nine call sites and
 * about twenty wrappers of pure ceremony, and the next dialog someone adds
 * forgets it.
 *
 * `setApplicationLocales` applies to the whole process instead, so popups,
 * sheets, toasts and the IME follow with no per-site handling at all.
 *
 * Guarded on inequality because setting it **restarts the activity**: calling it
 * unconditionally on every recomposition is an infinite restart loop. On a cold
 * start appcompat has already restored the stored value in `attachBaseContext`,
 * so the guard is satisfied and nothing restarts; only a genuine change costs a
 * recreate, which is the documented behaviour of this API.
 */
@Composable
private fun ApplyAppLocale(language: String) {
    LaunchedEffect(language) {
        if (AppCompatDelegate.getApplicationLocales().toLanguageTags() != language) {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(language))
        }
    }
}

@Composable
private fun AppNavigation(viewModel: ConfigViewModel, settings: org.pictokeyboard.data.prefs.Settings) {
    val board by viewModel.activeBoard.collectAsStateWithLifecycle()
    val boardLanguage = board?.language ?: "es"
    var unlocked by remember { mutableStateOf(false) }

    if (settings.hasPin && !unlocked) {
        UnlockScreen(
            verify = viewModel::verifyPin,
            onUnlocked = { unlocked = true },
        )
        return
    }

    val context = LocalContext.current
    val nav = rememberNavController()

    // Opening a `.pkb` a caregiver was sent. The same importer the whole-device
    // backup uses, because a file holding one board and a file holding twelve
    // are the same format and land the same way: added, never over what is
    // already here.
    val boardImportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        viewModel.importEverything(
            source = { requireNotNull(context.contentResolver.openInputStream(uri)) },
        ) { result ->
            val text = if (result.isSuccess) R.string.boards_import_done else R.string.boards_import_failed
            Toast.makeText(context, text, Toast.LENGTH_LONG).show()
        }
    }
    // Read once here rather than per screen: the Boards tab and the board's own
    // Try it sheet both need it, and it is a system-settings read, not state.
    val keyboardStatus = rememberKeyboardStatus()
    val backStack by nav.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val showBottomBar = currentRoute in NavItems.map { it.route }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    NavItems.forEach { item ->
                        NavigationBarItem(
                            selected = currentRoute == item.route,
                            onClick = {
                                if (currentRoute != item.route) {
                                    nav.navigate(item.route) {
                                        popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            },
                            icon = { Icon(item.icon, contentDescription = null) },
                            label = { Text(stringResource(item.label)) },
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        val bottom = innerPadding.calculateBottomPadding()
        NavHost(
            navController = nav,
            startDestination = Routes.BOARDS,
            modifier = Modifier
                .padding(bottom = bottom)
                .consumeWindowInsets(PaddingValues(bottom = bottom)),
        ) {
            composable(Routes.BOARDS) {
                val summaries by viewModel.boardSummaries.collectAsStateWithLifecycle()
                BoardsScreen(
                    summaries = summaries,
                    status = keyboardStatus,
                    // Opening a board hands it the keyboard, so that everything
                    // done to it from here on -- Try it included -- is done to
                    // the board the communicator is actually looking at.
                    onOpenBoard = { summary ->
                        viewModel.useBoard(summary.board.id)
                        nav.navigate(Routes.board(summary.board.id))
                    },
                    onUseBoard = { board -> viewModel.useBoard(board.id) },
                    onDuplicateBoard = viewModel::duplicateBoard,
                    // Written to a cache file first and then handed to the
                    // share sheet, rather than saved through the file picker.
                    // The caregiver's next move after exporting a board is
                    // always to send it to somebody, and asking them to save it
                    // and then find it again is a step that exists only because
                    // of how the code was arranged. Settings keeps the picker
                    // for the whole-device backup, where the destination is the
                    // point.
                    onExportBoard = { board ->
                        val file = BoardSharing.fileFor(context, board.name)
                        viewModel.exportBoard(board.id, file.outputStream()) { result ->
                            result.fold(
                                onSuccess = { BoardSharing.share(context, file, board.name) },
                                onFailure = {
                                    Toast.makeText(context, R.string.boards_share_failed, Toast.LENGTH_LONG).show()
                                },
                            )
                        }
                    },
                    onDeleteBoard = { board ->
                        viewModel.deleteBoard(board) { deleted ->
                            if (!deleted) {
                                Toast.makeText(context, R.string.boards_delete_last, Toast.LENGTH_LONG).show()
                            }
                        }
                    },
                    onEnableKeyboard = { openInputMethodSettings(context) },
                    onSelectKeyboard = { showKeyboardPicker(context) },
                    onImportBoard = { boardImportLauncher.launch(arrayOf(PKB_MIME, "application/zip", "*/*")) },
                    // A board is made to be filled, so both routes end on the
                    // board itself rather than back on the list.
                    //
                    // `useBoard` is not a nicety here. Adding a category writes
                    // to whichever board is *active* -- see
                    // PictoRepository.activeBoardId -- so a new board opened
                    // without being handed the keyboard would quietly collect
                    // its first categories on the previous board. That is also
                    // why opening any board activates it.
                    //
                    // The cost is real and worth naming: starting from scratch
                    // makes the live keyboard empty until the first category
                    // lands. It is the caregiver's own deliberate tap, on the
                    // phone in their hand, and the keyboard says so rather than
                    // showing a blank grid -- see kb_empty_hint.
                    onCreateBoard = { name ->
                        viewModel.addBoard(name) { id ->
                            viewModel.useBoard(id)
                            nav.navigate(Routes.board(id))
                        }
                    },
                    onCopyBoard = { board, name ->
                        viewModel.copyBoard(board, name) { id ->
                            viewModel.useBoard(id)
                            nav.navigate(Routes.board(id))
                        }
                    },
                )
            }
            composable("${Routes.BOARD}/{boardId}") { entry ->
                BoardDetailScreen(
                    viewModel = viewModel,
                    boardId = entry.arguments?.getString("boardId").orEmpty(),
                    status = keyboardStatus,
                    onBack = { nav.popBackStack() },
                    onOpenCategory = { id -> nav.navigate(Routes.pictos(id)) },
                    onEnableKeyboard = { openInputMethodSettings(context) },
                    onSelectKeyboard = { showKeyboardPicker(context) },
                )
            }
            composable("${Routes.PICTOS}/{categoryId}") { entry ->
                val categoryId = entry.arguments?.getString("categoryId").orEmpty()
                PictosScreen(
                    viewModel = viewModel,
                    categoryId = categoryId,
                    onBack = { nav.popBackStack() },
                    onAddPictos = { nav.navigate(Routes.addPictos(categoryId)) },
                )
            }
            composable("${Routes.ADD_PICTOS}/{categoryId}") { entry ->
                val categoryId = entry.arguments?.getString("categoryId").orEmpty()
                AddPictosScreen(
                    viewModel = viewModel,
                    categoryId = categoryId,
                    // The board's vocabulary language, not the interface's.
                    defaultLanguage = boardLanguage,
                    onBack = { nav.popBackStack() },
                )
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(
                    viewModel = viewModel,
                    onOpenAbout = { nav.navigate(Routes.ABOUT) },
                    onBack = null,
                )
            }
            composable(Routes.ABOUT) {
                // Pushed from Settings now, so it gets a back arrow.
                AboutScreen(onBack = { nav.popBackStack() })
            }
        }
    }
}
