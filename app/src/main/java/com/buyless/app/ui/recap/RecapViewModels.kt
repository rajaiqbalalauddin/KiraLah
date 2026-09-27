package com.buyless.app.ui.recap

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.buyless.app.data.model.Category
import com.buyless.app.data.model.Direction
import com.buyless.app.data.repo.TransactionRepository
import com.buyless.app.recap.DayTotal
import com.buyless.app.recap.RecapBuilder
import com.buyless.app.recap.RecapData
import com.buyless.app.recap.RecapTx
import com.buyless.app.util.Dates
import com.buyless.app.util.Money
import com.buyless.app.util.MonthPeriods
import com.buyless.app.util.MonthStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

@Immutable
data class MonthCard(
    val period: String,
    val month: String,
    val year: Int,
    /** "25 Sep – 24 Oct" when months start on a day other than the 1st, else null. */
    val rangeText: String?,
    val totalText: String,
    val countText: String,
    val inProgress: Boolean,
    val colorIndex: Int,
)

@Immutable
data class YearCard(val period: String, val year: Int, val totalText: String, val months: Int, val inProgress: Boolean)

@Immutable
data class RecapArchiveState(
    val loading: Boolean = true,
    val years: List<YearCard> = emptyList(),
    /** Finished months only. */
    val months: List<MonthCard> = emptyList(),
    /** "September's recap unlocks on 25 October" (the next month start), or null when the current month has no spending yet. */
    val lockedNote: String? = null,
)

/**
 * The archive grid. One grouped SQL query gives every month's total, so opening the tab never loads
 * individual transactions; those are only read when a story is played.
 */
class RecapArchiveViewModel(tx: TransactionRepository) : ViewModel() {

    // Day totals are folded into months here, so a new month start day regroups the archive at once.
    val state: StateFlow<RecapArchiveState> = combine(tx.observeDays(), MonthStart.day) { rows, day ->
        val today = LocalDate.now(Dates.zone)
        val now = MonthPeriods.periodOf(today, day)
        val totals = RecapBuilder.monthTotals(
            rows.mapNotNull { r -> runCatching { LocalDate.parse(r.day) }.getOrNull()?.let { DayTotal(it, r.outSen, r.count) } },
            day,
        )
        val months = totals.map { t ->
            MonthCard(
                period = t.ym.toString(),
                month = t.ym.month.getDisplayName(TextStyle.FULL, Locale.ENGLISH),
                year = t.ym.year,
                rangeText = if (day == 1) null else MonthPeriods.rangeText(t.ym, day, today),
                totalText = Money.format(t.outSen),
                countText = if (t.count == 1) "1 transaction" else "${t.count} transactions",
                inProgress = t.ym == now,
                colorIndex = t.ym.monthValue % CARD_COLORS,
            )
        }
        // The year banner still counts this month ("so far"), so it is built from every month. A year
        // is the twelve periods that start in it, so it always equals the sum of its month cards.
        val years = months.groupBy { it.year }.map { (year, list) ->
            val total = totals.filter { it.ym.year == year }.sumOf { it.outSen }
            YearCard(year.toString(), year, Money.format(total), list.size, inProgress = year == now.year)
        }
        // A month's story only makes sense once the month is over, so the current month is held back.
        val current = months.firstOrNull { it.inProgress }
        val lockedNote = current?.let {
            val unlocks = MonthPeriods.endExclusive(now, day)
            "${it.month}'s recap unlocks on ${unlocks.dayOfMonth} ${unlocks.month.getDisplayName(TextStyle.FULL, Locale.ENGLISH)}"
        }
        RecapArchiveState(loading = false, years = years, months = months.filterNot { it.inProgress }, lockedNote = lockedNote)
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
        val day = MonthStart.value
        val isYear = period.length == 4
        val start: LocalDate
        val endExclusive: LocalDate
        val prevStart: LocalDate
        val label: String
        if (isYear) {
            // The twelve month periods that start in this year, so it matches the month cards.
            val year = period.toIntOrNull() ?: LocalDate.now(zone).year
            start = MonthPeriods.yearStart(year, day)
            endExclusive = MonthPeriods.yearStart(year + 1, day)
            prevStart = MonthPeriods.yearStart(year - 1, day)
            label = year.toString()
        } else {
            val ym = runCatching { YearMonth.parse(period) }.getOrDefault(MonthPeriods.current(zone, day))
            start = MonthPeriods.startOf(ym, day)
            endExclusive = MonthPeriods.endExclusive(ym, day)
            prevStart = MonthPeriods.startOf(ym.minusMonths(1), day)
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
                    category = it.category,
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
            monthStartDay = day,
        )
    }

    companion object {
        const val ARG_PERIOD = "period"
    }
}
