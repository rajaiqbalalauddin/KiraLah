package com.buyless.app.data.repo

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import com.buyless.app.data.db.AppBalance
import com.buyless.app.data.db.WatchedAppDao
import com.buyless.app.data.db.WatchedAppEntity
import com.buyless.app.data.model.AppKind
import com.buyless.app.util.KnownApps
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** A launchable app on the phone, as shown in the Apps screen. */
data class InstalledApp(
    val packageName: String,
    val label: String,
    val kind: AppKind,
    val isMoneyApp: Boolean,
    val isDefault: Boolean,
)

/**
 * Knows which apps exist on the phone and which ones the user watches.
 *
 * Two caches keep this cheap:
 * - The installed-app scan hits PackageManager (slow IPC), so it runs once and is reused until refresh.
 * - App icons are decoded to bitmaps once and held in a size-bounded LruCache, so scrolling lists
 *   never decode the same icon twice and memory stays capped.
 */
class AppsRepository(
    private val context: Context,
    private val dao: WatchedAppDao,
) {
    private val pm: PackageManager = context.packageManager
    private val scanLock = Mutex()

    @Volatile
    private var installedCache: List<InstalledApp>? = null

    // 1/32 of the app heap, measured in KB. Enough for a few hundred small icons.
    private val iconCache = object : LruCache<String, ImageBitmap>(
        (Runtime.getRuntime().maxMemory() / 1024 / 32).toInt(),
    ) {
        override fun sizeOf(key: String, value: ImageBitmap): Int = maxOf(1, value.width * value.height * 4 / 1024)
    }

    fun observeWatched(): Flow<List<WatchedAppEntity>> = dao.observeAll().distinctUntilChanged()

    suspend fun installedApps(forceRefresh: Boolean = false): List<InstalledApp> = scanLock.withLock {
        val cached = installedCache
        if (cached != null && !forceRefresh) return@withLock cached
        withContext(Dispatchers.IO) { scan() }.also { installedCache = it }
    }

    /** Cached icon lookup. Returns null when the app is gone, and the UI shows a tile instead. */
    fun cachedIcon(packageName: String): ImageBitmap? = iconCache.get(packageName)

    suspend fun loadIcon(packageName: String, sizePx: Int): ImageBitmap? {
        iconCache.get(packageName)?.let { return it }
        val bitmap = withContext(Dispatchers.IO) {
            try {
                pm.getApplicationIcon(packageName).toBitmap(sizePx, sizePx).asImageBitmap()
            } catch (e: PackageManager.NameNotFoundException) {
                null
            }
        }
        if (bitmap != null) iconCache.put(packageName, bitmap)
        return bitmap
    }

    suspend fun watch(app: InstalledApp) {
        dao.upsert(
            WatchedAppEntity(
                packageName = app.packageName,
                label = app.label,
                kind = app.kind.name,
                addedAt = System.currentTimeMillis(),
            ),
        )
    }

    suspend fun unwatch(packageName: String) = dao.delete(packageName)

    fun observeBalances(): Flow<List<AppBalance>> = dao.observeBalances().distinctUntilChanged()

    /** Records what the app shows right now as the new starting point. Null clears it. */
    suspend fun setBalance(packageName: String, balanceSen: Long?) =
        dao.setBalance(packageName, balanceSen, balanceSen?.let { System.currentTimeMillis() })

    /** First run only: pre-select MAE, Bank Islam and TNG if they are installed. */
    suspend fun seedDefaultsIfEmpty() {
        if (dao.count() > 0) return
        installedApps().filter { it.isDefault }.forEach { watch(it) }
    }

    private fun scan(): List<InstalledApp> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val infos = if (Build.VERSION.SDK_INT >= 33) {
            pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0L))
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(intent, 0)
        }
        val seen = HashSet<String>(infos.size)
        val result = ArrayList<InstalledApp>(infos.size)
        for (info in infos) {
            val pkg = info.activityInfo.packageName
            if (pkg == context.packageName || !seen.add(pkg)) continue
            val label = info.loadLabel(pm).toString()
            val match = KnownApps.match(pkg, label)
            result += InstalledApp(
                packageName = pkg,
                label = label,
                kind = match?.kind ?: AppKind.WALLET,
                isMoneyApp = match != null,
                isDefault = match?.isDefault == true,
            )
        }
        result.sortWith(compareByDescending<InstalledApp> { it.isMoneyApp }.thenBy { it.label.lowercase() })
        return result
    }
}
