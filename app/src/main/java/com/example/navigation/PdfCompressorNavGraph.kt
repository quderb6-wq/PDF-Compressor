package com.example.navigation

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.example.AppContainer
import com.example.R
import com.example.presentation.history.HistoryScreen
import com.example.presentation.history.HistoryViewModel
import com.example.presentation.home.HomeScreen
import com.example.presentation.home.HomeViewModel
import com.example.presentation.result.ResultScreen
import com.example.presentation.result.ResultViewModel
import com.example.presentation.settings.SettingsScreen
import com.example.presentation.settings.SettingsViewModel
import kotlinx.coroutines.launch

private data class TopLevelNavItem(
    val labelRes: Int,
    val route: Any,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

@Composable
fun PdfCompressorNavGraph(
    container: AppContainer,
    isDarkTheme: Boolean,
    onToggleTheme: () -> Unit,
    modifier: Modifier = Modifier
) {
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val showSnackbar: (String) -> Unit = remember {
        { message ->
            scope.launch {
                snackbarHostState.currentSnackbarData?.dismiss()
                snackbarHostState.showSnackbar(message)
            }
        }
    }

    val navItems = remember {
        listOf(
            TopLevelNavItem(
                labelRes = R.string.nav_home,
                route = HomeRoute,
                selectedIcon = Icons.Filled.Home,
                unselectedIcon = Icons.Outlined.Home,
                testTag = "nav_home"
            ),
            TopLevelNavItem(
                labelRes = R.string.nav_history,
                route = HistoryRoute,
                selectedIcon = Icons.Filled.History,
                unselectedIcon = Icons.Outlined.History,
                testTag = "nav_history"
            ),
            TopLevelNavItem(
                labelRes = R.string.nav_settings,
                route = SettingsRoute,
                selectedIcon = Icons.Filled.Settings,
                unselectedIcon = Icons.Outlined.Settings,
                testTag = "nav_settings"
            )
        )
    }

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val isTopLevel = currentDestination?.let { dest ->
        dest.hasRoute<HomeRoute>() || dest.hasRoute<HistoryRoute>() || dest.hasRoute<SettingsRoute>()
    } ?: true

    val navigateToTopLevel: (Any) -> Unit = { route ->
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val useNavigationRail = maxWidth >= 600.dp

        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            bottomBar = {
                if (!useNavigationRail && isTopLevel) {
                    NavigationBar {
                        navItems.forEach { item ->
                            val selected = currentDestination?.let { dest ->
                                when (item.route) {
                                    is HomeRoute -> dest.hasRoute<HomeRoute>()
                                    is HistoryRoute -> dest.hasRoute<HistoryRoute>()
                                    is SettingsRoute -> dest.hasRoute<SettingsRoute>()
                                    else -> false
                                }
                            } ?: false

                            NavigationBarItem(
                                selected = selected,
                                onClick = { navigateToTopLevel(item.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                        contentDescription = stringResource(item.labelRes)
                                    )
                                },
                                label = { Text(stringResource(item.labelRes)) },
                                modifier = Modifier.testTag(item.testTag)
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useNavigationRail && isTopLevel) {
                    NavigationRail {
                        navItems.forEach { item ->
                            val selected = currentDestination?.let { dest ->
                                when (item.route) {
                                    is HomeRoute -> dest.hasRoute<HomeRoute>()
                                    is HistoryRoute -> dest.hasRoute<HistoryRoute>()
                                    is SettingsRoute -> dest.hasRoute<SettingsRoute>()
                                    else -> false
                                }
                            } ?: false

                            NavigationRailItem(
                                selected = selected,
                                onClick = { navigateToTopLevel(item.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                        contentDescription = stringResource(item.labelRes)
                                    )
                                },
                                label = { Text(stringResource(item.labelRes)) },
                                modifier = Modifier.testTag(item.testTag)
                            )
                        }
                    }
                }

                NavHost(
                    navController = navController,
                    startDestination = HomeRoute,
                    modifier = Modifier.fillMaxSize()
                ) {
                    composable<HomeRoute> {
                        val homeViewModel: HomeViewModel = viewModel(
                            factory = HomeViewModel.Factory(container)
                        )
                        HomeScreen(
                            viewModel = homeViewModel,
                            isDarkTheme = isDarkTheme,
                            onToggleTheme = onToggleTheme,
                            onOpenSettings = { navigateToTopLevel(SettingsRoute) },
                            onOpenHistory = { navigateToTopLevel(HistoryRoute) },
                            onNavigateToResult = { resultId ->
                                navController.navigate(ResultRoute(resultId))
                            },
                            onShowSnackbar = showSnackbar
                        )
                    }

                    composable<HistoryRoute> {
                        val historyViewModel: HistoryViewModel = viewModel(
                            factory = HistoryViewModel.Factory(container.historyRepository)
                        )
                        HistoryScreen(
                            viewModel = historyViewModel,
                            onShowSnackbar = showSnackbar
                        )
                    }

                    composable<SettingsRoute> {
                        val settingsViewModel: SettingsViewModel = viewModel(
                            factory = SettingsViewModel.Factory(
                                container.settingsRepository,
                                container.pdfCompressionRepository
                            )
                        )
                        SettingsScreen(
                            viewModel = settingsViewModel,
                            onShowSnackbar = showSnackbar
                        )
                    }

                    composable<ResultRoute> { backStack ->
                        val route = backStack.toRoute<ResultRoute>()
                        val resultViewModel: ResultViewModel = viewModel(
                            factory = ResultViewModel.Factory(route.resultId, container)
                        )
                        ResultScreen(
                            viewModel = resultViewModel,
                            onNavigateBack = { navController.popBackStack() },
                            onShowSnackbar = showSnackbar
                        )
                    }
                }
            }
        }
    }
}
