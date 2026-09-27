package com.buyless.app.limits

import com.buyless.app.data.db.CustomCategoryDao
import com.buyless.app.data.db.TransactionEntity
import com.buyless.app.data.model.ALL_CATEGORIES
import com.buyless.app.data.model.CategoryKeys
import com.buyless.app.data.model.Direction
import com.buyless.app.data.repo.LimitRepository
import com.buyless.app.util.Dates
import kotlinx.coroutines.flow.first

/**
 * Glue between "a payment was saved" and "tell the user". TransactionRepository calls [onSpent]
 * after every new payment out; this loads the limits, asks LimitChecker what tripped, and hands
 * the result to LimitNotifier. Kept out of the repository so money records stay free of UI concerns.
 */
class LimitAlerts(
    private val limits: LimitRepository,
    private val customCategories: CustomCategoryDao,
    private val notifier: LimitNotifier,
) {

    suspend fun onSpent(tx: TransactionEntity, now: Long = System.currentTimeMillis()) {
        if (tx.direction != Direction.OUT.name || tx.isInternal) return
        val all = limits.all()
        if (all.isEmpty()) return // the common case costs one tiny query
        val zone = Dates.zone
        val rows = limits.spendRows(LimitPeriods.earliestStart(now, zone))
        val hits = LimitChecker.check(all, rows, SpendRow(tx.category, tx.amountSen, tx.timestamp), now, zone)
        if (hits.isEmpty()) return
        val custom = customCategories.observeAll().first().associate { CategoryKeys.custom(it.id) to it.name }
        notifier.post(tx.id, tx.merchant, tx.amountSen, hits) { key -> labelOf(key, custom) }
    }

    companion object {
        /** Display name for a limit's category. The all-categories limit reads as "spending". */
        fun labelOf(key: String, custom: Map<String, String>): String = when {
            key == ALL_CATEGORIES -> "spending"
            CategoryKeys.builtIn(key) != null -> key.lowercase().replaceFirstChar { it.uppercase() }
            else -> custom[key] ?: "Other"
        }
    }
}
