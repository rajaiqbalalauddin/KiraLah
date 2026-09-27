package com.buyless.app.ui.limits

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.buyless.app.data.model.LimitPeriod
import com.buyless.app.data.repo.LimitRepository
import com.buyless.app.limits.Limit
import com.buyless.app.limits.LimitChecker
import com.buyless.app.limits.LimitPeriods
import com.buyless.app.limits.LimitStatus
import com.buyless.app.util.Dates
import com.buyless.app.util.MonthStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@Immutable
data class LimitsState(val loaded: Boolean = false, val statuses: List<LimitStatus> = emptyList())

/**
 * Feeds the Limits screen: every limit with how much of it is used right now. Uses the same
 * LimitChecker as the notification path, so the bar and the alert never disagree.
 */
class LimitsViewModel(private val repo: LimitRepository) : ViewModel() {

    // Rows since the earliest window start, reloaded when the month start day changes. If a period
    // rolls over while the screen stays open, the extra older rows are simply outside the new window.
    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<LimitsState> = MonthStart.day.flatMapLatest { day ->
        val from = LimitPeriods.earliestStart(System.currentTimeMillis(), Dates.zone, day)
        combine(repo.observeLimits(), repo.observeSpendRows(from)) { limits, rows ->
            LimitsState(true, LimitChecker.statuses(limits, rows, System.currentTimeMillis(), Dates.zone, day))
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LimitsState())

    fun save(existing: Limit?, period: LimitPeriod, categoryKey: String, amountSen: Long) {
        viewModelScope.launch {
            repo.save(existing?.id ?: 0L, period, categoryKey, amountSen)
        }
    }

    fun delete(id: Long) {
        viewModelScope.launch { repo.delete(id) }
    }
}
