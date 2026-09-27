package com.buyless.app.data.model

// Shared domain types. Kept as plain enums stored by name in Room, so the database stays readable
// and adding a value never needs a migration.

/** Which way money moved. Needed because notifications mix payments and incoming transfers. */
enum class Direction { IN, OUT }

/** Spending buckets shown as coloured icons. TRANSFER is reserved for moves between your own apps. */
enum class Category { FOOD, TRANSPORT, SHOPPING, BILLS, INCOME, TRANSFER, OTHER }

/**
 * How a category is written in the transactions table. Built-ins use their enum name ("FOOD");
 * the user's own categories use "custom:<id>". One text column holds both, so adding custom
 * categories needed no change to transactions, totals or the notification parser.
 */
object CategoryKeys {
    private const val CUSTOM_PREFIX = "custom:"

    fun custom(id: Long): String = CUSTOM_PREFIX + id

    /** The custom category id in a key, or null for built-in keys. */
    fun customId(key: String): Long? = if (key.startsWith(CUSTOM_PREFIX)) key.removePrefix(CUSTOM_PREFIX).toLongOrNull() else null

    /** The built-in category for a key, or null when the key is a custom one (or unknown). */
    fun builtIn(key: String): Category? = runCatching { Category.valueOf(key) }.getOrNull()
}

/** Rough type of a watched app, only used to pick a fallback icon when the real one is unavailable. */
enum class AppKind { BANK, WALLET }

/** Where a transaction came from, so auto-recorded rows can be told apart from ones the user typed. */
enum class Origin { AUTO, REVIEWED, MANUAL }

/** Lifecycle of a notification waiting in Quick check. Resolved rows are kept for de-duplication. */
enum class PendingStatus { OPEN, SAVED, IGNORED }

/**
 * Number of correct guesses an app needs before Buyless records its notifications without asking.
 * Every bank words alerts differently, so each app earns trust instead of being assumed right.
 */
const val LEARNING_THRESHOLD = 3

/** Everything needed to create or update a transaction, independent of how it was entered. */
data class TransactionDraft(
    val amountSen: Long,
    val direction: Direction,
    val merchant: String,
    /** Category key as stored: a built-in name ("FOOD") or a custom key ("custom:3"). See CategoryKeys. */
    val category: String,
    val sourcePackage: String,
    val sourceLabel: String,
    val timestamp: Long,
    val isInternal: Boolean = false,
    val rawText: String? = null,
)

/** Package name used for transactions typed in by hand that did not come from any app. */
const val MANUAL_SOURCE = "manual"
