package com.buyless.app.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

// Room tables. Indexes are chosen for the queries the app actually runs:
// month ranges (timestamp), per-app totals (sourcePackage) and duplicate checks (dedupKey).

/**
 * One recorded money movement. Amount is stored in sen (1/100 ringgit) as a Long so sums are exact.
 * sourceLabel is copied in (denormalised) so lists never need a join, even after an app is removed.
 */
@Entity(
    tableName = "transactions",
    indices = [
        Index("timestamp"),
        Index(value = ["sourcePackage", "timestamp"]),
        Index(value = ["dedupKey"], unique = true),
    ],
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val amountSen: Long,
    val direction: String,
    val merchant: String,
    val category: String,
    val sourcePackage: String,
    val sourceLabel: String,
    val timestamp: Long,
    val isInternal: Boolean = false,
    val linkedId: Long? = null,
    val origin: String,
    val rawText: String? = null,
    val dedupKey: String? = null,
)

/**
 * An app the user asked Buyless to watch. confirmedCount tracks how many parser guesses the user
 * accepted unchanged; once it reaches LEARNING_THRESHOLD the app is recorded automatically.
 */
@Entity(tableName = "watched_apps")
data class WatchedAppEntity(
    @PrimaryKey val packageName: String,
    val label: String,
    val kind: String,
    val confirmedCount: Int = 0,
    val addedAt: Long,
    /** Balance the user typed in, and when. Live balance = this + money in - money out since then. */
    val balanceSen: Long? = null,
    val balanceSetAt: Long? = null,
)

/**
 * A notification that needs a human look (unparsed, or from an app still learning).
 * Rows are never deleted on save or ignore, only marked, so the same notification seen again
 * (for example after the listener reconnects) is recognised by dedupKey and skipped.
 */
@Entity(
    tableName = "pending",
    indices = [
        Index(value = ["dedupKey"], unique = true),
        Index(value = ["status", "postedAt"]),
    ],
)
data class PendingEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sourcePackage: String,
    val sourceLabel: String,
    val text: String,
    val postedAt: Long,
    val dedupKey: String,
    val status: String,
    val guessAmountSen: Long? = null,
    val guessDirection: String? = null,
    val guessMerchant: String? = null,
    val guessCategory: String? = null,
)

/** Live balance of one app, worked out in SQL from the starting balance plus later transactions. */
data class AppBalance(val packageName: String, val balanceSen: Long)

/** One month in the Recap archive. ym is "2026-09". */
data class MonthSummary(val ym: String, val outSen: Long, val count: Int)

/** Result row of the per-app spending query. */
data class AppTotal(
    val sourcePackage: String,
    val outSen: Long,
    val count: Int,
)

/**
 * A payment QR the user saved (for example their MAE or TNG DuitNow QR). The image itself lives in
 * app storage at filePath; only the path is in the database, so rows stay tiny.
 */
@Entity(tableName = "payment_qr")
data class PaymentQrEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val label: String,
    val filePath: String,
    val addedAt: Long,
)

/**
 * A raw notification from a watched app, kept so new bank formats can be studied and turned into
 * exact templates. Capped to the latest few hundred rows, never includes OTP / TAC messages, and
 * never leaves the phone unless the user shares it from Settings.
 */
@Entity(tableName = "raw_notifications", indices = [Index("postedAt")])
data class RawNotificationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sourcePackage: String,
    val sourceLabel: String,
    val title: String?,
    val body: String,
    val postedAt: Long,
    val matched: Boolean,
)

/**
 * A saved bill split. Summary columns (title, total, paid count) drive the history list without
 * parsing anything; the full board (people, items, owners, charges, who paid) is one JSON blob,
 * because it is always loaded and saved as a whole.
 */
@Entity(tableName = "split_bills", indices = [Index("updatedAt")])
data class SplitBillEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
    val totalSen: Long,
    val peopleCount: Int,
    val paidCount: Int,
    val receiptPath: String?,
    val stateJson: String,
)

/**
 * Someone you split bills with. Saved automatically the first time they join a bill, so the people
 * drawer fills itself. nameKey (lowercase, single spaces) is unique, so "Aina" is never saved twice.
 * phone is digits with country code ("60123456789"), ready for WhatsApp.
 */
@Entity(tableName = "friends", indices = [Index(value = ["nameKey"], unique = true)])
data class FriendEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val nameKey: String,
    val phone: String? = null,
    val colorIndex: Int,
    val favourite: Boolean = false,
    val timesSplit: Int = 0,
    val lastSplitAt: Long = 0,
    val createdAt: Long,
)
