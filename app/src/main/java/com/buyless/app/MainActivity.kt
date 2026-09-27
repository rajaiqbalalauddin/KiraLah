package com.buyless.app

import android.content.res.Configuration
import android.graphics.Color as AndroidColor
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.buyless.app.ui.categories.CategoryCatalog
import com.buyless.app.ui.categories.LocalCategoryCatalog
import androidx.compose.ui.graphics.toArgb
import com.buyless.app.ui.components.LocalAppsRepository
import com.buyless.app.ui.intro.StartupIntro
import com.buyless.app.ui.nav.BuylessNavHost
import com.buyless.app.ui.nav.Routes
import com.buyless.app.ui.theme.BColors
import com.buyless.app.ui.theme.BuylessTheme
import com.buyless.app.ui.theme.ThemeState
import com.buyless.app.util.SwipeDeleteLock

/** Single activity. Picks the first screen synchronously (Setup or Home) so there is no flicker. */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val appContainer = this.container
        // Both inputs to night mode are set before setContent, so the first frame is already right.
        // The activity is recreated when the phone's dark mode changes, which re-runs this line.
        ThemeState.mode = appContainer.prefs.themeMode
        SwipeDeleteLock.locked = appContainer.prefs.swipeDeleteLocked
        ThemeState.systemDark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        applySystemBars(ThemeState.isDark)
        val start = if (appContainer.prefs.onboardingDone) Routes.HOME else Routes.SETUP
        // Intro plays on a real cold start only, not on rotation, dark mode flips or process restore.
        val playIntro = savedInstanceState == null
        setContent {
            // Status bar icons and the window colour follow the app's mode, not only the phone's.
            val dark = ThemeState.isDark
            LaunchedEffect(dark) { applySystemBars(dark) }
            // Custom categories are read once here and shared, so every list resolves keys the same way.
            val customFlow = remember { appContainer.categories.observeCustom() }
            val custom by customFlow.collectAsStateWithLifecycle(initialValue = emptyList())
            val catalog = remember(custom) { CategoryCatalog(custom) }
            var introVisible by rememberSaveable { mutableStateOf(playIntro) }
            BuylessTheme {
                CompositionLocalProvider(LocalAppsRepository provides appContainer.apps, LocalCategoryCatalog provides catalog) {
                    // The app composes underneath while the intro plays, so it is ready when the intro opens.
                    Box(Modifier.fillMaxSize()) {
                        BuylessNavHost(startDestination = start)
                        if (introVisible) StartupIntro(onFinished = { introVisible = false })
                    }
                }
            }
        }
    }

    /**
     * Light icons on dark bars at night, dark icons by day. The window background is recoloured
     * too, so there is no light flash behind the keyboard or during screen transitions.
     */
    private fun applySystemBars(dark: Boolean) {
        val style = if (dark) {
            SystemBarStyle.dark(AndroidColor.TRANSPARENT)
        } else {
            SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT)
        }
        enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
        window.setBackgroundDrawable(ColorDrawable(BColors.Lavender.toArgb()))
    }
}
