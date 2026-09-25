package com.buyless.app.ui.home

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.buyless.app.data.model.Direction
import com.buyless.app.data.repo.AppsRepository
import com.buyless.app.data.repo.TransactionRepository
import com.buyless.app.ui.components.AppTileUi
import com.buyless.app.ui.components.TxnUi
import com.buyless.app.ui.components.kindOf
import com.buyless.app.ui.components.toUi
import com.buyless.app.util.Dates
import com.buyless.app.util.Money
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.YearMonth

@Immutable
data class HomeUiState(
    val loading: Boolean = true,
    val monthName: String = "",
    val canGoNext: Boolean = false,
    val spentText: String = Money.format(0),
    val inText: String = Money.format(0),
    val leftText: String = Money.format(0),
    val isOver: Boolean = false,
    val apps: List<AppTileUi> = emptyList(),
    val pendingCount: Int = 0,
    val recent: List<TxnUi> = emptyList(),
)

/**
 * Builds the Home dashboard from several small Room flows. flatMapLatest cancels the previous month's
 * queries as soon as the user switches month, and WhileSubscribed(5s) stops all queries shortly after
 * the app leaves the screen, while surviving a quick rotation without re-querying.
 */
class HomeViewModel(
    private val tx: TransactionRepository,
    apps: AppsRepository,
    private val month: MutableStateFlow<YearMonth>,
) : ViewModel() {

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<HomeUiState> = month.flatMapLatest { ym ->
        val (from, to) = Dates.monthRange(ym)
        val totals = combine(
            tx.observeTotal(Direction.OUT, from, to),
            tx.observeTotal(Direction.IN, from, to),
        ) { out, inn -> out to inn }

        combine(
            totals,
            tx.observePerApp(from, to),
            apps.observeWatched(),
            tx.observeRecent(from, to, RECENT_LIMIT),
            tx.observeOpenPendingCount(),
        ) { (out, inn), perApp, watched, recent, pending ->
            val spentByPkg = perApp.associateBy { it.sourcePackage }
            val today = LocalDate.now(Dates.zone)
            val left = inn - out
            HomeUiState(
                loading = false,
                monthName = Dates.monthLabel(ym),
                canGoNext = ym < YearMonth.now(Dates.zone),
                spentText = Money.format(out),
                inText = Money.format(inn),
                leftText = Money.format(left),
                isOver = left < 0,
                apps = watched.map { app ->
                    val total = spentByPkg[app.packageName]
                    AppTileUi(
                        packageName = app.packageName,
                        label = app.label,
                        kind = kindOf(app.kind),
                        spentText = Money.format(total?.outSen ?: 0),
                        countText = when (val c = total?.count ?: 0) {
                            0 -> "No spending"
                            1 -> "1 payment"
                            else -> "$c payments"
                        },
                    )
                },
                pendingCount = pending,
                recent = recent.map { it.toUi(today) },
            )
        }
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    fun previousMonth() {
        month.value = month.value.minusMonths(1)
    }

    fun nextMonth() {
        if (month.value < YearMonth.now(Dates.zone)) month.value = month.value.plusMonths(1)
    }

    private companion object {
        const val RECENT_LIMIT = 5
    }
}
