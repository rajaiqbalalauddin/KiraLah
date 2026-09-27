package com.buyless.app.data.model

// Types for spending limits. Stored by name in Room, like the other enums, so the table stays readable.

/** How long a limit's window is. Weeks start on Monday, the usual choice in Malaysia. */
enum class LimitPeriod { DAY, WEEK, MONTH }

/**
 * Category key meaning "every category". Stored in the same column as "FOOD" or "custom:3", so one
 * unique index (category + period) covers both kinds of limit. Never a real category name.
 */
const val ALL_CATEGORIES = "ALL"
