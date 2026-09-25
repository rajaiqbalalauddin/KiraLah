package com.buyless.app.data.repo

import androidx.room.withTransaction
import com.buyless.app.data.db.AppTotal
import com.buyless.app.data.db.BuylessDatabase
import com.buyless.app.data.db.PendingEntity
import com.buyless.app.data.db.TransactionEntity
import com.buyless.app.data.model.Category
import com.buyless.app.data.model.Direction
import com.buyless.app.data.model.Origin
import com.buyless.app.data.model.PendingStatus
import com.buyless.app.data.model.TransactionDraft
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Single entry point for reading and writing money records. Screens and the listener service never
 * touch DAOs directly, so rules like transfer matching and parser learning live in one place.
 */
class TransactionRepository(private val db: BuylessDatabase) {

    private val txDao = db.transactionDao()
    private val pendingDao = db.pendingDao()
    private val appDao = db.watchedAppDao()

    // Reads. distinctUntilChanged stops Room re-emitting identical results after unrelated writes,
    // which would otherwise trigger needless recomposition.

    fun observeRange(from: Long, to: Long): Flow<List<TransactionEntity>> =
        txDao.observeRange(from, to).distinctUntilChanged()

    fun observeRecent(from: Long, to: Long, limit: Int): Flow<List<TransactionEntity>> =
        txDao.observeRecent(from, to, limit).distinctUntilChanged()

    fun observeTotal(direction: Direction, from: Long, to: Long): Flow<Long> =
        txDao.observeTotal(direction.name, from, to).distinctUntilChanged()

    fun observePerApp(from: Long, to: Long): Flow<List<AppTotal>> =
        txDao.observePerApp(from, to).distinctUntilChanged()

    fun observeOpenPending(): Flow<List<PendingEntity>> = pendingDao.observeOpen().distinctUntilChanged()

    fun observeOpenPendingCount(): Flow<Int> = pendingDao.observeOpenCount().distinctUntilChanged()

    suspend fun getTransaction(id: Long): TransactionEntity? = txDao.getById(id)

    suspend fun getPending(id: Long): PendingEntity? = pendingDao.getById(id)

    suspend fun firstOpenPendingId(): Long? = pendingDao.firstOpenId()

    // Writes from the listener.

    /** Auto-record a confident parse. A repeated notification hits the unique dedupKey and is dropped. */
    suspend fun recordAuto(draft: TransactionDraft, dedupKey: String) {
        db.withTransaction {
            val id = txDao.insert(draft.toEntity(Origin.AUTO, dedupKey))
            if (id > 0 && !draft.isInternal) linkTransferPartner(id)
        }
    }

    /** Queue a notification for Quick check. Returns false when it was already seen. */
    suspend fun addPending(pending: PendingEntity): Boolean = pendingDao.insert(pending) > 0

    // Writes from the UI.

    /**
     * Save a Quick check item. If the user kept the parser's amount and direction, that counts as a
     * correct guess and moves the app one step closer to automatic recording.
     */
    suspend fun savePending(pendingId: Long, draft: TransactionDraft) {
        db.withTransaction {
            val pending = pendingDao.getById(pendingId) ?: return@withTransaction
            val id = txDao.insert(draft.toEntity(Origin.REVIEWED, pending.dedupKey))
            pendingDao.setStatus(pendingId, PendingStatus.SAVED.name)
            val guessWasRight = pending.guessAmountSen == draft.amountSen &&
                pending.guessDirection == draft.direction.name
            if (guessWasRight) appDao.incrementConfirmed(pending.sourcePackage)
            if (id > 0 && !draft.isInternal) linkTransferPartner(id)
        }
    }

    suspend fun ignorePending(pendingId: Long) {
        pendingDao.setStatus(pendingId, PendingStatus.IGNORED.name)
    }

    suspend fun addManual(draft: TransactionDraft) {
        db.withTransaction {
            val id = txDao.insert(draft.toEntity(Origin.MANUAL, null))
            if (id > 0 && !draft.isInternal) linkTransferPartner(id)
        }
    }

    /** Edit an existing row. Turning "internal" off also releases its partner so totals stay right. */
    suspend fun update(id: Long, draft: TransactionDraft) {
        db.withTransaction {
            val old = txDao.getById(id) ?: return@withTransaction
            if (old.isInternal && !draft.isInternal) {
                old.linkedId?.let { partnerId ->
                    val partner = txDao.getById(partnerId)
                    if (partner != null) txDao.clearInternal(partnerId, defaultCategory(partner.direction))
                }
            }
            txDao.update(
                old.copy(
                    amountSen = draft.amountSen,
                    direction = draft.direction.name,
                    merchant = draft.merchant,
                    category = if (draft.isInternal) Category.TRANSFER.name else draft.category.name,
                    sourcePackage = draft.sourcePackage,
                    sourceLabel = draft.sourceLabel,
                    timestamp = draft.timestamp,
                    isInternal = draft.isInternal,
                    linkedId = if (draft.isInternal) old.linkedId else null,
                ),
            )
        }
    }

    /**
     * Puts back a row removed by [delete]. If it was one half of a transfer between your own apps,
     * its partner is re-linked too, so totals end up exactly as before.
     */
    suspend fun restore(entity: TransactionEntity) {
        db.withTransaction {
            val partner = entity.linkedId?.let { txDao.getById(it) }
            txDao.restore(if (partner == null) entity.copy(isInternal = false, linkedId = null) else entity)
            if (partner != null) txDao.markInternal(partner.id, entity.id)
        }
    }

    fun observeMonths() = txDao.observeMonths().distinctUntilChanged()

    suspend fun countedInRange(from: Long, to: Long): List<TransactionEntity> = txDao.countedInRange(from, to)

    /** Deletes a row and returns it, so the caller can offer Undo. */
    suspend fun deleteForUndo(id: Long): TransactionEntity? {
        val old = txDao.getById(id) ?: return null
        delete(id)
        return old
    }

    suspend fun delete(id: Long) {
        db.withTransaction {
            val old = txDao.getById(id) ?: return@withTransaction
            old.linkedId?.let { partnerId ->
                val partner = txDao.getById(partnerId)
                if (partner != null) txDao.clearInternal(partnerId, defaultCategory(partner.direction))
            }
            txDao.delete(id)
        }
    }

    /** Drops resolved Quick check rows older than a month. Called once per app start. */
    suspend fun housekeeping(now: Long = System.currentTimeMillis()) {
        pendingDao.pruneResolved(now - 30L * 24 * 60 * 60 * 1000)
    }

    /**
     * Money moved between the user's own apps shows up twice: out of one, into another. Pair them
     * (same amount, opposite direction, within 10 minutes) and exclude both from totals.
     */
    private suspend fun linkTransferPartner(id: Long) {
        val tx = txDao.getById(id) ?: return
        val opposite = if (tx.direction == Direction.OUT.name) Direction.IN.name else Direction.OUT.name
        val partner = txDao.findTransferPartner(
            amountSen = tx.amountSen,
            direction = opposite,
            excludePackage = tx.sourcePackage,
            excludeId = tx.id,
            from = tx.timestamp - TRANSFER_WINDOW_MS,
            to = tx.timestamp + TRANSFER_WINDOW_MS,
            around = tx.timestamp,
        ) ?: return
        txDao.markInternal(tx.id, partner.id)
        txDao.markInternal(partner.id, tx.id)
    }

    private fun defaultCategory(direction: String): String =
        if (direction == Direction.IN.name) Category.INCOME.name else Category.OTHER.name

    private fun TransactionDraft.toEntity(origin: Origin, dedupKey: String?) = TransactionEntity(
        amountSen = amountSen,
        direction = direction.name,
        merchant = merchant,
        category = if (isInternal) Category.TRANSFER.name else category.name,
        sourcePackage = sourcePackage,
        sourceLabel = sourceLabel,
        timestamp = timestamp,
        isInternal = isInternal,
        origin = origin.name,
        rawText = rawText,
        dedupKey = dedupKey,
    )

    private companion object {
        const val TRANSFER_WINDOW_MS = 10 * 60 * 1000L
    }
}
