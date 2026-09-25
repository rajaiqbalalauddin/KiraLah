package com.buyless.app.ui.nav

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.buyless.app.ui.activity.ActivityScreen
import com.buyless.app.ui.apps.AppsScreen
import com.buyless.app.ui.editor.EditorScreen
import com.buyless.app.ui.editor.EditorViewModel
import com.buyless.app.ui.home.HomeScreen
import com.buyless.app.ui.recap.RecapScreen
import com.buyless.app.ui.recap.RecapStoryScreen
import com.buyless.app.ui.recap.RecapStoryViewModel
import com.buyless.app.ui.settings.SamplesScreen
import com.buyless.app.ui.settings.SettingsScreen
import com.buyless.app.ui.split.SplitScreen
import com.buyless.app.ui.setup.SetupScreen
import com.buyless.app.ui.theme.BColors

/** Every screen's route in one place, so links cannot drift out of sync. */
object Routes {
    const val SETUP = "setup"
    const val SETUP_APPS = "setup/apps"
    const val HOME = "home"
    const val ACTIVITY = "activity"
    const val SPLIT = "split"
    const val RECAP = "recap"
    const val RECAP_STORY = "recap/story/{${RecapStoryViewModel.ARG_PERIOD}}"

    /** period is "2026-09" for a month or "2026" for a whole year. */
    fun recapStory(period: String) = "recap/story/$period"
    const val APPS = "apps"
    const val SETTINGS = "settings"
    const val SAMPLES = "settings/samples"
    const val EDITOR = "editor?${EditorViewModel.ARG_PENDING}={${EditorViewModel.ARG_PENDING}}&${EditorViewModel.ARG_TXN}={${EditorViewModel.ARG_TXN}}"

    /** 0 = start from the oldest waiting item. */
    fun review(pendingId: Long = 0L) = "editor?${EditorViewModel.ARG_PENDING}=$pendingId"
    fun edit(txnId: Long) = "editor?${EditorViewModel.ARG_TXN}=$txnId"
    fun manual() = "editor"
}

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab(Routes.HOME, "Home", Icons.Rounded.Home),
    Tab(Routes.ACTIVITY, "Activity", Icons.Rounded.BarChart),
    Tab(Routes.SPLIT, "Split", Icons.Rounded.Groups),
    Tab(Routes.RECAP, "Recap", Icons.Rounded.AutoAwesome),
    Tab(Routes.SETTINGS, "Settings", Icons.Rounded.Settings),
)
private val tabRoutes = tabs.map { it.route }.toSet()

/**
 * App navigation. The bottom bar shows only on the four main tabs; Setup and the editor are focused
 * flows without it, so users finish (or back out of) one task at a time.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BuylessNavHost(startDestination: String) {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route

    Scaffold(
        containerColor = BColors.Lavender,
        bottomBar = { if (route in tabRoutes) BottomBar(nav, route) },
    ) { padding ->
        NavHost(
            navController = nav,
            startDestination = startDestination,
            modifier = Modifier.padding(padding).consumeWindowInsets(padding),
        ) {
            composable(Routes.SETUP) {
                SetupScreen(
                    onDone = { nav.navigate(Routes.HOME) { popUpTo(Routes.SETUP) { inclusive = true } } },
                    onAddApp = { nav.navigate(Routes.SETUP_APPS) },
                )
            }
            composable(Routes.SETUP_APPS) { AppsScreen(onBack = { nav.popBackStack() }) }
            composable(Routes.HOME) {
                HomeScreen(
                    onOpenReview = { nav.navigate(Routes.review()) },
                    onOpenActivity = { nav.switchTab(Routes.ACTIVITY) },
                    onOpenTransaction = { nav.navigate(Routes.edit(it)) },
                    onAddManual = { nav.navigate(Routes.manual()) },
                    onAddApp = { nav.navigate(Routes.APPS) },
                )
            }
            composable(Routes.ACTIVITY) { ActivityScreen(onOpenTransaction = { nav.navigate(Routes.edit(it)) }) }
            composable(Routes.RECAP) { RecapScreen(onOpen = { nav.navigate(Routes.recapStory(it)) }) }
            composable(
                Routes.RECAP_STORY,
                arguments = listOf(navArgument(RecapStoryViewModel.ARG_PERIOD) { type = NavType.StringType }),
            ) { RecapStoryScreen(onClose = { nav.popBackStack() }) }
            composable(Routes.SPLIT) { SplitScreen() }
            // Apps is no longer a tab (five tabs is the comfortable maximum); it opens from Home and Settings.
            composable(Routes.APPS) { AppsScreen(onBack = { nav.popBackStack() }) }
            composable(Routes.SETTINGS) {
                SettingsScreen(onOpenApps = { nav.navigate(Routes.APPS) }, onOpenSamples = { nav.navigate(Routes.SAMPLES) })
            }
            composable(Routes.SAMPLES) { SamplesScreen(onBack = { nav.popBackStack() }) }
            composable(
                Routes.EDITOR,
                arguments = listOf(
                    navArgument(EditorViewModel.ARG_PENDING) { type = NavType.LongType; defaultValue = -1L },
                    navArgument(EditorViewModel.ARG_TXN) { type = NavType.LongType; defaultValue = -1L },
                ),
            ) { EditorScreen(onClose = { nav.popBackStack() }) }
        }
    }
}

/**
 * Standard tab switch: one copy of each tab, and each tab keeps its scroll position.
 * Home is the root after Setup finishes, so tabs pop back to it rather than to the graph start.
 */
private fun NavHostController.switchTab(route: String) {
    navigate(route) {
        popUpTo(Routes.HOME) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
private fun BottomBar(nav: NavHostController, current: String?) {
    NavigationBar(containerColor = BColors.White) {
        tabs.forEach { tab ->
            NavigationBarItem(
                selected = current == tab.route,
                onClick = { nav.switchTab(tab.route) },
                icon = { Icon(tab.icon, contentDescription = null) },
                label = { Text(tab.label) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = BColors.Violet,
                    selectedTextColor = BColors.Violet,
                    indicatorColor = BColors.VioletSoft,
                    unselectedIconColor = BColors.Muted,
                    unselectedTextColor = BColors.Muted,
                ),
            )
        }
    }
}
