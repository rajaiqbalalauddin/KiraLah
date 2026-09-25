package com.buyless.app

import android.app.Application
import android.content.Context
import com.buyless.app.data.db.BuylessDatabase
import com.buyless.app.data.repo.AppsRepository
import com.buyless.app.data.repo.FriendsRepository
import com.buyless.app.data.repo.QrRepository
import com.buyless.app.data.repo.SampleRepository
import com.buyless.app.data.repo.SplitHistoryRepository
import com.buyless.app.data.repo.TransactionRepository
import com.buyless.app.share.PayCardFiles
import com.buyless.app.split.ReceiptScanner
import com.buyless.app.util.AppPrefs
import java.time.YearMonth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
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
    val transactions: TransactionRepository by lazy { TransactionRepository(database) }
    val apps: AppsRepository by lazy { AppsRepository(appContext, database.watchedAppDao()) }
    val prefs: AppPrefs by lazy { AppPrefs(appContext) }
    val samples: SampleRepository by lazy { SampleRepository(database.rawNotificationDao()) }
    val splitHistory: SplitHistoryRepository by lazy { SplitHistoryRepository(appContext, database.splitBillDao()) }
    val friends: FriendsRepository by lazy { FriendsRepository(database.friendDao(), database.splitBillDao()) }
    val payCards: PayCardFiles by lazy { PayCardFiles(appContext) }
    val qrs: QrRepository by lazy { QrRepository(appContext, database.paymentQrDao()) }
    val receiptScanner: ReceiptScanner by lazy { ReceiptScanner(appContext, BuildConfig.GEMINI_API_KEY) }

    /** Month being viewed. Shared so Home and Activity always show the same month. */
    val selectedMonth = MutableStateFlow(YearMonth.now())
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
