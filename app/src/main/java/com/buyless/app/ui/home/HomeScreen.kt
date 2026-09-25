package com.buyless.app.ui.home

import com.buyless.app.ui.components.KiraLahWordmark
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.NotificationsNone
import androidx.compose.material.icons.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.SouthWest
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.KeyboardType
import com.buyless.app.ui.components.SwipeToDelete
import com.buyless.app.util.Money
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.buyless.app.ui.components.AppBadge
import com.buyless.app.ui.components.AppTileUi
import com.buyless.app.ui.components.EmptyState
import com.buyless.app.ui.components.IconDot
import com.buyless.app.ui.components.MonthSwitcher
import com.buyless.app.ui.components.SectionHeader
import com.buyless.app.ui.components.TransactionRow
import com.buyless.app.ui.components.appViewModel
import com.buyless.app.ui.theme.BColors

/** Dashboard: how much went out this month, where it went, and what needs a quick check. */
@Composable
fun HomeScreen(
    onOpenReview: () -> Unit,
    onOpenActivity: () -> Unit,
    onOpenTransaction: (Long) -> Unit,
    onAddManual: () -> Unit,
    onAddApp: () -> Unit,
) {
    val vm = appViewModel { c, _ -> HomeViewModel(c.transactions, c.apps, c.selectedMonth) }
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var editingBalance by rememberSaveable { mutableStateOf<String?>(null) }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item(key = "header", contentType = "header") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    KiraLahWordmark(style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                    MonthSwitcher(state.monthName, state.canGoNext, vm::previousMonth, vm::nextMonth)
                    Spacer(Modifier.width(4.dp))
                    IconButton(onClick = onOpenReview) {
                        BadgedBox(badge = { if (state.pendingCount > 0) Badge(containerColor = BColors.Coral) }) {
                            Icon(Icons.Rounded.NotificationsNone, contentDescription = "Quick check, ${state.pendingCount} waiting")
                        }
                    }
                }
            }

            item(key = "hero", contentType = "hero") { HeroCard(state) }

            item(key = "apps", contentType = "apps") {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    SectionHeader("Your apps") {
                        val total = state.totalBalanceText
                        if (total != null) {
                            Text("Total $total", style = MaterialTheme.typography.titleSmall, color = BColors.Violet)
                        } else {
                            Text("Tap an app to set its balance", style = MaterialTheme.typography.bodySmall, color = BColors.Muted)
                        }
                    }
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(state.apps, key = { it.packageName }) { tile -> AppTile(tile, onClick = { editingBalance = tile.packageName }) }
                        item(key = "add") { AddAppTile(onAddApp) }
                    }
                }
            }

            if (state.pendingCount > 0) {
                item(key = "review", contentType = "banner") { ReviewBanner(state.pendingCount, onOpenReview) }
            }

            item(key = "recentHeader", contentType = "header") {
                SectionHeader("Recent") {
                    TextButton(onClick = onOpenActivity) { Text("See all") }
                }
            }

            if (!state.loading && state.recent.isEmpty()) {
                item(key = "empty", contentType = "empty") {
                    EmptyState(
                        Icons.Rounded.ReceiptLong,
                        "Nothing recorded yet",
                        "Payments from your watched apps will show up here. Tap + to add one by hand.",
                    )
                }
            }

            items(state.recent, key = { it.id }, contentType = { "txn" }) { item ->
                SwipeToDelete(
                    surface = BColors.Lavender,
                    onDelete = {
                        vm.delete(item.id) { removed ->
                            scope.launch {
                                val result = snackbar.showSnackbar("Deleted ${item.title}", actionLabel = "Undo", duration = SnackbarDuration.Short)
                                if (result == SnackbarResult.ActionPerformed) vm.restore(removed)
                            }
                        }
                    },
                ) {
                    TransactionRow(item, onClick = { onOpenTransaction(item.id) })
                }
            }
        }

        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(start = 16.dp, end = 88.dp, bottom = 16.dp))

        FloatingActionButton(
            onClick = onAddManual,
            containerColor = BColors.Ink,
            contentColor = BColors.White,
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp),
        ) {
            Icon(Icons.Rounded.Add, contentDescription = "Add a transaction by hand")
        }
    }

    editingBalance?.let { pkg ->
        state.apps.firstOrNull { it.packageName == pkg }?.let { tile ->
            BalanceDialog(
                tile = tile,
                onDismiss = { editingBalance = null },
                onSave = { input ->
                    vm.setBalance(pkg, input)
                    editingBalance = null
                },
            )
        }
    }
}

/** Violet summary card. Decorative circles are drawn in drawBehind, which costs no extra layout nodes. */
@Composable
private fun HeroCard(state: HomeUiState) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(BColors.Violet)
            .drawBehind {
                drawCircle(BColors.VioletRing, radius = size.width * 0.26f, center = Offset(size.width * 0.92f, size.height * 0.05f))
                drawCircle(BColors.Yellow, radius = size.width * 0.12f, center = Offset(size.width * 0.8f, size.height * 1.02f))
            }
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Spent in ${state.monthName}", style = MaterialTheme.typography.bodyMedium, color = BColors.VioletOnDark)
            Text(state.spentText, style = MaterialTheme.typography.displaySmall, color = BColors.White)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            HeroStat(Icons.Rounded.SouthWest, BColors.Green, "Money in", state.inText, Modifier.weight(1f))
            HeroStat(
                Icons.Rounded.AccountBalanceWallet,
                if (state.isOver) BColors.CoralInk else BColors.Violet,
                if (state.isOver) "Over by" else "Left over",
                state.leftText,
                Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun HeroStat(icon: ImageVector, iconTint: Color, label: String, value: String, modifier: Modifier) {
    Row(
        modifier.clip(RoundedCornerShape(14.dp)).background(BColors.VioletDark).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        IconDot(icon, BColors.White, iconTint, size = 30.dp, iconSize = 16.dp)
        Column {
            Text(label, style = MaterialTheme.typography.bodySmall, color = BColors.VioletOnDark)
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = BColors.White, maxLines = 1)
        }
    }
}

/** App tile: balance first (what you have), then this month's spending. Tap to set the balance. */
@Composable
private fun AppTile(tile: AppTileUi, onClick: () -> Unit) {
    Column(
        Modifier
            .width(136.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(BColors.White)
            .border(1.dp, BColors.Border, RoundedCornerShape(18.dp))
            .clickable(onClickLabel = "Set ${tile.label} balance", onClick = onClick)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        AppBadge(tile.packageName, tile.kind, size = 36.dp)
        Column {
            Text(tile.label, style = MaterialTheme.typography.titleSmall, color = BColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val balance = tile.balanceText
            if (balance != null) {
                Text(balance, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1)
            } else {
                Text("Set balance", style = MaterialTheme.typography.titleSmall, color = BColors.Violet)
            }
            Text("Spent ${tile.spentText}", style = MaterialTheme.typography.bodySmall, color = BColors.Muted, maxLines = 1)
        }
    }
}

/** Type what the bank app shows now. Nothing is transferred and no transaction is added. */
@Composable
private fun BalanceDialog(tile: AppTileUi, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var text by rememberSaveable { mutableStateOf(tile.balanceSen?.let { Money.toInput(kotlin.math.abs(it)) } ?: "") }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { AppBadge(tile.packageName, tile.kind, size = 40.dp) },
        title = { Text("${tile.label} balance") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Enter what ${tile.label} shows right now. KiraLah keeps it up to date from your alerts. No money moves and nothing is added to your spending.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = BColors.Muted,
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = { v -> text = v.filter { it.isDigit() || it == '.' }.take(12) },
                    prefix = { Text("RM ") },
                    placeholder = { Text("0.00") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    shape = RoundedCornerShape(14.dp),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSave(text) }) { Text("Save") } },
        dismissButton = {
            Row {
                if (tile.balanceSen != null) TextButton(onClick = { onSave("") }) { Text("Clear", color = BColors.Danger) }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}

@Composable
private fun AddAppTile(onClick: () -> Unit) {
    Column(
        Modifier
            .width(128.dp)
            .heightIn(min = 128.dp)
            .clip(RoundedCornerShape(18.dp))
            .border(2.dp, BColors.VioletSoft, RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        IconDot(Icons.Rounded.Add, BColors.VioletSoft, BColors.Violet, size = 36.dp)
        Spacer(Modifier.height(8.dp))
        Text("Add app", style = MaterialTheme.typography.titleSmall, color = BColors.Violet)
    }
}

@Composable
private fun ReviewBanner(count: Int, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(BColors.AmberSoft)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier.size(32.dp).clip(RoundedCornerShape(10.dp)).background(BColors.Yellow),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.WarningAmber, contentDescription = null, tint = BColors.YellowInk, modifier = Modifier.size(18.dp))
        }
        Text(
            if (count == 1) "1 notification needs a quick check" else "$count notifications need a quick check",
            style = MaterialTheme.typography.titleMedium,
            color = BColors.AmberInk,
            modifier = Modifier.weight(1f),
        )
        Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = BColors.AmberInk)
    }
}

