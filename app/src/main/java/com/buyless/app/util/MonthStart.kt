package com.buyless.app.util

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The live "month starts on" day. Seeded from AppPrefs when the app process starts (before any screen
 * or the notification listener runs) and updated when Settings changes it. A StateFlow, so Home,
 * Activity, Recap and Limits recompute the moment the day changes, with no restart.
 * Change it through [set] after saving to AppPrefs, so the choice also survives a restart.
 */
object MonthStart {
    private val _day = MutableStateFlow(1)
    val day: StateFlow<Int> = _day.asStateFlow()

    /** Current value, for code that runs once rather than observing (the limit checker, queries). */
    val value: Int get() = _day.value

    fun set(day: Int) {
        _day.value = MonthPeriods.clampDay(day)
    }
}
