package com.buyless.app.ui.activity

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.buyless.app.data.model.AppKind
import com.buyless.app.data.model.Direction
import com.buyless.app.data.repo.AppsRepository
import com.buyless.app.data.repo.TransactionRepository
import com.buyless.app.ui.components.TxnUi
import com.buyless.app.ui.components.kindOf
import com.buyless.app.ui.components.toUi
import com.buyless.app.util.Dates
import com.buyless.app.util.Money
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

@Immutable
data class FilterChipUi(val packageName: String?, val label: String, val kind: AppKind?, val selected: Boolean)

@Immutable
data class DaySection(val epochDay: Long, val label: String, val totalText: String, val items: List<TxnUi>)

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

    @OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
    val state: StateFlow<ActivityUiState> = combine(
        month.flatMapLatest { ym ->
            val (from, to) = Dates.monthRange(ym)
            tx.observeRange(from, to)
        },
        apps.observeWatched(),
        selectedApp,
        _query.debounce(200),
        month,
    ) { rows, watched, selected, query, ym ->
        val today = LocalDate.now(Dates.zone)
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
            monthName = Dates.monthLabel(ym),
            canGoNext = ym < YearMonth.now(Dates.zone),
            outText = Money.format(outSen),
            inText = Money.format(inSen),
            chips = chips,
            sections = sections,
            hasFilter = selected != null || q.isNotEmpty(),
        )
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ActivityUiState())

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

    fun previousMonth() {
        month.value = month.value.minusMonths(1)
    }

    fun nextMonth() {
        if (month.value < YearMonth.now(Dates.zone)) month.value = month.value.plusMonths(1)
    }
}
