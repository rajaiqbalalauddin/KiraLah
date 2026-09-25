package com.buyless.app.ui.recap

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.buyless.app.data.model.Category
import com.buyless.app.data.model.Direction
import com.buyless.app.data.repo.TransactionRepository
import com.buyless.app.recap.RecapBuilder
import com.buyless.app.recap.RecapData
import com.buyless.app.recap.RecapTx
import com.buyless.app.util.Dates
import com.buyless.app.util.Money
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

@Immutable
data class MonthCard(val period: String, val month: String, val year: Int, val totalText: String, val countText: String, val inProgress: Boolean, val colorIndex: Int)

@Immutable
data class YearCard(val period: String, val year: Int, val totalText: String, val months: Int, val inProgress: Boolean)

@Immutable
data class RecapArchiveState(val loading: Boolean = true, val years: List<YearCard> = emptyList(), val months: List<MonthCard> = emptyList())

/**
 * The archive grid. One grouped SQL query gives every month's total, so opening the tab never loads
 * individual transactions; those are only read when a story is played.
 */
class RecapArchiveViewModel(tx: TransactionRepository) : ViewModel() {

    val state: StateFlow<RecapArchiveState> = tx.observeMonths().map { rows ->
        val now = YearMonth.now(Dates.zone)
        val months = rows.mapNotNull { row ->
            val ym = runCatching { YearMonth.parse(row.ym) }.getOrNull() ?: return@mapNotNull null
            MonthCard(
                period = row.ym,
                month = ym.month.getDisplayName(TextStyle.FULL, Locale.ENGLISH),
                year = ym.year,
                totalText = Money.format(row.outSen),
                countText = if (row.count == 1) "1 transaction" else "${row.count} transactions",
                inProgress = ym == now,
                colorIndex = ym.monthValue % CARD_COLORS,
            )
        }
        val years = months.groupBy { it.year }.map { (year, list) ->
            val total = rows.filter { it.ym.startsWith("$year-") }.sumOf { it.outSen }
            YearCard(year.toString(), year, Money.format(total), list.size, inProgress = year == now.year)
        }
        RecapArchiveState(loading = false, years = years, months = months)
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RecapArchiveState())

    companion object {
        const val CARD_COLORS = 7
    }
}

/**
 * Loads one period ("2026-09" or "2026") and builds its recap off the main thread. The previous
 * period's spending is loaded too, for the "compared with last month" slide.
 */
class RecapStoryViewModel(private val tx: TransactionRepository, handle: SavedStateHandle) : ViewModel() {

    private val period: String = handle.get<String>(ARG_PERIOD).orEmpty()
    private val _data = MutableStateFlow<RecapData?>(null)
    val data: StateFlow<RecapData?> = _data.asStateFlow()

    init {
        viewModelScope.launch { _data.value = load() }
    }

    private suspend fun load(): RecapData = withContext(Dispatchers.Default) {
        val zone = Dates.zone
        val isYear = period.length == 4
        val start: LocalDate
        val endExclusive: LocalDate
        val prevStart: LocalDate
        val label: String
        if (isYear) {
            val year = period.toIntOrNull() ?: LocalDate.now(zone).year
            start = LocalDate.of(year, 1, 1)
            endExclusive = start.plusYears(1)
            prevStart = start.minusYears(1)
            label = year.toString()
        } else {
            val ym = runCatching { YearMonth.parse(period) }.getOrDefault(YearMonth.now(zone))
            start = ym.atDay(1)
            endExclusive = ym.plusMonths(1).atDay(1)
            prevStart = ym.minusMonths(1).atDay(1)
            label = "${ym.month.getDisplayName(TextStyle.FULL, Locale.ENGLISH)} ${ym.year}"
        }
        fun millis(d: LocalDate) = d.atStartOfDay(zone).toInstant().toEpochMilli()

        val rows = tx.countedInRange(millis(start), millis(endExclusive))
        val previous = tx.countedInRange(millis(prevStart), millis(start))
            .filter { it.direction == Direction.OUT.name }
            .sumOf { it.amountSen }
            .takeIf { it > 0 }

        RecapBuilder.build(
            txs = rows.map {
                RecapTx(
                    amountSen = it.amountSen,
                    direction = if (it.direction == Direction.IN.name) Direction.IN else Direction.OUT,
                    merchant = it.merchant,
                    category = runCatching { Category.valueOf(it.category) }.getOrDefault(Category.OTHER),
                    sourceLabel = it.sourceLabel,
                    timestamp = it.timestamp,
                )
            },
            periodLabel = label,
            isYear = isYear,
            periodStart = start,
            periodEnd = endExclusive.minusDays(1),
            previousSpentSen = previous,
            zone = zone,
        )
    }

    companion object {
        const val ARG_PERIOD = "period"
    }
}
