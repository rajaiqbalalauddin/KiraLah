package com.buyless.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

// Data access objects. Totals are computed with SQL aggregates so only a single number crosses into
// Kotlin, instead of loading every row just to add them up.

@Dao
interface TransactionDao {

    /** Every transaction in a time window, newest first. Drives the Activity list. */
    @Query("SELECT * FROM transactions WHERE timestamp >= :from AND timestamp < :to ORDER BY timestamp DESC")
    fun observeRange(from: Long, to: Long): Flow<List<TransactionEntity>>

    /** The latest few rows for the Home preview. LIMIT keeps it cheap however big the table grows. */
    @Query("SELECT * FROM transactions WHERE timestamp >= :from AND timestamp < :to ORDER BY timestamp DESC LIMIT :limit")
    fun observeRecent(from: Long, to: Long, limit: Int): Flow<List<TransactionEntity>>

    /** Sum for one direction, skipping transfers between the user's own apps. */
    @Query(
        "SELECT COALESCE(SUM(amountSen), 0) FROM transactions " +
            "WHERE direction = :direction AND isInternal = 0 AND timestamp >= :from AND timestamp < :to",
    )
    fun observeTotal(direction: String, from: Long, to: Long): Flow<Long>

    /** Spending per source app for the Home tiles. */
    @Query(
        "SELECT sourcePackage, COALESCE(SUM(amountSen), 0) AS outSen, COUNT(*) AS count FROM transactions " +
            "WHERE direction = 'OUT' AND isInternal = 0 AND timestamp >= :from AND timestamp < :to " +
            "GROUP BY sourcePackage",
    )
    fun observePerApp(from: Long, to: Long): Flow<List<AppTotal>>

    /** Returns -1 when the dedupKey already exists, which is how repeated notifications are dropped. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entity: TransactionEntity): Long

    @Update
    suspend fun update(entity: TransactionEntity)

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun getById(id: Long): TransactionEntity?

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun delete(id: Long)

    /** Re-inserts a row exactly as it was (same id), used by Undo after a swipe delete. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun restore(entity: TransactionEntity)

    /** Every counted transaction in a window, oldest first. Feeds the Recap stories. */
    @Query("SELECT * FROM transactions WHERE isInternal = 0 AND timestamp >= :from AND timestamp < :to ORDER BY timestamp")
    suspend fun countedInRange(from: Long, to: Long): List<TransactionEntity>

    /** Month-by-month spending for the Recap archive, grouped by local calendar month in SQL. */
    @Query(
        "SELECT strftime('%Y-%m', timestamp / 1000, 'unixepoch', 'localtime') AS ym, " +
            "COALESCE(SUM(CASE WHEN direction = 'OUT' THEN amountSen ELSE 0 END), 0) AS outSen, COUNT(*) AS count " +
            "FROM transactions WHERE isInternal = 0 GROUP BY ym ORDER BY ym DESC",
    )
    fun observeMonths(): Flow<List<MonthSummary>>

    /**
     * Finds the other half of a transfer between the user's own apps: same amount, opposite
     * direction, a different app, close in time. Closest in time wins.
     */
    @Query(
        "SELECT * FROM transactions WHERE amountSen = :amountSen AND direction = :direction " +
            "AND sourcePackage != :excludePackage AND isInternal = 0 AND id != :excludeId " +
            "AND timestamp BETWEEN :from AND :to ORDER BY ABS(timestamp - :around) LIMIT 1",
    )
    suspend fun findTransferPartner(
        amountSen: Long,
        direction: String,
        excludePackage: String,
        excludeId: Long,
        from: Long,
        to: Long,
        around: Long,
    ): TransactionEntity?

    @Query("UPDATE transactions SET isInternal = 1, linkedId = :partnerId, category = 'TRANSFER' WHERE id = :id")
    suspend fun markInternal(id: Long, partnerId: Long)

    @Query("UPDATE transactions SET isInternal = 0, linkedId = NULL, category = :category WHERE id = :id")
    suspend fun clearInternal(id: Long, category: String)
}

@Dao
interface WatchedAppDao {

    @Query("SELECT * FROM watched_apps ORDER BY addedAt")
    fun observeAll(): Flow<List<WatchedAppEntity>>

    @Query("SELECT COUNT(*) FROM watched_apps")
    suspend fun count(): Int

    @Upsert
    suspend fun upsert(app: WatchedAppEntity)

    @Query("DELETE FROM watched_apps WHERE packageName = :packageName")
    suspend fun delete(packageName: String)

    @Query("UPDATE watched_apps SET confirmedCount = confirmedCount + 1 WHERE packageName = :packageName")
    suspend fun incrementConfirmed(packageName: String)

    /** Sets the starting point for an app's balance. No transaction is created. */
    @Query("UPDATE watched_apps SET balanceSen = :balanceSen, balanceSetAt = :at WHERE packageName = :packageName")
    suspend fun setBalance(packageName: String, balanceSen: Long?, at: Long?)

    /**
     * Live balance per app. Transfers between your own apps count here (unlike in spending totals),
     * because the money really did leave one app and land in another.
     */
    @Query(
        "SELECT w.packageName AS packageName, w.balanceSen + COALESCE(SUM(CASE WHEN t.direction = 'IN' THEN t.amountSen ELSE -t.amountSen END), 0) AS balanceSen " +
            "FROM watched_apps w LEFT JOIN transactions t ON t.sourcePackage = w.packageName AND t.timestamp >= w.balanceSetAt " +
            "WHERE w.balanceSen IS NOT NULL GROUP BY w.packageName",
    )
    fun observeBalances(): Flow<List<AppBalance>>
}

@Dao
interface PendingDao {

    /** Open items, oldest first, so Quick check works through them in the order they arrived. */
    @Query("SELECT * FROM pending WHERE status = 'OPEN' ORDER BY postedAt ASC")
    fun observeOpen(): Flow<List<PendingEntity>>

    @Query("SELECT COUNT(*) FROM pending WHERE status = 'OPEN'")
    fun observeOpenCount(): Flow<Int>

    @Query("SELECT * FROM pending WHERE id = :id")
    suspend fun getById(id: Long): PendingEntity?

    @Query("SELECT id FROM pending WHERE status = 'OPEN' ORDER BY postedAt ASC LIMIT 1")
    suspend fun firstOpenId(): Long?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entity: PendingEntity): Long

    @Query("UPDATE pending SET status = :status WHERE id = :id")
    suspend fun setStatus(id: Long, status: String)

    /** Housekeeping: resolved rows only matter for de-duplication for a short while. */
    @Query("DELETE FROM pending WHERE status != 'OPEN' AND postedAt < :before")
    suspend fun pruneResolved(before: Long)
}

@Dao
interface PaymentQrDao {
    @Query("SELECT * FROM payment_qr ORDER BY addedAt")
    fun observeAll(): Flow<List<PaymentQrEntity>>

    @Query("SELECT * FROM payment_qr")
    suspend fun all(): List<PaymentQrEntity>

    @Insert
    suspend fun insert(entity: PaymentQrEntity): Long

    @Query("DELETE FROM payment_qr WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface RawNotificationDao {
    @Query("SELECT * FROM raw_notifications ORDER BY postedAt DESC")
    fun observeAll(): Flow<List<RawNotificationEntity>>

    @Insert
    suspend fun insert(entity: RawNotificationEntity)

    /** Keeps only the newest [keep] rows. Cheap: the table never grows past a few hundred. */
    @Query("DELETE FROM raw_notifications WHERE id NOT IN (SELECT id FROM raw_notifications ORDER BY id DESC LIMIT :keep)")
    suspend fun trim(keep: Int)

    @Query("DELETE FROM raw_notifications")
    suspend fun clear()
}

@Dao
interface SplitBillDao {
    @Query("SELECT * FROM split_bills ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<SplitBillEntity>>

    @Query("SELECT * FROM split_bills WHERE id = :id")
    suspend fun get(id: Long): SplitBillEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: SplitBillEntity): Long

    @Query("DELETE FROM split_bills WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT receiptPath FROM split_bills WHERE receiptPath IS NOT NULL")
    suspend fun allReceiptPaths(): List<String>

    /** Oldest first, used once to fill the friends list from splits made before friends existed. */
    @Query("SELECT stateJson FROM split_bills ORDER BY createdAt")
    suspend fun allStates(): List<String>
}

@Dao
interface FriendDao {
    /** Favourites first, then the people you split with most, then the most recent. */
    @Query("SELECT * FROM friends ORDER BY favourite DESC, timesSplit DESC, lastSplitAt DESC, name COLLATE NOCASE")
    fun observeRanked(): Flow<List<FriendEntity>>

    @Query("SELECT * FROM friends WHERE nameKey = :key LIMIT 1")
    suspend fun findByKey(key: String): FriendEntity?

    @Query("SELECT * FROM friends WHERE id = :id")
    suspend fun get(id: Long): FriendEntity?

    @Query("SELECT COUNT(*) FROM friends")
    suspend fun count(): Int

    /** IGNORE so a race between two adds of the same name keeps the first row instead of crashing. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(friend: FriendEntity): Long

    @Update
    suspend fun update(friend: FriendEntity)

    @Query("UPDATE friends SET favourite = :favourite WHERE id = :id")
    suspend fun setFavourite(id: Long, favourite: Boolean)

    @Query("UPDATE friends SET phone = :phone WHERE id = :id")
    suspend fun setPhone(id: Long, phone: String?)

    @Query("UPDATE friends SET timesSplit = timesSplit + 1, lastSplitAt = :now WHERE id IN (:ids)")
    suspend fun recordSplit(ids: List<Long>, now: Long)

    @Query("DELETE FROM friends WHERE id = :id")
    suspend fun delete(id: Long)
}
