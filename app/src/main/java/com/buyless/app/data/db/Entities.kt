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
