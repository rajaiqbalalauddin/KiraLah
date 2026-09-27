package com.buyless.app.util

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.buyless.app.ui.theme.ThemeMode

/**
 * Tiny key-value store for app flags. SharedPreferences is used on purpose: it is read once into
 * memory, so the start screen can be decided synchronously with no splash or flicker.
 */
class AppPrefs(context: Context) {
    private val prefs = context.getSharedPreferences("buyless", Context.MODE_PRIVATE)

    var onboardingDone: Boolean
        get() = prefs.getBoolean(KEY_ONBOARDING, false)
        set(value) = prefs.edit().putBoolean(KEY_ONBOARDING, value).apply()

    /** Last QR picked on the Split pay screen, so it is pre-selected next time. -1 = none. */
    var lastQrId: Long
        get() = prefs.getLong(KEY_LAST_QR, -1L)
        set(value) = prefs.edit().putLong(KEY_LAST_QR, value).apply()

    /** Whether raw notifications from watched apps are kept as samples. On by default while formats are learnt. */
    var collectSamples: Boolean
        get() = prefs.getBoolean(KEY_SAMPLES, true)
        set(value) = prefs.edit().putBoolean(KEY_SAMPLES, value).apply()

    /** Set once QRs saved before auto-cropping have been cropped, so it only ever runs once. */
    var qrsCropped: Boolean
        get() = prefs.getBoolean(KEY_QRS_CROPPED, false)
        set(value) = prefs.edit().putBoolean(KEY_QRS_CROPPED, value).apply()

    /**
     * Masks app balances on Home, for when someone can see your screen. Stored here rather than
     * in Room because it is a display choice, not data, and must be ready before the first frame.
     */
    var hideBalances: Boolean
        get() = prefs.getBoolean(KEY_HIDE_BALANCES, false)
        set(value) = prefs.edit().putBoolean(KEY_HIDE_BALANCES, value).apply()

    /** System, Light or Dark. Read before the first frame so the app never flashes the wrong colours. */
    var themeMode: ThemeMode
        get() = prefs.getString(KEY_THEME, null)?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM
        set(value) = prefs.edit().putString(KEY_THEME, value.name).apply()

    /**
     * Swipe-to-delete lock. On by default: a stray swipe on Home was deleting entries. Writing here
     * also updates SwipeDeleteLock, so every list and both switches change at once.
     */
    var swipeDeleteLocked: Boolean
        get() = prefs.getBoolean(KEY_SWIPE_LOCK, true)
        set(value) {
            prefs.edit().putBoolean(KEY_SWIPE_LOCK, value).apply()
            SwipeDeleteLock.locked = value
        }

    /**
     * Day of the month a "month" starts on (1 to 31), for people who budget from payday to payday.
     * Read at start-up into MonthStart, which is what the screens observe.
     */
    var monthStartDay: Int
        get() = prefs.getInt(KEY_MONTH_START, 1)
        set(value) = prefs.edit().putInt(KEY_MONTH_START, value).apply()

    private companion object {
        const val KEY_MONTH_START = "month_start_day"
        const val KEY_SWIPE_LOCK = "swipe_delete_locked"
        const val KEY_THEME = "theme_mode"
        const val KEY_HIDE_BALANCES = "hide_balances"
        const val KEY_QRS_CROPPED = "qrs_cropped"
        const val KEY_SAMPLES = "collect_samples"
        const val KEY_ONBOARDING = "onboarding_done"
        const val KEY_LAST_QR = "last_qr_id"
    }
}

/**
 * Live copy of the swipe-to-delete lock as snapshot state, so SwipeToDelete rows recompose the
 * moment it changes. Seeded from AppPrefs in MainActivity; change it through AppPrefs so it persists.
 */
object SwipeDeleteLock {
    var locked by mutableStateOf(true)
        internal set
}
