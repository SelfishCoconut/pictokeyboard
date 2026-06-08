package org.pictokeyboard.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import org.pictokeyboard.R
import org.pictokeyboard.ui.screens.AboutScreen
import org.pictokeyboard.ui.screens.AddPictosScreen
import org.pictokeyboard.ui.screens.CategoriesScreen
import org.pictokeyboard.ui.screens.DashboardScreen
import org.pictokeyboard.ui.screens.PictosScreen
import org.pictokeyboard.ui.screens.SettingsScreen
import org.pictokeyboard.ui.screens.UnlockScreen
import org.pictokeyboard.ui.screens.openInputMethodSettings
import org.pictokeyboard.ui.screens.showKeyboardPicker
import org.pictokeyboard.ui.theme.PictoKeyboardTheme

object Routes {
    const val HOME = "home"
    const val CATEGORIES = "categories"
    const val PICTOS = "pictos"
    const val ADD_PICTOS = "addpictos"
    const val SETTINGS = "settings"
    const val ABOUT = "about"
    fun pictos(categoryId: String) = "$PICTOS/$categoryId"
    fun addPictos(categoryId: String) = "$ADD_PICTOS/$categoryId"
}

/** A top-level destination shown in the bottom navigation bar. */
private data class NavItem(val route: String, val label: Int, val icon: ImageVector)

private val NavItems = listOf(
    NavItem(Routes.HOME, R.string.nav_home, Icons.Filled.Home),
    NavItem(Routes.CATEGORIES, R.string.nav_board, Icons.Filled.GridView),
    NavItem(Routes.SETTINGS, R.string.nav_settings, Icons.Filled.Settings),
    NavItem(Routes.ABOUT, R.string.nav_about, Icons.Filled.Info),
)

class MainActivity : ComponentActivity() {

    private val viewModel: ConfigViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
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
    // The whole app follows the in-app "Default language" setting, not the system locale.
    ProvideAppLocale(settings.defaultLanguage) {
        AppNavigation(viewModel, settings)
    }
}

@Composable
private fun AppNavigation(viewModel: ConfigViewModel, settings: org.pictokeyboard.data.prefs.Settings) {
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
            startDestination = Routes.HOME,
            modifier = Modifier
                .padding(bottom = bottom)
                .consumeWindowInsets(PaddingValues(bottom = bottom)),
        ) {
            composable(Routes.HOME) {
                DashboardScreen(
                    viewModel = viewModel,
                    onEnableKeyboard = { openInputMethodSettings(context) },
                    onSelectKeyboard = { showKeyboardPicker(context) },
                    onOpenBoard = {
                        nav.navigate(Routes.CATEGORIES) {
                            popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                )
            }
            composable(Routes.CATEGORIES) {
                CategoriesScreen(
                    viewModel = viewModel,
                    onBack = null,
                    onOpenCategory = { id -> nav.navigate(Routes.pictos(id)) },
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
                    defaultLanguage = settings.defaultLanguage,
                    onBack = { nav.popBackStack() },
                )
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(viewModel = viewModel, onBack = null)
            }
            composable(Routes.ABOUT) {
                AboutScreen(onBack = null)
            }
        }
    }
}
