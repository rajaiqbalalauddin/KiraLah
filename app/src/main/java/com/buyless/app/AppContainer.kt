package com.buyless.app

import android.app.Application
import android.content.Context
import com.buyless.app.data.db.BuylessDatabase
import com.buyless.app.data.repo.AppsRepository
import com.buyless.app.data.repo.CategoryRepository
import com.buyless.app.data.repo.FriendsRepository
import com.buyless.app.data.repo.LimitRepository
import com.buyless.app.data.repo.QrRepository
import com.buyless.app.data.repo.SampleRepository
import com.buyless.app.data.repo.SplitHistoryRepository
import com.buyless.app.data.repo.TransactionRepository
import com.buyless.app.limits.LimitAlerts
import com.buyless.app.limits.LimitNotifier
import com.buyless.app.share.PayCardFiles
import com.buyless.app.split.ReceiptScanner
import com.buyless.app.util.AppPrefs
import com.buyless.app.util.Dates
import com.buyless.app.util.MonthPeriods
import com.buyless.app.util.MonthStart
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

/**
 * Manual dependency injection. One shared instance of each repository for the whole process, created
 * lazily so app start only pays for what the first screen actually uses. Chosen over Hilt to keep
 * the build simple for a single-module app.
 */
class AppContainer(val application: Application) {
    private val appContext: Context = application

    /** Process-wide scope for fire-and-forget work that must outlive a screen (e.g. housekeeping). */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val database: BuylessDatabase by lazy { BuylessDatabase.build(appContext) }
    val transactions: TransactionRepository by lazy { TransactionRepository(database) { limitAlerts.onSpent(it) } }
    val limits: LimitRepository by lazy { LimitRepository(database.spendingLimitDao()) }
    val limitNotifier: LimitNotifier by lazy { LimitNotifier(appContext) }
    val limitAlerts: LimitAlerts by lazy { LimitAlerts(limits, database.customCategoryDao(), limitNotifier) }
    val apps: AppsRepository by lazy { AppsRepository(appContext, database.watchedAppDao()) }
    val prefs: AppPrefs by lazy { AppPrefs(appContext) }
    val categories: CategoryRepository by lazy { CategoryRepository(database) }
    val samples: SampleRepository by lazy { SampleRepository(database.rawNotificationDao()) }
    val splitHistory: SplitHistoryRepository by lazy { SplitHistoryRepository(appContext, database.splitBillDao()) }
    val friends: FriendsRepository by lazy { FriendsRepository(database.friendDao(), database.splitBillDao()) }
    val payCards: PayCardFiles by lazy { PayCardFiles(appContext) }
    val qrs: QrRepository by lazy { QrRepository(appContext, database.paymentQrDao()) }
    val receiptScanner: ReceiptScanner by lazy { ReceiptScanner(appContext, BuildConfig.GEMINI_API_KEY) }

    // The month start day must be known before anything works out "this month", including the line below.
    init {
        MonthStart.set(prefs.monthStartDay)
    }

    /**
     * Month being viewed, as the period that starts in it (see MonthPeriods). Shared so Home and
     * Activity always show the same month.
     */
    val selectedMonth = MutableStateFlow(MonthPeriods.current(Dates.zone, MonthStart.value))

    /**
     * The period that was "this month" the last time we looked. The notification listener keeps this
     * process alive for days, so without this the tabs stayed on last month after a new one began and
     * fresh entries (which belong to the new month) seemed to vanish while app balances still moved.
     */
    @Volatile
    private var lastCurrentMonth = selectedMonth.value

    init {
        // A new start day reshapes every period, so both tabs jump back to the current one.
        appScope.launch {
            MonthStart.day.drop(1).collect {
                val now = MonthPeriods.current(Dates.zone, it)
                lastCurrentMonth = now
                selectedMonth.value = now
            }
        }
    }

    /**
     * Called whenever the app comes to the front. If a new month has started since we last looked and
     * the user was on the current month, both tabs move to the new one. A past month the user picked
     * on purpose is left alone.
     */
    fun rollToCurrentMonth() {
        val now = MonthPeriods.current(Dates.zone, MonthStart.value)
        if (now == lastCurrentMonth) return
        if (selectedMonth.value == lastCurrentMonth) selectedMonth.value = now
        lastCurrentMonth = now
    }
}

/** Application class that owns the container, so the Activity and the listener service share it. */
class BuylessApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        // Off the main thread so it never delays the first frame.
        container.appScope.launch {
            container.transactions.housekeeping()
            container.splitHistory.cleanOrphanPhotos()
            container.friends.backfillFromHistory()
            if (!container.prefs.qrsCropped) {
                runCatching { container.qrs.recropSaved() }.onSuccess { container.prefs.qrsCropped = true }
            }
        }
    }
}

/** Shortcut used by ViewModel factories and the service. */
val Context.container: AppContainer get() = (applicationContext as BuylessApp).container
