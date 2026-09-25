package com.buyless.app.util

import android.content.Context

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

    private companion object {
        const val KEY_SAMPLES = "collect_samples"
        const val KEY_ONBOARDING = "onboarding_done"
        const val KEY_LAST_QR = "last_qr_id"
    }
}
