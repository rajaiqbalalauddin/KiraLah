package com.buyless.app.ui.activity

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.NorthEast
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material.icons.rounded.SouthWest
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import com.buyless.app.ui.components.SwipeToDelete
import kotlinx.coroutines.launch
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.buyless.app.ui.components.AppBadge
import com.buyless.app.ui.components.EmptyState
import com.buyless.app.ui.components.IconDot
import com.buyless.app.ui.components.MonthSwitcher
import com.buyless.app.ui.components.TransactionRow
import com.buyless.app.ui.components.appViewModel
import com.buyless.app.ui.theme.BColors

/** Full list for the month, grouped by day, filterable by app and searchable by merchant. */
@Composable
fun ActivityScreen(onOpenTransaction: (Long) -> Unit) {
    val vm = appViewModel { c, _ -> ActivityViewModel(c.transactions, c.apps, c.selectedMonth) }
    val state by vm.state.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    var searching by rememberSaveable { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Box(Modifier.fillMaxSize()) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "header") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Activity", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                MonthSwitcher(state.monthName, state.canGoNext, vm::previousMonth, vm::nextMonth)
                IconButton(onClick = {
                    if (searching) vm.setQuery("")
                    searching = !searching
                }) {
                    Icon(
                        if (searching) Icons.Rounded.Close else Icons.Rounded.Search,
                        contentDescription = if (searching) "Close search" else "Search transactions",
                    )
                }
            }
        }

        if (searching) {
            item(key = "search") {
                OutlinedTextField(
                    value = query,
                    onValueChange = vm::setQuery,
                    placeholder = { Text("Search merchant or app") },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedContainerColor = BColors.White,
                        focusedContainerColor = BColors.White,
                        unfocusedBorderColor = BColors.Border,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        item(key = "summary") {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SummaryCard(Icons.Rounded.NorthEast, BColors.CoralSoft, BColors.CoralInk, "Out", state.outText, Modifier.weight(1f))
                SummaryCard(Icons.Rounded.SouthWest, BColors.GreenSoft, BColors.Green, "In", state.inText, Modifier.weight(1f))
            }
        }

        item(key = "chips") {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(state.chips, key = { it.packageName ?: "all" }) { chip ->
                    FilterPill(chip, onClick = { vm.selectApp(chip.packageName) })
                }
            }
        }

        if (!state.loading && state.sections.isEmpty()) {
            item(key = "empty") {
                if (state.hasFilter) {
                    EmptyState(Icons.Rounded.SearchOff, "No matches", "Try another app or search term.")
                } else {
                    EmptyState(Icons.Rounded.Search, "No activity this month", "Anything recorded or added by hand will appear here.")
                }
            }
        }

        state.sections.forEach { section ->
            item(key = "h${section.epochDay}", contentType = "dayHeader") {
                Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                    Text(section.label, style = MaterialTheme.typography.titleSmall, color = BColors.Muted, modifier = Modifier.weight(1f))
                    Text(section.totalText, style = MaterialTheme.typography.titleSmall, color = BColors.Muted)
                }
            }
            item(key = "d${section.epochDay}", contentType = "dayCard") {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(BColors.White)
                        .border(1.dp, BColors.Border, RoundedCornerShape(18.dp))
                        .padding(horizontal = 14.dp, vertical = 4.dp),
                ) {
                    section.items.forEach { item ->
                        // key() ties each swipe state to its transaction, not to its position in the list.
                        key(item.id) {
                            SwipeToDelete(onDelete = {
                                vm.delete(item.id) { removed ->
                                    scope.launch {
                                        val result = snackbar.showSnackbar("Deleted ${item.title}", actionLabel = "Undo", duration = SnackbarDuration.Short)
                                        if (result == SnackbarResult.ActionPerformed) vm.restore(removed)
                                    }
                                }
                            }) {
                                TransactionRow(item, onClick = { onOpenTransaction(item.id) })
                            }
                        }
                    }
                }
            }
        }
    }
    SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(16.dp))
    }
}

@Composable
private fun SummaryCard(icon: ImageVector, bg: Color, fg: Color, label: String, value: String, modifier: Modifier) {
    Row(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(BColors.White)
            .border(1.dp, BColors.Border, RoundedCornerShape(16.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        IconDot(icon, bg, fg, size = 34.dp)
        Column {
            Text(label, style = MaterialTheme.typography.bodySmall, color = BColors.Muted)
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1)
        }
    }
}

@Composable
private fun FilterPill(chip: FilterChipUi, onClick: () -> Unit) {
    val bg = if (chip.selected) BColors.Ink else BColors.White
    val fg = if (chip.selected) BColors.White else BColors.Ink
    Row(
        Modifier
            .height(40.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(bg)
            .border(1.dp, if (chip.selected) BColors.Ink else BColors.Border, RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(start = if (chip.kind != null) 6.dp else 16.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (chip.packageName != null && chip.kind != null) {
            AppBadge(chip.packageName, chip.kind, size = 28.dp)
        } else {
            Box(Modifier.size(8.dp).clip(CircleShape).background(if (chip.selected) BColors.White else BColors.Violet))
        }
        Text(chip.label, style = MaterialTheme.typography.titleSmall, color = fg)
    }
}
