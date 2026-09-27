package com.buyless.app.data.repo

import com.buyless.app.data.db.SpendingLimitDao
import com.buyless.app.data.db.SpendingLimitEntity
import com.buyless.app.data.model.LimitPeriod
import com.buyless.app.limits.Limit
import com.buyless.app.limits.SpendRow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * Reads and writes spending limits. Hands out [Limit] (not the Room entity) so the maths in
 * LimitChecker never depends on the database layer.
 */
class LimitRepository(private val dao: SpendingLimitDao) {

    fun observeLimits(): Flow<List<Limit>> = dao.observeAll().map { list -> list.mapNotNull { it.toLimit() } }.distinctUntilChanged()

    fun observeSpendRows(from: Long): Flow<List<SpendRow>> = dao.observeSpendRows(from).distinctUntilChanged()

    suspend fun all(): List<Limit> = dao.all().mapNotNull { it.toLimit() }

    suspend fun spendRows(from: Long): List<SpendRow> = dao.spendRows(from)

    /** Creates a limit (id = 0) or changes one. Keeps the original createdAt so list order stays put. */
    suspend fun save(id: Long, period: LimitPeriod, categoryKey: String, amountSen: Long, createdAt: Long = System.currentTimeMillis()) {
        // Changing an existing limit's category or period to one that already exists would clash on
        // the unique index; deleting the old row first keeps it a clean move instead of a REPLACE surprise.
        if (id != 0L) dao.delete(id)
        dao.upsert(SpendingLimitEntity(id = id, period = period.name, categoryKey = categoryKey, amountSen = amountSen, createdAt = createdAt))
    }

    suspend fun delete(id: Long) = dao.delete(id)

    /** Rows with a period name this version does not know are skipped rather than crashing. */
    private fun SpendingLimitEntity.toLimit(): Limit? =
        runCatching { LimitPeriod.valueOf(period) }.getOrNull()?.let { Limit(id, it, categoryKey, amountSen) }
}
