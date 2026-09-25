package com.buyless.app.ui.apps

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.buyless.app.data.model.AppKind
import com.buyless.app.data.model.LEARNING_THRESHOLD
import com.buyless.app.data.repo.AppsRepository
import com.buyless.app.data.repo.InstalledApp
import com.buyless.app.ui.components.kindOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@Immutable
data class AppRowUi(
    val packageName: String,
    val label: String,
    val kind: AppKind,
    val subtitle: String,
    val watched: Boolean,
    val learning: Boolean,
)

@Immutable
data class AppsUiState(
    val loading: Boolean = true,
    val watching: List<AppRowUi> = emptyList(),
    val suggestions: List<AppRowUi> = emptyList(),
    val others: List<AppRowUi> = emptyList(),
    val results: List<AppRowUi>? = null,
)

/**
 * Manages which apps Buyless listens to. The installed-app scan is cached by the repository, so
 * opening this screen again is instant; search filters that cached list in memory.
 */
class AppsViewModel(private val apps: AppsRepository) : ViewModel() {

    private val installed = MutableStateFlow<List<InstalledApp>?>(null)
    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    init {
        viewModelScope.launch { installed.value = apps.installedApps() }
    }

    @OptIn(FlowPreview::class)
    val state: StateFlow<AppsUiState> = combine(
        installed,
        apps.observeWatched(),
        _query.debounce(150),
    ) { all, watched, query ->
        val watchedPkgs = watched.associateBy { it.packageName }
        val watching = watched.map { w ->
            val learning = w.confirmedCount < LEARNING_THRESHOLD
            AppRowUi(
                packageName = w.packageName,
                label = w.label,
                kind = kindOf(w.kind),
                subtitle = if (learning) "Learning · ${w.confirmedCount} of $LEARNING_THRESHOLD confirmed" else "Recording automatically",
                watched = true,
                learning = learning,
            )
        }
        val candidates = (all ?: emptyList()).filter { it.packageName !in watchedPkgs }
        val toRow = { a: InstalledApp -> AppRowUi(a.packageName, a.label, a.kind, if (a.isMoneyApp) "Money app" else "Other app", false, false) }
        val q = query.trim()
        AppsUiState(
            loading = all == null,
            watching = watching,
            suggestions = candidates.filter { it.isMoneyApp }.map(toRow),
            others = candidates.filter { !it.isMoneyApp }.map(toRow),
            results = if (q.isEmpty()) {
                null
            } else {
                (all ?: emptyList())
                    .filter { it.label.contains(q, ignoreCase = true) }
                    .map { a ->
                        val w = watchedPkgs[a.packageName]
                        if (w != null) watching.first { it.packageName == a.packageName } else toRow(a)
                    }
            },
        )
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppsUiState())

    fun setQuery(value: String) {
        _query.value = value
    }

    fun toggle(row: AppRowUi) {
        viewModelScope.launch {
            if (row.watched) {
                apps.unwatch(row.packageName)
            } else {
                val app = installed.value?.firstOrNull { it.packageName == row.packageName } ?: return@launch
                apps.watch(app)
            }
        }
    }
}
