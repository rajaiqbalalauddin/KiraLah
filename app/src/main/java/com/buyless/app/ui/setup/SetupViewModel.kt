package com.buyless.app.ui.setup

import android.app.Application
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.buyless.app.data.model.AppKind
import com.buyless.app.data.repo.AppsRepository
import com.buyless.app.data.repo.InstalledApp
import com.buyless.app.ui.components.kindOf
import com.buyless.app.util.AppPrefs
import com.buyless.app.util.SystemAccess
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@Immutable
data class PermissionState(val notificationAccess: Boolean = false, val batteryExempt: Boolean = false)

@Immutable
data class SetupAppUi(val packageName: String, val label: String, val kind: AppKind, val checked: Boolean)

@Immutable
data class SetupUiState(
    val permissions: PermissionState = PermissionState(),
    val apps: List<SetupAppUi> = emptyList(),
    val loadingApps: Boolean = true,
) {
    /** Battery exemption is recommended, not required, so it does not block starting. */
    val canStart: Boolean get() = permissions.notificationAccess && apps.any { it.checked }
}

/**
 * First-run flow. Permission status is re-checked every time the screen resumes, because the user
 * grants access in the system Settings app and comes back; there is no callback for that.
 */
class SetupViewModel(
    private val app: Application,
    private val apps: AppsRepository,
    private val prefs: AppPrefs,
) : ViewModel() {

    private val permissions = MutableStateFlow(PermissionState())
    private val installed = MutableStateFlow<List<InstalledApp>?>(null)

    init {
        refreshPermissions()
        viewModelScope.launch {
            installed.value = apps.installedApps()
            if (!prefs.onboardingDone) apps.seedDefaultsIfEmpty()
        }
    }

    val state: StateFlow<SetupUiState> = combine(permissions, installed, apps.observeWatched()) { perms, all, watched ->
        val watchedPkgs = watched.mapTo(HashSet()) { it.packageName }
        // Watched apps first (checked), then money apps found on the phone as unchecked suggestions.
        val rows = watched.map { SetupAppUi(it.packageName, it.label, kindOf(it.kind), true) } +
            (all ?: emptyList()).filter { it.isMoneyApp && it.packageName !in watchedPkgs }
                .map { SetupAppUi(it.packageName, it.label, it.kind, false) }
        SetupUiState(perms, rows, loadingApps = all == null)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SetupUiState())

    fun refreshPermissions() {
        permissions.value = PermissionState(
            notificationAccess = SystemAccess.hasNotificationAccess(app),
            batteryExempt = SystemAccess.isIgnoringBatteryOptimisation(app),
        )
    }

    fun toggle(row: SetupAppUi) {
        viewModelScope.launch {
            if (row.checked) {
                apps.unwatch(row.packageName)
            } else {
                installed.value?.firstOrNull { it.packageName == row.packageName }?.let { apps.watch(it) }
            }
        }
    }

    fun finish() {
        prefs.onboardingDone = true
    }
}
