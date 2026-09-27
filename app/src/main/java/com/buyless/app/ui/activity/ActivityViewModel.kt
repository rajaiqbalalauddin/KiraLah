package com.buyless.app.ui.activity

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.buyless.app.data.model.AppKind
import com.buyless.app.data.model.Category
import com.buyless.app.data.model.Direction
import com.buyless.app.data.repo.AppsRepository
import com.buyless.app.data.repo.TransactionRepository
import com.buyless.app.ui.components.TxnUi
import com.buyless.app.ui.components.kindOf
import com.buyless.app.ui.components.toUi
import com.buyless.app.util.Dates
import com.buyless.app.util.Money
import com.buyless.app.util.MonthPeriods
import com.buyless.app.util.MonthStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import com.buyless.app.data.db.TransactionEntity
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

private const val TREND_MONTHS = 6
private const val TOP_CATEGORIES = 5
private val DAY_DETAIL = DateTimeFormatter.ofPattern("EEE, d MMM", Locale.ENGLISH)
private val MONTH_SHORT = DateTimeFormatter.ofPattern("MMM", Locale.ENGLISH)
private val MONTH_DETAIL = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH)

@Immutable
data class FilterChipUi(val packageName: String?, val label: String, val kind: AppKind?, val selected: Boolean)

@Immutable
data class DaySection(val epochDay: Long, val label: String, val totalText: String, val items: List<TxnUi>)

/** One bar in a spending chart. detail is what the tooltip line says when the bar is tapped. */
@Immutable
data class ChartBar(val label: String, val sen: Long, val detail: String, val isCurrent: Boolean)

/** One row of "Where it went": a category key and its share of the month's spending. */
@Immutable
data class CategoryShare(val key: String, val sen: Long, val amountText: String, val percent: Int, val fraction: Float)

/**
 * Everything the Activity charts draw. Built in the ViewModel (off the main thread) so the
 * charts only paint, and they follow the app filter chips like the list does.
 */
@Immutable
data class SpendCharts(
    val days: List<ChartBar> = emptyList(),
    val dayAverageText: String = "",
    val months: List<ChartBar> = emptyList(),
    val monthAverageText: String = "",
    val categories: List<CategoryShare> = emptyList(),
) {
    val hasSpending: Boolean get() = months.any { it.sen > 0 }
}

@Immutable
data class ActivityUiState(
    val loading: Boolean = true,
    val monthName: String = "",
    val canGoNext: Boolean = false,
    val outText: String = Money.format(0),
    val inText: String = Money.format(0),
    val chips: List<FilterChipUi> = emptyList(),
    val sections: List<DaySection> = emptyList(),
    val hasFilter: Boolean = false,
    val charts: SpendCharts = SpendCharts(),
)

/**
 * Month list grouped by day. One Room query per month; filtering by app and search runs in memory on
 * a background dispatcher, since a month of personal transactions is small and this avoids a new
 * query per keystroke. Search input is debounced so typing does not rebuild the list on every letter.
 */
class ActivityViewModel(
    private val tx: TransactionRepository,
    apps: AppsRepository,
    private val month: MutableStateFlow<YearMonth>,
) : ViewModel() {

    private val selectedApp = MutableStateFlow<String?>(null)
    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    // The month and the month start day travel together, so changing the day in Settings reloads at once.
    private val period = combine(month, MonthStart.day) { ym, day -> ym to day }

    @OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
    val state: StateFlow<ActivityUiState> = combine(
        // One query covers the chart's six months; the list below only uses the selected month.
        period.flatMapLatest { (ym, day) ->
            val from = Dates.monthRange(ym.minusMonths(TREND_MONTHS - 1L), day).first
            val to = Dates.monthRange(ym, day).second
            tx.observeRange(from, to)
        },
        apps.observeWatched(),
        selectedApp,
        _query.debounce(200),
        period,
    ) { trendRows, watched, selected, query, (ym, day) ->
        val today = LocalDate.now(Dates.zone)
        val (monthFrom, monthTo) = Dates.monthRange(ym, day)
        val rows = trendRows.filter { it.timestamp in monthFrom until monthTo }
        val q = query.trim()
        val filtered = rows.filter { row ->
            (selected == null || row.sourcePackage == selected) &&
                (q.isEmpty() || row.merchant.contains(q, ignoreCase = true) || row.sourceLabel.contains(q, ignoreCase = true))
        }

        var outSen = 0L
        var inSen = 0L
        for (r in filtered) {
            if (r.isInternal) continue
            if (r.direction == Direction.IN.name) inSen += r.amountSen else outSen += r.amountSen
        }

        // Rows arrive newest first, so grouping keeps day order without another sort.
        val sections = filtered.groupBy { Dates.toDate(it.timestamp) }.map { (date, dayRows) ->
            var net = 0L
            for (r in dayRows) if (!r.isInternal) net += if (r.direction == Direction.IN.name) r.amountSen else -r.amountSen
            DaySection(
                epochDay = date.toEpochDay(),
                label = Dates.sectionLabel(date, today),
                totalText = Money.formatSigned(kotlin.math.abs(net), net > 0),
                items = dayRows.map { it.toUi(today) },
            )
        }

        // Chips: watched apps plus any app that has rows this month but was since removed.
        val chipApps = LinkedHashMap<String, Pair<String, AppKind>>()
        watched.forEach { chipApps[it.packageName] = it.label to kindOf(it.kind) }
        rows.forEach { if (it.sourcePackage !in chipApps) chipApps[it.sourcePackage] = it.sourceLabel to AppKind.WALLET }
        val chips = buildList {
            add(FilterChipUi(null, "All", null, selected == null))
            chipApps.forEach { (pkg, v) -> add(FilterChipUi(pkg, v.first, v.second, selected == pkg)) }
        }

        ActivityUiState(
            loading = false,
            monthName = Dates.monthLabel(ym, day),
            canGoNext = ym < MonthPeriods.current(Dates.zone, day),
            outText = Money.format(outSen),
            inText = Money.format(inSen),
            chips = chips,
            sections = sections,
            hasFilter = selected != null || q.isNotEmpty(),
            // Charts follow the app chip but not the search box: search is for finding rows, not for totals.
            charts = buildCharts(trendRows.filter { selected == null || it.sourcePackage == selected }, ym, day, today),
        )
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ActivityUiState())

    /**
     * Spending (money out, own transfers excluded) per day of the selected month, per month for the
     * last six months, and per category for the selected month.
     */
    private fun buildCharts(rows: List<TransactionEntity>, ym: YearMonth, startDay: Int, today: LocalDate): SpendCharts {
        val spend = rows.filter { !it.isInternal && it.direction == Direction.OUT.name }

        // Days: every day of the period gets a slot, so a bar's position is its date. With a custom
        // start day the first bar is that day (25, 26 ... 24), not the 1st.
        val start = MonthPeriods.startOf(ym, startDay)
        val byDay = LongArray(MonthPeriods.length(ym, startDay))
        val byMonth = HashMap<YearMonth, Long>()
        val byCategory = HashMap<String, Long>()
        for (r in spend) {
            val date = Dates.toDate(r.timestamp)
            val rowMonth = MonthPeriods.periodOf(date, startDay)
            byMonth[rowMonth] = (byMonth[rowMonth] ?: 0L) + r.amountSen
            if (rowMonth == ym) {
                byDay[(date.toEpochDay() - start.toEpochDay()).toInt()] += r.amountSen
                byCategory[r.category] = (byCategory[r.category] ?: 0L) + r.amountSen
            }
        }
        val days = byDay.mapIndexed { i, sen ->
            val date = start.plusDays(i.toLong())
            ChartBar(
                label = date.dayOfMonth.toString(),
                sen = sen,
                detail = "${date.format(DAY_DETAIL)}: ${Money.format(sen)}",
                isCurrent = date == today,
            )
        }
        // Average over days that have happened, so a young month is not diluted by empty future days.
        val currentPeriod = MonthPeriods.periodOf(today, startDay)
        val daysSoFar = when {
            ym == currentPeriod -> (today.toEpochDay() - start.toEpochDay() + 1).toInt()
            ym.isAfter(currentPeriod) -> 0
            else -> byDay.size
        }.coerceAtLeast(1)
        val monthTotal = byDay.sum()

        val months = (TREND_MONTHS - 1 downTo 0).map { back ->
            val m = ym.minusMonths(back.toLong())
            val sen = byMonth[m] ?: 0L
            ChartBar(
                label = m.format(MONTH_SHORT),
                sen = sen,
                detail = "${if (startDay == 1) m.format(MONTH_DETAIL) else MonthPeriods.rangeText(m, startDay, today)}: ${Money.format(sen)}",
                isCurrent = m == ym,
            )
        }

        val sortedCategories = byCategory.entries.sortedByDescending { it.value }
        val top = sortedCategories.take(TOP_CATEGORIES).map { it.key to it.value }
        val rest = sortedCategories.drop(TOP_CATEGORIES).sumOf { it.value }
        // Anything past the top five folds into Other, so the list never grows past six rows.
        val merged = if (rest > 0) {
            val other = Category.OTHER.name
            val otherSen = (top.firstOrNull { it.first == other }?.second ?: 0L) + rest
            top.filterNot { it.first == other } + (other to otherSen)
        } else {
            top
        }
        val biggest = merged.maxOfOrNull { it.second }?.coerceAtLeast(1) ?: 1
        val categories = merged.sortedByDescending { it.second }.map { (key, sen) ->
            CategoryShare(
                key = key,
                sen = sen,
                amountText = Money.format(sen),
                percent = if (monthTotal > 0) ((sen * 100 + monthTotal / 2) / monthTotal).toInt() else 0,
                fraction = sen.toFloat() / biggest,
            )
        }

        return SpendCharts(
            days = days,
            dayAverageText = "${Money.format(monthTotal / daysSoFar)} a day on average",
            months = months,
            monthAverageText = "${Money.format(months.sumOf { it.sen } / TREND_MONTHS)} a month on average",
            categories = categories,
        )
    }

    /** Swipe delete. Hands the removed row back so the screen can offer Undo. */
    fun delete(id: Long, onDeleted: (TransactionEntity) -> Unit) {
        viewModelScope.launch { tx.deleteForUndo(id)?.let(onDeleted) }
    }

    fun restore(entity: TransactionEntity) {
        viewModelScope.launch { tx.restore(entity) }
    }

    fun selectApp(packageName: String?) {
        selectedApp.value = packageName
    }

    fun setQuery(value: String) {
        _query.value = value
    }

    /** The tab's default view: this month, every app, no search. Used when Activity is tapped again. */
    fun resetFilters() {
        selectedApp.value = null
        _query.value = ""
        month.value = MonthPeriods.current(Dates.zone, MonthStart.value)
    }

    fun previousMonth() {
        month.value = month.value.minusMonths(1)
    }

    fun nextMonth() {
        if (month.value < MonthPeriods.current(Dates.zone, MonthStart.value)) month.value = month.value.plusMonths(1)
    }
}
