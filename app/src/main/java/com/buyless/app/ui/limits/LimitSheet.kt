package com.buyless.app.ui.limits

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.buyless.app.data.model.ALL_CATEGORIES
import com.buyless.app.data.model.Category
import com.buyless.app.data.model.LimitPeriod
import com.buyless.app.limits.Limit
import com.buyless.app.limits.LimitNotifier
import com.buyless.app.ui.categories.LocalCategoryCatalog
import com.buyless.app.ui.theme.BColors
import com.buyless.app.util.Money

/**
 * Add or change one limit: how often (daily, weekly, monthly), which category (or all), and how
 * much. [taken] holds the category + period pairs other limits already use, so the sheet can say
 * so up front instead of silently replacing one.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun LimitSheet(
    existing: Limit?,
    taken: Set<Pair<String, LimitPeriod>>,
    onDismiss: () -> Unit,
    onSave: (period: LimitPeriod, categoryKey: String, amountSen: Long) -> Unit,
    onDelete: (() -> Unit)?,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val catalog = LocalCategoryCatalog.current
    var period by rememberSaveable(existing?.id) { mutableStateOf(existing?.period ?: LimitPeriod.MONTH) }
    var categoryKey by rememberSaveable(existing?.id) { mutableStateOf(existing?.categoryKey ?: ALL_CATEGORIES) }
    var amountText by rememberSaveable(existing?.id) { mutableStateOf(existing?.let { Money.toInput(it.amountSen) } ?: "") }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }

    val amountSen = Money.parse(amountText)
    val clash = (categoryKey to period) in taken
    // Income is money in, so a spending limit on it would never move. Transfer is never picked by hand.
    val options = listOf(ALL_CATEGORIES) + catalog.pickable.map { it.key }.filter { it != Category.INCOME.name }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = BColors.Surface) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 16.dp).imePadding(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (existing == null) "New limit" else "Edit limit", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                if (onDelete != null) {
                    TextButton(onClick = { confirmDelete = true }) {
                        Icon(Icons.Rounded.DeleteOutline, contentDescription = null, tint = BColors.Danger)
                        Spacer(Modifier.size(4.dp))
                        Text("Delete", color = BColors.Danger)
                    }
                }
            }

            Text("How often", style = MaterialTheme.typography.titleSmall)
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(BColors.Lavender).padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                LimitPeriod.entries.forEach { option ->
                    val chosen = option == period
                    Row(
                        Modifier
                            .weight(1f)
                            .height(40.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (chosen) BColors.Surface else Color.Transparent)
                            .selectable(selected = chosen, role = Role.RadioButton, onClick = { period = option }),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            LimitNotifier.periodAdjective(option),
                            style = MaterialTheme.typography.titleSmall,
                            color = if (chosen) BColors.Ink else BColors.Muted,
                        )
                    }
                }
            }

            Text("Category", style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                options.forEach { key ->
                    val s = limitStyle(key, catalog)
                    val chosen = key == categoryKey
                    Row(
                        Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(if (chosen) s.bg else BColors.Lavender)
                            .border(1.5.dp, if (chosen) s.fg else Color.Transparent, RoundedCornerShape(20.dp))
                            .selectable(selected = chosen, role = Role.RadioButton, onClick = { categoryKey = key })
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(s.icon, contentDescription = null, tint = s.fg, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(s.label, style = MaterialTheme.typography.labelLarge, color = BColors.Ink)
                    }
                }
            }

            Text("Limit", style = MaterialTheme.typography.titleSmall)
            OutlinedTextField(
                value = amountText,
                onValueChange = { amountText = it.filter { c -> c.isDigit() || c == '.' }.take(10) },
                prefix = { Text("RM ") },
                placeholder = { Text("600.00") },
                singleLine = true,
                isError = amountText.isNotEmpty() && amountSen == null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedContainerColor = BColors.Lavender,
                    focusedContainerColor = BColors.Lavender,
                    unfocusedBorderColor = BColors.Lavender,
                    focusedBorderColor = BColors.Violet,
                ),
                modifier = Modifier.fillMaxWidth(),
            )

            if (clash) {
                Text(
                    "You already have a ${LimitNotifier.periodAdjective(period).lowercase()} limit for ${limitStyle(categoryKey, catalog).label}. Edit that one instead.",
                    style = MaterialTheme.typography.bodySmall,
                    color = BColors.Danger,
                )
            }

            Button(
                onClick = { amountSen?.let { onSave(period, categoryKey, it) } },
                enabled = amountSen != null && !clash,
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = BColors.Violet, contentColor = BColors.OnColor),
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Text(if (existing == null) "Add limit" else "Save changes") }
        }
    }

    if (confirmDelete && onDelete != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this limit?") },
            text = { Text("Your transactions stay as they are. Only the limit and its reminders go.") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete() }) { Text("Delete", color = BColors.Danger) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}
