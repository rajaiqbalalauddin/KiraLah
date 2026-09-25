package com.buyless.app.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.NorthEast
import androidx.compose.material.icons.rounded.SouthWest
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.buyless.app.data.model.Category
import com.buyless.app.data.model.Direction
import com.buyless.app.ui.components.AppBadge
import com.buyless.app.ui.components.EmptyState
import com.buyless.app.ui.components.Pill
import com.buyless.app.ui.components.appViewModel
import com.buyless.app.ui.components.style
import com.buyless.app.ui.theme.BColors
import com.buyless.app.util.Dates
import java.time.ZoneOffset

private val pickableCategories = listOf(
    Category.FOOD, Category.TRANSPORT, Category.SHOPPING, Category.BILLS, Category.INCOME, Category.OTHER,
)

/** Quick check, manual add and edit, all on one form. */
@Composable
fun EditorScreen(onClose: () -> Unit) {
    val vm = appViewModel { c, handle -> EditorViewModel(c.transactions, c.apps, handle) }
    val sources by vm.sources.collectAsStateWithLifecycle()
    val openCount by vm.openCount.collectAsStateWithLifecycle()
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var pickDate by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(vm.finished) { if (vm.finished) onClose() }

    Column(Modifier.fillMaxSize().imePadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back") }
            Text(
                when (vm.mode) {
                    EditorMode.REVIEW -> "Quick check"
                    EditorMode.MANUAL -> "Add transaction"
                    EditorMode.EDIT -> "Edit transaction"
                },
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.weight(1f),
            )
            if (vm.mode == EditorMode.REVIEW && !vm.queueEmpty && openCount > 0) {
                Pill("${vm.reviewedCount + 1} of ${vm.reviewedCount + openCount}", BColors.AmberSoft, BColors.AmberInk)
                Spacer(Modifier.width(12.dp))
            }
            if (vm.mode == EditorMode.EDIT) {
                IconButton(onClick = { confirmDelete = true }) {
                    Icon(Icons.Rounded.DeleteOutline, contentDescription = "Delete transaction", tint = BColors.Danger)
                }
            }
        }

        if (vm.queueEmpty) {
            EmptyState(Icons.Rounded.DoneAll, "All caught up", "Nothing is waiting for a quick check.", Modifier.padding(top = 48.dp))
            return@Column
        }

        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            vm.raw?.let { raw ->
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(BColors.White)
                        .border(1.dp, BColors.Border, RoundedCornerShape(18.dp))
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AppBadge(raw.packageName, raw.kind, size = 22.dp)
                        Text(raw.label, style = MaterialTheme.typography.titleSmall)
                        Text("· ${raw.timeText}", style = MaterialTheme.typography.bodySmall, color = BColors.Muted)
                    }
                    Text(raw.text, style = MaterialTheme.typography.bodyMedium)
                    raw.foreign?.let { fx ->
                        Text(
                            "Charged in $fx. Enter the ringgit amount from your card app or statement.",
                            style = MaterialTheme.typography.bodySmall,
                            color = BColors.AmberInk,
                            modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(BColors.AmberSoft).padding(horizontal = 10.dp, vertical = 6.dp),
                        )
                    }
                }
            }

            FormCard {
                Label("Amount")
                OutlinedTextField(
                    value = vm.amountText,
                    onValueChange = { v -> vm.amountText = v.filter { it.isDigit() || it == '.' || it == ',' }.take(12) },
                    prefix = { Text("RM ", fontWeight = FontWeight.Bold, color = BColors.Muted) },
                    placeholder = { Text("0.00") },
                    singleLine = true,
                    isError = vm.error != null,
                    supportingText = vm.error?.let { msg -> @Composable { Text(msg) } },
                    textStyle = MaterialTheme.typography.headlineSmall.copy(fontSize = 22.sp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    shape = RoundedCornerShape(14.dp),
                    colors = fieldColors(),
                    modifier = Modifier.fillMaxWidth(),
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DirectionButton("Money out", Icons.Rounded.NorthEast, vm.direction == Direction.OUT, Modifier.weight(1f)) {
                        vm.setDirectionAndFixCategory(Direction.OUT)
                    }
                    DirectionButton("Money in", Icons.Rounded.SouthWest, vm.direction == Direction.IN, Modifier.weight(1f)) {
                        vm.setDirectionAndFixCategory(Direction.IN)
                    }
                }

                Label(if (vm.direction == Direction.IN) "From" else "Merchant or person")
                OutlinedTextField(
                    value = vm.merchant,
                    onValueChange = { vm.merchant = it.take(60) },
                    placeholder = { Text("Who was it?") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    colors = fieldColors(),
                    modifier = Modifier.fillMaxWidth(),
                )

                if (!vm.isInternal) {
                    Label("Category")
                    ChipFlow {
                        pickableCategories.forEach { cat ->
                            val s = cat.style()
                            SelectPill(s.label, selected = vm.category == cat, onClick = { vm.category = cat }) {
                                Icon(s.icon, contentDescription = null, tint = s.fg, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }

                if (vm.mode != EditorMode.REVIEW) {
                    Label("Paid with")
                    ChipFlow {
                        sources.forEach { src ->
                            SelectPill(src.label, selected = vm.sourcePackage == src.packageName, onClick = { vm.sourcePackage = src.packageName }) {
                                if (src.kind != null) {
                                    AppBadge(src.packageName, src.kind, size = 20.dp)
                                } else {
                                    Icon(Icons.Rounded.AccountBalanceWallet, contentDescription = null, tint = BColors.Muted, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }

                    Label("Date")
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 48.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(BColors.Lavender)
                            .clickable(onClickLabel = "Change date") { pickDate = true }
                            .padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Icon(Icons.Rounded.CalendarMonth, contentDescription = null, tint = BColors.Violet)
                        Text(Dates.fullDate(vm.dateMillis), style = MaterialTheme.typography.titleMedium)
                    }
                }

                Row(
                    Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 48.dp)
                        .toggleable(value = vm.isInternal, role = Role.Checkbox, onValueChange = { vm.isInternal = it }),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = vm.isInternal, onCheckedChange = null, colors = CheckboxDefaults.colors(checkedColor = BColors.Violet))
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text("Transfer between my own apps", style = MaterialTheme.typography.titleMedium)
                        Text("Not counted in spending or income", style = MaterialTheme.typography.bodySmall, color = BColors.Muted)
                    }
                }
            }
        }

        Row(Modifier.fillMaxWidth().padding(20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (vm.mode == EditorMode.REVIEW) {
                OutlinedButton(
                    onClick = vm::ignore,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.weight(1f).height(54.dp),
                ) { Text("Ignore", color = BColors.Ink) }
            }
            Button(
                onClick = vm::save,
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = BColors.Violet),
                modifier = Modifier.weight(2f).height(54.dp),
            ) {
                Icon(Icons.Rounded.Check, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Save", style = MaterialTheme.typography.labelLarge)
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this transaction?") },
            text = { Text("It will be removed from your totals. This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    vm.delete()
                }) { Text("Delete", color = BColors.Danger) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }

    if (pickDate) DatePick(vm.dateMillis, onDismiss = { pickDate = false }, onPick = { vm.setDate(it); pickDate = false })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DatePick(currentMillis: Long, onDismiss: () -> Unit, onPick: (Long) -> Unit) {
    // DatePicker works in UTC midnights, so convert the local date both ways.
    val initial = Dates.toDate(currentMillis).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initial,
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean = utcTimeMillis <= System.currentTimeMillis()
        },
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { state.selectedDateMillis?.let(onPick) ?: onDismiss() }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    ) { DatePicker(state = state) }
}

@Composable
private fun FormCard(content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(BColors.White)
            .border(1.dp, BColors.Border, RoundedCornerShape(20.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) { content() }
}

@Composable
private fun Label(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 4.dp))
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChipFlow(content: @Composable () -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { content() }
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    unfocusedContainerColor = BColors.Lavender,
    focusedContainerColor = BColors.Lavender,
    unfocusedBorderColor = BColors.Lavender,
    focusedBorderColor = BColors.Violet,
)

@Composable
private fun DirectionButton(label: String, icon: ImageVector, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Row(
        modifier
            .height(48.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) BColors.Ink else BColors.White)
            .border(2.dp, if (selected) BColors.Ink else BColors.Border, RoundedCornerShape(14.dp))
            .toggleable(value = selected, role = Role.RadioButton, onValueChange = { onClick() }),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = if (selected) BColors.White else BColors.Ink, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.titleSmall, color = if (selected) BColors.White else BColors.Ink)
    }
}

@Composable
private fun SelectPill(label: String, selected: Boolean, onClick: () -> Unit, leading: @Composable () -> Unit) {
    Row(
        Modifier
            .height(40.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(BColors.White)
            .border(2.dp, if (selected) BColors.Violet else BColors.Border, RoundedCornerShape(20.dp))
            .toggleable(value = selected, role = Role.RadioButton, onValueChange = { onClick() })
            .padding(start = 10.dp, end = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        leading()
        Text(label, style = MaterialTheme.typography.titleSmall)
    }
}
