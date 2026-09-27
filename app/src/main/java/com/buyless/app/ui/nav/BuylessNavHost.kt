package com.buyless.app.ui.nav

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.Stable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
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
import com.buyless.app.ui.categories.CategoriesScreen
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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** Every screen's route in one place, so links cannot drift out of sync. */
object Routes {
    const val SETUP = "setup"
    const val SETUP_APPS = "setup/apps"

    /** Hosts all five tabs in one swipeable pager. The tab routes below name pager pages, not destinations. */
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
    const val CATEGORIES = "settings/categories"
    const val EDITOR = "editor?${EditorViewModel.ARG_PENDING}={${EditorViewModel.ARG_PENDING}}&${EditorViewModel.ARG_TXN}={${EditorViewModel.ARG_TXN}}"

    /** 0 = start from the oldest waiting item. */
    fun review(pendingId: Long = 0L) = "editor?${EditorViewModel.ARG_PENDING}=$pendingId"
    fun edit(txnId: Long) = "editor?${EditorViewModel.ARG_TXN}=$txnId"
    fun manual() = "editor"
}

private data class Tab(val route: String, val label: String, val icon: ImageVector)

/** Order here is the pager order and the bottom bar order. */
private val tabs = listOf(
    Tab(Routes.HOME, "Home", Icons.Rounded.Home),
    Tab(Routes.ACTIVITY, "Activity", Icons.Rounded.BarChart),
    Tab(Routes.SPLIT, "Split", Icons.Rounded.Groups),
    Tab(Routes.RECAP, "Recap", Icons.Rounded.AutoAwesome),
    Tab(Routes.SETTINGS, "Settings", Icons.Rounded.Settings),
)

private fun pageOf(route: String) = tabs.indexOfFirst { it.route == route }

/**
 * Ease-in-out (easeInOutCubic): starts slowly, speeds up through the middle, then eases into place.
 * The earlier ease-in hit full speed at the moment it stopped, which read as a jolt at the end.
 */
private val TabSlideEasing = CubicBezierEasing(0.65f, 0f, 0.35f, 1f)

/**
 * The tab pager plus the tab a tap is sliding to.
 *
 * Why not PagerState.animateScrollToPage: for jumps of more than three pages it snaps most of the
 * way first and only animates the last stretch, which is a visible jump (Home to Settings). Here
 * the whole distance is one continuous scroll in pixels.
 *
 * Why track slidingTo: during a scroll the pager's targetPage steps through every tab it passes,
 * so the bottom bar highlight hopped along. The bar reads [selected] instead, which points at the
 * destination for the whole slide.
 */
@Stable
private class TabPager(val state: PagerState) {
    var slidingTo by mutableStateOf<Int?>(null)
        private set

    val selected: Int get() = slidingTo ?: state.targetPage

    suspend fun slideTo(page: Int) {
        val pages = page - state.currentPage - state.currentPageOffsetFraction
        if (pages == 0f) return
        val pagePx = state.layoutInfo.pageSize + state.layoutInfo.pageSpacing
        // Longer jumps get a little more time so they stay one readable slide, not a blur.
        val duration = 380 + 70 * (kotlin.math.abs(pages).toInt() - 1).coerceAtLeast(0)
        slidingTo = page
        try {
            state.animateScrollBy(pages * pagePx, tween(durationMillis = duration, easing = TabSlideEasing))
            state.scrollToPage(page) // settles any sub-pixel rounding left by the pixel scroll
        } finally {
            // A newer tap may already have claimed slidingTo; only clear our own.
            if (slidingTo == page) slidingTo = null
        }
    }
}

/**
 * App navigation. The five tabs live in one HorizontalPager inside the HOME destination, so they
 * slide like pages and can be swiped. Setup, the editor, Apps, Samples and the Recap story are real
 * destinations on top, without the bottom bar, so users finish (or back out of) one task at a time.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BuylessNavHost(startDestination: String) {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route

    // Hoisted here, not inside the HOME destination, because the bottom bar (outside NavHost) drives it.
    val pagerState = rememberPagerState { tabs.size }
    val pager = remember(pagerState) { TabPager(pagerState) }

    // Tapping the tab you are already on. A shared event, not state, so it fires once per tap and
    // never replays when a page is recomposed. Each page only hears its own index.
    val reselects = remember { MutableSharedFlow<Int>(extraBufferCapacity = 1) }

    Scaffold(
        containerColor = BColors.Lavender,
        bottomBar = { if (route == Routes.HOME) BottomBar(pager, onReselect = { reselects.tryEmit(it) }) },
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
            composable(Routes.HOME) { MainTabs(nav, pager, reselects) }
            composable(
                Routes.RECAP_STORY,
                arguments = listOf(navArgument(RecapStoryViewModel.ARG_PERIOD) { type = NavType.StringType }),
            ) { RecapStoryScreen(onClose = { nav.popBackStack() }) }
            // Apps is not a tab (five tabs is the comfortable maximum); it opens from Home and Settings.
            composable(Routes.APPS) { AppsScreen(onBack = { nav.popBackStack() }) }
            composable(Routes.SAMPLES) { SamplesScreen(onBack = { nav.popBackStack() }) }
            composable(Routes.CATEGORIES) { CategoriesScreen(onBack = { nav.popBackStack() }) }
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
 * The swipeable tabs. Only the visible page (and a neighbour mid-swipe) is composed, so five tabs
 * cost about as much as one. The SaveableStateHolder keeps each page's scroll position and
 * rememberSaveable values after it leaves the screen, which the old per-tab back stack did for free.
 */
@Composable
private fun MainTabs(nav: NavHostController, pager: TabPager, reselects: Flow<Int>) {
    val scope = rememberCoroutineScope()
    val home = pageOf(Routes.HOME)

    // Back on any other tab slides to Home first, like the old stack where tabs sat above Home.
    // Declared before the pager so a page's own BackHandler (Split's steps) still wins.
    BackHandler(enabled = pager.state.currentPage != home) { scope.launch { pager.slideTo(home) } }

    val holder = rememberSaveableStateHolder()
    HorizontalPager(
        state = pager.state,
        key = { tabs[it].route },
        // Keep every tab composed. Otherwise a tap from Home to Settings builds Split and Recap from
        // scratch mid-slide, and those dropped frames are the stutter. Five light screens is cheap.
        beyondViewportPageCount = tabs.size - 1,
        modifier = Modifier.fillMaxSize(),
    ) { page ->
        val tab = tabs[page]
        val reselect = remember(page) { reselects.filter { it == page }.map { } }
        holder.SaveableStateProvider(tab.route) {
            when (tab.route) {
                Routes.HOME -> HomeScreen(
                    onOpenReview = { nav.navigate(Routes.review()) },
                    onOpenActivity = { scope.launch { pager.slideTo(pageOf(Routes.ACTIVITY)) } },
                    onOpenTransaction = { nav.navigate(Routes.edit(it)) },
                    onAddManual = { nav.navigate(Routes.manual()) },
                    onAddApp = { nav.navigate(Routes.APPS) },
                    reselect = reselect,
                )
                Routes.ACTIVITY -> ActivityScreen(onOpenTransaction = { nav.navigate(Routes.edit(it)) }, reselect = reselect)
                Routes.SPLIT -> SplitScreen(reselect = reselect, isCurrentTab = pager.state.currentPage == page)
                Routes.RECAP -> RecapScreen(onOpen = { nav.navigate(Routes.recapStory(it)) }, reselect = reselect)
                Routes.SETTINGS -> SettingsScreen(
                    onOpenApps = { nav.navigate(Routes.APPS) },
                    onOpenSamples = { nav.navigate(Routes.SAMPLES) },
                    onOpenCategories = { nav.navigate(Routes.CATEGORIES) },
                    reselect = reselect,
                )
            }
        }
    }
}

/**
 * Tapping another tab slides the pager there, so taps and swipes animate the same way. Tapping the
 * tab you are on sends a reselect instead, which each screen treats as "back to your default".
 * targetPage highlights the destination as soon as a tap or swipe commits to it.
 */
@Composable
private fun BottomBar(pager: TabPager, onReselect: (Int) -> Unit) {
    val scope = rememberCoroutineScope()
    NavigationBar(containerColor = BColors.Surface) {
        tabs.forEachIndexed { index, tab ->
            NavigationBarItem(
                selected = pager.selected == index,
                onClick = {
                    if (pager.slidingTo == null && pager.state.currentPage == index && pager.state.targetPage == index) onReselect(index)
                    else scope.launch { pager.slideTo(index) }
                },
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
