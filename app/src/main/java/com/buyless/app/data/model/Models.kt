package com.buyless.app.data.model

// Shared domain types. Kept as plain enums stored by name in Room, so the database stays readable
// and adding a value never needs a migration.

/** Which way money moved. Needed because notifications mix payments and incoming transfers. */
enum class Direction { IN, OUT }

/** Spending buckets shown as coloured icons. TRANSFER is reserved for moves between your own apps. */
enum class Category { FOOD, TRANSPORT, SHOPPING, BILLS, INCOME, TRANSFER, OTHER }

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
    val category: Category,
    val sourcePackage: String,
    val sourceLabel: String,
    val timestamp: Long,
    val isInternal: Boolean = false,
    val rawText: String? = null,
)

/** Package name used for transactions typed in by hand that did not come from any app. */
const val MANUAL_SOURCE = "manual"
