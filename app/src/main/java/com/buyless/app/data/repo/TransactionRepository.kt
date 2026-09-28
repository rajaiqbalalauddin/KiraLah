package com.buyless.app.data.repo

import androidx.room.withTransaction
import com.buyless.app.data.db.AppTotal
import com.buyless.app.data.db.BuylessDatabase
import com.buyless.app.data.db.CategoryRuleEntity
import com.buyless.app.data.db.PendingEntity
import com.buyless.app.data.db.TransactionEntity
import com.buyless.app.data.model.Category
import com.buyless.app.data.model.CategoryRules
import com.buyless.app.data.model.Direction
import com.buyless.app.data.model.MANUAL_SOURCE
import com.buyless.app.data.model.Origin
import com.buyless.app.data.model.PendingStatus
import com.buyless.app.data.model.TransactionDraft
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Single entry point for reading and writing money records. Screens and the listener service never
 * touch DAOs directly, so rules like transfer matching, remembered categories and parser learning
 * live in one place.
 */
class TransactionRepository(
    private val db: BuylessDatabase,
    /**
     * Called after a new payment is saved (auto, Quick check or manual), outside the database
     * transaction and after transfer pairing, so a move between your own apps is already marked
     * internal. Used for spending-limit alerts; a failure here never undoes the save.
     */
    private val afterRecord: suspend (TransactionEntity) -> Unit = {},
) {

    private val txDao = db.transactionDao()
    private val pendingDao = db.pendingDao()
    private val appDao = db.watchedAppDao()
    private val ruleDao = db.categoryRuleDao()

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

    // Remembered categories.

    /** The category the user asked to always use for this merchant and direction, or null. */
    suspend fun rememberedCategory(merchant: String?, direction: String?): String? {
        val key = merchant?.let(CategoryRules::key) ?: return null
        if (direction == null) return null
        return ruleDao.find(key, direction)?.category
    }

    /**
     * "Every <merchant> entry": future entries from this merchant get this category. Past entries
     * are left as they are on purpose, so old months never change behind the user's back.
     */
    suspend fun rememberCategory(merchant: String, direction: Direction, category: String) {
        val key = CategoryRules.key(merchant) ?: return
        if (category == Category.TRANSFER.name) return
        ruleDao.upsert(
            CategoryRuleEntity(
                merchantKey = key,
                merchant = merchant.trim(),
                direction = direction.name,
                category = category,
                createdAt = System.currentTimeMillis(),
            ),
        )
    }

    // Writes from the listener.

    /** Auto-record a confident parse. A repeated notification hits the unique dedupKey and is dropped. */
    suspend fun recordAuto(draft: TransactionDraft, dedupKey: String) {
        val id = db.withTransaction {
            val remembered = if (draft.isInternal) null else rememberedCategory(draft.merchant, draft.direction.name)
            val toSave = if (remembered != null) draft.copy(category = remembered) else draft
            val id = txDao.insert(toSave.toEntity(Origin.AUTO, dedupKey))
            if (id > 0) linkAfterInsert(id)
            id
        }
        afterInsert(id)
    }

    /**
     * Queue a notification for Quick check. A remembered category replaces the parser's guess, so the
     * right chip is already selected. Returns false when it was already seen.
     */
    suspend fun addPending(pending: PendingEntity): Boolean {
        val remembered = rememberedCategory(pending.guessMerchant, pending.guessDirection)
        return pendingDao.insert(if (remembered != null) pending.copy(guessCategory = remembered) else pending) > 0
    }

    // Writes from the UI.

    /**
     * Save a Quick check item. If the user kept the parser's amount and direction, that counts as a
     * correct guess and moves the app one step closer to automatic recording.
     */
    suspend fun savePending(pendingId: Long, draft: TransactionDraft) {
        val id = db.withTransaction {
            val pending = pendingDao.getById(pendingId) ?: return@withTransaction -1L
            val id = txDao.insert(draft.toEntity(Origin.REVIEWED, pending.dedupKey))
            pendingDao.setStatus(pendingId, PendingStatus.SAVED.name)
            val guessWasRight = pending.guessAmountSen == draft.amountSen &&
                pending.guessDirection == draft.direction.name
            if (guessWasRight) appDao.incrementConfirmed(pending.sourcePackage)
            if (id > 0) linkAfterInsert(id)
            id
        }
        afterInsert(id)
    }

    suspend fun ignorePending(pendingId: Long) {
        pendingDao.setStatus(pendingId, PendingStatus.IGNORED.name)
    }

    suspend fun addManual(draft: TransactionDraft) {
        val id = db.withTransaction {
            val id = txDao.insert(draft.toEntity(Origin.MANUAL, null))
            if (id > 0) linkAfterInsert(id)
            id
        }
        afterInsert(id)
    }

    /** Re-reads the saved row (pairing may have changed it) and passes it on. -1 = nothing was inserted. */
    private suspend fun afterInsert(id: Long) {
        if (id <= 0) return
        val saved = txDao.getById(id) ?: return
        runCatching { afterRecord(saved) }
    }

    /**
     * Edit an existing row. If anything that ties a transfer to its other half changed (the other app,
     * amount, direction, source or time), the old link is released and a fresh one is made, so the
     * stand-in entry on the other app always matches.
     */
    suspend fun update(id: Long, draft: TransactionDraft) {
        db.withTransaction {
            val old = txDao.getById(id) ?: return@withTransaction
            val counterpart = if (draft.isInternal) draft.counterpartPackage else null
            val linkChanged = !draft.isInternal || !old.isInternal ||
                old.counterpartPackage != counterpart ||
                old.amountSen != draft.amountSen ||
                old.direction != draft.direction.name ||
                old.sourcePackage != draft.sourcePackage ||
                old.timestamp != draft.timestamp
            if (linkChanged) releasePartner(old)
            val updated = old.copy(
                amountSen = draft.amountSen,
                direction = draft.direction.name,
                merchant = draft.merchant,
                category = if (draft.isInternal) Category.TRANSFER.name else draft.category,
                sourcePackage = draft.sourcePackage,
                sourceLabel = draft.sourceLabel,
                timestamp = draft.timestamp,
                isInternal = draft.isInternal,
                linkedId = if (draft.isInternal && !linkChanged) old.linkedId else null,
                counterpartPackage = counterpart,
                counterpartLabel = if (draft.isInternal) draft.counterpartLabel else null,
                // Once the user edits a stand-in it is theirs, so a later notification will not replace it.
                origin = if (old.origin == Origin.MIRROR.name) Origin.MANUAL.name else old.origin,
            )
            txDao.update(updated)
            // An edited stand-in must not spawn a stand-in of its own on the original app.
            if (draft.isInternal && linkChanged) linkOrMirror(updated, allowMirror = old.origin != Origin.MIRROR.name)
        }
    }

    /**
     * Puts back a row removed by [delete]. If it was one half of a transfer between your own apps,
     * its partner is re-linked (or its stand-in re-made), so totals and balances end up as before.
     */
    suspend fun restore(entity: TransactionEntity) {
        db.withTransaction {
            val partner = entity.linkedId?.let { txDao.getById(it) }
            when {
                partner != null -> {
                    txDao.restore(entity)
                    txDao.markInternal(partner.id, entity.id, entity.sourcePackage, entity.sourceLabel)
                }
                entity.isInternal && entity.counterpartPackage != null && entity.origin != Origin.MIRROR.name -> {
                    val unlinked = entity.copy(linkedId = null)
                    txDao.restore(unlinked)
                    linkOrMirror(unlinked)
                }
                else -> txDao.restore(entity.copy(isInternal = false, linkedId = null, counterpartPackage = null, counterpartLabel = null))
            }
        }
    }

    fun observeDays() = txDao.observeDays().distinctUntilChanged()

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
            releasePartner(old)
            txDao.delete(id)
        }
    }

    /** Drops resolved Quick check rows older than a month. Called once per app start. */
    suspend fun housekeeping(now: Long = System.currentTimeMillis()) {
        pendingDao.pruneResolved(now - 30L * 24 * 60 * 60 * 1000)
    }

    // Transfers between your own apps.

    /**
     * Runs after every insert. A declared transfer looks for its other half (or adds a stand-in).
     * Anything else first checks whether it IS the other half of a transfer already waiting for it,
     * then falls back to automatic pairing.
     */
    private suspend fun linkAfterInsert(id: Long) {
        val tx = txDao.getById(id) ?: return
        if (tx.isInternal) {
            linkOrMirror(tx)
            return
        }
        if (absorbMirror(tx)) return
        if (joinWaitingTransfer(tx)) return
        linkTransferPartner(tx)
    }

    /**
     * The user said "this went to TNG". Link to TNG's own entry if there is one. If not, add a
     * stand-in money-in entry on TNG so its balance is right; TNG's real alert replaces it later.
     * Cash has no balance to keep, so it gets no stand-in.
     */
    private suspend fun linkOrMirror(tx: TransactionEntity, allowMirror: Boolean = true) {
        val other = tx.counterpartPackage ?: return
        val opposite = opposite(tx.direction)
        val partner = txDao.findPartnerIn(
            amountSen = tx.amountSen,
            direction = opposite,
            inPackage = other,
            fromPackage = tx.sourcePackage,
            excludeId = tx.id,
            from = tx.timestamp - DECLARED_WINDOW_MS,
            to = tx.timestamp + DECLARED_WINDOW_MS,
            around = tx.timestamp,
        )
        if (partner != null) {
            link(tx, partner)
            return
        }
        if (!allowMirror || other == MANUAL_SOURCE) return
        val mirrorId = txDao.insert(
            TransactionEntity(
                amountSen = tx.amountSen,
                direction = opposite,
                merchant = if (opposite == Direction.IN.name) "From ${tx.sourceLabel}" else "To ${tx.sourceLabel}",
                category = Category.TRANSFER.name,
                sourcePackage = other,
                sourceLabel = tx.counterpartLabel ?: other,
                timestamp = tx.timestamp,
                isInternal = true,
                linkedId = tx.id,
                origin = Origin.MIRROR.name,
                counterpartPackage = tx.sourcePackage,
                counterpartLabel = tx.sourceLabel,
            ),
        )
        if (mirrorId > 0) txDao.setLinked(tx.id, mirrorId)
    }

    /** The real alert for a transfer arrived: it takes the stand-in's place. Returns true if it did. */
    private suspend fun absorbMirror(tx: TransactionEntity): Boolean {
        val mirror = txDao.findMirror(
            amountSen = tx.amountSen,
            direction = tx.direction,
            inPackage = tx.sourcePackage,
            excludeId = tx.id,
            from = tx.timestamp - DECLARED_WINDOW_MS,
            to = tx.timestamp + DECLARED_WINDOW_MS,
            around = tx.timestamp,
        ) ?: return false
        val original = mirror.linkedId?.let { txDao.getById(it) }
        txDao.delete(mirror.id)
        if (original == null) return false
        link(tx, original)
        return true
    }

    /** A declared transfer with no other half yet (its stand-in was deleted) claims this entry. */
    private suspend fun joinWaitingTransfer(tx: TransactionEntity): Boolean {
        val waiting = txDao.findWaitingTransfer(
            amountSen = tx.amountSen,
            direction = opposite(tx.direction),
            counterpart = tx.sourcePackage,
            excludeId = tx.id,
            from = tx.timestamp - DECLARED_WINDOW_MS,
            to = tx.timestamp + DECLARED_WINDOW_MS,
            around = tx.timestamp,
        ) ?: return false
        link(tx, waiting)
        return true
    }

    /**
     * Money moved between the user's own apps shows up twice: out of one, into another. Pair them
     * (same amount, opposite direction, within 10 minutes) and exclude both from totals.
     */
    private suspend fun linkTransferPartner(tx: TransactionEntity) {
        val partner = txDao.findTransferPartner(
            amountSen = tx.amountSen,
            direction = opposite(tx.direction),
            excludePackage = tx.sourcePackage,
            excludeId = tx.id,
            from = tx.timestamp - TRANSFER_WINDOW_MS,
            to = tx.timestamp + TRANSFER_WINDOW_MS,
            around = tx.timestamp,
        ) ?: return
        link(tx, partner)
    }

    /** Marks two rows as the two halves of one transfer, each naming the other's app. */
    private suspend fun link(a: TransactionEntity, b: TransactionEntity) {
        txDao.markInternal(a.id, b.id, b.sourcePackage, b.sourceLabel)
        txDao.markInternal(b.id, a.id, a.sourcePackage, a.sourceLabel)
    }

    /**
     * Undoes a row's link before it is deleted or re-linked. A stand-in goes with its original. If the
     * row being removed is itself a stand-in, the original stays a transfer (the user did say where
     * the money went). A real partner goes back to being ordinary spending or income.
     */
    private suspend fun releasePartner(old: TransactionEntity) {
        val partner = old.linkedId?.let { txDao.getById(it) } ?: return
        when {
            partner.origin == Origin.MIRROR.name -> txDao.delete(partner.id)
            old.origin == Origin.MIRROR.name -> txDao.setLinked(partner.id, null)
            else -> txDao.clearInternal(partner.id, defaultCategory(partner.direction))
        }
    }

    private fun opposite(direction: String): String =
        if (direction == Direction.OUT.name) Direction.IN.name else Direction.OUT.name

    private fun defaultCategory(direction: String): String =
        if (direction == Direction.IN.name) Category.INCOME.name else Category.OTHER.name

    private fun TransactionDraft.toEntity(origin: Origin, dedupKey: String?) = TransactionEntity(
        amountSen = amountSen,
        direction = direction.name,
        merchant = merchant,
        category = if (isInternal) Category.TRANSFER.name else category,
        sourcePackage = sourcePackage,
        sourceLabel = sourceLabel,
        timestamp = timestamp,
        isInternal = isInternal,
        origin = origin.name,
        rawText = rawText,
        dedupKey = dedupKey,
        counterpartPackage = if (isInternal) counterpartPackage else null,
        counterpartLabel = if (isInternal) counterpartLabel else null,
    )

    private companion object {
        /** Automatic pairing only trusts alerts that land close together. */
        const val TRANSFER_WINDOW_MS = 10 * 60 * 1000L

        /** When the user names the other app, a whole day either side is safe to search. */
        const val DECLARED_WINDOW_MS = 24 * 60 * 60 * 1000L
    }
}
