package com.buyless.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import com.buyless.app.ui.components.LocalAppsRepository
import com.buyless.app.ui.nav.BuylessNavHost
import com.buyless.app.ui.nav.Routes
import com.buyless.app.ui.theme.BuylessTheme

/** Single activity. Picks the first screen synchronously (Setup or Home) so there is no flicker. */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val appContainer = this.container
        val start = if (appContainer.prefs.onboardingDone) Routes.HOME else Routes.SETUP
        setContent {
            BuylessTheme {
                CompositionLocalProvider(LocalAppsRepository provides appContainer.apps) {
                    BuylessNavHost(startDestination = start)
                }
            }
        }
    }
}
