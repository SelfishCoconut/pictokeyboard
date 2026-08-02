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
import androidx.compose.material.icons.filled.Explore
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
import androidx.compose.runtime.rememberCoroutineScope
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
import kotlinx.coroutines.launch
import org.pictokeyboard.R
import org.pictokeyboard.ui.screens.AboutScreen
import org.pictokeyboard.ui.screens.AccountScreen
import org.pictokeyboard.ui.screens.AddPictosScreen
import org.pictokeyboard.ui.screens.BoardDetailScreen
import org.pictokeyboard.ui.screens.BoardsScreen
import org.pictokeyboard.ui.screens.DiscoverScreen
import org.pictokeyboard.ui.screens.PictosScreen
import org.pictokeyboard.ui.screens.SettingsScreen
import org.pictokeyboard.ui.screens.UnlockScreen
import org.pictokeyboard.ui.screens.openInputMethodSettings
import org.pictokeyboard.ui.screens.rememberKeyboardStatus
import org.pictokeyboard.ui.screens.showKeyboardPicker
import org.pictokeyboard.ui.theme.PictoKeyboardTheme

object Routes {
    const val BOARDS = "boards"
    const val DISCOVER = "discover"
    const val BOARD = "board"
    const val PICTOS = "pictos"
    const val ADD_PICTOS = "addpictos"
    const val SETTINGS = "settings"
    const val ABOUT = "about"
    const val ACCOUNT = "account"
    fun board(boardId: String) = "$BOARD/$boardId"
    fun pictos(categoryId: String) = "$PICTOS/$categoryId"
    fun addPictos(categoryId: String) = "$ADD_PICTOS/$categoryId"
}

/** A top-level destination shown in the bottom navigation bar. */
private data class NavItem(val route: String, val label: Int, val icon: ImageVector)

/**
 * Three destinations, not four.
 *
 * About was a quarter of the navigation bar for a screen read once, and is now
 * a Settings row. The old Home tab was a dashboard *about the app* -- setup
 * status, a build-your-board CTA, a tips card -- sitting above the caregiver's
 * actual content; Boards makes the content the home. Discover is empty until
 * #37, but the boards empty state has to be able to point somewhere. (#32)
 */
private val NavItems = listOf(
    NavItem(Routes.BOARDS, R.string.nav_boards, Icons.Filled.GridView),
    NavItem(Routes.DISCOVER, R.string.nav_discover, Icons.Filled.Explore),
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
            PictoKeyboardTheme {
                ConfigApp(viewModel)
            }
        }
    }
}

@Composable
private fun ConfigApp(viewModel: ConfigViewModel) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
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
    val scope = rememberCoroutineScope()
    val nav = rememberNavController()

    // Exporting a board writes the same JSON the settings screen has always
    // written, scoped to one board (#31). The launcher lives here because both
    // the Boards tab and Settings need it and neither owns the other.
    var pendingExportJson by remember { mutableStateOf<String?>(null) }
    val boardExportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        val json = pendingExportJson
        if (uri != null && json != null) {
            context.contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) }
            Toast.makeText(context, R.string.settings_export_done, Toast.LENGTH_SHORT).show()
        }
        pendingExportJson = null
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
                    onExportBoard = { board ->
                        scope.launch {
                            pendingExportJson = viewModel.exportJson(board.id)
                            boardExportLauncher.launch("pictokeyboard-\${board.name}.json")
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
                    onOpenDiscover = {
                        nav.navigate(Routes.DISCOVER) {
                            popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                )
            }
            composable(Routes.DISCOVER) {
                DiscoverScreen()
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
                    onOpenAccount = { nav.navigate(Routes.ACCOUNT) },
                    onBack = null,
                )
            }
            composable(Routes.ACCOUNT) {
                AccountScreen(onBack = { nav.popBackStack() })
            }
            composable(Routes.ABOUT) {
                // Pushed from Settings now, so it gets a back arrow.
                AboutScreen(onBack = { nav.popBackStack() })
            }
        }
    }
}
