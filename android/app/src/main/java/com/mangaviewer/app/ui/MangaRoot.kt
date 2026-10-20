package com.mangaviewer.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sell
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.mangaviewer.app.data.AppContainer
import com.mangaviewer.app.ui.screens.DetailScreen
import com.mangaviewer.app.ui.screens.DownloadsScreen
import com.mangaviewer.app.ui.screens.FavoritesScreen
import com.mangaviewer.app.ui.screens.GateScreen
import com.mangaviewer.app.ui.screens.HistoryScreen
import com.mangaviewer.app.ui.screens.HomeScreen
import com.mangaviewer.app.ui.screens.LoginScreen
import com.mangaviewer.app.ui.screens.ReaderScreen
import com.mangaviewer.app.ui.screens.SearchScreen
import com.mangaviewer.app.ui.screens.TagBrowseScreen
import com.mangaviewer.app.ui.screens.SettingsScreen
import com.mangaviewer.app.ui.screens.TagScreen
import com.mangaviewer.app.ui.theme.MangaTheme

private data class TabRoute(val route: String, val label: String, val icon: ImageVector)

private val TABS = listOf(
    TabRoute("home", "首页", Icons.Filled.Home),
    TabRoute("browse", "分类", Icons.Filled.Sell),
    TabRoute("favorites", "收藏", Icons.Filled.Favorite),
    TabRoute("downloads", "下载", Icons.Filled.Download),
    TabRoute("settings", "设置", Icons.Filled.Settings),
)

@Composable
fun MangaRoot(container: AppContainer) {
    val settingsRepo = container.settings
    val scope = rememberCoroutineScope()
    val settings by settingsRepo.settingsFlow.collectAsState(initial = null)
    val current = settings
    if (current == null) return
    MangaTheme(current) {
        if (!current.adultConfirmed) {
            GateScreen(onConfirm = { scope.launch { settingsRepo.setAdultConfirmed(true) } })
            return@MangaTheme
        }
        val navController = rememberNavController()
        val backStack by navController.currentBackStackEntryAsState()
        val route = backStack?.destination?.route
        val showBar = TABS.any { it.route == route }
        Scaffold(
            bottomBar = {
                if (showBar) {
                    NavigationBar {
                        TABS.forEach { tab ->
                            NavigationBarItem(
                                selected = route == tab.route,
                                onClick = {
                                    navController.navigate(tab.route) {
                                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = { Icon(tab.icon, contentDescription = tab.label) },
                                label = { Text(tab.label) },
                            )
                        }
                    }
                }
            },
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                NavHost(navController = navController, startDestination = "home") {
                    composable("home") { HomeScreen(container, navController) }
                    composable("browse") { TagBrowseScreen(container, navController) }
                    composable("search") { SearchScreen(container, navController) }
                    composable("favorites") { FavoritesScreen(container, navController) }
                    composable("downloads") { DownloadsScreen(container, navController) }
                    composable("settings") { SettingsScreen(container, navController) }
                    composable("login") { LoginScreen(container, navController) }
                    composable("history") { HistoryScreen(container, navController) }
                    composable("detail/{id}") { entry ->
                        val id = entry.arguments?.getString("id")?.toLongOrNull() ?: 0L
                        DetailScreen(container, id, navController)
                    }
                    composable("reader/{id}?page={page}") { entry ->
                        val id = entry.arguments?.getString("id")?.toLongOrNull() ?: 0L
                        val page = entry.arguments?.getString("page")?.toIntOrNull() ?: 1
                        ReaderScreen(container, id, page, navController)
                    }
                    composable("tag/{type}/{id}?name={name}") { entry ->
                        val type = entry.arguments?.getString("type").orEmpty()
                        val id = entry.arguments?.getString("id")?.toLongOrNull() ?: 0L
                        val name = entry.arguments?.getString("name").orEmpty()
                        TagScreen(container, type, id, name, navController)
                    }
                }
            }
        }
    }
}