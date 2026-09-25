package com.buyless.app.ui.components

import androidx.compose.runtime.Immutable
import com.buyless.app.data.db.TransactionEntity
import com.buyless.app.data.model.AppKind
import com.buyless.app.data.model.Category
import com.buyless.app.data.model.Direction
import com.buyless.app.util.Dates
import com.buyless.app.util.Money
import java.time.LocalDate

// Screen-ready models. Strings are formatted in the ViewModel (off the main thread) instead of inside
// composables, and @Immutable lets Compose skip rows whose data did not change.

enum class AmountTone { OUT, IN, NEUTRAL }

@Immutable
data class TxnUi(
    val id: Long,
    val title: String,
    val subtitle: String,
    val amountText: String,
    val tone: AmountTone,
    val category: Category,
    val isInternal: Boolean,
)

@Immutable
data class AppTileUi(
    val packageName: String,
    val label: String,
    val kind: AppKind,
    val spentText: String,
    val countText: String,
    /** Live balance, or null when the user has not set a starting balance for this app yet. */
    val balanceText: String? = null,
    val balanceSen: Long? = null,
)

fun TransactionEntity.toUi(today: LocalDate): TxnUi {
    val incoming = direction == Direction.IN.name
    val date = Dates.toDate(timestamp)
    return TxnUi(
        id = id,
        title = merchant.ifBlank { sourceLabel },
        subtitle = "$sourceLabel · ${Dates.relativeDay(date, today)}, ${Dates.timeOf(timestamp)}",
        amountText = Money.formatSigned(amountSen, incoming),
        tone = when {
            isInternal -> AmountTone.NEUTRAL
            incoming -> AmountTone.IN
            else -> AmountTone.OUT
        },
        category = runCatching { Category.valueOf(category) }.getOrDefault(Category.OTHER),
        isInternal = isInternal,
    )
}

fun kindOf(name: String): AppKind = runCatching { AppKind.valueOf(name) }.getOrDefault(AppKind.WALLET)
